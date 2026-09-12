package com.dailysatori.service.diary

import com.dailysatori.data.repository.DiaryRepository
import com.dailysatori.data.repository.DiaryThoughtRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
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
    private var job: Job? = null

    /** Application 是唯一启动者；collectLatest 保证旧日记快照不会在新快照之后发布。 */
    fun start(scope: CoroutineScope) {
        if (job != null) return
        job = scope.launch(Dispatchers.IO) {
            _state.value = DiaryThoughtState(corrections = repository.corrections(), useInChat = repository.useInChat())
            diaryRepository.getAll().map { diaries ->
                diaries.filter { it.content.isNotBlank() }.map { DiaryThoughtSource(it.id, it.content, it.created_at) }
            }.distinctUntilChanged().combine(requests) { sources, revision -> sources to revision }
                .collectLatest { (sources, revision) -> refresh(sources, revision > 0) }
        }
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

    private suspend fun refresh(sources: List<DiaryThoughtSource>, force: Boolean) {
        val previous = repository.load().supportedBy(sources)
        val corrections = repository.corrections()
        val stale = previous.fingerprint != diaryThoughtFingerprint(sources, corrections)
        _state.update { it.copy(archive = previous, corrections = corrections, isStale = stale, error = null) }
        try {
            if (stale) repository.save(previous)
            if (stale || force) delay(2_000)
            _state.update { it.copy(isUpdating = stale || force) }
            val archive = generator.generate(sources, corrections, previous, force,
                onProgress = { progress -> _state.update { it.copy(progress = progress) } },
                onCheckpoint = repository::save,
            )
            coroutineContext.ensureActive()
            repository.save(archive)
            _state.update { it.copy(archive = archive, isUpdating = false, isStale = false, progress = "") }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            val message = if (error is DiaryAssistantMissingConfigurationException) "请先在设置中配置默认 AI 模型" else "思想档案更新失败，请稍后重试"
            _state.update { it.copy(isUpdating = false, isStale = true, progress = "", error = message) }
        }
    }
}
