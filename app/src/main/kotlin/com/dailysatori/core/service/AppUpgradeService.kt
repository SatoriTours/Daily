package com.dailysatori.core.service

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import co.touchlab.kermit.Logger
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.serialization.json.*
import java.io.File

data class AppRelease(
    val version: String,
    val releaseUrl: String,
    val apkAsset: ReleaseAsset?,
)

data class ReleaseAsset(
    val name: String,
    val downloadUrl: String,
)

class AppUpgradeService(private val client: HttpClient) {
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
            assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }

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
        } catch (e: Exception) {
            log.e(e) { "Failed to check for updates" }
            if (!suppressErrors) throw e
            null
        }
    }

    fun enqueueApkDownload(context: Context, release: AppRelease): ApkDownload {
        val asset = release.apkAsset ?: throw IllegalStateException("新版本没有可下载的 APK")
        val fileName = "DailySatori-${release.version.removePrefix("v").removePrefix("V")}.apk"
        val request = DownloadManager.Request(Uri.parse(asset.downloadUrl))
            .setTitle("Daily Satori ${release.version}")
            .setDescription("正在下载更新")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName)
        val manager = context.getSystemService(DownloadManager::class.java)
        val id = manager.enqueue(request)
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

    fun clearPendingDownload() {
        pendingDownload = null
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
