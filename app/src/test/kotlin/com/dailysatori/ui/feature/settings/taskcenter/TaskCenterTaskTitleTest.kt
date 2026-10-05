package com.dailysatori.ui.feature.settings.taskcenter

import kotlin.test.Test
import kotlin.test.assertEquals

class TaskCenterTaskTitleTest {
    private val sources = TaskCenterSourceNames(
        remote = mapOf(1L to "科技新闻", 2L to "每日资讯", 3L to "已停用源"),
        enabledRemote = mapOf(1L to "科技新闻", 2L to "每日资讯"),
        favorites = mapOf(1L to "X · @daily", 2L to "GitHub · daily"),
    )

    @Test
    fun singleSourceSyncUsesTheSourceNameIncludingDisabledSourcesInHistory() {
        assertEquals("同步远程文章 · 科技新闻", title("remote_article_sync", """{"sourceId":1}"""))
        assertEquals("同步远程文章 · 已停用源", title("remote_article_sync", """{"sourceId":3}"""))
    }

    @Test
    fun batchTasksListEnabledNewsSourcesAndSummaryIncludesLocalFavorites() {
        assertEquals("同步远程文章 · 科技新闻、每日资讯", title("remote_article_sync", """{"mode":"due"}"""))
        assertEquals("获取远程新闻 · 科技新闻、每日资讯、本地收藏", title("remote_news_fetch", "{}"))
    }

    @Test
    fun favoritesUseTheirOwnSourceNamesRatherThanNewsSourcesWithTheSameId() {
        assertEquals("外部收藏同步 · X · @daily", title("external_favorite_sync", """{"sourceId":1}"""))
        assertEquals("收藏 AI 整理 · GitHub · daily", title("external_favorite_organize", """{"sourceId":2}"""))
    }

    @Test
    fun missingSourcesAndInvalidPayloadsHaveReadableFallbacks() {
        assertEquals("同步远程文章 · 新闻源 #99", title("remote_article_sync", """{"sourceId":99}"""))
        assertEquals("外部收藏同步 · 收藏源 #99", title("external_favorite_sync", """{"sourceId":99}"""))
        assertEquals("外部收藏同步", title("external_favorite_sync", "invalid"))
        assertEquals("同步远程文章 · 全部已启用新闻源", taskCenterTaskTitle("remote_article_sync", "{}", TaskCenterSourceNames()))
        assertEquals("获取远程新闻 · 本地收藏", taskCenterTaskTitle("remote_news_fetch", "{}", TaskCenterSourceNames()))
        assertEquals("保存文章", title("save_article", "invalid"))
    }

    @Test
    fun favoriteLabelsIdentifyTheProviderAndAccount() {
        assertEquals("X · 我的账号", taskCenterFavoriteSourceName("x", "我的账号", "@daily"))
        assertEquals("GitHub · daily", taskCenterFavoriteSourceName("github", "", "daily"))
        assertEquals("X", taskCenterFavoriteSourceName("x", "X", ""))
        assertEquals("X @daily", taskCenterFavoriteSourceName("x", "X @daily", "@daily"))
        assertEquals("GitHub Stars", taskCenterFavoriteSourceName("github", "GitHub Stars", "daily"))
    }

    private fun title(type: String, payload: String) = taskCenterTaskTitle(type, payload, sources)
}
