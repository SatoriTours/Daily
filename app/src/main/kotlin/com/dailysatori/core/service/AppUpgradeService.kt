package com.dailysatori.core.service

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import co.touchlab.kermit.Logger
import com.dailysatori.service.diagnostics.*
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.serialization.json.*
import java.io.File
import java.net.URI
import kotlinx.coroutines.CancellationException

data class AppRelease(
    val version: String,
    val releaseUrl: String,
    val apkAsset: ReleaseAsset?,
    val versionCode: Int? = null,
    val channel: UpdateChannel = UpdateChannel.STABLE,
    val schemaVersion: Long? = null,
    val sha256: String? = null,
    val size: Long? = null,
)

data class ReleaseAsset(
    val name: String,
    val downloadUrl: String,
)

class AppUpgradeService(private val client: HttpClient) {
    private var reportedDownloadId: Long? = null
    private var downloadTrace: DiagnosticTrace? = null
    private var downloadRequestId: String? = null
    private val log = Logger.withTag("Upgrade")
    private var pendingDownload: ApkDownload? = null

    companion object {
        fun isNewerVersion(latestVersion: String, currentVersion: String): Boolean {
            val latest = versionParts(latestVersion)
            val current = versionParts(currentVersion)
            val size = maxOf(latest.size, current.size)
            for (index in 0 until size) {
                val latestPart = latest.getOrElse(index) { 0 }
                val currentPart = current.getOrElse(index) { 0 }
                if (latestPart != currentPart) return latestPart > currentPart
            }
            return false
        }

        fun findApkAsset(assets: List<ReleaseAsset>): ReleaseAsset? =
            assets.firstOrNull {
                it.name.endsWith(".apk", ignoreCase = true) && isTrustedApkDownloadUrl(it.downloadUrl)
            }

        fun isTrustedApkDownloadUrl(url: String): Boolean {
            val uri = runCatching { URI(url) }.getOrNull() ?: return false
            if (uri.scheme != "https") return false
            return when (uri.host?.lowercase()) {
                "github.com", "objects.githubusercontent.com" -> true
                else -> false
            }
        }

        private fun versionParts(version: String): List<Int> =
            version.trim()
                .removePrefix("v")
                .removePrefix("V")
                .substringBefore("-")
                .split('.')
                .map { it.toIntOrNull() ?: 0 }
    }

