package com.dailysatori.ui.feature.diary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailysatori.core.util.diaryTags
import com.dailysatori.data.repository.DiaryMonthSummaryRepository
import com.dailysatori.data.repository.DiaryAttachmentDraft
import com.dailysatori.data.repository.DiaryAttachmentKind
import com.dailysatori.data.repository.DiaryAttachmentRepository
import com.dailysatori.data.repository.DiaryRepository
import com.dailysatori.core.recording.DiaryRecordingState
import com.dailysatori.core.recording.DiaryRecordingStore
import com.dailysatori.core.worker.AsyncTaskScheduler
import com.dailysatori.service.memory.MemoryExtractor
import com.dailysatori.data.repository.DiaryThreadRepository
import com.dailysatori.service.diary.DiaryThreadOverview
import com.dailysatori.service.diary.DiaryThreadSnapshot
import com.dailysatori.service.diary.DiaryThreadSummaryCoordinator
import com.dailysatori.service.diary.DiaryTranscriptionCoordinator
import com.dailysatori.service.diary.TranscriptionRetryResult
import com.dailysatori.service.diary.DiaryMonthSummaryService
import com.dailysatori.data.repository.DiaryTagRepository
import com.dailysatori.service.diary.DiaryTagCoordinator
import com.dailysatori.service.diary.DiaryTagDraft
import com.dailysatori.service.diary.DiaryPolishedTranscript
import com.dailysatori.service.diary.DiaryTagVocabulary
import com.dailysatori.service.diary.matchesDiaryTag
import com.dailysatori.shared.db.Diary
import com.dailysatori.shared.db.Diary_attachment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.Clock

