package com.dailysatori.service.assistant

import app.cash.sqldelight.db.SqlDriver
import com.dailysatori.data.repository.*
import com.dailysatori.service.assistant.data.AssistantDataGateway
import com.dailysatori.service.assistant.data.AssistantCredentialRedactor
import com.dailysatori.service.asynctask.*
import com.dailysatori.service.externalfavorites.sha256Hex
import com.dailysatori.service.memory.ConversationSummaryDraft
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** One fully grounded fragment per invocation; the existing task runner yields between fragments. */
class AssistantConversationSummarizer(
    driver: SqlDriver, private val segmentSize: Int = 6000,
    private val summarize: suspend (String, String) -> ConversationSummaryDraft,
) {
    private val store = AssistantStore(driver)
    private val db = DailySatoriDatabase(driver)
    private val tasks = AsyncTaskRepository(db)
    private val settings = SettingRepository(db)
    private val privacy = AssistantPrivacyRepository(driver)
    private val chats = ChatConversationRepository(db)
    private val data = AssistantDataGateway(driver)
    private val coverageJson = Json { encodeDefaults = true }
    init { require(segmentSize in 1..6000) }

    internal suspend fun process(taskId: Long, request: SummaryRequest, reporter: AsyncTaskProgressReporter): AsyncTaskExecutionResult {
        val lease = reporter.executionLease?.takeIf { it.taskId == taskId }
            ?: return failed("assistant_lease_required", "历史归纳需要有效运行身份")
        val frame = tasks.withRunningLease(taskId, lease.owner) {
            if (!authorized() || tasks.getById(taskId)?.type != ASSISTANT_SUMMARY_TYPE) emptyList() else {
                if (settings.get(ASSISTANT_HISTORY_RESTORE_HOLD) == "true") settings.upsert(ASSISTANT_HISTORY_RESTORE_HOLD, "false")
                listOf(load(request.sessionId))
            }
        }?.firstOrNull() ?: return failed("cloud_consent_required", "原文保留，等待云端授权或明确继续")
        if (!valid(request.sessionId, frame.sources)) return invalidate(request.sessionId, frame, lease)
        val piece = next(request, frame.sources) ?: return AsyncTaskExecutionResult.Success()
        val revision = privacy.revision()
        val draft = if (piece.text.isBlank()) ConversationSummaryDraft(frame.text) else summarize(frame.text, piece.text)
        require(draft.summary.length <= 4000 && data.redact(draft.summary) == draft.summary)
        require(draft.quotes.size <= 8 && draft.quotes.all { it.isNotBlank() && it.length <= 200 && piece.text.contains(it) })
        require(draft.summary == frame.text || draft.quotes.isNotEmpty()) { "New summary claims require exact source evidence" }
        val safeText = draft.summary.replace(Regex("\\[(?:chat|diary|article|memory_entry)/[^]]+]"), "[模型来源标记已移除]")
        return tasks.withRunningLease(taskId, lease.owner) {
            if (!authorized()) return@withRunningLease failed("cloud_consent_required", "原文保留，等待云端授权")
            if (privacy.revision() != revision || !valid(request.sessionId, frame.sources) || !current(piece))
                return@withRunningLease invalidate(request.sessionId, frame, lease)
            val previous = frame.sources.firstOrNull { it.id == piece.id }
            val quotes = (previous?.quotes.orEmpty() + draft.quotes.map { SummaryQuote(piece.offset + piece.text.indexOf(it), it.length) }).takeLast(12)
            val source = SummarySource(piece.id, piece.version, piece.offset + piece.text.length, piece.total, true, quotes = quotes)
            val sources = frame.sources.filterNot { it.id == piece.id } + source
            val changed = store.connection.execute(null, """UPDATE assistant_session SET summary=?,summary_sources_json=?,
                summary_version=summary_version+1 WHERE id=? AND summary_version=?""", 4) {
                bindString(0, safeText); bindString(1, coverageJson.encodeToString(sources)); bindString(2, request.sessionId); bindLong(3, frame.version)
            }.value
            if (changed == 1L) {
                tasks.updateProgress(taskId, source.offset.toLong(), source.total.toLong(), "已归纳一个完整片段；近期对话继续使用原文", "")
                store.changed()
            }
            if (changed == 1L) AsyncTaskExecutionResult.Yield() else
                AsyncTaskExecutionResult.RetryableFailure("assistant_summary_conflict", "归纳已发生并发更新，未覆盖较新结果")
        } ?: failed("assistant_lease_lost", "历史归纳执行权已失效")
    }

    private fun load(sessionId: String): SummaryFrame {
        val now = Clock.System.now().toEpochMilliseconds()
        store.exec("INSERT OR IGNORE INTO assistant_session(id,created_at) VALUES(?,?)", sessionId, now)
        return store.rows("SELECT summary,summary_sources_json,summary_version FROM assistant_session WHERE id=?", sessionId) {
            val sources = runCatching { Json.decodeFromString<List<SummarySource>>(it.getString(1).orEmpty()) }.getOrDefault(emptyList())
            val ai = sources.any { source -> source.ai }
            SummaryFrame(if (ai) data.redact(it.getString(0).orEmpty()) else "", if (ai) sources else emptyList(), it.getLong(2)!!)
        }.single()
    }

    private fun next(request: SummaryRequest, sources: List<SummarySource>): SummaryPiece? {
        val cutoff = store.rows("""SELECT c.id FROM chat_conversation c WHERE session_id=? AND role='user' AND id<=?
            AND NOT EXISTS(SELECT 1 FROM assistant_memory_exclusion x WHERE x.source_type='chat' AND x.source_id=CAST(c.id AS TEXT))
            AND NOT EXISTS(SELECT 1 FROM assistant_turn t WHERE t.id=c.id AND t.response_message_id IS NULL)
            ORDER BY id DESC LIMIT 1 OFFSET 12""", request.sessionId, request.upperId) { it.getLong(0)!! }.firstOrNull() ?: return null
        // Read one original at a time. Coverage is local metadata, never a bulk cloud upload.
        val ids = store.rows("""SELECT c.id FROM chat_conversation c WHERE session_id=? AND role='user' AND id<=?
            AND NOT EXISTS(SELECT 1 FROM assistant_memory_exclusion x WHERE x.source_type='chat' AND x.source_id=CAST(c.id AS TEXT))
            AND NOT EXISTS(SELECT 1 FROM assistant_turn t WHERE t.id=c.id AND t.response_message_id IS NULL)
            ORDER BY id""", request.sessionId, cutoff) { it.getLong(0)!! }
        val covered = sources.associateBy { it.id }
        val redact = AssistantCredentialRedactor(store.connection).snapshot()
        ids.forEach { id ->
            val old = covered[id]
            if (old != null && old.offset == old.total) return@forEach
            val original = chats.getById(id) ?: return@forEach
            val text = redact(original.content)
            if (old == null || old.offset < text.length) {
                val offset = old?.offset ?: 0
                return SummaryPiece(id, sha256Hex(original.content), offset, text.length, text.drop(offset).take(segmentSize))
            }
        }
        return null
    }

    private fun current(piece: SummaryPiece): Boolean = chats.getById(piece.id)?.let {
        it.role == "user" && !privacy.isExcluded("chat", piece.id.toString()) && sha256Hex(it.content) == piece.version &&
            data.redact(it.content).drop(piece.offset).take(segmentSize) == piece.text
    } == true

    private fun valid(sessionId: String, sources: List<SummarySource>): Boolean =
        validSummarySources(store.connection, sessionId, sources, Long.MAX_VALUE)

    private fun invalidate(sessionId: String, frame: SummaryFrame, lease: AsyncTaskExecutionLease): AsyncTaskExecutionResult {
        tasks.withRunningLease(lease.taskId, lease.owner) {
            store.exec("UPDATE assistant_session SET summary='',summary_sources_json='[]',summary_version=summary_version+1 WHERE id=? AND summary_version=?", sessionId, frame.version)
            store.changed()
        }
        return AsyncTaskExecutionResult.Yield()
    }

    private fun authorized() = settings.get(PersistentAssistantConversation.CONSENT_KEY) == "true"
    private fun failed(code: String, message: String) = AsyncTaskExecutionResult.PermanentFailure(code, message)
}

