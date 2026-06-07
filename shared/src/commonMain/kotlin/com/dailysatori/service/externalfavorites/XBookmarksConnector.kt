package com.dailysatori.service.externalfavorites

import com.dailysatori.shared.db.External_favorite_source
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.parameters
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

class XBookmarksConnector(
    private val client: HttpClient? = null,
    private val apiBaseUrl: String = "https://api.x.com",
) : FavoriteConnector {
    override val provider: String = ExternalFavoriteProvider.X.id

    override val capabilities: FavoriteConnectorCapabilities = FavoriteConnectorCapabilities(
        maxPageSize = 100,
        defaultBackoffMinutes = 15,
        maxPagesPerRun = 3,
        maxItemsPerRun = 300,
        supportsFolders = false,
        supportsFavoritedAt = false,
        supportsWriteBack = false,
        supportsRefreshToken = true,
    )

    override suspend fun refreshAuth(source: External_favorite_source): External_favorite_source {
        val now = Clock.System.now().toEpochMilliseconds()
        if (!xAuthShouldRefresh(source.auth_json, now)) return source
        val auth = parseXAuth(source.auth_json) ?: return source
        if (auth.clientId.isBlank() || auth.refreshToken.isBlank()) return source
        val httpClient = client ?: error("XBookmarksConnector requires an HttpClient to refresh OAuth tokens")
        val response = httpClient.submitForm(
            url = "$apiBaseUrl/2/oauth2/token",
            formParameters = parameters {
                append("grant_type", "refresh_token")
                append("refresh_token", auth.refreshToken)
                append("client_id", auth.clientId)
            },
        )
        val body = response.bodyAsText()
        if (response.status.value == 401 || response.status.value == 403) throw XFavoriteAuthException(response.status.value)
        if (response.status.value !in 200..299) {
            throw XFavoriteProviderException(
                statusCode = response.status.value,
                message = "X OAuth token refresh failed with HTTP ${response.status.value}",
            )
        }
        val token = parseXRefreshTokenResponse(body)
        return source.copy(
            auth_json = xRefreshedAuthJson(
                existingAuthJson = source.auth_json,
                accessToken = token.accessToken,
                refreshToken = token.refreshToken,
                expiresInSeconds = token.expiresInSeconds,
                nowMillis = now,
                scope = token.scope,
                tokenType = token.tokenType,
            ),
        )
    }

    override suspend fun fetchPage(
        source: External_favorite_source,
        cursor: String?,
        pageSize: Int,
    ): FavoriteFetchPage {
        val httpClient = client ?: error("XBookmarksConnector requires an HttpClient to fetch remote bookmarks")
        val token = extractAccessToken(source.auth_json)
            ?: error("X bookmarks auth_json must contain access_token, bearer_token, or token")
        val response = httpClient.get("$apiBaseUrl/2/users/${source.account_id}/bookmarks") {
            bearerAuth(token)
            parameter("max_results", pageSize.coerceIn(1, capabilities.maxPageSize))
            parameter("tweet.fields", "created_at,author_id,attachments")
            parameter("user.fields", "username,name")
            parameter("expansions", "author_id,attachments.media_keys")
            parameter("media.fields", "type,url,preview_image_url")
            if (!cursor.isNullOrBlank()) {
                parameter("pagination_token", cursor)
            }
        }
        return parseXBookmarksHttpResponse(
            statusCode = response.status.value,
            body = response.bodyAsText(),
            headers = mapOf(
                X_RATE_LIMIT_RESET_HEADER to response.headers[X_RATE_LIMIT_RESET_HEADER].orEmpty(),
            ),
        )
    }
}

open class XFavoriteProviderException(
    val statusCode: Int,
    message: String,
) : RuntimeException(message)

class XFavoriteAuthException(statusCode: Int) : XFavoriteProviderException(
    statusCode = statusCode,
    message = "X bookmarks authorization failed with HTTP $statusCode",
)

