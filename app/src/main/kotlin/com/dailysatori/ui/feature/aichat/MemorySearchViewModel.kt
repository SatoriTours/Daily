package com.dailysatori.ui.feature.aichat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailysatori.data.repository.ArticleRepository
import com.dailysatori.data.repository.BookRepository
import com.dailysatori.data.repository.BookViewpointRepository
import com.dailysatori.data.repository.DiaryRepository
import com.dailysatori.data.repository.MemoryRepository
import com.dailysatori.service.memory.MemoryExtractService
import com.dailysatori.shared.db.Memory_entry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

data class MemorySearchState(
    val searchQuery: String = "",
    val memories: List<Memory_entry> = emptyList(),
    val isRebuilding: Boolean = false,
    val rebuildProgress: String = "",
)

class MemorySearchViewModel(
    private val memoryRepo: MemoryRepository,
    private val extractService: MemoryExtractService,
    private val articleRepo: ArticleRepository,
    private val diaryRepo: DiaryRepository,
    private val bookRepo: BookRepository,
    private val viewpointRepo: BookViewpointRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(MemorySearchState())
    val state: StateFlow<MemorySearchState> = _state.asStateFlow()
    private var searchJob: Job? = null
    private var rebuildJob: Job? = null
    private val sheetSession = AtomicLong(0L)

    fun openSheet() {
        val session = sheetSession.incrementAndGet()
        searchJob?.cancel()
        _state.value = MemorySearchState()
        loadMemories("", session)
    }

    fun closeSheet() {
        sheetSession.incrementAndGet()
        searchJob?.cancel()
        searchJob = null
        _state.value = MemorySearchState()
    }

    fun loadMemories() {
        loadMemories(_state.value.searchQuery, sheetSession.get())
    }

    fun search(query: String) {
        val session = sheetSession.get()
        updateIfCurrent(session) { it.copy(searchQuery = query) }
        loadMemories(query, session)
    }

    fun rebuildAll() {
        if (rebuildJob?.isCompleted == false) return
        val session = sheetSession.get()
        rebuildJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                updateIfCurrent(session) { it.copy(isRebuilding = true) }
                extractService.rebuildAll(
                    articleRepo,
                    diaryRepo,
                    bookRepo,
                    viewpointRepo,
                    onProgress = { progress ->
                        updateIfCurrent(session) { it.copy(rebuildProgress = progress) }
                    },
                )
                val memories = memoryRepo.getAllSync()
                updateIfCurrent(session) { it.copy(memories = memories) }
            } finally {
                updateIfCurrent(session) { it.copy(isRebuilding = false) }
            }
        }
    }

    private fun loadMemories(query: String, session: Long) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch(Dispatchers.IO) {
            val memories = if (query.isBlank()) {
                memoryRepo.getAllSync()
            } else {
                memoryRepo.search(query, 50)
            }
            updateIfCurrent(session) { state ->
                if (state.searchQuery == query) state.copy(memories = memories) else state
            }
        }
    }

    private fun updateIfCurrent(
        session: Long,
        transform: (MemorySearchState) -> MemorySearchState,
    ) {
        if (session == sheetSession.get()) {
            _state.update { state ->
                if (session == sheetSession.get()) transform(state) else state
            }
        }
    }
}
