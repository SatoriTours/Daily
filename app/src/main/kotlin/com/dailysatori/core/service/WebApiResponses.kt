package com.dailysatori.core.service

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respondText
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal fun respondOk(data: JsonObject? = null): String =
    """{"code":0,"msg":"success","data":${data?.toString() ?: "null"}}"""

internal fun respondFail(msg: String): String =
    """{"code":-1,"msg":"$msg"}"""

internal fun fileUrl(path: String?): String {
    if (path.isNullOrEmpty() || path == "null" || path.endsWith("/null")) return ""
    if (path.startsWith("http://") || path.startsWith("https://")) return path
    val normalized = path
        .substringAfter("DailySatori/", path)
        .removePrefix("/")
        .removePrefix("files/")
    return "/files/$normalized"
}

internal suspend fun ApplicationCall.checkEditVersion(body: JsonObject, current: String): Boolean {
    if (body.stringValue("version") == current) return true
    editConflict()
    return false
}

internal suspend fun ApplicationCall.editConflict() {
    respondText(respondFail("手机上的内容已有更新，请保留当前修改并重新读取"),
        ContentType.Application.Json, HttpStatusCode.Conflict)
}

internal fun JsonObject.stringValue(name: String): String =
    (this[name] as? JsonPrimitive)?.content.orEmpty()

internal fun JsonObject.optionalStringValue(name: String, fallback: String?): String? =
    if (containsKey(name)) (this[name] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() } else fallback

internal fun JsonObject.booleanValue(name: String): Boolean? =
    (this[name] as? JsonPrimitive)?.content?.toBooleanStrictOrNull()

internal fun webPagination(page: Int, total: Int): JsonObject = buildJsonObject {
    put("page", JsonPrimitive(page))
    put("totalItems", JsonPrimitive(total))
    put("totalPages", JsonPrimitive(maxOf(1, (total + 19) / 20)))
}
