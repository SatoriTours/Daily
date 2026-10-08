package com.dailysatori.service.opportunity

import com.dailysatori.data.repository.SettingRepository
import com.dailysatori.service.externalfavorites.sha256Hex
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class NewsOpportunityStore(private val settings: SettingRepository) {
    private val json = Json { ignoreUnknownKeys = true }

    internal fun load(): OpportunityArchive = settings.transaction {
        val stored = settings.getLargeValue(STORAGE_KEY) ?: return@transaction OpportunityArchive()
        val chunked = stored.startsWith(MANIFEST_PREFIX)
        val encoded = if (chunked) readChunks(stored) else stored
        val archive = json.decodeFromString<OpportunityArchive>(encoded)
        // Only migrate after successful decoding; never replace a failed read with an empty archive.
        if (!chunked && stored.length > CHUNK_SIZE) save(archive)
        archive
    }

    internal fun save(archive: OpportunityArchive) {
        val encoded = json.encodeToString(archive)
        settings.transaction {
            settings.deleteByKeyPrefix(CHUNK_PREFIX)
            if (encoded.length <= CHUNK_SIZE) {
                settings.upsert(STORAGE_KEY, encoded)
            } else {
                val count = writeChunks(encoded)
                settings.upsert(STORAGE_KEY, "$MANIFEST_PREFIX$count:${sha256Hex(encoded)}")
            }
        }
    }

    private fun readChunks(manifest: String): String {
        val fields = manifest.removePrefix(MANIFEST_PREFIX).split(':')
        require(fields.size == 2) { "Invalid opportunity storage manifest" }
        val count = fields[0].toInt()
        require(count > 0) { "Invalid opportunity chunk count" }
        val encoded = buildString {
            repeat(count) { index ->
                append(checkNotNull(settings.get("$CHUNK_PREFIX$index")) { "Missing opportunity storage chunk" })
            }
        }
        check(sha256Hex(encoded) == fields[1]) { "Opportunity storage checksum mismatch" }
        return encoded
    }

    private fun writeChunks(encoded: String): Int {
        var start = 0
        var index = 0
        while (start < encoded.length) {
            var end = (start + CHUNK_SIZE).coerceAtMost(encoded.length)
            // Do not store half of an emoji/supplementary character in separate SQLite TEXT values.
            if (end < encoded.length && encoded[end - 1] in '\uD800'..'\uDBFF') end--
            settings.upsert("$CHUNK_PREFIX${index++}", encoded.substring(start, end))
            start = end
        }
        return index
    }

    private companion object {
        const val STORAGE_KEY = "news_opportunity_archive_v1"
        const val CHUNK_PREFIX = "$STORAGE_KEY:chunk:"
        const val MANIFEST_PREFIX = "news-opportunity-chunks-v2:"
        const val CHUNK_SIZE = 32_768
    }
}

@Serializable
internal data class OpportunityArchive(
    val articles: List<ReadNewsArticle> = emptyList(),
    val items: List<NewsOpportunity> = emptyList(),
    val checkpoints: List<OpportunityCheckpoint> = emptyList(),
    val focus: String = "",
    val candidates: List<ReadNewsArticle> = emptyList(),
    val lastAttemptAt: Long = 0,
    val lastAttemptContext: String = "",
    val lastError: String? = null,
    val dismissedErrorTaskId: Long? = null,
)

@Serializable
internal data class OpportunityCheckpoint(val identity: String, val fingerprint: String)
