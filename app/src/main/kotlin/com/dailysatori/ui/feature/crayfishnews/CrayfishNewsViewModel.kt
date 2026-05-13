package com.dailysatori.ui.feature.crayfishnews

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailysatori.config.SettingKeys
import com.dailysatori.data.repository.SettingRepository
import com.dailysatori.service.crayfishnews.CrayfishNewsConfigValues
import com.dailysatori.service.crayfishnews.CrayfishNewsDetail
import com.dailysatori.service.crayfishnews.CrayfishNewsListItem
import com.dailysatori.service.crayfishnews.CrayfishNewsListResponse
import com.dailysatori.service.crayfishnews.CrayfishNewsResult
import com.dailysatori.service.crayfishnews.CrayfishNewsService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class CrayfishNewsMode(val title: String) {
    LATEST("小龙虾新闻"),
    DJI("大疆新闻"),
    ARCHIVE("历史新闻"),
}

data class CrayfishNewsState(
    val mode: CrayfishNewsMode = CrayfishNewsMode.LATEST,
    val latestNews: CrayfishNewsDetail? = null,
    val djiNews: CrayfishNewsDetail? = null,
    val archiveGeneral: List<CrayfishNewsListItem> = emptyList(),
    val archiveDji: List<CrayfishNewsListItem> = emptyList(),
    val selectedNews: CrayfishNewsDetail? = null,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,
)

class CrayfishNewsViewModel(
    private val settingRepo: SettingRepository,
    private val crayfishNewsService: CrayfishNewsService,
) : ViewModel() {
    private val _state = MutableStateFlow(CrayfishNewsState())
    val state: StateFlow<CrayfishNewsState> = _state.asStateFlow()

    fun loadInitial() {
        if (_state.value.latestNews == null) loadMode(CrayfishNewsMode.LATEST)
    }

    fun switchMode(mode: CrayfishNewsMode) {
        _state.update { it.copy(mode = mode, error = null) }
        val needsLoad = when (mode) {
            CrayfishNewsMode.LATEST -> _state.value.latestNews == null
            CrayfishNewsMode.DJI -> _state.value.djiNews == null
            CrayfishNewsMode.ARCHIVE -> _state.value.archiveGeneral.isEmpty() && _state.value.archiveDji.isEmpty()
        }
        if (needsLoad) loadMode(mode)
    }

    fun refresh() = loadMode(_state.value.mode, refresh = true)

    fun openArchiveItem(filename: String, category: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isLoading = true, error = null) }
            val config = currentConfigOrSetError() ?: return@launch
            when (val result = crayfishNewsService.fetchNewsFile(config, category, filename)) {
                is CrayfishNewsResult.Success -> _state.update { it.copy(selectedNews = result.value, isLoading = false) }
                is CrayfishNewsResult.Failure -> _state.update { it.copy(error = result.message, isLoading = false) }
            }
        }
    }

    fun openLatestDetail() {
        val news = _state.value.latestNews ?: return
        _state.update { it.copy(selectedNews = news) }
    }

    fun openDjiDetail() {
        val news = _state.value.djiNews ?: return
        _state.update { it.copy(selectedNews = news) }
    }

    fun closeNews() = _state.update { it.copy(selectedNews = null) }

    private fun loadMode(mode: CrayfishNewsMode, refresh: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isLoading = !refresh, isRefreshing = refresh, error = null) }
            val config = currentConfigOrSetError() ?: return@launch
            when (mode) {
                CrayfishNewsMode.LATEST -> loadLatest(config, refresh)
                CrayfishNewsMode.DJI -> loadDji(config, refresh)
                CrayfishNewsMode.ARCHIVE -> loadArchive(config, refresh)
            }
        }
    }

    private suspend fun loadLatest(config: CrayfishNewsConfigValues, refresh: Boolean) {
        when (val result = crayfishNewsService.fetchLatest(config)) {
            is CrayfishNewsResult.Success -> _state.update {
                it.copy(latestNews = result.value, isLoading = false, isRefreshing = false)
            }
            is CrayfishNewsResult.Failure -> applyFailure(result.message)
        }
    }

    private suspend fun loadDji(config: CrayfishNewsConfigValues, refresh: Boolean) {
        when (val result = crayfishNewsService.fetchDji(config)) {
            is CrayfishNewsResult.Success -> _state.update {
                it.copy(djiNews = result.value, isLoading = false, isRefreshing = false)
            }
            is CrayfishNewsResult.Failure -> applyFailure(result.message)
        }
    }

    private suspend fun loadArchive(config: CrayfishNewsConfigValues, refresh: Boolean) {
        when (val result = crayfishNewsService.fetchNewsList(config, limit = 20)) {
            is CrayfishNewsResult.Success -> _state.update {
                it.copy(
                    archiveGeneral = result.value.general,
                    archiveDji = result.value.dji,
                    isLoading = false,
                    isRefreshing = false,
                )
            }
            is CrayfishNewsResult.Failure -> applyFailure(result.message)
        }
    }

    private fun applyFailure(message: String) {
        _state.update { it.copy(error = message, isLoading = false, isRefreshing = false) }
    }

    private fun currentConfigOrSetError(): CrayfishNewsConfigValues? {
        return when (val config = crayfishNewsService.configOrFailure(settingRepo.get(SettingKeys.crayfishNewsBaseUrl), settingRepo.get(SettingKeys.crayfishNewsApiToken))) {
            is CrayfishNewsResult.Success -> config.value
            is CrayfishNewsResult.Failure -> {
                _state.update { it.copy(error = config.message, isLoading = false, isRefreshing = false) }
                null
            }
        }
    }
}
