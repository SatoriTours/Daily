package com.dailysatori.core.service

import java.security.MessageDigest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.long

internal fun isRepositoryReleaseUrl(url: String): Boolean =
    url.startsWith("https://github.com/SatoriTours/Daily/releases/download/") &&
        !url.contains("..") && !url.contains('?') && !url.contains('#')

internal fun parseUpdateManifest(text: String, releaseUrl: String): AppRelease {
    val json = Json.parseToJsonElement(text).jsonObject
    fun field(name: String): String = json.getValue(name).jsonPrimitive.content
    val channelId = field("channel")
    require(channelId in UpdateChannel.entries.map { it.id }) { "未知更新渠道" }
    val code = json.getValue("versionCode").jsonPrimitive.int
    val schema = json.getValue("schemaVersion").jsonPrimitive.long
    val version = field("versionName")
    val hash = field("sha256")
    val url = field("apkUrl")
    val size = json.getValue("size").jsonPrimitive.long
    require(code in 1..2100000000 && schema > 0 && size > 0) { "更新版本信息无效" }
    require(Regex("[0-9]+\\.[0-9]+\\.[0-9]+(-commit\\.[0-9]+)?").matches(version) && '4' !in version) { "更新版本名称无效" }
    require(Regex("[0-9a-f]{64}").matches(hash)) { "安装包校验信息无效" }
    require(isRepositoryReleaseUrl(url) && url.endsWith(".apk")) { "安装包下载地址不可信" }
    return AppRelease(version, releaseUrl, ReleaseAsset(url.substringAfterLast('/'), url), code,
        UpdateChannel.fromId(channelId), schema, hash, size)
}

internal fun verifyApkChecksum(bytes: ByteArray, expected: String?) {
    require(expected != null) { "安装包缺少校验信息，请重新检查更新" }
    val actual = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    require(actual == expected) { "安装包校验失败，请重新检查更新" }
}
