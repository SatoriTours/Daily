package com.dailysatori.service.opportunity

import com.dailysatori.data.repository.SettingRepository
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class NewsOpportunityStore(private val settings: SettingRepository) {
    private val json = Json { ignoreUnknownKeys = true }

    internal fun load(): OpportunityArchive = settings.get(STORAGE_KEY)?.let { encoded ->
        json.decodeFromString<OpportunityArchive>(encoded)
    } ?: OpportunityArchive()

    internal fun save(archive: OpportunityArchive) {
        settings.upsert(STORAGE_KEY, json.encodeToString(archive))
    }

    private companion object {
        const val STORAGE_KEY = "news_opportunity_archive_v1"
    }
}

@Serializable
internal data class OpportunityArchive(
    val articles: List<ReadNewsArticle> = emptyList(),
    val items: List<NewsOpportunity> = emptyList(),
    val checkpoints: List<OpportunityCheckpoint> = emptyList(),
    val focus: String = "",
)

@Serializable
internal data class OpportunityCheckpoint(val identity: String, val fingerprint: String)
