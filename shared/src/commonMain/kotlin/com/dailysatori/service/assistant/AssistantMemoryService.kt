package com.dailysatori.service.assistant

import app.cash.sqldelight.db.SqlDriver
import com.dailysatori.data.repository.ChatConversationRepository
import com.dailysatori.service.assistant.data.AssistantDataGateway
import com.dailysatori.service.asynctask.*
import com.dailysatori.service.externalfavorites.sha256Hex
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlinx.datetime.Clock
import kotlinx.serialization.json.*

class AssistantMemoryService(driver: SqlDriver) {
    private val store = AssistantStore(driver)
    private val privacy = AssistantPrivacyRepository(driver)
    private val chats = ChatConversationRepository(DailySatoriDatabase(driver))
    private val turns = AssistantTurnRepository(driver)
    private val data = AssistantDataGateway(driver)

    fun remember(turn: AssistantTurn, owner: String, kind: String, content: String, quote: String): Long = store.transactionWithResult {
        require(turns.canExecute(requireNotNull(turns.get(turn.id)), owner))
        require(kind in setOf("fact", "preference", "goal", "project", "candidate", "note"))
        require(content.isNotBlank() && content.length <= 4000 && quote.isNotBlank() && quote.length <= 6000)
        require(data.redact(content) == content) { "Credentials cannot become personal memory" }
        val original = requireNotNull(turns.input(turn))
        require(modelInput(original).contains(quote))
        if (isQuotedAssistantEvidence(modelInput(original), quote)) require(kind in setOf("candidate", "note"))
        insert(turn.id, kind, data.redact(content), data.redact(quote), sha256Hex(original))
    }.also { store.changed() }

    private fun insert(sourceId: Long, kind: String, content: String, evidence: String, version: String): Long {
        val existing = store.rows("SELECT id FROM memory_entry WHERE source_type='chat' AND source_id=? AND content=? AND assistant_state='active'", sourceId, content) { it.getLong(0)!! }.firstOrNull()
        if (existing != null) return existing
        val now = Clock.System.now().toEpochMilliseconds()
        store.exec("""INSERT INTO memory_entry(type,source_type,source_id,title,content,tags,created_at,updated_at,
            assistant_kind,assistant_state,evidence,source_version) VALUES('assistant','chat',?,?,?,?,?,? ,?,'active',?,?)""".trimIndent(),
            sourceId, content.take(80), content, "", now, now, kind, evidence, version)
        return store.rows("SELECT last_insert_rowid()") { it.getLong(0)!! }.single()
    }

    fun list(): List<AssistantMemoryView> = store.rows("""SELECT m.id,m.content,m.assistant_kind,m.source_type,m.source_id,m.assistant_state,m.evidence
        FROM memory_entry m WHERE m.type='assistant' AND NOT EXISTS (
            SELECT 1 FROM assistant_memory_exclusion x WHERE x.source_type=m.source_type AND x.source_id=CAST(m.source_id AS TEXT)
        ) ORDER BY m.id DESC""".trimIndent()) { c -> AssistantMemoryView(c.getLong(0)!!, c.getString(1)!!,
        c.getString(2)!!, c.getString(3).orEmpty(), c.getLong(4).toString(), c.getString(5) == "active", c.getString(6).orEmpty()) }

    fun correct(id: Long, corrected: String) {
        require(corrected.isNotBlank() && corrected.length <= 4000)
        store.transaction {
            val old = list().first { it.id == id && it.active }
            val original = old.sourceId.toLongOrNull()?.let(chats::getById)
            val sourceId = chats.acceptUserInput(original?.session_id ?: "assistant-memory", corrected)
            store.exec("UPDATE memory_entry SET assistant_state='superseded' WHERE id=?", id)
            privacy.exclude("memory_entry", id.toString())
            insert(sourceId, "fact", data.redact(corrected), data.redact(corrected), sha256Hex(corrected))
        }
        store.changed()
    }

    fun forget(id: Long) {
        store.transaction {
            val memory = list().firstOrNull { it.id == id } ?: return@transaction
            privacy.exclude(memory.source, memory.sourceId)
            store.exec("UPDATE memory_entry SET assistant_state='forgotten', content='', evidence='' WHERE id=?", id)
        }
        store.changed()
    }

    /** Full source is segmented, never an opening-only summary; rebuild is versioned and local. */
    fun index(sourceId: Long) {
        store.transaction {
            val original = chats.getById(sourceId)?.takeIf { it.role == "user" }
            if (original == null || privacy.isExcluded("chat", sourceId.toString())) {
                store.exec("DELETE FROM assistant_index_segment WHERE source_id=?", sourceId)
                return@transaction
            }
            val version = sha256Hex(original.content)
            if (store.text("SELECT source_version FROM assistant_index_segment WHERE source_id=? LIMIT 1",sourceId) == version) return@transaction
            store.exec("DELETE FROM assistant_index_segment WHERE source_id=?", sourceId)
            data.redact(original.content).chunked(6000).forEachIndexed { index, text ->
                store.exec("INSERT INTO assistant_index_segment(source_id,source_version,segment_index,content) VALUES(?,?,?,?)",
                    sourceId, version, index, text)
            }
            rebuildSummary(original.session_id)
        }
        store.changed()
    }

    fun segments(sourceId: Long): List<String> {
        if (privacy.isExcluded("chat", sourceId.toString())) return emptyList()
        return store.rows("SELECT content FROM assistant_index_segment WHERE source_id=? ORDER BY segment_index", sourceId) { it.getString(0)!! }
    }

    internal fun pendingSegments(sourceId: Long): List<AssistantMemorySegment> =
        store.rows("SELECT source_version,segment_index,content FROM assistant_index_segment WHERE source_id=? AND ai_state='pending' ORDER BY segment_index", sourceId) {
            AssistantMemorySegment(it.getString(0)!!,it.getLong(1)!!.toInt(),it.getString(2)!!)
        }

