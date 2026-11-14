package com.dailysatori.service.diary

import com.dailysatori.data.repository.DiaryThoughtRepository
import com.dailysatori.data.repository.DiaryThreadRepository
import co.touchlab.kermit.Logger
import com.dailysatori.service.diagnostics.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.coroutineContext

/** 后台整理的有界结果：区分“已处理”、“确实没有待处理工作”和“来源尚未就绪”。 */
sealed interface DiaryRefreshOutcome {
    data object Completed : DiaryRefreshOutcome
    data object NothingPending : DiaryRefreshOutcome
    data object NotReady : DiaryRefreshOutcome
}

internal const val DIARY_REQUEST_WAIT_MS = 30_000L

class DiaryThoughtService(
    private val threads: DiaryThreadRepository,
    private val repository: DiaryThoughtRepository,
    private val generator: DiaryThoughtGenerator,
    /** 等待来源快照就绪的上限。就绪后由 [pending] 决定是否真有工作；未就绪必须有界返回。 */
    private val requestWaitMs: Long = DIARY_REQUEST_WAIT_MS,
) {
    private val _state = MutableStateFlow(DiaryThoughtState())
    val state = _state.asStateFlow()
    private val requests = MutableStateFlow(0L)
    private val pending = MutableStateFlow<RefreshRequest?>(null)
    private val execution = Mutex()
    private var handledRevision = 0L
    private var completedRequest: RefreshRequest? = null
    private val log = Logger.withTag("DiaryThought")
    private var job: Job? = null

    /** Application 只观察变更并调度持久任务；后台执行者独占生成和断点写入。 */
    fun start(scope: CoroutineScope, scheduleRefresh: () -> Unit) {
        if (job?.isActive == true) return
        job = scope.launch(Dispatchers.IO) {
            try {
                _state.value = DiaryThoughtState(corrections = repository.corrections(), useInChat = repository.useInChat())
                threads.observeSources().map { sources ->
                    sources.filter { it.content.isNotBlank() }
                        .map { DiaryThoughtSource(it.rootId, it.content, it.createdAt) }
                }.distinctUntilChanged().combine(requests) { sources, revision -> sources to revision }
                    .collectLatest { (sources, revision) -> updatePendingRefresh(sources, revision, scheduleRefresh) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                observationFailed(error)
            }
        }
    }

    private fun updatePendingRefresh(sources: List<DiaryThoughtSource>, revision: Long, scheduleRefresh: () -> Unit) {
        try {
            val corrections = repository.corrections()
            val archive = repository.load().supportedBy(sources)
            val stale = archive.fingerprint != diaryThoughtFingerprint(sources, corrections)
            val needsWork = stale || revision > (pending.value?.revision ?: 0L)
            _state.update { it.copy(
                archive = archive, corrections = corrections, isStale = stale,
                isUpdating = needsWork, error = null,
                progress = if (needsWork) "等待后台整理…" else "",
            ) }
            pending.value = RefreshRequest(sources, corrections, revision)
            if (needsWork) scheduleRefresh()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            observationFailed(error)
        }
    }

    private fun observationFailed(error: Exception) {
        log.w { "Diary thought startup observation failed (${error::class.simpleName})" }
        _state.update { it.copy(isUpdating = false, progress = "", error = "思想整理暂时不可用，请稍后重试") }
        // 只记固定阶段值（白名单键 diaryStage），不记录异常类型/消息/日记正文。
        DiagnosticLog.diagnostics.emit(
            DiagnosticCode.OPERATION_FAILED, DiagnosticSource.DIARY,
            fields = mapOf("diaryStage" to "sources_observation_failed"),
        )
    }

    /**
     * 来源快照是否已就绪：只有观察者填入请求后才知道有没有工作。
     * 尚未加载时最多等待 [requestWaitMs]，绝不无限等待。
     */
    suspend fun awaitPendingRequest(): Boolean =
        withTimeoutOrNull(requestWaitMs) { pending.filterNotNull().first() } != null

    /** Worker 生命周期拥有执行权；日记变更取消旧快照，系统中断后从持久断点续作。 */
    suspend fun runPendingRefresh(): DiaryRefreshOutcome =
        DiagnosticLog.diagnostics.operation(DiagnosticSource.DIARY) {
            runPendingRefreshRecorded()
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun runPendingRefreshRecorded(): DiaryRefreshOutcome = execution.withLock {
        // 暂未加载（或观察失败）时就绪信号不会出现：有界返回，把前台让给真正的调度者。
        if (!awaitPendingRequest()) return@withLock DiaryRefreshOutcome.NotReady
        var generated = false
        pending.filterNotNull().transformLatest { request ->
            if (request != completedRequest) {
                val force = request.revision > handledRevision
                handledRevision = request.revision
                generated = refresh(request.sources, request.corrections, force) || generated
                completedRequest = request
            }
            emit(Unit)
        }.first()
        if (generated) DiaryRefreshOutcome.Completed else DiaryRefreshOutcome.NothingPending
    }

    fun requestRefresh() = requests.update { it + 1 }

    fun setUseInChat(enabled: Boolean) {
        repository.setUseInChat(enabled)
        _state.update { it.copy(useInChat = enabled) }
    }

    fun saveCorrections(text: String) {
        repository.saveCorrections(text)
        _state.update { it.copy(corrections = text.trim()) }
        requestRefresh()
    }

    /** @return 是否真的执行了生成（快照未变化时为 false，代表确实没有待处理工作）。 */
    private suspend fun refresh(sources: List<DiaryThoughtSource>, corrections: String, force: Boolean): Boolean {
        val stored = repository.load().supportedBy(sources)
        val fingerprint = diaryThoughtFingerprint(sources, corrections)
        val rebuild = force && stored.fingerprint == fingerprint
        val previous = if (rebuild) stored.copy(fingerprint = "", mergeCheckpoint = null) else stored
        val stale = previous.fingerprint != fingerprint
        _state.update { it.copy(
            archive = previous, corrections = corrections, isStale = stale, error = null,
            isUpdating = stale, isPaused = false,
            progress = if (stale) "准备更新…" else "",
        ) }
        if (stale) repository.save(previous)
        if (!stale) return false
        try {
            delay(2_000)
            for (attempt in 0..2) {
                if (generateArchive(sources, corrections, attempt)) return true
                delay(if (attempt == 0) 5_000 else 15_000)
            }
        } finally {
            _state.update { it.copy(isUpdating = false) }
        }
        return true
    }

    /** 每次重试重新读取已持久化的读取和合并断点。 */
    private suspend fun generateArchive(sources: List<DiaryThoughtSource>, corrections: String, attempt: Int): Boolean {
        try {
            _state.update { it.copy(error = null) }
            val archive = generator.generate(sources, corrections, repository.load(),
                onProgress = { progress -> _state.update { it.copy(progress = progress) } },
                onCheckpoint = repository::save,
            )
            coroutineContext.ensureActive()
            repository.save(archive)
            _state.update { it.copy(archive = archive, isUpdating = false, isStale = false, progress = "", error = null) }
            return true
        } catch (error: Exception) {
            // AI 超时不代表任务被取消；系统停止任务/新快照取消仍须立即传播。
            coroutineContext.ensureActive()
            val failure = diaryThoughtFailure(error)
            val retry = failure.retryable && attempt < 2
            val stage = _state.value.progress
            log.w { "Generation failed: stage=$stage type=${error::class.simpleName} attempt=${attempt + 1}" }
            _state.update { it.copy(
                isUpdating = retry, isStale = true,
                progress = if (retry) "$stage · ${if (attempt == 0) 5 else 15} 秒后自动重试 ${attempt + 1}/2" else stage,
                error = failure.message + if (retry) "，已保存进度" else "。已保存进度，可点击更新继续",
            ) }
            return !retry
        }
    }

    private data class RefreshRequest(val sources: List<DiaryThoughtSource>, val corrections: String, val revision: Long)
}

internal class DiaryThoughtResponseException(message: String) : IllegalArgumentException(message)

internal data class DiaryThoughtFailure(val message: String, val retryable: Boolean = true)

internal fun diaryThoughtFailure(error: Exception): DiaryThoughtFailure {
    val message = error.message.orEmpty().lowercase()
    return when {
        error is DiaryAssistantMissingConfigurationException -> DiaryThoughtFailure("请先在设置中配置默认 AI 模型", false)
        error is DiaryThoughtResponseException -> DiaryThoughtFailure(error.message.orEmpty())
        error is TimeoutCancellationException || error::class.simpleName.orEmpty().contains("Timeout") ->
            DiaryThoughtFailure("AI 响应超时")
        Regex("(^|\\D)(401|403)(\\D|$)").containsMatchIn(message) ||
            listOf("unauthorized", "forbidden", "invalid api key", "authentication").any { it in message } ->
            DiaryThoughtFailure("AI 鉴权失败，请检查默认模型的密钥和权限", false)
        "429" in message || "rate limit" in message -> DiaryThoughtFailure("AI 服务限流，请稍后继续")
        "context length" in message || "context_length" in message ->
            DiaryThoughtFailure("内容超出当前模型的上下文限制，请更换模型后继续", false)
        else -> DiaryThoughtFailure("AI 请求未完成，请检查网络或模型服务")
    }
}
