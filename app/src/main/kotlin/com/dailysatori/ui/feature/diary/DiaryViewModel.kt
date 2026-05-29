package com.dailysatori.ui.feature.diary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailysatori.data.repository.DiaryMonthSummaryRepository
import com.dailysatori.data.repository.DiaryRepository
import com.dailysatori.service.memory.MemoryExtractService
import com.dailysatori.service.diary.DiaryMonthSummaryService
import com.dailysatori.shared.db.Diary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DiaryState(
    val diaries: List<Diary> = emptyList(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val searchQuery: String = "",
    val selectedTag: String? = null,
    val isSearchVisible: Boolean = false,
    val availableTags: List<String> = emptyList(),
    val monthSummaries: Map<String, String> = emptyMap(),
    val error: String? = null,
)

class DiaryViewModel(
    private val diaryRepo: DiaryRepository,
    private val memoryExtractService: MemoryExtractService,
    private val monthSummaryRepo: DiaryMonthSummaryRepository,
    private val monthSummaryService: DiaryMonthSummaryService,
) : ViewModel() {
    private val _state = MutableStateFlow(DiaryState())
    val state: StateFlow<DiaryState> = _state.asStateFlow()

    private var loadJob: Job? = null

    init {
        loadDiaries()
        observeMonthSummaries()
        viewModelScope.launch(Dispatchers.IO) {
            refreshAvailableTags()
            monthSummaryService.refreshRecentMonthsIfNeeded()
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
        val allDiaries = diaryRepo.getAllSync()
        val tags = allDiaries
            .flatMap { diary ->
                diary.tags
                    ?.split(",")
                    ?.map { it.trim() }
                    ?.filter { it.isNotBlank() && it != "null" }
                    ?: emptyList()
            }
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
            _state.update { it.copy(isSaving = true, error = null) }
            try {
                if (existingId != null) {
                    diaryRepo.update(existingId, content, tags, mood, images)
                } else {
                    diaryRepo.insert(content, tags, mood, images)
                }
                if (content.isNotBlank()) {
                    memoryExtractService.extractAndSave(
                        sourceType = "diary",
                        sourceId = existingId ?: 0L,
                        title = "日记",
                        content = content,
                    )
                }
                refreshAvailableTags()
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message) }
            } finally {
                _state.update { it.copy(isSaving = false) }
            }
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
