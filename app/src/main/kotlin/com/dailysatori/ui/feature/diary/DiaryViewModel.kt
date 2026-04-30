package com.dailysatori.ui.feature.diary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailysatori.data.repository.DiaryRepository
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
    val error: String? = null,
)

class DiaryViewModel(
    private val diaryRepo: DiaryRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(DiaryState())
    val state: StateFlow<DiaryState> = _state.asStateFlow()

    private var loadJob: Job? = null

    init {
        loadDiaries()
        viewModelScope.launch(Dispatchers.IO) {
            refreshAvailableTags()
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
