package com.dailysatori.service.diary

import kotlinx.serialization.json.Json

internal fun isLegacyNullImagePath(path:String):Boolean =
    path in setOf("null","images/null","diary_images/null") ||
        (path.startsWith('/') && (path.endsWith("/images/null") || path.endsWith("/diary_images/null")))

internal fun legacyDiaryImagePaths(value:String?):List<String> = value.orEmpty().split(',').map(String::trim)
    .filter { it.isNotEmpty() && !isLegacyNullImagePath(it) }

internal fun isRemoteDiaryImage(path:String):Boolean = path.startsWith("https://") || path.startsWith("http://")

internal fun preferredDiaryImagePath(localPath:String,remoteUrl:String?):String =
    localPath.takeIf(String::isNotBlank) ?: remoteUrl.orEmpty()

/** API/draft transport only, never a second persisted database representation. */
fun parseDiaryImagePaths(value:String?):List<String> {
    if(value?.startsWith("[")==true) {
        val decoded=runCatching { Json.decodeFromString<List<String>>(value) }.getOrNull()
        if(decoded!=null) return decoded
    }
    return legacyDiaryImagePaths(value)
}

fun encodeDiaryImagePaths(paths:List<String>):String? {
    if(paths.isEmpty()) return null
    return if(paths.any { ',' in it || it.startsWith("[") || it.trim()!=it || it.isBlank() || isLegacyNullImagePath(it) })
        Json.encodeToString(paths) else paths.joinToString(",")
}
