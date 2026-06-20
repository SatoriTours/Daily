package com.dailysatori.service.ai

import co.touchlab.kermit.Logger
import com.dailysatori.config.AiModel
import com.dailysatori.config.AiModelDiscoveryProtocol
import com.dailysatori.config.AiProvider
import com.dailysatori.config.SettingKeys
import com.dailysatori.data.repository.SettingRepository
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private const val AiModelCatalogCacheTtlMillis = 24L * 60L * 60L * 1000L

data class DiscoveredAiModel(
    val id: String,
    val name: String,
)

class AiModelCatalogService(
    private val client: HttpClient,
    private val settingRepo: SettingRepository,
) {
    private val log = Logger.withTag("AiModelCatalog")

    suspend fun refreshModels(provider: AiProvider, apiToken: String): Result<List<AiModel>> = runCatching {
        val protocol = provider.modelDiscovery.protocol
        if (protocol == AiModelDiscoveryProtocol.None) return@runCatching emptyList()
        val url = buildAiModelDiscoveryUrl(provider.apiHost, protocol)
        val response = client.get(url) {
            when (protocol) {
                AiModelDiscoveryProtocol.AnthropicCompatible -> {
                    header("x-api-key", apiToken.trim())
                    header("anthropic-version", "2023-06-01")
                }
                AiModelDiscoveryProtocol.Gemini -> {
                    header("x-goog-api-key", apiToken.trim())
                }
                AiModelDiscoveryProtocol.OpenAiCompatible -> {
                    bearerAuth(apiToken.trim())
                }
                AiModelDiscoveryProtocol.None -> Unit
            }
            header(HttpHeaders.Accept, "application/json")
        }
        val body = response.bodyAsText()
        val models = parseAiModelDiscoveryResponse(body, protocol).map { AiModel(it.id, it.name) }
        if (models.isNotEmpty()) saveCachedModels(provider.id, models)
        models
    }.onFailure { error ->
        log.w(error) { "Could not refresh AI models for ${provider.id}" }
    }

    fun cachedModels(providerId: String): List<AiModel> =
        readCachedModels(providerId)?.models?.map { AiModel(it.id, it.name) }.orEmpty()

    fun shouldAutoRefresh(providerId: String, nowMillis: Long = Clock.System.now().toEpochMilliseconds()): Boolean {
        val cachedAt = readCachedModels(providerId)?.cachedAtMillis ?: return true
        return nowMillis - cachedAt > AiModelCatalogCacheTtlMillis
    }

    private fun saveCachedModels(providerId: String, models: List<AiModel>) {
        val cache = CachedAiModelCatalog(
            cachedAtMillis = Clock.System.now().toEpochMilliseconds(),
            models = models.map { CachedAiModel(it.id, it.name) },
        )
        settingRepo.upsert(cacheKey(providerId), json.encodeToString(cache))
    }

    private fun readCachedModels(providerId: String): CachedAiModelCatalog? =
        settingRepo.get(cacheKey(providerId))?.let { raw ->
            runCatching { json.decodeFromString<CachedAiModelCatalog>(raw) }.getOrNull()
        }

    private fun cacheKey(providerId: String): String =
        "${SettingKeys.aiModelCatalogCache}:$providerId"
}

fun buildAiModelDiscoveryUrl(apiHost: String, protocol: AiModelDiscoveryProtocol): String {
    val base = apiHost.trim().trimEnd('/')
    return when (protocol) {
        AiModelDiscoveryProtocol.OpenAiCompatible,
        AiModelDiscoveryProtocol.AnthropicCompatible,
        -> if (base.hasVersionSuffix()) "$base/models" else "$base/v1/models"
        AiModelDiscoveryProtocol.Gemini -> if (base.endsWith("/v1beta")) "$base/models" else "$base/v1beta/models"
        AiModelDiscoveryProtocol.None -> base
    }
}

private fun String.hasVersionSuffix(): Boolean =
    Regex(""".*/v\d+(?:beta)?$""").matches(this)

fun parseAiModelDiscoveryResponse(body: String, protocol: AiModelDiscoveryProtocol): List<DiscoveredAiModel> {
    val root = json.parseToJsonElement(body).jsonObject
    val discovered = when (protocol) {
        AiModelDiscoveryProtocol.OpenAiCompatible,
        AiModelDiscoveryProtocol.AnthropicCompatible,
        -> root["data"]?.jsonArray.orEmpty().mapNotNull { element ->
            val item = element as? JsonObject ?: return@mapNotNull null
            val id = item.string("id") ?: return@mapNotNull null
            val name = item.string("display_name") ?: item.string("name") ?: id
            DiscoveredAiModel(id, name)
        }
        AiModelDiscoveryProtocol.Gemini -> root["models"]?.jsonArray.orEmpty().mapNotNull { element ->
            val item = element as? JsonObject ?: return@mapNotNull null
            val rawName = item.string("name") ?: return@mapNotNull null
            val id = rawName.removePrefix("models/")
            if (!id.contains("gemini", ignoreCase = true)) return@mapNotNull null
            val name = item.string("displayName") ?: id
            DiscoveredAiModel(id, name)
        }
        AiModelDiscoveryProtocol.None -> emptyList()
    }
    return discovered.distinctBy { it.id }
}

private fun JsonObject?.string(key: String): String? =
    this?.get(key)?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotBlank() }

private fun JsonArray?.orEmpty(): JsonArray = this ?: JsonArray(emptyList())

@Serializable
private data class CachedAiModelCatalog(
    val cachedAtMillis: Long,
    val models: List<CachedAiModel>,
)

@Serializable
private data class CachedAiModel(
    val id: String,
    val name: String,
)

private val json = Json { ignoreUnknownKeys = true; isLenient = true }
