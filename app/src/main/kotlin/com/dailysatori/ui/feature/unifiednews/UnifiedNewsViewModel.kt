package com.dailysatori.ui.feature.unifiednews

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailysatori.config.SettingKeys
import com.dailysatori.data.repository.ArticleRepository
import com.dailysatori.data.repository.needsLocalAiReprocessingForChineseOutput
import com.dailysatori.data.repository.RemoteNewsSourceRepository
import com.dailysatori.data.repository.SettingRepository
import com.dailysatori.data.repository.UnifiedNewsSummaryRepository
import com.dailysatori.service.remotenews.RemoteArticle
import com.dailysatori.service.remotenews.RemoteDigest
import com.dailysatori.service.remotenews.RemoteNewsResult
import com.dailysatori.service.remotenews.RemoteNewsService
import com.dailysatori.service.parser.WebpageParserService
import com.dailysatori.service.unifiednews.UnifiedNewsGenerationResult
import com.dailysatori.service.unifiednews.UnifiedNewsSummaryService
import com.dailysatori.service.unifiednews.UnifiedNewsSummaryStatus
import com.dailysatori.service.unifiednews.dailyUnifiedNewsWindowFor
import com.dailysatori.service.unifiednews.remoteNewsSourceRouteKey
import com.dailysatori.shared.db.Unified_news_source
import com.dailysatori.shared.db.Unified_news_summary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

enum class UnifiedNewsPage {
    SUMMARY,
    LOCAL_ARTICLES,
    LOCAL_FAVORITES,
    SETTINGS,
}

sealed class UnifiedNewsSourceSelection {
    data object Summary : UnifiedNewsSourceSelection()
    data class RemoteSource(val id: Long, val name: String) : UnifiedNewsSourceSelection()
    data object LocalArticles : UnifiedNewsSourceSelection()
}

data class UnifiedNewsRemoteSourceOption(val id: Long, val name: String)

data class SourceArticleCacheKey(val sourceId: Long, val summaryDate: String)

fun sourceArticleCacheKey(sourceId: Long, summaryDate: String): SourceArticleCacheKey =
    SourceArticleCacheKey(sourceId, summaryDate)

sealed class UnifiedNewsNavigationTarget {
    data class RemoteDigest(val id: Long) : UnifiedNewsNavigationTarget()
    data class RemoteArticle(val id: Long, val remoteSourceId: Long?) : UnifiedNewsNavigationTarget()
    data class LocalArticle(val id: Long) : UnifiedNewsNavigationTarget()
}

data class UnifiedNewsState(
    val page: UnifiedNewsPage = UnifiedNewsPage.SUMMARY,
    val sourceSelection: UnifiedNewsSourceSelection = UnifiedNewsSourceSelection.Summary,
    val remoteSources: List<UnifiedNewsRemoteSourceOption> = emptyList(),
    val sourceArticlesByCacheKey: Map<SourceArticleCacheKey, List<RemoteArticle>> = emptyMap(),
    val sourceArticlesLoadingSourceId: Long? = null,
    val sourceArticlesError: String? = null,
    val summaries: List<Unified_news_summary> = emptyList(),
    val selectedSummary: Unified_news_summary? = null,
    val lastSuccessfulSummary: Unified_news_summary? = null,
    val lastSuccessfulSources: List<Unified_news_source> = emptyList(),
    val sources: List<Unified_news_source> = emptyList(),
    val sourcesBySummaryId: Map<Long, List<Unified_news_source>> = emptyMap(),
    val navigationTarget: UnifiedNewsNavigationTarget? = null,
    val selectedRemoteDigest: RemoteDigest? = null,
    val selectedRemoteArticle: RemoteArticle? = null,
    val selectedRemoteArticleLocalId: Long? = null,
    val selectedRemoteArticleIsFavorite: Boolean = false,
    val isLoading: Boolean = false,
    val isRegenerating: Boolean = false,
    val regeneratingSummaryDate: String? = null,
    val summaryRefreshCompletedToken: Int = 0,
    val localArticleRefreshRequestKey: Int = 0,
    val manualRefreshMessage: String? = null,
    val error: String? = null,
)

