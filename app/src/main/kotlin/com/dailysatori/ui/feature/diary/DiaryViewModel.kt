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
import com.dailysatori.service.memory.MemoryExtractor
import com.dailysatori.service.diary.DiaryTranscriptionCoordinator
import com.dailysatori.service.diary.DiaryMonthSummaryService
import com.dailysatori.shared.db.Diary
import com.dailysatori.shared.db.Diary_attachment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
)

class DiaryViewModel(
    private val diaryRepo: DiaryRepository,
    private val memoryExtractor: MemoryExtractor,
    private val monthSummaryRepo: DiaryMonthSummaryRepository,
    private val monthSummaryService: DiaryMonthSummaryService,
    private val attachmentRepo: DiaryAttachmentRepository? = null,
    private val recordingStore: DiaryRecordingStore? = null,
) : ViewModel() {
    private val _state = MutableStateFlow(DiaryState())
    val state: StateFlow<DiaryState> = _state.asStateFlow()

    private var loadJob: Job? = null
    private val attachmentJobs = mutableMapOf<Long, Job>()

    init {
        loadDiaries()
        observeMonthSummaries()
        observeRecording()
        viewModelScope.launch(Dispatchers.IO) {
            refreshAvailableTags()
            monthSummaryService.refreshRecentMonthsIfNeeded()
        }
    }

    private fun observeRecording() {
        val store = recordingStore ?: return
        viewModelScope.launch(Dispatchers.IO) {
            store.state.collect { recordingState ->
                _state.update { it.copy(recordingState = recordingState) }
            }
        }
    }

    private fun observeAttachments(diaries: List<Diary>) {
        val repository = attachmentRepo ?: return
        val diaryIds = diaries.mapTo(mutableSetOf()) { it.id }
        attachmentJobs.keys.filterNot(diaryIds::contains).forEach { id ->
            attachmentJobs.remove(id)?.cancel()
            _state.update { it.copy(attachmentsByDiary = it.attachmentsByDiary - id) }
        }
        diaries.forEach { diary ->
            if (attachmentJobs.containsKey(diary.id)) return@forEach
            attachmentJobs[diary.id] = viewModelScope.launch(Dispatchers.IO) {
                repository.observeForDiary(diary.id).collect { attachments ->
                    _state.update {
                        it.copy(attachmentsByDiary = it.attachmentsByDiary + (diary.id to attachments))
                    }
                }
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
        val tags = diaryRepo.getAllSync()
            .flatMap { diary -> diaryTags(diary.tags) }
            .distinct()
            .sorted()
        _state.update { it.copy(availableTags = tags) }
    }

    fun loadDiaries() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isLoading = true) }
            val currentState = _state.value
            val flow = when {
                currentState.searchQuery.isNotBlank() -> diaryRepo.search(currentState.searchQuery)
                else -> diaryRepo.getAll()
            }
            flow.collect { diaries ->
                val filtered = if (currentState.selectedTag != null) {
                    diaries.filter { d -> d.tags?.contains(currentState.selectedTag) == true }
                } else {
                    diaries
                }
                _state.update { it.copy(diaries = filtered, isLoading = false) }
                observeAttachments(filtered)
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

    fun saveDiary(
        content: String,
        tags: String? = null,
        mood: String? = null,
        images: String? = null,
        existingId: Long? = null,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            saveDiaryAndGetId(
                content = content,
                tags = tags,
                mood = mood,
                images = images,
                existingId = existingId,
            )
        }
    }

    suspend fun saveDiaryAndGetId(
        content: String,
        tags: String? = null,
        mood: String? = null,
        images: String? = null,
        existingId: Long? = null,
    ): Long? = withContext(Dispatchers.IO) {
        _state.update { it.copy(isSaving = true, error = null) }
        try {
            val persistedId = try {
                if (existingId != null) {
                    diaryRepo.update(existingId, content, tags, mood, images)
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

    private suspend fun runPostSaveOperation(operation: suspend () -> Unit) {
        try {
            operation()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.update { it.copy(error = e.message) }
        }
    }

    fun deleteDiary(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                diaryRepo.delete(id)
                refreshAvailableTags()
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message) }
            }
        }
    }
}
