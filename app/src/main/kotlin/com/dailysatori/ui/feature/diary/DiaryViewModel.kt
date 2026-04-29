package com.dailysatori.ui.feature.diary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailysatori.data.repository.DiaryRepository
import com.dailysatori.data.repository.TagRepository
import com.dailysatori.shared.db.Diary
import com.dailysatori.shared.db.Tag
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
    val selectedTagId: Long? = null,
    val isSearchVisible: Boolean = false,
    val tags: List<Tag> = emptyList(),
    val error: String? = null,
)

class DiaryViewModel(
    private val diaryRepo: DiaryRepository,
    private val tagRepo: TagRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(DiaryState())
    val state: StateFlow<DiaryState> = _state.asStateFlow()

    private var loadJob: Job? = null

    init {
        loadDiaries()
        viewModelScope.launch(Dispatchers.IO) {
            tagRepo.getAll().collect { tags ->
                _state.update { it.copy(tags = tags) }
            }
        }
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
                val filtered = if (currentState.selectedTagId != null) {
                    val tagName = currentState.tags.find { it.id == currentState.selectedTagId }?.name
                    diaries.filter { d -> d.tags?.contains(tagName ?: "") == true }
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

    fun filterByTag(tagId: Long?) {
        _state.update { it.copy(selectedTagId = tagId) }
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
        existingId: Long? = null,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isSaving = true, error = null) }
            try {
                if (existingId != null) {
                    diaryRepo.update(existingId, content, tags, mood, null)
                } else {
                    diaryRepo.insert(content, tags, mood)
                }
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
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message) }
            }
        }
    }
}