data class DiaryState(
    val diaries: List<Diary> = emptyList(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val searchQuery: String = "",
    val selectedTag: String? = null,
    val isSearchVisible: Boolean = false,
    val availableTags: List<String> = emptyList(),
    val monthSummaries: Map<String, String> = emptyMap(),
    val attachmentsByDiary: Map<Long, List<Diary_attachment>> = emptyMap(),
    val recordingState: DiaryRecordingState = DiaryRecordingState.Idle,
    val error: String? = null,
    val threadOverviews: Map<Long, DiaryThreadOverview> = emptyMap(),
    val selectedThread: DiaryThreadSnapshot? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class DiaryViewModel(
    private val diaryRepo: DiaryRepository,
    private val memoryExtractor: MemoryExtractor,
    private val monthSummaryRepo: DiaryMonthSummaryRepository,
    private val monthSummaryService: DiaryMonthSummaryService,
    private val attachmentRepo: DiaryAttachmentRepository? = null,
    private val recordingStore: DiaryRecordingStore? = null,
    private val transcriptionCoordinator: DiaryTranscriptionCoordinator? = null,
    private val taskScheduler: AsyncTaskScheduler? = null,
    private val tagRepo: DiaryTagRepository? = null,
    private val tagCoordinator: DiaryTagCoordinator? = null,
    private val threadRepo: DiaryThreadRepository? = null,
    private val threadSummaryCoordinator: DiaryThreadSummaryCoordinator? = null,
) : ViewModel() {
    private val _state = MutableStateFlow(DiaryState())
    val state: StateFlow<DiaryState> = _state.asStateFlow()

    private var loadJob: Job? = null
    private var threadJob: Job? = null
    private val visibleDiaryIds = MutableStateFlow<List<Long>>(emptyList())

    init {
        val recoveryCutoff = Clock.System.now().toEpochMilliseconds()
        observeAttachments()
        loadDiaries()
        observeMonthSummaries()
        observeRecording()
        observeTags()
        observeThreadOverviews()
        viewModelScope.launch(Dispatchers.IO) {
            if (recordingStore?.state?.value is DiaryRecordingState.Idle || recordingStore == null) {
                attachmentRepo?.recoverInterruptedRecordings(startedBefore = recoveryCutoff)
            }
            refreshAvailableTags()
            monthSummaryService.refreshRecentMonthsIfNeeded()
        }
    }

    private fun observeTags() {
        val repository = tagRepo ?: return
        viewModelScope.launch(Dispatchers.IO) {
            repository.observeCatalog().collect { catalog ->
                _state.update { it.copy(availableTags = catalog.counts.keys.sorted(),
                    selectedTag = it.selectedTag?.let(catalog.vocabulary::canonical)) }
            }
        }
    }

    /** 列表卡片只用轻量概览，不逐卡片加载整串过程。 */
    private fun observeThreadOverviews() {
        val repository = threadRepo ?: return
        viewModelScope.launch(Dispatchers.IO) {
            repository.observeOverviews().collect { overviews ->
                _state.update { it.copy(threadOverviews = overviews.associateBy { overview -> overview.rootId }) }
            }
        }
    }

    /** 打开日记串时只观察这一串的完整过程与汇总状态。 */
    fun openThread(rootId: Long) {
        val repository = threadRepo ?: return
        threadJob?.cancel()
        threadJob = viewModelScope.launch(Dispatchers.IO) {
            repository.observeThread(rootId).collect { snapshot ->
                _state.update { it.copy(selectedThread = snapshot) }
            }
        }
    }

    fun closeThread() {
        threadJob?.cancel()
        threadJob = null
        _state.update { it.copy(selectedThread = null) }
    }

    /** 显式重试：终态失败不被观察循环自动重排，只能由用户触发或新原文版本失效。 */
    fun retryThreadSummary(rootId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            runPostSaveOperation {
                val taskId = threadSummaryCoordinator?.enqueue(rootId, force = true)
                if (taskId == null) {
                    _state.update { it.copy(error = "请先在设置中配置默认 AI 模型") }
                    return@runPostSaveOperation
                }
                taskScheduler?.enqueue(taskId)
            }
        }
    }

    suspend fun getThreadSnapshot(rootId: Long): DiaryThreadSnapshot? = withContext(Dispatchers.IO) {
        threadRepo?.getSnapshot(rootId)
    }

    private fun observeRecording() {
        val store = recordingStore ?: return
        viewModelScope.launch(Dispatchers.IO) {
            store.state.collect { recordingState ->
                _state.update { it.copy(recordingState = recordingState) }
            }
        }
    }

    private fun observeAttachments() {
        val repository = attachmentRepo ?: return
        viewModelScope.launch(Dispatchers.IO) {
            repository.observeAll()
                .map { attachments -> attachments.groupBy { it.diary_id } }
                .combine(visibleDiaryIds) { attachments, diaryIds ->
                    diaryIds.associateWith { attachments[it].orEmpty() }
                }
                .collect { attachmentsByDiary ->
                    _state.update { it.copy(attachmentsByDiary = attachmentsByDiary) }
                }
        }
    }

    private fun observeMonthSummaries() {
        viewModelScope.launch(Dispatchers.IO) {
            monthSummaryRepo.getAll().collect { summaries ->
                _state.update { state ->
                    state.copy(monthSummaries = summaries.filter { it.summary.isNotBlank() }.associate { it.month_key to it.summary })
                }
            }
        }
    }

    private fun refreshAvailableTags() {
        if (tagRepo != null) {
            _state.update { it.copy(availableTags = tagRepo.currentCatalog().counts.keys.sorted()) }
            return
        }
        val vocabulary = tagRepo?.vocabulary() ?: DiaryTagVocabulary()
        val tags = diaryRepo.getAllSync()
            .flatMap { diary -> diaryTags(diary.tags).map(vocabulary::canonical) }
            .distinct()
            .sorted()
        _state.update { it.copy(availableTags = tags) }
    }

    fun loadDiaries() {
        if (loadJob?.isActive == true) return
        loadJob = viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isLoading = true) }
            val diaries = _state.map { it.searchQuery }.distinctUntilChanged()
                .flatMapLatest { query ->
                    if (query.isNotBlank()) diaryRepo.search(query) else diaryRepo.getAll()
                }
            val selectedTag = _state.map { it.selectedTag }.distinctUntilChanged()
            diaries.combine(selectedTag) { entries, tag ->
                val vocabulary = tagRepo?.vocabulary() ?: DiaryTagVocabulary()
                if (tag == null) entries else entries.filter { matchesDiaryTag(it.tags, tag, vocabulary) }
            }.collect { filtered ->
                _state.update { it.copy(diaries = filtered, isLoading = false) }
                visibleDiaryIds.value = filtered.map { it.id }
            }
        }
    }

    fun createVoiceDiary(onCreated: (diaryId: Long, attachmentId: Long) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            var createdDiaryId: Long? = null
            try {
                val repository = checkNotNull(attachmentRepo) { "Diary attachments are unavailable" }
                val diaryId = diaryRepo.create(DiaryTranscriptionCoordinator.AUTO_TRANSCRIBING_BODY)
                createdDiaryId = diaryId
                val attachmentId = repository.create(
                    diaryId,
                    DiaryAttachmentDraft(
                        kind = DiaryAttachmentKind.audio,
                        localPath = "",
                        displayName = "语音日记.m4a",
                        mimeType = "audio/mp4",
                    ),
                )
                onCreated(diaryId, attachmentId)
            } catch (error: Exception) {
                createdDiaryId?.let { diaryId -> runCatching { diaryRepo.delete(diaryId) } }
                _state.update { it.copy(error = error.message) }
            }
        }
    }

    fun search(query: String) {
        _state.update { it.copy(searchQuery = query) }
        loadDiaries()
    }

    fun filterByTag(tag: String?) {
        _state.update { it.copy(selectedTag = if (_state.value.selectedTag == tag) null else tag) }
        loadDiaries()
    }

    fun toggleSearch() {
        _state.update { it.copy(isSearchVisible = !_state.value.isSearchVisible) }
        if (!_state.value.isSearchVisible) {
            _state.update { it.copy(searchQuery = "") }
            loadDiaries()
        }
    }

    fun setError(message: String?) {
        _state.update { it.copy(error = message) }
    }

    suspend fun loadPolishedTranscripts(diaryId: Long): Map<Long, DiaryPolishedTranscript> =
        withContext(Dispatchers.IO) { attachmentRepo?.polishedTranscripts(diaryId).orEmpty() }

    fun saveDiary(
        content: String,
        tags: String? = null,
        mood: String? = null,
        images: String? = null,
        existingId: Long? = null,
        tagDraft: DiaryTagDraft? = null,
        polishedTranscripts: Map<Long, DiaryPolishedTranscript>? = null,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            saveDiaryAndGetId(
                content = content,
                tags = tags,
                mood = mood,
                images = images,
                existingId = existingId,
                tagDraft = tagDraft,
                polishedTranscripts = polishedTranscripts,
            )
        }
    }

    suspend fun saveDiaryAndGetId(
        content: String,
        tags: String? = null,
        mood: String? = null,
        images: String? = null,
        existingId: Long? = null,
        tagDraft: DiaryTagDraft? = null,
        polishedTranscripts: Map<Long, DiaryPolishedTranscript>? = null,
    ): Long? = withContext(Dispatchers.IO) {
        _state.update { it.copy(isSaving = true, error = null) }
        try {
            val contentChanged = existingId == null || diaryRepo.getById(existingId)?.content != content
            val persistedId = try {
                if (existingId != null) {
                    if (tagRepo != null && tagDraft != null) {
                        tagRepo.saveEditedDiary(existingId, content, tagDraft.tags.takeIf { tagDraft.edited }, mood, images, tagDraft)
                    } else {
                        diaryRepo.update(existingId, content, tags, mood, images)
                    }
                    existingId
                } else {
                    diaryRepo.create(content, tags, mood, images)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message) }
                return@withContext null
            }

            runPostSaveOperation {
                polishedTranscripts?.let { attachmentRepo?.savePolishedTranscripts(persistedId, it) }
                if (existingId == null && tagDraft != null) tagRepo?.saveDraft(persistedId, tagDraft)
                if (contentChanged) tagCoordinator?.enqueue(persistedId)?.let { taskScheduler?.enqueue(it) }
            }

            runPostSaveOperation {
                if (content.isNotBlank()) {
                    memoryExtractor.extractAndSave(
                        sourceType = "diary",
                        sourceId = persistedId,
                        title = "日记",
                        content = content,
                    )
                }
            }
            runPostSaveOperation(::refreshAvailableTags)
            persistedId
        } finally {
            _state.update { it.copy(isSaving = false) }
        }
    }

    /**
     * 保存一笔续写：原文始终落在独立记录，不覆盖主日记；
     * 保存成功先返回 UI，再调度汇总、主标签与原文消费者。
     */
    suspend fun saveReplyAndGetId(
        rootId: Long,
        content: String,
        mood: String? = null,
        images: String? = null,
        existingReplyId: Long? = null,
        polishedTranscripts: Map<Long, DiaryPolishedTranscript>? = null,
    ): Long? = withContext(Dispatchers.IO) {
        _state.update { it.copy(isSaving = true, error = null) }
        try {
            val repository = threadRepo
            if (repository == null || repository.getSnapshot(rootId) == null) {
                _state.update { it.copy(error = "日记串不存在") }
                return@withContext null
            }
            val replyId = if (existingReplyId != null) {
                if (repository.rootId(existingReplyId) != rootId) {
                    _state.update { it.copy(error = "续写不属于该日记") }
                    return@withContext null
                }
                diaryRepo.update(existingReplyId, content, null, mood, images)
                existingReplyId
            } else {
                repository.createReply(rootId, content, mood, images)
            }
            runPostSaveOperation {
                polishedTranscripts?.let { attachmentRepo?.savePolishedTranscripts(replyId, it) }
            }
            runPostSaveOperation {
                threadSummaryCoordinator?.enqueue(rootId)?.let { taskScheduler?.enqueue(it) }
            }
            runPostSaveOperation {
                if (content.isNotBlank()) tagCoordinator?.enqueue(rootId)?.let { taskScheduler?.enqueue(it) }
            }
            runPostSaveOperation {
                val source = repository.getSource(rootId)?.content.orEmpty()
                if (source.isNotBlank()) {
                    memoryExtractor.extractAndSave(
                        sourceType = "diary",
                        sourceId = rootId,
                        title = "日记",
                        content = source,
                    )
                }
            }
            runPostSaveOperation(::refreshAvailableTags)
            replyId
        } finally {
            _state.update { it.copy(isSaving = false) }
        }
    }

    /**
     * 录音前先建立这条续写及附件，返回 (replyId, attachmentId) 供现有录音控制器使用。
     * 占位正文不会进入汇总，也不会覆盖主日记。
     */
    suspend fun prepareReplyRecording(rootId: Long): Pair<Long, Long>? = withContext(Dispatchers.IO) {
        try {
            val repository = threadRepo ?: return@withContext null
            val attachments = attachmentRepo ?: return@withContext null
            if (repository.getSnapshot(rootId) == null) {
                _state.update { it.copy(error = "日记串不存在") }
                return@withContext null
            }
            val replyId = repository.createReply(rootId, DiaryTranscriptionCoordinator.AUTO_TRANSCRIBING_BODY)
            val attachmentId = attachments.create(
                replyId,
                DiaryAttachmentDraft(
                    kind = DiaryAttachmentKind.audio,
                    localPath = "",
                    displayName = "语音续写.m4a",
                    mimeType = "audio/mp4",
                ),
            )
            replyId to attachmentId
        } catch (error: Exception) {
            _state.update { it.copy(error = error.message) }
            null
        }
    }

    /** 取消未保存草稿：只清理无正文、无图片且无附件的空续写，有录音成果的记录不会被删。 */
    suspend fun discardDraftReply(replyId: Long): Boolean = withContext(Dispatchers.IO) {
        threadRepo?.discardEmptyReply(replyId) ?: false
    }

    private suspend fun runPostSaveOperation(operation: suspend () -> Unit) {
        try {
            operation()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.update { it.copy(error = e.message) }
        }
    }

    fun deleteDiary(id: Long, onStopRecording: () -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val store = recordingStore
                val recording = store?.state?.value
                val recordingRoot = recording?.diaryId?.let { diaryId -> threadRepo?.rootId(diaryId) ?: diaryId }
                if (recording != null && recording !is DiaryRecordingState.Idle && recordingRoot == id) {
                    onStopRecording()
                    val stopped = withTimeoutOrNull(15_000) {
                        store.state.first { state ->
                            state is DiaryRecordingState.Idle || state.diaryId != id
                        }
                    } != null
                    if (!stopped) {
                        _state.update { it.copy(error = "等待录音停止超时，未删除日记") }
                        return@launch
                    }
                }
                diaryRepo.delete(id)
                tagRepo?.deleteState(id)
                refreshAvailableTags()
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message) }
            }
        }
    }

    fun deleteAttachment(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                attachmentRepo?.delete(id)
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message) }
            }
        }
    }

    fun retryTranscription(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                when (val result = transcriptionCoordinator?.retry(id)) {
                    is TranscriptionRetryResult.Enqueued -> {
                        taskScheduler?.enqueue(result.taskId)
                        _state.update { it.copy(error = null) }
                    }
                    is TranscriptionRetryResult.Unavailable ->
                        _state.update { it.copy(error = transcriptionErrorText(result.errorCode)) }
                    null -> _state.update { it.copy(error = "语音转写服务暂不可用") }
                }
            } catch (error: Exception) {
                _state.update { it.copy(error = error.message ?: "重新转写失败") }
            }
        }
    }

    suspend fun getDiaryById(id: Long): Diary? = withContext(Dispatchers.IO) {
        diaryRepo.getById(id)
    }
}
