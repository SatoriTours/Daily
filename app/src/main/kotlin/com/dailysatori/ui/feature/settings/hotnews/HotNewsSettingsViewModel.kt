package com.dailysatori.ui.feature.settings.hotnews

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailysatori.service.hotnews.*
import com.dailysatori.service.i18n.I18nService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HotNewsSettingsState(
    val preferences: HotNewsSourcePreferences = HotNewsSourcePreferences(),
    val saving: Boolean = false,
    val testing: HotNewsPlatform? = null,
    val message: String? = null,
    val editor: HotNewsPlatform? = null,
    val feedUrlDraft: String = "",
    val editorError: String? = null,
)

class HotNewsSettingsViewModel(
    private val service: HotNewsService,
    private val client: HotNewsClient,
    private val i18n: I18nService,
) : ViewModel() {
    private val mutableState = MutableStateFlow(HotNewsSettingsState(preferences = service.preferences.value))
    val state = mutableState.asStateFlow()

    init {
        viewModelScope.launch { service.preferences.collect { prefs -> mutableState.update { it.copy(preferences = prefs) } } }
    }

    fun setEnabled(platform: HotNewsPlatform, enabled: Boolean) {
        save(platform, enabled, service.preferences.value.source(platform).feedUrl, closeEditor = false)
    }

    fun edit(platform: HotNewsPlatform) {
        mutableState.update { it.copy(editor = platform, feedUrlDraft = it.preferences.source(platform).feedUrl, editorError = null) }
    }

    fun updateFeedUrl(value: String) { mutableState.update { it.copy(feedUrlDraft = value, editorError = null) } }
    fun closeEditor() { if (!state.value.saving) mutableState.update { it.copy(editor = null, editorError = null) } }

    fun saveFeedUrl() {
        val platform = state.value.editor ?: return
        val url = try { normalizeNewsFeedUrl(state.value.feedUrlDraft) } catch (_: IllegalArgumentException) {
            mutableState.update { it.copy(editorError = i18n.t("news_sources.invalid_url")) }; return
        }
        save(platform, service.preferences.value.source(platform).enabled, url, closeEditor = true)
    }

    private fun save(platform: HotNewsPlatform, enabled: Boolean, url: String, closeEditor: Boolean) {
        if (state.value.saving) return
        mutableState.update { it.copy(saving = true, message = null, editorError = null) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                service.configure(platform, enabled, url)
                mutableState.update { it.copy(editor = if (closeEditor) null else it.editor) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                val message = i18n.t("news_sources.save_failed")
                mutableState.update { if (closeEditor) it.copy(editorError = message) else it.copy(message = message) }
            } finally { mutableState.update { it.copy(saving = false) } }
        }
    }

    fun test(platform: HotNewsPlatform) {
        if (state.value.testing != null || state.value.saving) return
        val config = service.preferences.value.source(platform)
        mutableState.update { it.copy(testing = platform, message = null) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val count = client.fetch(platform, config.feedUrl).size
                mutableState.update { it.copy(message = i18n.t("news_sources.test_success", platform.displayName, count)) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                mutableState.update { it.copy(message = i18n.t("news_sources.test_failed")) }
            } finally { mutableState.update { it.copy(testing = null) } }
        }
    }
}