class XFavoriteRateLimitException(
    statusCode: Int,
    val rateLimitResetAt: Long?,
) : XFavoriteProviderException(
    statusCode = statusCode,
    message = "X bookmarks rate limited with HTTP $statusCode",
)

private const val X_RATE_LIMIT_RESET_HEADER = "x-rate-limit-reset"
private const val X_REFRESH_BUFFER_MS = 60_000L

fun parseXBookmarksHttpResponse(
    statusCode: Int,
    body: String,
    headers: Map<String, String> = emptyMap(),
): FavoriteFetchPage {
    if (statusCode in 200..299) return XBookmarksResponseParser.parse(body)
    if (statusCode == 401 || statusCode == 403) throw XFavoriteAuthException(statusCode)
    if (statusCode == 429) {
        throw XFavoriteRateLimitException(
            statusCode = statusCode,
            rateLimitResetAt = headers.headerValue(X_RATE_LIMIT_RESET_HEADER)?.toRateLimitResetMillis(),
        )
    }
    throw XFavoriteProviderException(
        statusCode = statusCode,
        message = "X bookmarks provider request failed with HTTP $statusCode",
    )
}

object XBookmarksResponseParser {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    fun parse(rawJson: String): FavoriteFetchPage {
        val root = json.parseToJsonElement(rawJson).jsonObject
        val usersById = root["includes"]
            ?.jsonObjectOrNull()
            ?.get("users")
            ?.jsonArrayOrNull()
            ?.mapNotNull { user ->
                val obj = user.jsonObjectOrNull() ?: return@mapNotNull null
                val id = obj.string("id") ?: return@mapNotNull null
                id to XUser(
                    username = obj.string("username"),
                    name = obj.string("name"),
                )
            }
            ?.toMap()
            .orEmpty()
        val mediaByKey = root["includes"]
            ?.jsonObjectOrNull()
            ?.get("media")
            ?.jsonArrayOrNull()
            ?.mapNotNull { media ->
                val obj = media.jsonObjectOrNull() ?: return@mapNotNull null
                val key = obj.string("media_key") ?: return@mapNotNull null
                key to obj
            }
            ?.toMap()
            .orEmpty()

        val items = root["data"]
            ?.jsonArrayOrNull()
            ?.mapNotNull { tweet -> tweet.toDraft(usersById, mediaByKey) }
            .orEmpty()
        val nextCursor = root["meta"]
            ?.jsonObjectOrNull()
            ?.string("next_token")
            ?.takeIf { it.isNotBlank() }

        return FavoriteFetchPage(
            items = items,
            nextCursor = nextCursor,
            rateLimitResetAt = null,
        )
    }

    private fun JsonElement.toDraft(
        usersById: Map<String, XUser>,
        mediaByKey: Map<String, JsonObject>,
    ): ExternalFavoriteItemDraft? {
        val tweet = jsonObjectOrNull() ?: return null
        val id = tweet.string("id") ?: return null
        val text = tweet.string("text").orEmpty()
        val author = usersById[tweet.string("author_id")]
        val media = tweet["attachments"]
            ?.jsonObjectOrNull()
            ?.get("media_keys")
            ?.jsonArrayOrNull()
            ?.mapNotNull { key -> key.jsonPrimitiveOrNull()?.contentOrNull }
            ?.mapNotNull { mediaByKey[it] }
            .orEmpty()
        val createdAt = tweet.string("created_at")?.let(::parseInstantMillis)
        val normalizedJson = normalizedTweetJson(
            id = id,
            text = text,
            author = author,
            createdAt = tweet.string("created_at"),
            media = media,
        )
        val authorName = author?.name ?: author?.username.orEmpty()
        val hashInput = listOf(id, text, authorName, normalizedJson).joinToString("\n")
        val canonicalUrl = xStatusUrl(id, author?.username)
        val mediaUrls = media.mapNotNull { it.string("url") ?: it.string("preview_image_url") }.sorted()

        return ExternalFavoriteItemDraft(
            provider = ExternalFavoriteProvider.X.id,
            externalId = id,
            canonicalUrl = canonicalUrl,
            title = text,
            text = text,
            authorName = authorName,
            sourceCreatedAt = createdAt,
            favoritedAt = null,
            normalizedJson = normalizedJson,
            debugJson = "",
            contentHash = sha256Hex(hashInput),
            aiInputHash = sha256Hex(aiInputHashText(id, canonicalUrl, text, authorName, createdAt, mediaUrls)),
        )
    }

