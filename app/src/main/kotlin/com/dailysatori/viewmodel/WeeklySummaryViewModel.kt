package com.dailysatori.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailysatori.data.repository.WeeklySummaryRepository
import com.dailysatori.service.weekly.WeeklySummaryService
import com.dailysatori.shared.db.Weekly_summary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WeeklySummaryState(
    val summaries: List<Weekly_summary> = emptyList(),
    val currentSummary: Weekly_summary? = null,
    val isLoading: Boolean = false,
    val isGenerating: Boolean = false,
    val error: String? = null,
)

class WeeklySummaryViewModel(
    private val weeklySummaryService: WeeklySummaryService,
    private val repo: WeeklySummaryRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(WeeklySummaryState())
    val state: StateFlow<WeeklySummaryState> = _state.asStateFlow()

    init {
        loadSummaries()
    }

    fun loadSummaries() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isLoading = true) }
            try {
                repo.getAll().collect { summaries ->
                    val latest = summaries.firstOrNull()
                    _state.update {
                        it.copy(
                            summaries = summaries,
                            currentSummary = latest,
                            isLoading = false,
                        )
                    }
                }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message, isLoading = false) }
            }
        }
    }

    fun checkAndGenerate() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isGenerating = true, error = null) }
            try {
                val range = weeklySummaryService.getLastCompletedWeekRange()
                if (range != null) {
                    weeklySummaryService.generateWeeklySummary(range.first, range.second)
                }
                loadSummaries()
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message) }
            } finally {
                _state.update { it.copy(isGenerating = false) }
            }
        }
    }

    fun regenerateCurrentSummary() {
        checkAndGenerate()
    }

    fun selectSummary(summary: Weekly_summary) {
        _state.update { it.copy(currentSummary = summary) }
    }
}
