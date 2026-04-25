package com.dailysatori.service

import co.touchlab.kermit.Logger
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.serialization.json.*

class AppUpgradeService(private val client: HttpClient) {
    private val log = Logger.withTag("Upgrade")

    suspend fun checkForUpdate(currentVersion: String): String? {
        return try {
            val response = client.get("https://api.github.com/repos/SatoriTours/Daily/releases/latest")
            val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            val tagName = json["tag_name"]?.jsonPrimitive?.content ?: return null
            if (tagName != currentVersion) tagName else null
        } catch (e: Exception) {
            log.e(e) { "Failed to check for updates" }
            null
        }
    }
}
