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

data class AppUrlIntakeState(
    val clipboardUrl: String? = null,
    val duplicateUrl: String? = null,
    val isSavingUrl: Boolean = false,
)

class AppUrlIntakeViewModel(
    private val articleRepo: ArticleRepository,
    private val clipboardMonitorService: ClipboardMonitorService,
    private val articleProcessingScheduler: ArticleProcessingScheduler,
) : ViewModel() {
    private val clipboardPromptState = ClipboardUrlPromptState()
    private val clipboardCheckGate = ClipboardCheckGate()
    private val _state = MutableStateFlow(AppUrlIntakeState())
    val state: StateFlow<AppUrlIntakeState> = _state.asStateFlow()

    fun handleSharedText(text: String?) {
        val url = extractFirstUrl(text) ?: return
        clipboardCheckGate.suppressNextCheck()
        viewModelScope.launch(Dispatchers.IO) {
            if (retryExistingArticle(url)) {
                _state.update { it.copy(clipboardUrl = null, duplicateUrl = null) }
            } else if (isExistingArticle(url)) {
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
        viewModelScope.launch(Dispatchers.IO) {
            if (retryExistingArticle(url)) {
                clipboardPromptState.markHandled(url)
                clipboardMonitorService.markProcessed(url)
                _state.update { it.copy(duplicateUrl = null, clipboardUrl = null) }
            } else if (isExistingArticle(url)) {
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
        clipboardPromptState.markHandled(url)
        clipboardMonitorService.markProcessed(url)
        _state.update { it.copy(clipboardUrl = null) }
        viewModelScope.launch(Dispatchers.IO) { saveUrl(url) }
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

    private fun isExistingArticle(url: String): Boolean {
        return articleUrlExists(url, articleRepo.getAllSync().mapNotNull { it.url })
    }

    private fun retryExistingArticle(url: String): Boolean {
        val article = articleRepo.getAllSync()
            .firstOrNull { normalizeArticleUrl(it.url) == normalizeArticleUrl(url) }
            ?: return false
        if (!shouldRetryExistingSharedArticle(article.status)) return false
        articleProcessingScheduler.enqueueRetrySave(url)
        return true
    }

    private suspend fun saveUrl(url: String) {
        _state.update { it.copy(isSavingUrl = true) }
        try {
            articleProcessingScheduler.enqueueSave(url)
        } finally {
            _state.update { it.copy(isSavingUrl = false) }
        }
    }
}
