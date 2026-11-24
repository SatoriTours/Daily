package com.dailysatori.ui.feature.share

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.dailysatori.core.worker.ArticleProcessingScheduler
import com.dailysatori.data.repository.ArticleRepository
import com.dailysatori.data.repository.TagRepository
import com.dailysatori.service.parser.WebpageParserService
import com.dailysatori.shared.db.Article
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ShareDialogState(
    val shareURL: String = "",
    val title: String = "",
    val comment: String = "",
    val tags: List<String> = emptyList(),
    val tagInput: String = "",
    val aiAnalysis: Boolean = true,
    val isUpdate: Boolean = false,
    val existingArticle: Article? = null,
    val isSaving: Boolean = false,
    val isInitializing: Boolean = false,
    val error: String? = null,
)

class ShareDialogViewModel(
    private val articleRepo: ArticleRepository,
    private val tagRepo: TagRepository? = null,
    private val scheduler: ArticleProcessingScheduler? = null,
    private val parserService: WebpageParserService? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) : ViewModel() {
    private val log = Logger.withTag("ShareDialog")
    private val _state = MutableStateFlow(ShareDialogState())
    val state: StateFlow<ShareDialogState> = _state.asStateFlow()

    fun initialize(url: String) {
        _state.update {
            it.copy(
                shareURL = url, title = "", comment = "", tags = emptyList(), tagInput = "",
                isUpdate = false, existingArticle = null, isSaving = false, isInitializing = true,
                error = null,
            )
        }
        viewModelScope.launch(ioDispatcher) {
            try {
                val existing = articleRepo.findIntakeArticle(url)?.id?.let(articleRepo::getById)
                if (existing == null) {
                    _state.update { it.copy(isUpdate = false, existingArticle = null) }
                    return@launch
                }
                val tags = existingTags(existing.id)
                _state.update {
                    it.copy(
                        isUpdate = true,
                        existingArticle = existing,
                        title = existing.title.orEmpty(),
                        comment = existing.comment.orEmpty(),
                        tags = tags,
                    )
                }
            } catch (e: Exception) {
                log.w(e) { "Share intake lookup failed" }
                _state.update { it.copy(error = ERROR_LOAD_FAILED, isUpdate = false, existingArticle = null) }
            } finally {
                _state.update { it.copy(isInitializing = false) }
            }
        }
    }

    private suspend fun existingTags(articleId: Long): List<String> =
        tagRepo?.getByArticle(articleId)?.first()?.map { it.name }.orEmpty()

    fun onTitleChanged(title: String) {
        _state.update { it.copy(title = title) }
    }

    fun onCommentChanged(comment: String) {
        _state.update { it.copy(comment = comment) }
    }

    fun onTagInputChanged(input: String) {
        _state.update { it.copy(tagInput = input) }
    }

    fun addTag() {
        val tag = _state.value.tagInput.trim()
        if (tag.isNotEmpty() && tag !in _state.value.tags) {
            _state.update { it.copy(tags = it.tags + tag, tagInput = "") }
        }
    }

    fun removeTag(tag: String) {
        _state.update { it.copy(tags = it.tags - tag) }
    }

    fun toggleAiAnalysis() {
        _state.update { it.copy(aiAnalysis = !it.aiAnalysis) }
    }

    fun save(onSuccess: () -> Unit) {
        // Saving before the intake lookup finishes could insert a second row for the same URL.
        if (_state.value.isSaving || _state.value.isInitializing) return
        _state.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch(ioDispatcher) {
            try {
                persist(_state.value)
                withContext(mainDispatcher) {
                    _state.update { it.copy(isSaving = false) }
                    onSuccess()
                }
            } catch (e: Exception) {
                log.w(e) { "Share save failed" }
                _state.update { it.copy(isSaving = false, error = ERROR_SAVE_FAILED) }
            }
        }
    }

    private suspend fun persist(s: ShareDialogState) {
        val cleanTitle = s.title.trim().ifBlank { null }
        val cleanComment = s.comment.trim().ifBlank { null }
        val hasUserInput = cleanTitle != null || cleanComment != null || s.tags.isNotEmpty()
        if (!hasUserInput && s.aiAnalysis) {
            // The save task owns the row; inserting first would produce a second article for the same URL.
            val existingId = articleRepo.findIntakeArticle(s.shareURL)?.id
            if (existingId == null) {
                scheduler?.enqueueSave(s.shareURL)
                return
            }
            tagRepo?.setTagsForArticle(existingId, s.tags)
            reprocessQuietly(existingId)
            return
        }
        // Atomic: finds the intake row for this URL and updates only user-editable fields.
        val (articleId, existed) = articleRepo.upsertShareIntake(s.shareURL, cleanTitle, cleanComment)
        tagRepo?.setTagsForArticle(articleId, s.tags)
        if (s.isUpdate) articleRepo.getById(articleId)?.let { refreshed -> _state.update { it.copy(existingArticle = refreshed) } }
        if (!s.aiAnalysis) return
        if (existed) reprocessQuietly(articleId) else scheduler?.enqueueSave(s.shareURL)
    }

    private suspend fun reprocessQuietly(articleId: Long) {
        val parser = parserService ?: return
        try {
            parser.reprocessArticle(articleId)
        } catch (e: Exception) {
            // A saved title/comment/tag is still a success; re-analysis failure must not undo it.
            log.w(e) { "Share reprocess failed: articleId=$articleId" }
        }
    }

    companion object {
        const val ERROR_LOAD_FAILED = "share.load_failed"
        const val ERROR_SAVE_FAILED = "share.save_failed"
    }
}