    private fun normalizedTweetJson(
        id: String,
        text: String,
        author: XUser?,
        createdAt: String?,
        media: List<JsonObject>,
    ): String = json.encodeToString(
        buildJsonObject {
            put("id", id)
            put("text", text)
            if (author != null) {
                put("author", buildJsonObject {
                    author.username?.let { put("username", it) }
                    author.name?.let { put("name", it) }
                })
            }
            createdAt?.let { put("created_at", it) }
            if (media.isNotEmpty()) {
                putJsonArray("media") {
                    media.forEach { item ->
                        add(
                            buildJsonObject {
                                item.string("media_key")?.let { put("media_key", it) }
                                item.string("type")?.let { put("type", it) }
                                item.string("url")?.let { put("url", it) }
                                item.string("preview_image_url")?.let { put("preview_image_url", it) }
                            },
                        )
                    }
                }
            }
        },
    )
}

private data class XUser(
    val username: String?,
    val name: String?,
)

private data class XAuth(
    val clientId: String,
    val accessToken: String,
    val refreshToken: String,
    val expiresAt: Long?,
)

private data class XRefreshTokenPayload(
    val accessToken: String,
    val refreshToken: String,
    val expiresInSeconds: Long?,
    val scope: String,
    val tokenType: String,
)

internal fun xAuthShouldRefresh(authJson: String, nowMillis: Long): Boolean {
    val auth = parseXAuth(authJson) ?: return false
    val expiresAt = auth.expiresAt ?: return false
    return auth.refreshToken.isNotBlank() && expiresAt <= nowMillis + X_REFRESH_BUFFER_MS
}

internal fun xRefreshedAuthJson(
    existingAuthJson: String,
    accessToken: String,
    refreshToken: String,
    expiresInSeconds: Long?,
    nowMillis: Long,
    scope: String,
    tokenType: String,
): String {
    val existing = parseXAuthJson(existingAuthJson)
    val nextRefreshToken = refreshToken.ifBlank {
        existing?.string("refresh_token").orEmpty()
    }
    val clientId = existing?.string("client_id").orEmpty()
    return Json.encodeToString(
        buildJsonObject {
            if (clientId.isNotBlank()) put("client_id", clientId)
            put("access_token", accessToken)
            if (nextRefreshToken.isNotBlank()) put("refresh_token", nextRefreshToken)
            expiresInSeconds?.let {
                put("expires_in", it)
                put("expires_at", nowMillis + it * 1_000L)
            }
            put("issued_at", nowMillis)
            val nextScope = scope.ifBlank { existing?.string("scope").orEmpty() }
            if (nextScope.isNotBlank()) put("scope", nextScope)
            val nextTokenType = tokenType.ifBlank { existing?.string("token_type").orEmpty() }
            if (nextTokenType.isNotBlank()) put("token_type", nextTokenType)
        },
    )
}

private fun parseXAuth(authJson: String): XAuth? = parseXAuthJson(authJson)?.let { auth ->
    XAuth(
        clientId = auth.string("client_id").orEmpty(),
        accessToken = auth.string("access_token") ?: auth.string("bearer_token") ?: auth.string("token").orEmpty(),
        refreshToken = auth.string("refresh_token").orEmpty(),
        expiresAt = auth.long("expires_at"),
    )
}

