package com.dailysatori.service.opportunity

import com.dailysatori.service.externalfavorites.sha256Hex
import com.dailysatori.shared.db.DailySatoriDatabase
import com.dailysatori.shared.db.SelectOpportunityItems
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class NewsOpportunityStore(db: DailySatoriDatabase) {
    private val q = db.newsOpportunityQueries
    private val json = Json { ignoreUnknownKeys = true }

    internal fun load(): OpportunityArchive = q.transactionWithResult {
        val sources = q.selectOpportunitySources().executeAsList().associate { row ->
            val encoded = joinParts(row.payload_head, row.payload_length) {
                q.selectOpportunitySourcePart(it.toString(), row.id).executeAsOne()
            }
            row.id to json.decodeFromString<ReadNewsArticle>(encoded)
        }
        val members = q.selectOpportunityMembership().executeAsList().groupBy { it.kind }
        val state = q.selectOpportunityState().executeAsOneOrNull()
        OpportunityArchive(
            articles = members[READ].orEmpty().map { sources.getValue(it.source_id) },
            items = q.selectOpportunityItems().executeAsList().map { decodeItem(it, sources.getValue(it.source_id)) },
            checkpoints = q.selectOpportunityCheckpoints(::OpportunityCheckpoint).executeAsList(),
            focus = state?.focus.orEmpty(),
            candidates = members[CANDIDATE].orEmpty().map { sources.getValue(it.source_id) },
            lastAttemptAt = state?.last_attempt_at ?: 0,
            lastAttemptContext = state?.last_attempt_context.orEmpty(),
            lastError = state?.last_error,
            dismissedErrorTaskId = state?.dismissed_error_task_id,
        )
    }

    internal fun saveState(archive: OpportunityArchive, validate: () -> Unit = {}) = write(validate) {
        q.putOpportunityState(archive.focus, archive.lastAttemptAt, archive.lastAttemptContext,
            archive.lastError, archive.dismissedErrorTaskId)
    }

    internal fun updateActions(item: NewsOpportunity) =
        q.updateOpportunityActions(item.saved, item.ignored, item.reminderId, item.savedAt, item.id)

    internal fun updateRead(identity: String, article: ReadNewsArticle?) = q.transaction {
        val previous = q.selectOpportunityMemberSources(READ, identity).executeAsList()
        q.deleteOpportunityMembers(READ, identity)
        if (article != null) insertMember(READ, article)
        previous.distinct().forEach(q::deleteUnreferencedOpportunitySource)
    }

    internal fun replaceCandidates(articles: List<ReadNewsArticle>, validate: () -> Unit) = write(validate) {
        val previous = q.selectOpportunityCandidateSources().executeAsList()
        q.deleteOpportunityCandidates()
        articles.forEach { insertMember(CANDIDATE, it) }
        previous.distinct().forEach(q::deleteUnreferencedOpportunitySource)
    }

    internal fun recordResult(item: NewsOpportunity?, checkpoint: OpportunityCheckpoint, validate: () -> Unit) = write(validate) {
        val previous = q.selectOpportunityItemSources(checkpoint.identity).executeAsList()
        q.deleteOpportunityItems(checkpoint.identity)
        if (item != null) insertItem(item)
        q.deleteOpportunityCheckpoint(checkpoint.identity)
        q.insertOpportunityCheckpoint(checkpoint.identity, checkpoint.fingerprint)
        previous.distinct().forEach(q::deleteUnreferencedOpportunitySource)
    }

    /** Called by legacy conversion, never by a normal service write. */
    internal fun importLegacy(archive: OpportunityArchive) = q.transaction {
        check(load() == OpportunityArchive()) { "机会业务表已有数据，未覆盖历史归档" }
        archive.articles.forEach { insertMember(READ, it) }
        archive.candidates.forEach { insertMember(CANDIDATE, it) }
        archive.items.forEach(::insertItem)
        archive.checkpoints.forEach { q.insertOpportunityCheckpoint(it.identity, it.fingerprint) }
        if (archive != OpportunityArchive()) saveState(archive)
        check(load() == archive) { "机会归档迁移核对失败" }
    }

    private fun write(validate: () -> Unit, change: () -> Unit) = q.transaction {
        validate()
        change()
    }

    private fun insertMember(kind: String, article: ReadNewsArticle) =
        q.insertOpportunityMembership(kind, article.key.trim(), insertSource(article))

    private fun insertSource(article: ReadNewsArticle): String {
        val encoded = json.encodeToString(article)
        return sha256Hex(encoded).also { q.insertOpportunitySource(it, encoded) }
    }

    private fun insertItem(item: NewsOpportunity) {
        val analysis = OpportunityDraft(item.title, item.category, item.fact, item.relevance, item.action, item.caveat,
            item.quote, item.relevanceScore, item.actionabilityScore, item.preview)
        q.insertOpportunityItem(item.id, item.article.key.trim(), insertSource(item.article), item.createdAt, item.saved,
            item.ignored, item.reminderId, item.savedAt, json.encodeToString(analysis))
    }

    private fun decodeItem(row: SelectOpportunityItems, article: ReadNewsArticle): NewsOpportunity {
        val encoded = joinParts(row.analysis_head, row.analysis_length) {
            q.selectOpportunityItemPart(it.toString(), row.id).executeAsOne()
        }
        val analysis = json.decodeFromString<OpportunityDraft>(encoded)
        return NewsOpportunity(row.id, article, analysis.title, analysis.category, analysis.fact, analysis.relevance,
            analysis.action, analysis.caveat, analysis.quote, row.created_at, row.saved, row.ignored, row.reminder_id,
            row.saved_at, analysis.relevanceScore, analysis.actionabilityScore, analysis.preview)
    }

    private fun joinParts(head: String?, length: Long?, read: (Long) -> String?): String {
        val first = checkNotNull(head)
        val size = checkNotNull(length)
        if (size <= PART_SIZE) return first
        return buildString {
            append(first)
            var offset = PART_SIZE + 1
            while (offset <= size) {
                append(checkNotNull(read(offset)))
                offset += PART_SIZE
            }
        }
    }

    private companion object {
        const val READ = "read"
        const val CANDIDATE = "candidate"
        const val PART_SIZE = 32_768L
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
