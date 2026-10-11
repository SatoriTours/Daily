package com.dailysatori.ui.feature.settings.hotnews

import androidx.lifecycle.viewModelScope
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.data.repository.SettingRepository
import com.dailysatori.service.hotnews.*
import com.dailysatori.service.i18n.I18nService
import com.dailysatori.shared.db.DailySatoriDatabase
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import java.io.File
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class HotNewsSettingsViewModelTest {
    @Test fun editorRejectsInvalidAddressesThenPersistsValidFeedAndEnabledState() = withModel { model, service ->
        model.edit(HotNewsPlatform.XIAOHONGSHU)
        model.updateFeedUrl("javascript:alert(1)")
        model.saveFeedUrl()
        assertNotNull(model.state.value.editorError)
        assertEquals(HotNewsPlatform.XIAOHONGSHU, model.state.value.editor)
        assertEquals("", service.preferences.value.source(HotNewsPlatform.XIAOHONGSHU).feedUrl)
        model.updateFeedUrl(" https://feed.example/xiaohongshu/user-id/notes ")
        model.saveFeedUrl()
        withTimeout(5_000) { model.state.first { !it.saving && it.editor == null } }
        assertEquals("https://feed.example/xiaohongshu/user-id/notes",
            service.preferences.value.source(HotNewsPlatform.XIAOHONGSHU).feedUrl)
        model.setEnabled(HotNewsPlatform.XIAOHONGSHU, false)
        withTimeout(5_000) { model.state.first { !it.saving && !it.preferences.source(HotNewsPlatform.XIAOHONGSHU).enabled } }
        assertFalse(service.preferences.value.source(HotNewsPlatform.XIAOHONGSHU).enabled)
    }

    @Test fun connectionTestReturnsCountWithoutTranslatingOrChangingBoardCache() = withModel { model, service ->
        model.test(HotNewsPlatform.X)
        val result = withTimeout(5_000) { model.state.first { it.testing == null && it.message != null } }
        assertEquals("X：成功获取 1 条内容", result.message)
        assertTrue(service.boards.value.all { it.items.isEmpty() })
    }

    private fun withModel(block: suspend (HotNewsSettingsViewModel, HotNewsService) -> Unit) = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try {
            JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
                DailySatoriDatabase.Schema.create(driver)
                val settings = SettingRepository(DailySatoriDatabase(driver))
                val i18n = I18nService(settings).apply {
                    loadTranslation("zh", File("src/main/assets/i18n/zh.yaml").readText())
                    init("zh")
                }
                HttpClient(MockEngine { respond("""<rss><channel><item><title>测试新闻</title>
                    <link>https://x.com/user/status/1</link></item></channel></rss>""") }).use { http ->
                    val client = HotNewsClient(http)
                    val service = HotNewsService(client, HotNewsStore(settings), { error("Must not call AI") })
                    val model = HotNewsSettingsViewModel(service, client, i18n)
                    try { block(model, service) }
                    finally { model.viewModelScope.coroutineContext[Job]!!.cancelAndJoin() }
                }
            }
        } finally { Dispatchers.resetMain() }
    }
}