class UnifiedNewsViewModel(
    private val summaryRepo: UnifiedNewsSummaryRepository,
    private val summaryService: UnifiedNewsSummaryService,
    private val settingRepo: SettingRepository,
    private val remoteNewsService: RemoteNewsService,
    private val remoteNewsSourceRepo: RemoteNewsSourceRepository,
    private val articleRepo: ArticleRepository,
    private val webpageParserService: WebpageParserService,
    private val isDebugBuild: Boolean = false,
) : ViewModel() {
    private val _state = MutableStateFlow(UnifiedNewsState())
    val state: StateFlow<UnifiedNewsState> = _state.asStateFlow()
    private var loadJob: Job? = null
    private var detailLoadJob: Job? = null
    private var detailRequestToken: Long = 0L
    private val sourceArticleRequestToken = AtomicLong(0L)
    private val sourceArticleRequestLock = Any()

    fun loadInitial() {
        if (loadJob != null) return
        loadJob = viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val today = dailyUnifiedNewsWindowFor()
                summaryRepo.getAll().collect { summaries ->
                    val todaySummary = summaries.firstOrNull { it.summary_date == today.summaryDate && it.window_key == today.key.value }
                    val displaySummaries = summaries.withDisplayFallback(summaryRepo.getLatestSuccessful())
                    val sourcesBySummaryId = displaySummaries.associate { summary -> summary.id to summaryRepo.getSources(summary.id) }
                    val remoteSources = remoteNewsSourceRepo.getEnabled().map { source -> UnifiedNewsRemoteSourceOption(source.id, source.name) }
                    val currentState = _state.value
                    val currentSelection = currentState.sourceSelection
                    val shouldResetSelection = currentSelection is UnifiedNewsSourceSelection.RemoteSource &&
                        remoteSources.none { source -> source.id == currentSelection.id }
                    if (shouldResetSelection) invalidateSourceArticleRequest()
                    val nextSelection = if (shouldResetSelection) UnifiedNewsSourceSelection.Summary else currentSelection
                    val latestSuccessfulFallback = summaryRepo.getLatestSuccessful()
                    val lastSuccessful = if (todaySummary.isSuccessfulDisplaySummary) todaySummary else latestSuccessfulFallback ?: currentState.lastSuccessfulSummary
                    val lastSources = lastSuccessful?.let { summary -> sourcesBySummaryId[summary.id] ?: summaryRepo.getSources(summary.id) }.orEmpty()
                    _state.update {
                        it.copy(
                            sourceSelection = nextSelection,
                            remoteSources = remoteSources,
                            summaries = displaySummaries,
                            selectedSummary = displaySummaries.firstOrNull(),
                            lastSuccessfulSummary = lastSuccessful,
                            lastSuccessfulSources = lastSources,
                            sources = displaySummaries.firstOrNull()?.let { summary -> sourcesBySummaryId[summary.id] }.orEmpty(),
                            sourcesBySummaryId = sourcesBySummaryId,
                            isLoading = false,
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                loadJob = null
                _state.update { it.copy(isLoading = false, error = "新闻汇总加载失败，请稍后重试") }
            }
        }
    }

    fun openCitation(source: Unified_news_source) {
        val target = navigationTargetFor(source.source_type, source.source_id, source.source_filename) ?: return
        val token = beginDetailRequest(target)
        when (target) {
            is UnifiedNewsNavigationTarget.RemoteDigest -> openRemoteDigest(target.id, token)
            is UnifiedNewsNavigationTarget.RemoteArticle -> openRemoteArticle(source, token)
            is UnifiedNewsNavigationTarget.LocalArticle -> Unit
        }
    }

    fun openCitationSource(sourceType: String, sourceId: Long?, filename: String?) {
        val target = navigationTargetFor(sourceType, sourceId, filename) ?: return
        val token = beginDetailRequest(target)
        when (target) {
            is UnifiedNewsNavigationTarget.RemoteDigest -> openRemoteDigest(target.id, token)
            is UnifiedNewsNavigationTarget.RemoteArticle -> openRemoteArticle(target.id, target.remoteSourceId, token)
            is UnifiedNewsNavigationTarget.LocalArticle -> Unit
        }
    }

    fun closeSourceDetail() {
        detailLoadJob?.cancel()
        detailRequestToken += 1
        _state.update {
            it.clearSelectedSourceDetail().copy(navigationTarget = null, isLoading = false)
        }
    }

    fun switchPage(page: UnifiedNewsPage) {
        _state.update {
            it.copy(
                page = page,
                sourceSelection = if (page == UnifiedNewsPage.SUMMARY) UnifiedNewsSourceSelection.Summary else it.sourceSelection,
            )
        }
    }

    fun selectSummarySource() {
        invalidateSourceArticleRequest()
        _state.update { it.copy(sourceSelection = UnifiedNewsSourceSelection.Summary) }
    }

    fun selectRemoteSource(source: UnifiedNewsRemoteSourceOption) {
        val cacheKey = sourceArticleCacheKey(source.id, dailyUnifiedNewsWindowFor().summaryDate)
        val sourceArticlesCached = _state.value.sourceArticlesByCacheKey.containsKey(cacheKey)
        invalidateSourceArticleRequest()
        _state.update {
            it.copy(
                sourceSelection = UnifiedNewsSourceSelection.RemoteSource(source.id, source.name),
                sourceArticlesError = null,
            )
        }
        if (!sourceArticlesCached) {
            fetchSourceArticles(source.id, force = false)
        }
    }

    fun selectLocalArticlesSource() {
        invalidateSourceArticleRequest()
        _state.update {
            it.copy(sourceSelection = UnifiedNewsSourceSelection.LocalArticles)
        }
    }

    fun refreshSelectedSource() {
        when (_state.value.sourceSelection) {
            UnifiedNewsSourceSelection.Summary -> regenerateCurrentWindow()
            is UnifiedNewsSourceSelection.RemoteSource -> refreshSelectedRemoteSource()
            UnifiedNewsSourceSelection.LocalArticles -> incrementLocalArticleRefreshRequest()
        }
    }

    private fun incrementLocalArticleRefreshRequest() {
        _state.update { it.copy(localArticleRefreshRequestKey = it.localArticleRefreshRequestKey + 1) }
    }

    fun refreshSelectedRemoteSource() {
        val selection = _state.value.sourceSelection as? UnifiedNewsSourceSelection.RemoteSource ?: return
        fetchSourceArticles(selection.id, force = true)
    }

    fun openSourceArticle(article: RemoteArticle) {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update {
                it.clearSelectedSourceDetail().copy(
                    selectedRemoteArticle = article,
                    selectedRemoteArticleLocalId = null,
                    selectedRemoteArticleIsFavorite = false,
                    isLoading = true,
                    error = null,
                )
            }
            val local = runCatching { articleRepo.findLocalArticleForRemote(article) }.getOrNull()
            _state.update { state ->
                if (state.selectedRemoteArticle?.id == article.id) {
                    state.copy(
                        selectedRemoteArticleLocalId = local?.id,
                        selectedRemoteArticleIsFavorite = local?.is_favorite == 1L,
                        isLoading = false,
                    )
                } else {
                    state
                }
            }
        }
    }

    fun toggleSelectedRemoteArticleFavorite() {
        val current = _state.value
        val article = current.selectedRemoteArticle ?: return
        val localId = current.selectedRemoteArticleLocalId
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (localId != null) {
                    articleRepo.toggleFavorite(localId)
                    val updated = articleRepo.getById(localId)
                    _state.update { state ->
                        if (state.selectedRemoteArticle?.id == article.id) {
                            state.copy(selectedRemoteArticleIsFavorite = updated?.is_favorite == 1L)
                        } else {
                            state
                        }
                    }
                } else {
                    val saved = articleRepo.saveRemoteArticleAsFavorite(article)
                    reprocessEnglishRemoteArticle(article, saved?.id)
                    _state.update { state ->
                        if (state.selectedRemoteArticle?.id == article.id) {
                            state.copy(
                                selectedRemoteArticleLocalId = saved?.id,
                                selectedRemoteArticleIsFavorite = saved?.is_favorite == 1L,
                            )
                        } else {
                            state
                        }
                    }
                }
            } catch (_: Exception) {
                _state.update { state ->
                    if (state.selectedRemoteArticle?.id == article.id) state.copy(error = "收藏文章失败，请稍后重试") else state
                }
            }
        }
    }

    private suspend fun reprocessEnglishRemoteArticle(article: RemoteArticle, savedId: Long?) {
        if (savedId == null || !article.needsLocalAiReprocessingForChineseOutput()) return
        webpageParserService.reprocessArticle(savedId)
    }

    fun regenerateCurrentWindow() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val today = dailyUnifiedNewsWindowFor()
                _state.update {
                    it.copy(
                        isRegenerating = true,
                        regeneratingSummaryDate = today.summaryDate,
                        manualRefreshMessage = null,
                        error = null,
                        page = UnifiedNewsPage.SUMMARY,
                    )
                }
                val result = summaryService.generateDaily(
                    force = true,
                    ignoreSourceTimeFilter = isDebugBuild,
                )
                _state.update {
                    it.copy(
                        isRegenerating = false,
                        regeneratingSummaryDate = null,
                        summaryRefreshCompletedToken = it.summaryRefreshCompletedToken + 1,
                        manualRefreshMessage = manualRefreshMessage(result),
                        error = result.message?.takeIf { !result.success },
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(isRegenerating = false, regeneratingSummaryDate = null, error = "新闻汇总重新生成失败，请稍后重试") }
            }
        }
    }

    private fun manualRefreshMessage(result: UnifiedNewsGenerationResult): String? = when (result.status) {
        UnifiedNewsSummaryStatus.EMPTY -> result.message ?: "当前时间窗口暂无可总结新闻"
        UnifiedNewsSummaryStatus.SUCCESS -> null
        UnifiedNewsSummaryStatus.FAILED -> null
        UnifiedNewsSummaryStatus.PENDING -> null
    }

    private val Unified_news_summary?.isSuccessfulDisplaySummary: Boolean
        get() = this != null && status == UnifiedNewsSummaryStatus.SUCCESS.value && content.isNotBlank()

    private val Unified_news_summary?.hasDisplayContent: Boolean
        get() = this != null && content.isNotBlank()

    private fun List<Unified_news_summary>.withDisplayFallback(latestSuccessfulFallback: Unified_news_summary?): List<Unified_news_summary> {
        val displaySummaries = filter { it.hasDisplayContent }
        if (displaySummaries.isNotEmpty()) return displaySummaries
        return listOfNotNull(latestSuccessfulFallback?.takeIf { it.hasDisplayContent })
    }

    private fun navigationTargetFor(sourceType: String, sourceId: Long?, filename: String?): UnifiedNewsNavigationTarget? = when (sourceType) {
        "remote_digest" -> sourceId?.let { UnifiedNewsNavigationTarget.RemoteDigest(it) }
        "remote_article" -> sourceId?.let { UnifiedNewsNavigationTarget.RemoteArticle(it, parseRemoteNewsSourceRouteKey(filename)) }
        "crayfish_general" -> null
        "crayfish_dji" -> null
        "local_favorite" -> sourceId?.let { UnifiedNewsNavigationTarget.LocalArticle(it) }
        else -> null
    }

    private fun openRemoteDigest(id: Long, token: Long) {
        detailLoadJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                ifLatestDetailRequest(token) { it.copy(isLoading = true) }
                val config = remoteConfigOrSetError(token) ?: return@launch
                when (val result = remoteNewsService.fetchDigest(config, id)) {
                    is RemoteNewsResult.Success -> ifLatestDetailRequest(token) { it.copy(selectedRemoteDigest = result.value.digest, isLoading = false) }
                    is RemoteNewsResult.Failure -> ifLatestDetailRequest(token) { it.copy(error = result.message, isLoading = false) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ifLatestDetailRequest(token) { it.copy(error = "引用详情加载失败，请稍后重试", isLoading = false) }
            }
        }
    }

    private fun openRemoteArticle(id: Long, remoteSourceId: Long?, token: Long) {
        detailLoadJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                ifLatestDetailRequest(token) { it.copy(isLoading = true) }
                val local = articleRepo.getById(id)
                if (local == null) {
                    ifLatestDetailRequest(token) { it.copy(error = "文章内容不可用，请刷新新闻汇总或远程来源", isLoading = false) }
                    return@launch
                }
                ifLatestDetailRequest(token) {
                    it.copy(navigationTarget = UnifiedNewsNavigationTarget.LocalArticle(local.id), isLoading = false)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ifLatestDetailRequest(token) { it.copy(error = "文章内容不可用，请刷新新闻汇总或远程来源", isLoading = false) }
            }
        }
    }

    private fun openRemoteArticle(source: Unified_news_source, token: Long) {
        detailLoadJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                ifLatestDetailRequest(token) { it.copy(isLoading = true) }
                val local = source.source_url?.let(articleRepo::getByUrl)
                    ?: source.source_id?.let { id -> articleRepo.getById(id) }
                    ?: articleRepo.cacheRemoteArticle(source.toRemoteArticleForLocalCache(), source.source_time)
                if (local == null) {
                    ifLatestDetailRequest(token) { it.copy(error = "文章内容不可用，请刷新新闻汇总或远程来源", isLoading = false) }
                    return@launch
                }
                ifLatestDetailRequest(token) {
                    it.copy(navigationTarget = UnifiedNewsNavigationTarget.LocalArticle(local.id), isLoading = false)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ifLatestDetailRequest(token) { it.copy(error = "文章内容不可用，请刷新新闻汇总或远程来源", isLoading = false) }
            }
        }
    }

    private fun Unified_news_source.toRemoteArticleForLocalCache(): RemoteArticle = RemoteArticle(
        id = source_id ?: id,
        title = this.title,
        url = source_url,
        summary = this.summary,
        content = this.summary,
    )

    private fun fetchSourceArticles(sourceId: Long, force: Boolean) {
        val cacheKey = sourceArticleCacheKey(sourceId, dailyUnifiedNewsWindowFor().summaryDate)
        val current = _state.value
        if (current.sourceArticlesLoadingSourceId == sourceId) return
        if (!force && current.sourceArticlesByCacheKey.containsKey(cacheKey)) return
        val token = beginSourceArticleRequest(sourceId)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val source = remoteNewsSourceRepo.getById(sourceId)
                if (source == null) {
                    ifLatestSourceArticleRequest(token) { state ->
                        state.copy(sourceArticlesLoadingSourceId = null, sourceArticlesError = "远程新闻源不存在或已删除")
                    }
                    return@launch
                }
                when (val config = remoteNewsService.configOrFailure(source.base_url, source.api_token)) {
                    is RemoteNewsResult.Success -> when (val result = remoteNewsService.fetchTopArticlesToday(config.value, page = 1, limit = 50)) {
                        is RemoteNewsResult.Success -> ifLatestSourceArticleRequest(token) { state ->
                            state.copy(
                                sourceArticlesByCacheKey = state.sourceArticlesByCacheKey + (cacheKey to result.value.articles),
                                sourceArticlesLoadingSourceId = null,
                                sourceArticlesError = null,
                            )
                        }
                        is RemoteNewsResult.Failure -> ifLatestSourceArticleRequest(token) { state ->
                            state.copy(sourceArticlesLoadingSourceId = null, sourceArticlesError = result.message)
                        }
                    }
                    is RemoteNewsResult.Failure -> ifLatestSourceArticleRequest(token) { state ->
                        state.copy(sourceArticlesLoadingSourceId = null, sourceArticlesError = config.message)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ifLatestSourceArticleRequest(token) { state ->
                    state.copy(sourceArticlesLoadingSourceId = null, sourceArticlesError = "来源文章加载失败，请稍后重试")
                }
            }
        }
    }

    private fun parseRemoteNewsSourceRouteKey(value: String?): Long? =
        value?.removePrefix("remote_news_source:")?.takeIf { it != value }?.toLongOrNull()

    private fun beginDetailRequest(target: UnifiedNewsNavigationTarget): Long {
        detailLoadJob?.cancel()
        val token = detailRequestToken + 1
        detailRequestToken = token
        _state.update { it.clearSelectedSourceDetail().copy(navigationTarget = target, error = null) }
        return token
    }

    private fun ifLatestDetailRequest(token: Long, transform: (UnifiedNewsState) -> UnifiedNewsState) {
        if (token == detailRequestToken) _state.update(transform)
    }

    private fun ifLatestSourceArticleRequest(token: Long, transform: (UnifiedNewsState) -> UnifiedNewsState) {
        synchronized(sourceArticleRequestLock) {
            if (token == sourceArticleRequestToken.get()) _state.update(transform)
        }
    }

    private fun invalidateSourceArticleRequest() {
        synchronized(sourceArticleRequestLock) {
            sourceArticleRequestToken.incrementAndGet()
            _state.update { it.copy(sourceArticlesLoadingSourceId = null, sourceArticlesError = null) }
        }
    }

    private fun beginSourceArticleRequest(sourceId: Long): Long {
        synchronized(sourceArticleRequestLock) {
            val token = sourceArticleRequestToken.incrementAndGet()
            _state.update { it.copy(sourceArticlesLoadingSourceId = sourceId, sourceArticlesError = null) }
            return token
        }
    }

    private fun UnifiedNewsState.clearSelectedSourceDetail(): UnifiedNewsState = copy(
        selectedRemoteDigest = null,
        selectedRemoteArticle = null,
        selectedRemoteArticleLocalId = null,
        selectedRemoteArticleIsFavorite = false,
    )

    private fun remoteConfigOrSetError(token: Long, remoteSourceId: Long? = null): com.dailysatori.service.remotenews.RemoteNewsConfigValues? {
        val source = remoteSourceId?.let { remoteNewsSourceRepo.getById(remoteSourceId) }
        if (remoteSourceId != null && source == null) {
            ifLatestDetailRequest(token) { it.copy(error = "远程新闻源不存在或已删除", isLoading = false) }
            return null
        }
        val baseUrl = source?.base_url ?: settingRepo.get(SettingKeys.remoteNewsBaseUrl)
        val apiToken = source?.api_token ?: settingRepo.get(SettingKeys.remoteNewsApiToken)
        return when (val config = remoteNewsService.configOrFailure(baseUrl, apiToken)) {
        is RemoteNewsResult.Success -> config.value
        is RemoteNewsResult.Failure -> {
            ifLatestDetailRequest(token) { it.copy(error = config.message, isLoading = false) }
            null
        }
    }
    }

}