internal fun validAssistantSummarySources(driver: SqlDriver, sessionId: String, raw: String, beforeId: Long): Boolean =
    runCatching { validSummarySources(driver, sessionId, Json.decodeFromString<List<SummarySource>>(raw), beforeId) }.getOrDefault(false)

/** Bound query parameters and snapshot the redactor once, rather than doing N credential/schema reads. */
private fun validSummarySources(driver: SqlDriver, sessionId: String, sources: List<SummarySource>, beforeId: Long): Boolean {
    if (sources.any { it.id <= 0 || it.id >= beforeId } || sources.map { it.id }.distinct().size != sources.size) return false
    if (sources.isEmpty()) return true
    val store = AssistantStore(driver)
    val redact = AssistantCredentialRedactor(driver).snapshot()
    return sources.chunked(400).all { chunk ->
        val expected = chunk.associateBy { it.id }
        val marks = chunk.joinToString(",") { "?" }
        val rows = store.rows("""SELECT c.id,c.content FROM chat_conversation c WHERE c.id IN ($marks) AND c.session_id=? AND c.role='user'
            AND NOT EXISTS(SELECT 1 FROM assistant_memory_exclusion x WHERE x.source_type='chat' AND x.source_id=CAST(c.id AS TEXT))""",
            *(chunk.map { it.id as Any } + sessionId).toTypedArray()) { it.getLong(0)!! to it.getString(1)!! }
        rows.size == chunk.size && rows.all { (id, content) ->
            val source = expected.getValue(id)
            sha256Hex(content) == source.version && (!source.ai || (source.coordinate == "redacted-utf16" &&
                source.offset in 0..source.total && redact(content).length == source.total))
        }
    }
}

private data class SummaryFrame(val text: String, val sources: List<SummarySource>, val version: Long)
private data class SummaryPiece(val id: Long, val version: String, val offset: Int, val total: Int, val text: String)
@Serializable private data class SummaryQuote(val offset: Int, val length: Int)
@Serializable private data class SummarySource(
    val id: Long, val version: String, val offset: Int = 0, val total: Int = 0, val ai: Boolean = false,
    val coordinate: String = "redacted-utf16", val quotes: List<SummaryQuote> = emptyList(),
)

class AssistantSummaryTaskHandler(private val summarizer: AssistantConversationSummarizer) : AsyncTaskHandler {
    override val type = ASSISTANT_SUMMARY_TYPE
    override suspend fun execute(taskId: Long, payloadJson: String, checkpointJson: String, reporter: AsyncTaskProgressReporter): AsyncTaskExecutionResult {
        val request = runCatching { Json.decodeFromString<SummaryRequest>(payloadJson) }.getOrNull()
            ?: return AsyncTaskExecutionResult.PermanentFailure("invalid_summary", "历史归纳范围无效")
        return try { summarizer.process(taskId, request, reporter) }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { AsyncTaskExecutionResult.RetryableFailure("assistant_summary_failed", "历史归纳未完成，已完成片段保留") }
    }
}
