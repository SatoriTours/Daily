package com.dailysatori.service.externalfavorites

import com.dailysatori.shared.db.External_favorite_source
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

data class GitHubAccount(val id: String, val login: String, val name: String)

class GitHubStarsConnector(
    private val client: HttpClient? = null,
    private val apiBaseUrl: String = "https://api.github.com",
) : FavoriteConnector {
    override val provider: String = ExternalFavoriteProvider.GITHUB.id
    override val capabilities = FavoriteConnectorCapabilities(
        maxPageSize = 100,
        defaultBackoffMinutes = 15,
        maxPagesPerRun = 50,
        maxItemsPerRun = 5_000,
        supportsFolders = false,
        supportsFavoritedAt = true,
        supportsWriteBack = false,
        supportsRefreshToken = false,
    )

    suspend fun resolveAccount(accessToken: String): GitHubAccount {
        val token = accessToken.trim().takeIf(String::isNotBlank)
            ?: throw IllegalArgumentException("GitHub Token 不能为空")
        val response = httpClient().get("$apiBaseUrl/user") { githubHeaders(token) }
        val body = response.bodyAsText()
        validateResponse(response.status.value, body, response.headers[GITHUB_RATE_LIMIT_RESET])
        val root = json.parseToJsonElement(body).jsonObject
        return GitHubAccount(
            id = root.string("id") ?: error("GitHub 用户响应缺少 id"),
            login = root.string("login") ?: error("GitHub 用户响应缺少 login"),
            name = root.string("name").orEmpty(),
        )
    }

    override suspend fun fetchPage(
        source: External_favorite_source,
        cursor: String?,
        pageSize: Int,
        httpLogger: FavoriteSyncHttpLogger,
        taskId: Long?,
        shouldFetchDetail: FavoriteFetchDetailPolicy,
        sinceExternalId: String?,
    ): FavoriteFetchPage {
        val token = githubAccessToken(source.auth_json)
            ?: throw FavoriteAuthException(401, "GitHub Token 缺失，请重新连接")
        val page = cursor?.toIntOrNull()?.coerceAtLeast(1) ?: 1
        val size = pageSize.coerceIn(1, capabilities.maxPageSize)
        val url = "$apiBaseUrl/user/starred"
        httpLogger.logRequest(taskId, "github_stars", "GET", url, mapOf("page" to page.toString(), "per_page" to size.toString()))
        val response = httpClient().get(url) {
            githubHeaders(token, GITHUB_STAR_ACCEPT)
            parameter("page", page)
            parameter("per_page", size)
            parameter("sort", "created")
            parameter("direction", "desc")
        }
        val body = response.bodyAsText()
        httpLogger.logResponse(taskId, "github_stars", response.status.value, emptyMap(), body)
        validateResponse(response.status.value, body, response.headers[GITHUB_RATE_LIMIT_RESET])
        val items = json.parseToJsonElement(body).jsonArray.mapNotNull { element ->
            parseStar(element.jsonObject, token, shouldFetchDetail)
        }
        val hasNext = response.headers["Link"]?.contains("rel=\"next\"") == true
        return FavoriteFetchPage(
            items = items,
            nextCursor = if (hasNext) (page + 1).toString() else null,
            rateLimitResetAt = response.headers[GITHUB_RATE_LIMIT_RESET]?.toResetMillis(),
        )
    }

    private suspend fun parseStar(
        starred: JsonObject,
        token: String,
        shouldFetchDetail: FavoriteFetchDetailPolicy,
    ): ExternalFavoriteItemDraft? {
        val repo = (starred["repo"] as? JsonObject) ?: starred
        val id = repo.string("id") ?: return null
        val fullName = repo.string("full_name") ?: return null
        val url = repo.string("html_url") ?: "https://github.com/$fullName"
        val description = repo.string("description").orEmpty()
        val owner = (repo["owner"] as? JsonObject)?.string("login").orEmpty()
        val base = draft(id, url, fullName, description, owner, starred, repo, readme = "")
        val readme = if (shouldFetchDetail(base)) fetchReadme(fullName, token) else ""
        return draft(id, url, fullName, description, owner, starred, repo, readme)
    }

    private fun draft(
        id: String,
        url: String,
        fullName: String,
        description: String,
        owner: String,
        starred: JsonObject,
        repo: JsonObject,
        readme: String,
    ): ExternalFavoriteItemDraft {
        val language = repo.string("language").orEmpty()
        val topics = (repo["topics"] as? JsonArray).orEmpty().mapNotNull { it.jsonPrimitive.contentOrNull }
        val text = buildList {
            if (description.isNotBlank()) add(description)
            if (language.isNotBlank()) add("主要语言：$language")
            if (topics.isNotEmpty()) add("主题：${topics.joinToString(", ")}")
            if (readme.isNotBlank()) add("README：\n${readme.take(MAX_README_CHARS)}")
        }.joinToString("\n\n")
        val normalized = buildJsonObject {
            put("full_name", fullName)
            put("description", description)
            put("language", language)
            put("stars", repo.long("stargazers_count") ?: 0)
            putJsonArray("topics") { topics.forEach { add(JsonPrimitive(it)) } }
            repo.string("open_graph_image_url")?.let { image -> putJsonArray("url_images") { add(JsonPrimitive(image)) } }
        }.toString()
        val starredAt = starred.string("starred_at").toEpochMillisOrNull()
        val sourceTime = repo.string("updated_at").toEpochMillisOrNull()
        return ExternalFavoriteItemDraft(
            provider = provider,
            externalId = id,
            canonicalUrl = url,
            title = fullName,
            text = text,
            authorName = owner,
            sourceCreatedAt = sourceTime,
            favoritedAt = starredAt,
            normalizedJson = normalized,
            contentHash = sha256Hex("$id\n$url\n$text\n$normalized"),
            aiInputHash = sha256Hex("$id\n$fullName\n$text\n$owner"),
        )
    }

    private suspend fun fetchReadme(fullName: String, token: String): String {
        val response = httpClient().get("$apiBaseUrl/repos/$fullName/readme") {
            githubHeaders(token, GITHUB_RAW_ACCEPT)
        }
        return when (response.status.value) {
            in 200..299 -> response.bodyAsText()
            404 -> ""
            else -> {
                val body = response.bodyAsText()
                validateResponse(response.status.value, body, response.headers[GITHUB_RATE_LIMIT_RESET])
                ""
            }
        }
    }

    private fun httpClient(): HttpClient = client ?: error("GitHubStarsConnector requires an HttpClient")
}

