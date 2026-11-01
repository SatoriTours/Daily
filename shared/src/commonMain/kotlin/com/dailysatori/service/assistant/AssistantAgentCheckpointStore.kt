package com.dailysatori.service.assistant

import ai.koog.agents.snapshot.feature.AgentCheckpointData
import ai.koog.agents.snapshot.providers.PersistencyStorageProvider
import app.cash.sqldelight.db.SqlDriver
import com.dailysatori.data.repository.*
import com.dailysatori.service.assistant.data.*
import com.dailysatori.service.externalfavorites.sha256Hex
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import kotlin.time.TimeSource

/** Koog state is an existing task checkpoint, never a second queue or a second lease owner. */
internal class AssistantAgentCheckpointStore(
    driver: SqlDriver, private val turn: AssistantTurn, private val owner: String,
    private val contextHash: String, private val revision: Long,
    private val selectedContextHash: String, private val contextStillValid: () -> Boolean,
    summarySources: String = "[]",
    private val timeSource: TimeSource = TimeSource.Monotonic,
) : PersistencyStorageProvider {
    private val store = AssistantStore(driver)
    private val db = DailySatoriDatabase(driver)
    private val tasks = AsyncTaskRepository(db)
    private val turns = AssistantTurnRepository(driver)
    private val privacy = AssistantPrivacyRepository(driver)
    private val settings = SettingRepository(db)
    private val data = AssistantDataGateway(driver)
    private val inputVersion = turns.input(turn)?.let(::sha256Hex).orEmpty()
    private val reads = linkedMapOf<String, AssistantReadStamp>()
    private var latest: AgentCheckpointData? = null
    private var lastPartialAt = timeSource.markNow()
    private var lastPartial: String? = null
    private var activeSummarySources = summarySources
    private var requestState = ""

    fun requestState(stage: String) {
        if (stage == requestState) return
        guard(validateDerived = false)
        val message = when (stage) {
            "queued" -> "等待 AI 请求配额"
            "requesting" -> "等待 AI 返回"
            "receiving" -> "正在接收回答"
            else -> "正在处理返回内容"
        }
        tasks.withRunningLease(turn.taskId, owner) {
            val task = requireNotNull(tasks.getById(turn.taskId))
            tasks.updateProgress(turn.taskId, task.progress_current, task.progress_total, message, task.checkpoint_json)
        } ?: throw CancellationException("助手任务已失权")
        requestState = stage
    }

    fun guard(validateDerived: Boolean = true) {
        if (!turns.canExecute(turns.get(turn.id) ?: throw CancellationException("输入不存在"), owner) ||
            settings.get(PersistentAssistantConversation.CONSENT_KEY) != "true") throw CancellationException("助手执行权限已改变")
        if (privacy.revision() != revision || turns.input(turn)?.let(::sha256Hex) != inputVersion || !contextStillValid() ||
            (validateDerived && !validAssistantSummarySources(store.connection, turn.sessionId, activeSummarySources, turn.id))) {
            clear()
            throw AssistantContextChangedException()
        }
    }

    fun recordRead(name: String, args: String, result: String) {
        guard()
        require(reads.size < 128 || "$name:$args" in reads)
        reads["$name:$args"] = AssistantReadStamp(name, args, sha256Hex(result))
    }

    override suspend fun getCheckpoints(): List<AgentCheckpointData> = listOfNotNull(getLatestCheckpoint())

    override suspend fun getLatestCheckpoint(): AgentCheckpointData? {
        guard()
        val saved = decode(tasks.getById(turn.taskId)?.checkpoint_json.orEmpty()) ?: return null
        if (!valid(saved) || !validAssistantSummarySources(store.connection, turn.sessionId, saved.summarySources, turn.id) ||
            !validateReads(saved.reads)) { clear(); return null }
        activeSummarySources = saved.summarySources
        reads.putAll(saved.reads.associateBy { "${it.name}:${it.args}" })
        latest = saved.snapshot
        return latest
    }

    override suspend fun saveCheckpoint(agentCheckpointData: AgentCheckpointData) {
        guard()
        latest = agentCheckpointData
        save(AssistantAgentState(inputVersion = inputVersion, privacyRevision = revision, contextHash = contextHash,
            selectedContextHash = selectedContextHash, summarySources = activeSummarySources, snapshot = agentCheckpointData, reads = reads.values.toList(), actionsHash = actionHash()))
    }

    fun partial(text: String) {
        if (text == lastPartial) return
        // Publish the latest prefix at most once per 50ms, without a typewriter queue.
        // Empty prefixes clear a model round immediately; the first text is never throttled.
        if (text.isNotEmpty() && !lastPartial.isNullOrEmpty() && lastPartialAt.elapsedNow().inWholeMilliseconds < 50) return
        guard(validateDerived = false)
        lastPartial = text
        lastPartialAt = timeSource.markNow()
        val previous = decode(tasks.getById(turn.taskId)?.checkpoint_json.orEmpty())
        save(AssistantAgentState(inputVersion = inputVersion, privacyRevision = revision, contextHash = contextHash,
            selectedContextHash = selectedContextHash, summarySources = activeSummarySources, snapshot = latest ?: previous?.snapshot, reads = reads.values.toList(), actionsHash = actionHash(), partial = data.redact(text)), validateDerived = false)
    }

    private fun save(state: AssistantAgentState, validateDerived: Boolean = true) {
        val encoded = codec.encodeToString(state)
        // Checkpoints contain no credentials. If a state cannot be persisted verbatim and safely,
        // retain the original/actions, but do not silently corrupt tool arguments while serializing.
        val safe = if (encoded.length <= 2_000_000 && data.redact(encoded) == encoded) encoded else ""
        tasks.withRunningLease(turn.taskId, owner) {
            guard(validateDerived)
            val task = requireNotNull(tasks.getById(turn.taskId))
            tasks.updateProgress(turn.taskId, task.progress_current, task.progress_total, task.progress_message, safe)
            true
        } ?: throw CancellationException("助手任务已失权")
        // SQLDelight updateProgress already notifies async_task observers after commit.
    }

    private fun clear() {
        tasks.withRunningLease(turn.taskId, owner) {
            val task = requireNotNull(tasks.getById(turn.taskId))
            tasks.updateProgress(turn.taskId, task.progress_current, task.progress_total, task.progress_message, "")
            true
        }
    }

    private fun valid(state: AssistantAgentState): Boolean = state.version == 1 && state.format == FORMAT &&
        state.inputVersion == inputVersion && state.privacyRevision == revision && state.contextHash == contextHash && state.actionsHash == actionHash() &&
        state.snapshot?.nodeId in NODE_IDS &&
        // Elapsed time is not evidence that the saved dependencies have changed. getLatestCheckpoint
        // revalidates every source, privacy revision, input and committed action before resuming.
        state.snapshot?.createdAt?.toEpochMilliseconds()?.let { it <= Clock.System.now().toEpochMilliseconds() + 60_000 } == true

    private fun actionHash(): String = sha256Hex(store.rows(
        "SELECT id,status,target_id,task_id FROM assistant_action WHERE turn_id=? ORDER BY id", turn.id,
    ) { listOf(it.getString(0), it.getString(1), it.getString(2), it.getLong(3)).joinToString(":") }.joinToString("\n"))

    private suspend fun validateReads(stamps: List<AssistantReadStamp>): Boolean {
        if (stamps.size > 128) return false
        return try {
            stamps.all { stamp ->
                val args = Json.parseToJsonElement(stamp.args).jsonObject
                val current = when (stamp.name) {
                    "list_data_sources" -> data.catalog()
                    "query_data" -> data.query(Json.decodeFromString<AssistantQuery>(stamp.args))
                    "aggregate_data" -> data.aggregate(Json.decodeFromString<AssistantAggregate>(stamp.args))
                    "read_record" -> data.read(args["source"]!!.jsonPrimitive.content, args["id"]!!.jsonPrimitive.content,
                        args["offset"]?.jsonPrimitive?.intOrNull ?: 0)
                    "search_records" -> data.search(args["query"]!!.jsonPrimitive.content,
                        args["sources"]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty(),
                        args["limit"]?.jsonPrimitive?.intOrNull ?: 20, args["offset"]?.jsonPrimitive?.longOrNull ?: 0)
                    else -> return false
                }
                sha256Hex(current.toString()) == stamp.hash
            }
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { false }
    }

    companion object {
        private const val FORMAT = "koog-0.3.0/daily-steps-v1"
        private val codec = Json { encodeDefaults = true }
        val NODE_IDS = setOf("ask_model", "prepare_tools", "execute_one_tool", "send_results", "deliver_answer", "budget_exhausted")
        private fun decode(value: String): AssistantAgentState? = runCatching { codec.decodeFromString<AssistantAgentState>(value) }.getOrNull()
        fun displayedPartial(value: String, revision: Long, input: String, selectedContext: String): String {
            val state = decode(value) ?: return ""
            return if (state.version == 1 && state.format == FORMAT && state.privacyRevision == revision &&
                state.inputVersion == sha256Hex(input) && state.selectedContextHash == sha256Hex(selectedContext)) state.partial else ""
        }
    }
}

@Serializable
private data class AssistantAgentState(
    val version: Int = 1, val format: String = "koog-0.3.0/daily-steps-v1",
    val inputVersion: String, val privacyRevision: Long, val contextHash: String, val selectedContextHash: String,
    val snapshot: AgentCheckpointData? = null, val reads: List<AssistantReadStamp> = emptyList(),
    val partial: String = "", val actionsHash: String = "", val summarySources: String = "[]",
)

internal class AssistantContextChangedException : CancellationException("助手资料已改变，需要重新构建上下文")

@Serializable
private data class AssistantReadStamp(val name: String, val args: String, val hash: String)
