package com.dailysatori.service.migration

import com.dailysatori.data.repository.SettingRepository
import com.dailysatori.service.externalfavorites.sha256Hex
import com.dailysatori.service.opportunity.OpportunityArchive
import kotlinx.serialization.json.Json

/** Retired settings are read only during conversion and independent candidate verification. */
internal object OpportunityArchiveMigration {
    const val KEY = "news_opportunity_archive_v1"
    private const val CHUNK_PREFIX = "$KEY:chunk:"
    private const val MANIFEST = "news-opportunity-chunks-v2:"
    private val json = Json { ignoreUnknownKeys = true }

    fun hasLegacyData(settings: SettingRepository): Boolean =
        settings.getAllKeys().any { it == KEY || it.startsWith(CHUNK_PREFIX) }

    fun read(settings: SettingRepository): OpportunityArchive {
        val stored = settings.getLargeValue(KEY)
        if (stored == null) {
            check(!hasLegacyData(settings)) { "历史机会归档缺少主清单，原数据已保留" }
            return OpportunityArchive()
        }
        val payload = if (stored.startsWith(MANIFEST)) readChunks(settings, stored) else stored
        return try {
            json.decodeFromString<OpportunityArchive>(payload)
        } catch (_: IllegalArgumentException) {
            error("历史机会归档 JSON 无效，原数据已保留")
        }
    }

    private fun readChunks(settings: SettingRepository, manifest: String): String {
        val fields = manifest.removePrefix(MANIFEST).split(':')
        val count = fields.firstOrNull()?.toIntOrNull()
        check(fields.size == 2 && count != null && count > 0) { "历史机会归档分块清单无效" }
        val payload = buildString {
            repeat(count) { index ->
                append(checkNotNull(settings.getLargeValue("$CHUNK_PREFIX$index")) { "历史机会归档缺少分块" })
            }
        }
        check(sha256Hex(payload) == fields[1]) { "历史机会归档分块校验失败" }
        return payload
    }
}
