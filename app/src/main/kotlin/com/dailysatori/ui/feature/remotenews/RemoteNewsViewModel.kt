package com.dailysatori.ui.feature.remotenews

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailysatori.config.RemoteNewsConfig
import com.dailysatori.config.SettingKeys
import com.dailysatori.data.repository.ArticleRepository
import com.dailysatori.data.repository.SettingRepository
import com.dailysatori.service.remotenews.RemoteArticleFavoriteService
import com.dailysatori.service.remotenews.RemoteArticle
import com.dailysatori.service.remotenews.RemoteDigest
import com.dailysatori.service.remotenews.RemoteFeed
import com.dailysatori.service.remotenews.RemoteNewsConfigValues
import com.dailysatori.service.remotenews.RemoteNewsPagination
import com.dailysatori.service.remotenews.RemoteNewsResult
import com.dailysatori.service.remotenews.RemoteNewsService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class RemoteNewsMode(val title: String) {
    DIGESTS("远程新闻"),
    ARTICLES("远程文章"),
    FEEDS("信息源"),
    CRAYFISH("小龙虾新闻"),
}

data class RemoteNewsState(
    val mode: RemoteNewsMode = RemoteNewsMode.DIGESTS,
    val digests: List<RemoteDigest> = emptyList(),
    val articles: List<RemoteArticle> = emptyList(),
    val feeds: List<RemoteFeed> = emptyList(),
    val digestPagination: RemoteNewsPagination? = null,
    val articlePagination: RemoteNewsPagination? = null,
    val feedPagination: RemoteNewsPagination? = null,
    val selectedDigest: RemoteDigest? = null,
    val selectedArticle: RemoteArticle? = null,
    val selectedArticleLocalId: Long? = null,
    val selectedArticleIsFavorite: Boolean = false,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val refreshCompletedToken: Int = 0,
    val error: String? = null,
    val detailError: String? = null,
    val loadMoreError: String? = null,
)

