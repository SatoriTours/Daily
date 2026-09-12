package com.dailysatori.service.diary

import com.dailysatori.data.repository.DiaryRepository
import com.dailysatori.data.repository.DiaryThoughtRepository
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext

class DiaryThoughtService(
    private val diaryRepository: DiaryRepository,
    private val repository: DiaryThoughtRepository,
    private val generator: DiaryThoughtGenerator,
) {
    private val _state = MutableStateFlow(DiaryThoughtState())
    val state = _state.asStateFlow()
    private val requests = MutableStateFlow(0L)
    private val foreground = MutableStateFlow(true)
    private val log = Logger.withTag("DiaryThought")
    private var job: Job? = null

    /** Application 是唯一启动者；collectLatest 保证旧日记快照不会在新快照之后发布。 */
    fun start(scope: CoroutineScope, initiallyForeground: Boolean = true) {
        if (job?.isActive == true) return
        foreground.value = initiallyForeground
        job = scope.launch(Dispatchers.IO) {
            var handledRevision = 0L
            _state.value = DiaryThoughtState(corrections = repository.corrections(), useInChat = repository.useInChat())
            diaryRepository.getAll().map { diaries ->
                diaries.filter { it.content.isNotBlank() }.map { DiaryThoughtSource(it.id, it.content, it.created_at) }
            }.distinctUntilChanged().combine(requests) { sources, revision -> sources to revision }
                .combine(foreground) { snapshot, active -> Triple(snapshot.first, snapshot.second, active) }
                .collectLatest { (sources, revision, active) ->
                    val force = revision > handledRevision
                    if (active) handledRevision = revision
                    refresh(sources, force, active)
                }
        }
    }

    fun setForeground(active: Boolean) { foreground.value = active }

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

    private suspend fun refresh(sources: List<DiaryThoughtSource>, force: Boolean, active: Boolean) {
        val stored = repository.load().supportedBy(sources)
        val corrections = repository.corrections()
        val fingerprint = diaryThoughtFingerprint(sources, corrections)
        val rebuild = force && stored.fingerprint == fingerprint
        val previous = if (rebuild) stored.copy(fingerprint = "", mergeCheckpoint = null) else stored
        val stale = previous.fingerprint != fingerprint
        _state.update { it.copy(
            archive = previous, corrections = corrections, isStale = stale, error = null,
            isUpdating = stale && active, isPaused = stale && !active,
            progress = if (stale && !active) "整理已暂停，回到前台后自动继续" else "准备更新…",
        ) }
        if (stale) repository.save(previous)
        if (!active || !stale) return
        try {
            delay(2_000)
            for (attempt in 0..2) {
                if (generateArchive(sources, corrections, attempt)) return
                delay(if (attempt == 0) 5_000 else 15_000)
            }
        } finally {
            _state.update { it.copy(isUpdating = false) }
        }
    }

    /** collectLatest 是唯一执行者；每次重试重新读取已持久化的读取和合并断点。 */
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
            // AI 请求自身的超时不代表整理监听被取消；真正的后台切换/新快照取消仍须立即传播。
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
