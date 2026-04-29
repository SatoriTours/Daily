package com.dailysatori.service.plugin

import co.touchlab.kermit.Logger
import com.dailysatori.data.repository.SettingRepository
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*

class PluginService(
    private val client: HttpClient,
    private val settingRepo: SettingRepository,
) {
    private val log = Logger.withTag("Plugin")
    private val prompts = mutableMapOf<String, String>()
    private val models = mutableMapOf<String, List<String>>()

    val serverUrl: String get() = settingRepo.get("plugin_server_url") ?: ""

    fun getPrompt(key: String): String = prompts[key] ?: ""
    fun getModels(provider: String): List<String> = models[provider] ?: emptyList()

    suspend fun loadPrompts() {
        log.i { "Plugin service initialized" }
    }

    suspend fun loadModels() {
    }

    suspend fun forceUpdate(fileName: String): Boolean {
        val url = "$serverUrl/$fileName"
        return try {
            val content = client.get(url).bodyAsText()
            settingRepo.upsert("plugin_content_$fileName", content)
            true
        } catch (e: Exception) {
            log.e(e) { "Failed to update $fileName" }
            false
        }
    }
}