class RemoteNewsViewModel(
    private val settingRepo: SettingRepository,
    private val remoteNewsService: RemoteNewsService,
    private val articleRepo: ArticleRepository,
    private val remoteArticleFavoriteService: RemoteArticleFavoriteService,
) : ViewModel() {
    private val _state = MutableStateFlow(RemoteNewsState())
    val state: StateFlow<RemoteNewsState> = _state.asStateFlow()

    fun loadInitial() {
        if (_state.value.mode == RemoteNewsMode.CRAYFISH) return
        if (_state.value.digests.isEmpty()) loadMode(RemoteNewsMode.DIGESTS, refresh = false)
    }

    fun switchMode(mode: RemoteNewsMode) {
        _state.update { it.copy(mode = mode, error = null, loadMoreError = null) }
        if (mode == RemoteNewsMode.CRAYFISH) return
        val needsLoad = when (mode) {
            RemoteNewsMode.DIGESTS -> _state.value.digests.isEmpty()
            RemoteNewsMode.ARTICLES -> _state.value.articles.isEmpty()
            RemoteNewsMode.FEEDS -> _state.value.feeds.isEmpty()
            RemoteNewsMode.CRAYFISH -> false
        }
        if (needsLoad) loadMode(mode, refresh = false)
    }

    fun refresh() = loadMode(_state.value.mode, refresh = true)

    fun loadMore() {
        val current = _state.value
        if (current.isLoading || current.isRefreshing || current.isLoadingMore) return
        if (current.mode == RemoteNewsMode.CRAYFISH) return
        val nextPage = when (current.mode) {
            RemoteNewsMode.DIGESTS -> current.digestPagination?.next
            RemoteNewsMode.ARTICLES -> current.articlePagination?.next
            RemoteNewsMode.FEEDS -> current.feedPagination?.next
            RemoteNewsMode.CRAYFISH -> null
        } ?: return
        loadPage(current.mode, nextPage, append = true)
    }

    fun openDigest(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isLoading = true, detailError = null) }
            val config = currentConfigOrSetError() ?: return@launch
            when (val result = remoteNewsService.fetchDigest(config, id)) {
                is RemoteNewsResult.Success -> _state.update { it.copy(selectedDigest = result.value.digest, isLoading = false) }
                is RemoteNewsResult.Failure -> _state.update { it.copy(detailError = result.message, isLoading = false) }
            }
        }
    }

    fun closeDigest() = _state.update { it.copy(selectedDigest = null, detailError = null) }

    fun openArticle(article: RemoteArticle) {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update {
                it.copy(
                    selectedArticle = article,
                    selectedArticleLocalId = null,
                    selectedArticleIsFavorite = false,
                    isLoading = true,
                    detailError = null,
                )
            }
            val local = runCatching { articleRepo.findLocalArticleForRemote(article) }.getOrNull()
            _state.update { state ->
                if (state.selectedArticle?.id == article.id) {
                    state.copy(
                        selectedArticleLocalId = local?.id,
                        selectedArticleIsFavorite = local?.is_favorite == 1L,
                        isLoading = false,
                    )
                } else {
                    state
                }
            }
        }
    }

    fun toggleSelectedArticleFavorite() {
        val current = _state.value
        val article = current.selectedArticle ?: return
        val localId = current.selectedArticleLocalId
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = remoteArticleFavoriteService.toggleFavorite(article, localId)
                _state.update { state ->
                    if (state.selectedArticle?.id == article.id) {
                        state.copy(
                            selectedArticleLocalId = result.localArticle?.id,
                            selectedArticleIsFavorite = result.isFavorite,
                        )
                    } else {
                        state
                    }
                }
            } catch (_: Exception) {
                _state.update { state ->
                    if (state.selectedArticle?.id == article.id) state.copy(error = "收藏文章失败，请稍后重试") else state
                }
            }
        }
    }

    fun closeArticle() = _state.update {
        it.copy(selectedArticle = null, selectedArticleLocalId = null, selectedArticleIsFavorite = false, detailError = null)
    }

    fun closeDetailError() = _state.update { it.copy(detailError = null) }

    private fun loadMode(mode: RemoteNewsMode, refresh: Boolean) = loadPage(mode, 1, append = false, refresh = refresh)

    private fun loadPage(mode: RemoteNewsMode, page: Int, append: Boolean, refresh: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.withRemoteNewsPageLoadStarted(refresh, append) }
            val config = currentConfigOrSetError() ?: return@launch
            when (mode) {
                RemoteNewsMode.DIGESTS -> loadDigests(config, page, append, refresh)
                RemoteNewsMode.ARTICLES -> loadArticles(config, page, append, refresh)
                RemoteNewsMode.FEEDS -> loadFeeds(config, page, append, refresh)
                RemoteNewsMode.CRAYFISH -> Unit
            }
        }
    }

    private suspend fun loadDigests(config: RemoteNewsConfigValues, page: Int, append: Boolean, refresh: Boolean) {
        when (val result = remoteNewsService.fetchDigests(config, page, RemoteNewsConfig.digestsPageSize)) {
            is RemoteNewsResult.Success -> _state.update {
                val loaded = it.withRemoteNewsPageLoadFinished(refresh)
                loaded.copy(
                    digests = if (append) it.digests + result.value.digests else result.value.digests,
                    digestPagination = result.value.pagination,
                )
            }
            is RemoteNewsResult.Failure -> applyFailure(result.message, append)
        }
    }

    private suspend fun loadArticles(config: RemoteNewsConfigValues, page: Int, append: Boolean, refresh: Boolean) {
        when (val result = remoteNewsService.fetchArticles(config, page, RemoteNewsConfig.articlesPageSize)) {
            is RemoteNewsResult.Success -> _state.update {
                val loaded = it.withRemoteNewsPageLoadFinished(refresh)
                loaded.copy(
                    articles = if (append) it.articles + result.value.articles else result.value.articles,
                    articlePagination = result.value.pagination,
                )
            }
            is RemoteNewsResult.Failure -> applyFailure(result.message, append)
        }
    }

    private suspend fun loadFeeds(config: RemoteNewsConfigValues, page: Int, append: Boolean, refresh: Boolean) {
        when (val result = remoteNewsService.fetchFeeds(config, page, RemoteNewsConfig.feedsPageSize)) {
            is RemoteNewsResult.Success -> _state.update {
                val loaded = it.withRemoteNewsPageLoadFinished(refresh)
                loaded.copy(
                    feeds = if (append) it.feeds + result.value.feeds else result.value.feeds,
                    feedPagination = result.value.pagination,
                )
            }
            is RemoteNewsResult.Failure -> applyFailure(result.message, append)
        }
    }

    private fun applyFailure(message: String, append: Boolean) {
        _state.update { it.withRemoteNewsPageLoadFailure(message, append) }
    }

    private fun currentConfigOrSetError(): RemoteNewsConfigValues? {
        return when (val config = remoteNewsService.configOrFailure(settingRepo.get(SettingKeys.remoteNewsBaseUrl), settingRepo.get(SettingKeys.remoteNewsApiToken))) {
            is RemoteNewsResult.Success -> config.value
            is RemoteNewsResult.Failure -> {
                _state.update { it.copy(error = config.message, isLoading = false, isRefreshing = false, isLoadingMore = false) }
                null
            }
        }
    }
}