    internal fun saveIndexed(sourceId: Long, segment: AssistantMemorySegment,
        lease: AsyncTaskExecutionLease, items: List<com.dailysatori.service.memory.PersonalMemoryCandidate>): Boolean {
        val tasks = com.dailysatori.data.repository.AsyncTaskRepository(DailySatoriDatabase(store.connection))
        return tasks.withRunningLease(lease.taskId,lease.owner) {
            val original = chats.getById(sourceId)
            if (com.dailysatori.data.repository.SettingRepository(DailySatoriDatabase(store.connection)).get(PersistentAssistantConversation.CONSENT_KEY) != "true") return@withRunningLease false
            if (original == null || original.role != "user" || privacy.isExcluded("chat",sourceId.toString()) ||
                sha256Hex(original.content) != segment.version) return@withRunningLease false
            require(items.size <= 4)
            val corrected = store.text("SELECT CAST(id AS TEXT) FROM memory_entry WHERE source_type='chat' AND source_id=? AND assistant_state!='active' LIMIT 1",sourceId) != null
            if (!corrected) items.forEach { item ->
                require(item.kind in setOf("fact","preference","goal","project","candidate","note"))
                require(item.content.isNotBlank() && item.content.length <= 1000 && data.redact(item.content) == item.content)
                require(item.quote.isNotBlank() && segment.content.contains(item.quote))
                val kind = if (isQuotedAssistantEvidence(modelInput(original.content),item.quote)) "candidate" else item.kind
                insert(sourceId,kind,item.content,item.quote,segment.version)
            }
            store.exec("UPDATE assistant_index_segment SET ai_state='done' WHERE source_id=? AND source_version=? AND segment_index=?",sourceId,segment.version,segment.index)
            true
        }?.also { store.changed() } ?: false
    }

    private fun rebuildSummary(sessionId: String) {
        store.exec("INSERT OR IGNORE INTO assistant_session(id,created_at) VALUES(?,?)", sessionId, Clock.System.now().toEpochMilliseconds())
        val existing = store.text("SELECT summary_sources_json FROM assistant_session WHERE id=?", sessionId).orEmpty()
        if (runCatching { Json.parseToJsonElement(existing).jsonArray.any { it.jsonObject["ai"] == JsonPrimitive(true) } }.getOrDefault(false)) return
        val older = chats.getBySession(sessionId).filter { it.role == "user" && !privacy.isExcluded("chat", it.id.toString()) }
            .sortedBy { it.id }.takeLast(120).dropLast(12).takeLast(8)
        val sources = buildJsonArray { older.forEach { row -> add(buildJsonObject {
            put("id", row.id); put("version", sha256Hex(row.content))
        }) } }
        val excerpts = older.joinToString("\n") { row ->
            val text = data.redact(row.content)
            val excerpt = if (text.length <= 800) text else text.take(400) + "…[中间原文未覆盖，需分段读取]…" + text.takeLast(400)
            "[chat/${row.id} 原话摘录] $excerpt"
        }
        val encodedSources = sources.toString()
        store.exec("UPDATE assistant_session SET summary=?,summary_sources_json=?,summary_version=summary_version+1 WHERE id=? AND (summary<>? OR summary_sources_json<>?)",
            excerpts, encodedSources, sessionId, excerpts, encodedSources)
    }

    fun summary(sessionId: String, beforeInputId: Long): String {
        val row = store.rows("SELECT summary,summary_sources_json FROM assistant_session WHERE id=?", sessionId) {
            it.getString(0).orEmpty() to it.getString(1).orEmpty()
        }.firstOrNull() ?: return ""
        if (!validAssistantSummarySources(store.connection, sessionId, row.second, beforeInputId)) return ""
        val sources = runCatching { Json.parseToJsonElement(row.second).jsonArray }.getOrNull() ?: return ""
        val ai = sources.any { it.jsonObject["ai"] == JsonPrimitive(true) }
        return if (ai) "[AI历史归纳，不是用户原话或确定事实；压缩可能省略细节，需检索原文核查]\n" +
            data.redact(row.first) + "\n[部分覆盖来源] " +
            sources.takeLast(20).joinToString { "chat/${it.jsonObject["id"]!!.jsonPrimitive.content}" }
        else data.redact(row.first)
    }

    fun context(): String = list().filter { it.active && it.kind != "candidate" }.take(12)
        .joinToString("\n") { "[记忆 ${it.id}; ${it.kind}; 依据 ${it.source}/${it.sourceId}] ${it.content}" }.take(8000)
}

internal data class AssistantMemorySegment(val version:String,val index:Int,val content:String)

class AssistantIndexTaskHandler(private val memory: AssistantMemoryService, private val indexer: AssistantMemoryIndexer? = null) : AsyncTaskHandler {
    override val type = "assistant_index"
    override suspend fun execute(taskId: Long, payloadJson: String, checkpointJson: String, reporter: AsyncTaskProgressReporter): AsyncTaskExecutionResult {
        val id = runCatching { Json.parseToJsonElement(payloadJson).jsonObject["sourceId"]?.jsonPrimitive?.longOrNull }.getOrNull()
            ?: return AsyncTaskExecutionResult.PermanentFailure("invalid_index", "索引引用无效")
        reporter.report(0, 1, "正在更新助手索引")
        return try {
            if (indexer == null) memory.index(id) else indexer.process(id,reporter)
            AsyncTaskExecutionResult.Success()
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (_: Exception) { AsyncTaskExecutionResult.RetryableFailure("assistant_index_failed", "记忆索引未完成，原文与已完成分段保留") }
    }
}
