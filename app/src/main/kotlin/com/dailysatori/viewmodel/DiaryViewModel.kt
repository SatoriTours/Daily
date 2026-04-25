package com.dailysatori.viewmodel

import androidx.lifecycle.ViewModel
import com.dailysatori.data.repository.DiaryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class DiaryState(
    val diaries: List<com.dailysatori.shared.db.Diary> = emptyList(),
    val isLoading: Boolean = false,
    val searchQuery: String = "",
    val selectedTagId: Long? = null,
    val isSearchVisible: Boolean = false,
)

class DiaryViewModel(
    private val diaryRepo: DiaryRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(DiaryState())
    val state: StateFlow<DiaryState> = _state

    fun loadDiaries() {
        _state.value = _state.value.copy(isLoading = true)
        _state.value = _state.value.copy(isLoading = false)
    }

    fun search(query: String) {
        _state.value = _state.value.copy(searchQuery = query)
    }

    fun toggleSearch() {
        _state.value = _state.value.copy(isSearchVisible = !_state.value.isSearchVisible)
    }
}
