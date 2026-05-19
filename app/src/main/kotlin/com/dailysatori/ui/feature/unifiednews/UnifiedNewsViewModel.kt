package com.dailysatori.ui.feature.unifiednews

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailysatori.config.SettingKeys
import com.dailysatori.data.repository.ArticleRepository
import com.dailysatori.data.repository.RemoteNewsSourceRepository
import com.dailysatori.data.repository.SettingRepository
import com.dailysatori.data.repository.UnifiedNewsSummaryRepository
import com.dailysatori.service.remotenews.RemoteArticle
import com.dailysatori.service.remotenews.RemoteDigest
import com.dailysatori.service.remotenews.RemoteNewsResult
import com.dailysatori.service.remotenews.RemoteNewsService
import com.dailysatori.service.unifiednews.UnifiedNewsGenerationResult
import com.dailysatori.service.unifiednews.UnifiedNewsSummaryService
import com.dailysatori.service.unifiednews.UnifiedNewsSummaryStatus
import com.dailysatori.service.unifiednews.dailyUnifiedNewsWindowFor
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

enum class UnifiedNewsPage {
    SUMMARY,
    LOCAL_ARTICLES,
    LOCAL_FAVORITES,
    SETTINGS,
}

sealed class UnifiedNewsNavigationTarget {
    data class RemoteDigest(val id: Long) : UnifiedNewsNavigationTarget()
    data class RemoteArticle(val id: Long, val remoteSourceId: Long?) : UnifiedNewsNavigationTarget()
    data class LocalArticle(val id: Long) : UnifiedNewsNavigationTarget()
}

data class UnifiedNewsState(
    val page: UnifiedNewsPage = UnifiedNewsPage.SUMMARY,
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
    private val isDebugBuild: Boolean = false,
) : ViewModel() {
    private val _state = MutableStateFlow(UnifiedNewsState())
    val state: StateFlow<UnifiedNewsState> = _state.asStateFlow()
    private var loadJob: Job? = null
    private var detailLoadJob: Job? = null
    private var detailRequestToken: Long = 0L

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
                    _state.update {
                        val latestSuccessfulFallback = summaryRepo.getLatestSuccessful()
                        val lastSuccessful = if (todaySummary.isSuccessfulDisplaySummary) todaySummary else latestSuccessfulFallback ?: it.lastSuccessfulSummary
                        val lastSources = lastSuccessful?.let { summary -> sourcesBySummaryId[summary.id] ?: summaryRepo.getSources(summary.id) }.orEmpty()
                        it.copy(
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

    fun openCitation(source: Unified_news_source) = openCitationSource(source.source_type, source.source_id, source.source_filename)

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
        _state.update { it.copy(page = page) }
    }

    fun toggleSelectedRemoteArticleFavorite() {
        val article = _state.value.selectedRemoteArticle ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val localId = _state.value.selectedRemoteArticleLocalId
                if (localId != null) {
                    articleRepo.toggleFavorite(localId)
                    val updated = articleRepo.getById(localId)
                    _state.update { it.copy(selectedRemoteArticleIsFavorite = updated?.is_favorite == 1L) }
                } else {
                    val saved = articleRepo.saveRemoteArticleAsFavorite(article)
                    _state.update {
                        it.copy(
                            selectedRemoteArticleLocalId = saved?.id,
                            selectedRemoteArticleIsFavorite = saved?.is_favorite == 1L,
                        )
                    }
                }
            } catch (_: Exception) {
                _state.update { it.copy(error = "收藏文章失败，请稍后重试") }
            }
        }
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
                val config = remoteConfigOrSetError(token, remoteSourceId) ?: return@launch
                when (val result = remoteNewsService.fetchArticle(config, id)) {
                    is RemoteNewsResult.Success -> {
                        val article = result.value.article
                        val local = article.url?.let(articleRepo::getByUrl)
                        ifLatestDetailRequest(token) {
                            it.copy(
                                selectedRemoteArticle = article,
                                selectedRemoteArticleLocalId = local?.id,
                                selectedRemoteArticleIsFavorite = local?.is_favorite == 1L,
                                isLoading = false,
                            )
                        }
                    }
                    is RemoteNewsResult.Failure -> ifLatestDetailRequest(token) { it.copy(error = result.message, isLoading = false) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ifLatestDetailRequest(token) { it.copy(error = "引用详情加载失败，请稍后重试", isLoading = false) }
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