private fun parseXAuthJson(authJson: String): JsonObject? = runCatching {
    Json.parseToJsonElement(authJson).jsonObject
}.getOrNull()

private fun parseXRefreshTokenResponse(body: String): XRefreshTokenPayload {
    val root = Json.parseToJsonElement(body).jsonObject
    return XRefreshTokenPayload(
        accessToken = root.string("access_token").orEmpty(),
        refreshToken = root.string("refresh_token").orEmpty(),
        expiresInSeconds = root.long("expires_in"),
        scope = root.string("scope").orEmpty(),
        tokenType = root.string("token_type").orEmpty(),
    ).also {
        if (it.accessToken.isBlank()) error("X OAuth refresh response missing access_token")
    }
}

private fun extractAccessToken(authJson: String): String? = runCatching {
    Json.parseToJsonElement(authJson).jsonObject.let { auth ->
        auth.string("access_token")
            ?: auth.string("bearer_token")
            ?: auth.string("token")
    }
}.getOrNull()?.takeIf { it.isNotBlank() }

private fun parseInstantMillis(value: String): Long? = runCatching {
    Instant.parse(value).toEpochMilliseconds()
}.getOrNull()

private fun aiInputHashText(
    externalId: String,
    canonicalUrl: String?,
    text: String,
    authorName: String,
    sourceCreatedAt: Long?,
    mediaUrls: List<String>,
): String = buildString {
    appendLine(ExternalFavoriteProvider.X.id)
    appendLine(externalId)
    appendLine(canonicalUrl.orEmpty())
    appendLine(text)
    appendLine(authorName)
    appendLine(sourceCreatedAt?.toString().orEmpty())
    mediaUrls.forEach { appendLine(it) }
}

private fun Map<String, String>.headerValue(name: String): String? {
    val target = name.lowercase()
    return entries.firstOrNull { it.key.lowercase() == target }?.value?.takeIf { it.isNotBlank() }
}

private fun String.toRateLimitResetMillis(): Long? {
    val value = trim().toLongOrNull() ?: return null
    return if (value > 10_000_000_000L) value else value * 1_000L
}

private fun JsonObject.string(key: String): String? =
    get(key)?.jsonPrimitiveOrNull()?.contentOrNull

private fun JsonObject.long(key: String): Long? =
    string(key)?.toLongOrNull()

private fun JsonElement.jsonObjectOrNull(): JsonObject? = runCatching { jsonObject }.getOrNull()

private fun JsonElement.jsonArrayOrNull(): JsonArray? = runCatching { jsonArray }.getOrNull()

private fun JsonElement.jsonPrimitiveOrNull(): JsonPrimitive? = runCatching { jsonPrimitive }.getOrNull()

private fun sha256Hex(input: String): String = Sha256.digest(input.encodeToByteArray()).joinToString("") {
    it.toUByte().toString(16).padStart(2, '0')
}

private object Sha256 {
    private val k = intArrayOf(
        0x428a2f98, 0x71374491, 0xb5c0fbcf.toInt(), 0xe9b5dba5.toInt(), 0x3956c25b, 0x59f111f1,
        0x923f82a4.toInt(), 0xab1c5ed5.toInt(), 0xd807aa98.toInt(), 0x12835b01, 0x243185be,
        0x550c7dc3, 0x72be5d74, 0x80deb1fe.toInt(), 0x9bdc06a7.toInt(), 0xc19bf174.toInt(),
        0xe49b69c1.toInt(), 0xefbe4786.toInt(), 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f,
        0x4a7484aa, 0x5cb0a9dc, 0x76f988da, 0x983e5152.toInt(), 0xa831c66d.toInt(),
        0xb00327c8.toInt(), 0xbf597fc7.toInt(), 0xc6e00bf3.toInt(), 0xd5a79147.toInt(),
        0x06ca6351, 0x14292967, 0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13,
        0x650a7354, 0x766a0abb, 0x81c2c92e.toInt(), 0x92722c85.toInt(), 0xa2bfe8a1.toInt(),
        0xa81a664b.toInt(), 0xc24b8b70.toInt(), 0xc76c51a3.toInt(), 0xd192e819.toInt(),
        0xd6990624.toInt(), 0xf40e3585.toInt(), 0x106aa070, 0x19a4c116, 0x1e376c08,
        0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
        0x748f82ee, 0x78a5636f, 0x84c87814.toInt(), 0x8cc70208.toInt(), 0x90befffa.toInt(),
        0xa4506ceb.toInt(), 0xbef9a3f7.toInt(), 0xc67178f2.toInt(),
    )

