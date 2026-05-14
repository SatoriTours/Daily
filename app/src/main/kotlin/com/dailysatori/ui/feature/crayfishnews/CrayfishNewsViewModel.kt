package com.dailysatori.ui.feature.crayfishnews

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailysatori.config.SettingKeys
import com.dailysatori.data.repository.SettingRepository
import com.dailysatori.service.crayfishnews.CrayfishNewsConfigValues
import com.dailysatori.service.crayfishnews.CrayfishNewsDetail
import com.dailysatori.service.crayfishnews.CrayfishNewsListItem
import com.dailysatori.service.crayfishnews.CrayfishNewsResult
import com.dailysatori.service.crayfishnews.CrayfishNewsService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val CrayfishNewsListLimit = 50
private const val CrayfishNewsBatchSize = 3

enum class CrayfishNewsMode(val title: String) {
    LATEST("小龙虾新闻"),
    DJI("大疆新闻"),
    ARCHIVE("历史新闻"),
}

data class CrayfishNewsState(
    val mode: CrayfishNewsMode = CrayfishNewsMode.LATEST,
    val generalFiles: List<CrayfishNewsListItem> = emptyList(),
    val djiFiles: List<CrayfishNewsListItem> = emptyList(),
    val generalArticles: List<CrayfishNewsDetail> = emptyList(),
    val djiArticles: List<CrayfishNewsDetail> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
)

class CrayfishNewsViewModel(
    private val settingRepo: SettingRepository,
    private val crayfishNewsService: CrayfishNewsService,
) : ViewModel() {
    private val _state = MutableStateFlow(CrayfishNewsState())
    val state: StateFlow<CrayfishNewsState> = _state.asStateFlow()

    fun loadInitial() {
        if (_state.value.generalArticles.isEmpty()) loadCategory(CrayfishNewsMode.LATEST, refresh = false)
    }

    fun switchMode(mode: CrayfishNewsMode) {
        _state.update { it.copy(mode = mode, error = null) }
        if (articlesFor(_state.value, mode).isEmpty()) loadCategory(mode, refresh = false)
    }

    fun refresh() = loadCategory(_state.value.mode, refresh = true)

    fun loadMore() {
        val current = _state.value
        if (current.isLoading || current.isRefreshing || current.isLoadingMore) return
        val files = filesFor(current, current.mode)
        val loaded = articlesFor(current, current.mode).size
        if (loaded >= files.size) return
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isLoadingMore = true, error = null) }
            val config = currentConfigOrSetError() ?: return@launch
            loadArticleBatch(config, current.mode, files.drop(loaded).take(CrayfishNewsBatchSize))
        }
    }

    private fun loadCategory(mode: CrayfishNewsMode, refresh: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { state ->
                val cleared = if (refresh) state.clearMode(mode) else state
                cleared.copy(isLoading = !refresh, isRefreshing = refresh, isLoadingMore = false, error = null)
            }
            val config = currentConfigOrSetError() ?: return@launch
            val category = categoryFor(mode)
            when (val result = crayfishNewsService.fetchNewsList(config, category = category, limit = CrayfishNewsListLimit)) {
                is CrayfishNewsResult.Success -> {
                    val files = if (category == "dji") result.value.dji else result.value.general
                    _state.update { it.withFiles(mode, files) }
                    loadArticleBatch(config, mode, files.take(CrayfishNewsBatchSize))
                }
                is CrayfishNewsResult.Failure -> applyFailure(result.message)
            }
        }
    }

    private suspend fun loadArticleBatch(config: CrayfishNewsConfigValues, mode: CrayfishNewsMode, files: List<CrayfishNewsListItem>) {
        val category = categoryFor(mode)
        val loaded = mutableListOf<CrayfishNewsDetail>()
        for (file in files) {
            when (val result = crayfishNewsService.fetchNewsFile(config, category, file.filename)) {
                is CrayfishNewsResult.Success -> loaded += result.value
                is CrayfishNewsResult.Failure -> {
                    applyFailure(result.message)
                    return
                }
            }
        }
        _state.update {
            it.withArticles(mode, articlesFor(it, mode) + loaded)
                .copy(isLoading = false, isRefreshing = false, isLoadingMore = false)
        }
    }

    private fun filesFor(state: CrayfishNewsState, mode: CrayfishNewsMode): List<CrayfishNewsListItem> =
        if (categoryFor(mode) == "dji") state.djiFiles else state.generalFiles

    private fun articlesFor(state: CrayfishNewsState, mode: CrayfishNewsMode): List<CrayfishNewsDetail> =
        if (categoryFor(mode) == "dji") state.djiArticles else state.generalArticles

    private fun categoryFor(mode: CrayfishNewsMode): String = if (mode == CrayfishNewsMode.DJI) "dji" else "general"

    private fun CrayfishNewsState.withFiles(mode: CrayfishNewsMode, files: List<CrayfishNewsListItem>): CrayfishNewsState =
        if (categoryFor(mode) == "dji") copy(djiFiles = files) else copy(generalFiles = files)

    private fun CrayfishNewsState.withArticles(mode: CrayfishNewsMode, articles: List<CrayfishNewsDetail>): CrayfishNewsState =
        if (categoryFor(mode) == "dji") copy(djiArticles = articles) else copy(generalArticles = articles)

    private fun CrayfishNewsState.clearMode(mode: CrayfishNewsMode): CrayfishNewsState =
        if (categoryFor(mode) == "dji") copy(djiFiles = emptyList(), djiArticles = emptyList())
        else copy(generalFiles = emptyList(), generalArticles = emptyList())

    private fun applyFailure(message: String) {
        _state.update { it.copy(error = message, isLoading = false, isRefreshing = false, isLoadingMore = false) }
    }

    private fun currentConfigOrSetError(): CrayfishNewsConfigValues? {
        return when (val config = crayfishNewsService.configOrFailure(settingRepo.get(SettingKeys.crayfishNewsBaseUrl), settingRepo.get(SettingKeys.crayfishNewsApiToken))) {
            is CrayfishNewsResult.Success -> config.value
            is CrayfishNewsResult.Failure -> {
                _state.update { it.copy(error = config.message, isLoading = false, isRefreshing = false, isLoadingMore = false) }
                null
            }
        }
    }
}
