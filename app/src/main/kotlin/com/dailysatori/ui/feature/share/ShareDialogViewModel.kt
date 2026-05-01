package com.dailysatori.ui.feature.share

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailysatori.data.repository.ArticleRepository
import com.dailysatori.shared.db.Article
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
    val error: String? = null,
)

class ShareDialogViewModel(
    private val articleRepo: ArticleRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ShareDialogState())
    val state: StateFlow<ShareDialogState> = _state.asStateFlow()

    fun initialize(url: String) {
        _state.update { it.copy(shareURL = url, title = "", comment = "", tags = emptyList(), tagInput = "", error = null) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val articles = articleRepo.getAll().first()
                val existing = articles.find { article -> article.url == url }
                if (existing != null) {
                    _state.update {
                        it.copy(
                            isUpdate = true,
                            existingArticle = existing,
                            title = existing.title ?: "",
                            comment = existing.comment ?: "",
                        )
                    }
                }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message) }
            }
        }
    }

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
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isSaving = true, error = null) }
            try {
                val s = _state.value
                val tagsStr = s.tags.joinToString(",")
                if (s.isUpdate && s.existingArticle != null) {
                    articleRepo.update(
                        id = s.existingArticle.id,
                        title = s.title.ifBlank { null },
                        aiTitle = null,
                        aiContent = null,
                        aiMarkdownContent = null,
                        url = s.shareURL,
                        isFavorite = s.existingArticle.is_favorite ?: 0L,
                        comment = s.comment.ifBlank { null },
                        status = s.existingArticle.status ?: "pending",
                        coverImage = null,
                        coverImageUrl = null,
                        pubDate = null,
                    )
                } else {
                    articleRepo.insert(
                        title = s.title.ifBlank { null },
                        url = s.shareURL,
                        comment = s.comment.ifBlank { null },
                        status = "pending",
                    )
                }
                onSuccess()
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message, isSaving = false) }
            }
        }
    }
}