    suspend fun checkForUpdate(currentVersion: String, suppressErrors: Boolean = true): AppRelease? {
        return try {
            val response = client.get("https://api.github.com/repos/SatoriTours/Daily/releases/latest")
            val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            parseRelease(json)?.takeIf { isNewerVersion(it.version, currentVersion) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.e(e) { "Failed to check for updates" }
            if (!suppressErrors) throw e
            null
        }
    }

    suspend fun checkChannelUpdate(channel: UpdateChannel, installed: InstalledBuild): UpdateCheck {
        val path = if (channel == UpdateChannel.STABLE) "latest" else "tags/commit-build"
        val response = client.get("https://api.github.com/repos/SatoriTours/Daily/releases/$path")
        if (response.status.value == 404) return UpdateCheck(message = "${channel.label}暂未发布安装包")
        check(response.status.value == 200) { "检查更新失败（HTTP ${response.status.value}）" }
        val release = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        val metadataUrl = release["assets"]?.jsonArray.orEmpty().mapNotNull { parseAsset(it) }
            .firstOrNull { it.name == "update.json" }?.downloadUrl
            ?: return UpdateCheck(message = "已选择${channel.label}，等待提供渠道更新信息的新版本")
        require(isRepositoryReleaseUrl(metadataUrl)) { "更新信息下载地址不可信" }
        val metadata = client.get(metadataUrl)
        check(metadata.status.value == 200) { "获取更新信息失败" }
        val text = metadata.bodyAsText()
        require(text.length <= 65_536) { "更新信息格式异常" }
        val target = parseUpdateManifest(text, release["html_url"]?.jsonPrimitive?.content.orEmpty())
        return evaluateChannelUpdate(installed, target, channel)
    }

    suspend fun downloadApk(
        context: Context,
        release: AppRelease,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit = { _, _ -> },
    ): ApkDownload {
        val asset = release.apkAsset ?: throw IllegalStateException("新版本没有可下载的 APK")
        val file = apkFile(context, release.version)
        val bytes = client.get(asset.downloadUrl) {
            onDownload { bytesSentTotal, contentLength ->
                onProgress(bytesSentTotal, contentLength ?: -1L)
            }
        }.bodyAsBytes()
        require(release.size == null || release.size == bytes.size.toLong()) { "安装包大小不匹配，请重新检查更新" }
        verifyApkChecksum(bytes, release.sha256)
        file.parentFile?.mkdirs()
        file.writeBytes(bytes)
        return ApkDownload(id = 0L, filePath = file.absolutePath).also { pendingDownload = it }
    }

    fun enqueueApkDownload(context: Context, release: AppRelease): ApkDownload {
        val asset = release.apkAsset ?: throw IllegalStateException("新版本没有可下载的 APK")
        val fileName = apkFileName(release.version)
        val request = DownloadManager.Request(Uri.parse(asset.downloadUrl))
            .setTitle("Daily Satori ${release.version}")
            .setDescription("正在下载更新")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName)
        val manager = context.getSystemService(DownloadManager::class.java)
        val id = manager.enqueue(request)
        downloadTrace = DiagnosticLog.currentTrace()
        downloadRequestId = DiagnosticLog.newId()
        reportedDownloadId = null
        DiagnosticLog.registerCoverage(DiagnosticSource.DOWNLOAD, DiagnosticCoverage.LIFECYCLE_ONLY)
        DiagnosticLog.diagnostics.emit(
            DiagnosticCode.OPERATION_START, DiagnosticSource.DOWNLOAD,
            fields = mapOf("url" to asset.downloadUrl), trace = downloadTrace, requestId = downloadRequestId)
        val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)
        return ApkDownload(id, file.absolutePath).also { pendingDownload = it }
    }

    fun createInstallIntentForDownload(context: Context, completedId: Long): Intent? {
        val download = pendingDownload?.takeIf { it.id == completedId } ?: return null
        return createInstallIntentForFilePath(context, download.filePath)
    }

    fun hasPendingDownload(completedId: Long): Boolean = pendingDownload?.id == completedId

    fun createPendingInstallIntent(context: Context): Intent? {
        val download = pendingDownload ?: return null
        return createInstallIntentForFilePath(context, download.filePath)
    }

    fun createInstallIntentForFilePathIfExists(context: Context, filePath: String): Intent? =
        createInstallIntentForFilePath(context, filePath)

    fun clearPendingDownload() {
        pendingDownload = null
    }

    fun queryDownloadProgress(context: Context, downloadId: Long): ApkDownloadProgress? {
        val manager = context.getSystemService(DownloadManager::class.java)
        val query = DownloadManager.Query().setFilterById(downloadId)
        manager.query(query)?.use { cursor ->
            if (!cursor.moveToFirst()) return null
            return ApkDownloadProgress(
                status = cursor.longValue(DownloadManager.COLUMN_STATUS).toInt(),
                downloadedBytes = cursor.longValue(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR),
                totalBytes = cursor.longValue(DownloadManager.COLUMN_TOTAL_SIZE_BYTES),
            ).also { progress ->
                if (progress.status in setOf(DownloadManager.STATUS_SUCCESSFUL, DownloadManager.STATUS_FAILED) && reportedDownloadId != downloadId) {
                    reportedDownloadId = downloadId
                    DiagnosticLog.diagnostics.emit(
                        if (progress.status == DownloadManager.STATUS_SUCCESSFUL) DiagnosticCode.OPERATION_END
                        else DiagnosticCode.OPERATION_FAILED, DiagnosticSource.DOWNLOAD,
                        if (progress.status == DownloadManager.STATUS_SUCCESSFUL) DiagnosticLevel.INFO else DiagnosticLevel.ERROR,
                        fields = mapOf("bytes" to progress.downloadedBytes.toString(), "reason" to cursor.longValue(DownloadManager.COLUMN_REASON).toString()),
                        trace = downloadTrace, requestId = downloadRequestId)
                }
            }
        }
        return null
    }

    private fun android.database.Cursor.longValue(columnName: String): Long {
        val index = getColumnIndex(columnName)
        return if (index >= 0) getLong(index) else -1L
    }

    fun createInstallIntent(context: Context, file: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private fun createInstallIntentForFilePath(context: Context, filePath: String): Intent? {
        val file = File(filePath)
        if (!file.exists()) return null
        return createInstallIntent(context, file)
    }

    private fun apkFile(context: Context, version: String): File =
        File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), apkFileName(version))

    private fun apkFileName(version: String): String =
        "DailySatori-${version.removePrefix("v").removePrefix("V")}.apk"

    private fun parseRelease(json: JsonObject): AppRelease? {
        val tagName = json["tag_name"]?.jsonPrimitive?.content ?: return null
        val releaseUrl = json["html_url"]?.jsonPrimitive?.content ?: ""
        val assets = json["assets"]?.jsonArray.orEmpty().mapNotNull { parseAsset(it) }
        return AppRelease(
            version = tagName,
            releaseUrl = releaseUrl,
            apkAsset = findApkAsset(assets),
        )
    }

    private fun parseAsset(element: JsonElement): ReleaseAsset? {
        val obj = element.jsonObject
        val name = obj["name"]?.jsonPrimitive?.content ?: return null
        val downloadUrl = obj["browser_download_url"]?.jsonPrimitive?.content ?: return null
        return ReleaseAsset(name, downloadUrl)
    }
}

data class ApkDownload(
    val id: Long,
    val filePath: String,
)

data class ApkDownloadProgress(
    val status: Int,
    val downloadedBytes: Long,
    val totalBytes: Long,
)