    fun digest(message: ByteArray): ByteArray {
        val padded = pad(message)
        val hash = intArrayOf(
            0x6a09e667,
            0xbb67ae85.toInt(),
            0x3c6ef372,
            0xa54ff53a.toInt(),
            0x510e527f,
            0x9b05688c.toInt(),
            0x1f83d9ab,
            0x5be0cd19,
        )
        val w = IntArray(64)

        for (offset in padded.indices step 64) {
            for (i in 0 until 16) {
                val index = offset + i * 4
                w[i] = ((padded[index].toInt() and 0xff) shl 24) or
                    ((padded[index + 1].toInt() and 0xff) shl 16) or
                    ((padded[index + 2].toInt() and 0xff) shl 8) or
                    (padded[index + 3].toInt() and 0xff)
            }
            for (i in 16 until 64) {
                val s0 = rotateRight(w[i - 15], 7) xor rotateRight(w[i - 15], 18) xor (w[i - 15] ushr 3)
                val s1 = rotateRight(w[i - 2], 17) xor rotateRight(w[i - 2], 19) xor (w[i - 2] ushr 10)
                w[i] = w[i - 16] + s0 + w[i - 7] + s1
            }

            var a = hash[0]
            var b = hash[1]
            var c = hash[2]
            var d = hash[3]
            var e = hash[4]
            var f = hash[5]
            var g = hash[6]
            var h = hash[7]

            for (i in 0 until 64) {
                val s1 = rotateRight(e, 6) xor rotateRight(e, 11) xor rotateRight(e, 25)
                val ch = (e and f) xor (e.inv() and g)
                val temp1 = h + s1 + ch + k[i] + w[i]
                val s0 = rotateRight(a, 2) xor rotateRight(a, 13) xor rotateRight(a, 22)
                val maj = (a and b) xor (a and c) xor (b and c)
                val temp2 = s0 + maj
                h = g
                g = f
                f = e
                e = d + temp1
                d = c
                c = b
                b = a
                a = temp1 + temp2
            }

            hash[0] += a
            hash[1] += b
            hash[2] += c
            hash[3] += d
            hash[4] += e
            hash[5] += f
            hash[6] += g
            hash[7] += h
        }

        return ByteArray(32).also { output ->
            hash.forEachIndexed { i, value ->
                output[i * 4] = (value ushr 24).toByte()
                output[i * 4 + 1] = (value ushr 16).toByte()
                output[i * 4 + 2] = (value ushr 8).toByte()
                output[i * 4 + 3] = value.toByte()
            }
        }
    }

    private fun pad(message: ByteArray): ByteArray {
        val bitLength = message.size.toLong() * 8
        val paddingLength = ((56 - (message.size + 1) % 64) + 64) % 64
        return ByteArray(message.size + 1 + paddingLength + 8).also { output ->
            message.copyInto(output)
            output[message.size] = 0x80.toByte()
            for (i in 0 until 8) {
                output[output.lastIndex - i] = (bitLength ushr (8 * i)).toByte()
            }
        }
    }

    private fun rotateRight(value: Int, bits: Int): Int = (value ushr bits) or (value shl (32 - bits))
}
