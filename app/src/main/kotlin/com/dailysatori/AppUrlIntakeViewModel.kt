package com.dailysatori

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailysatori.core.service.ClipboardMonitorService
import com.dailysatori.core.worker.ArticleProcessingScheduler
import com.dailysatori.data.repository.ArticleRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext

data class AppUrlIntakeState(
    val clipboardUrl: String? = null,
    val duplicateUrl: String? = null,
    val isSavingUrl: Boolean = false,
    val saveMessage: String? = null,
)

class AppUrlIntakeViewModel(
    private val articleRepo: ArticleRepository,
    private val clipboardMonitorService: ClipboardMonitorService,
    private val articleProcessingScheduler: ArticleProcessingScheduler,
) : ViewModel() {
    private val clipboardPromptState = ClipboardUrlPromptState()
    private val clipboardCheckGate = ClipboardCheckGate()
    private var clipboardCheckJob: Job? = null
    private val _state = MutableStateFlow(AppUrlIntakeState())
    val state: StateFlow<AppUrlIntakeState> = _state.asStateFlow()

    fun handleSharedText(text: String?) {
        val url = extractFirstUrl(text) ?: return
        clipboardCheckGate.suppressNextCheck()
        clipboardCheckJob?.cancel()
        viewModelScope.launch {
            val existing = withContext(Dispatchers.IO) { articleRepo.findIntakeArticle(url) }
            if (existing != null && !shouldRetryExistingSharedArticle(existing.status)) {
                _state.update { it.copy(duplicateUrl = url, clipboardUrl = null) }
            } else {
                saveUrl(url)
            }
        }
    }

    fun checkClipboard() {
        if (!clipboardCheckGate.shouldCheck()) return
        val url = clipboardMonitorService.checkClipboard() ?: return
        if (!clipboardPromptState.shouldPrompt(url)) return
        clipboardCheckJob?.cancel()
        clipboardCheckJob = viewModelScope.launch {
            val existing = withContext(Dispatchers.IO) { articleRepo.findIntakeArticle(url) }
            if (existing != null && !shouldRetryExistingSharedArticle(existing.status)) {
                clipboardPromptState.markHandled(url)
                clipboardMonitorService.markProcessed(url)
                _state.update { it.copy(duplicateUrl = url, clipboardUrl = null) }
            } else {
                _state.update { it.copy(clipboardUrl = url, duplicateUrl = null) }
            }
        }
    }

    fun confirmClipboardUrl() {
        val url = _state.value.clipboardUrl ?: return
        if (_state.value.isSavingUrl) return
        _state.update { it.copy(isSavingUrl = true, saveMessage = null) }
        viewModelScope.launch { saveUrl(url) }
    }

    fun dismissClipboardUrl() {
        _state.value.clipboardUrl?.let {
            clipboardPromptState.markHandled(it)
            clipboardMonitorService.markProcessed(it)
        }
        _state.update { it.copy(clipboardUrl = null) }
    }

    fun dismissDuplicateUrl() {
        _state.update { it.copy(duplicateUrl = null) }
    }

    fun dismissSaveMessage() {
        _state.update { it.copy(saveMessage = null) }
    }

    private suspend fun saveUrl(url: String) {
        _state.update { it.copy(isSavingUrl = true) }
        try {
            withContext(Dispatchers.IO) { articleProcessingScheduler.enqueueSave(url) }
            clipboardPromptState.markHandled(url)
            clipboardMonitorService.markProcessed(url)
            _state.update { it.copy(clipboardUrl = null, duplicateUrl = null,
                saveMessage = "文章已保存，正在后台整理") }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            _state.update { it.copy(saveMessage = "保存未完成，请重试") }
        } finally {
            _state.update { it.copy(isSavingUrl = false) }
        }
    }
}