fun githubAuthJson(accessToken: String): String = buildJsonObject {
    put("access_token", accessToken.trim())
}.toString()

internal fun githubAccessToken(authJson: String): String? = runCatching {
    json.parseToJsonElement(authJson).jsonObject.string("access_token")?.takeIf(String::isNotBlank)
}.getOrNull()

private fun io.ktor.client.request.HttpRequestBuilder.githubHeaders(token: String, accept: String = GITHUB_JSON_ACCEPT) {
    bearerAuth(token)
    header("Accept", accept)
}

private fun validateResponse(status: Int, body: String, reset: String?) {
    if (status == 401 || status == 403 && !body.contains("rate limit", ignoreCase = true)) {
        throw FavoriteAuthException(status, "GitHub 授权失败（HTTP $status），请检查 Token 权限")
    }
    if (status == 429 || status == 403 && body.contains("rate limit", ignoreCase = true)) {
        throw FavoriteRateLimitException(status, reset.toResetMillis(), "GitHub API 已达到速率限制")
    }
    if (status !in 200..299) throw FavoriteProviderException(status, "GitHub API 请求失败（HTTP $status）")
}

private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull
private fun JsonObject.long(key: String): Long? = string(key)?.toLongOrNull()
private fun String?.toEpochMillisOrNull(): Long? = this?.let { runCatching { Instant.parse(it).toEpochMilliseconds() }.getOrNull() }
private fun String?.toResetMillis(): Long? = this?.toLongOrNull()?.times(1_000L)

private val json = Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false }
private const val GITHUB_JSON_ACCEPT = "application/vnd.github+json"
private const val GITHUB_STAR_ACCEPT = "application/vnd.github.star+json"
private const val GITHUB_RAW_ACCEPT = "application/vnd.github.raw+json"
private const val GITHUB_RATE_LIMIT_RESET = "x-ratelimit-reset"
private const val MAX_README_CHARS = 80_000
