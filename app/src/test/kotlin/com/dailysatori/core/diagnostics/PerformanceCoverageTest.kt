package com.dailysatori.core.diagnostics

import com.dailysatori.service.diagnostics.PerformanceScreen
import com.dailysatori.ui.feature.article.ArticleReadMode
import com.dailysatori.ui.feature.article.performanceScreen
import com.dailysatori.ui.feature.settings.groupPages
import java.io.File
import kotlin.test.*

class PerformanceCoverageTest {
    @Test
    fun everyRegisteredRouteHasATypedContentTarget() {
        val nav = File("src/main/kotlin/com/dailysatori/core/navigation/NavHost.kt").readText()
        val routes = Regex("measuredDestination<(\\w+)>").findAll(nav).map { it.groupValues[1] }.toSet()
        assertTrue(routes.isNotEmpty())
        assertEquals(routes, contentTargets.keys.map { it.name }.toSet())
        contentTargets.forEach { (route, file) ->
            val source = File("src/main/kotlin/com/dailysatori/ui/feature/$file.kt").readText()
            assertTrue("performanceContent(" in source || "performanceTab(" in source || "MeasuredUi(" in source, route.name)
        }
        assertEquals(routes, PerformanceScreen.entries.filter { it.name.endsWith("Route") }.map { it.name }.toSet())
    }
    @Test
    fun switchableTabFamiliesHaveDistinctWhitelistedTargets() {
        val settings = com.dailysatori.ui.feature.settings.SettingsPage.entries
            .filter { with(it) { groupPages().isNotEmpty() } }
            .map { PerformanceScreen.valueOf("SETTINGS_" + it.name) }
        assertEquals(12, settings.distinct().size)

        val families = mapOf(
            "home/HomeScreen" to listOf("TODAY", "READING", "DIARY", "MY"),
            "unifiednews/UnifiedNewsScreen" to listOf("NEWS_FEED", "NEWS_SOURCE", "NEWS_HOTLISTS", "NEWS_OPPORTUNITIES", "ARTICLE_LIST", "ARTICLE_FAVORITES", "ARTICLE_EXTERNAL_LIST"),
            "ledger/LedgerScreen" to listOf("LEDGER_DETAIL", "LEDGER_ANALYSIS"),
            "settings/taskcenter/TaskCenterScreen" to listOf("TASK_CURRENT", "TASK_PENDING", "TASK_HISTORY"),
            "settings/taskcenter/TaskCenterTaskDetail" to listOf("TASK_REQUEST", "TASK_RESPONSE", "TASK_ERROR_TIMING"),
            "reminder/ReminderListScreen" to listOf("REMINDER_RECENT", "REMINDER_MONTHS", "REMINDER_FINISHED"),
            "ideatopic/IdeaTopicDetailScreen" to listOf("IDEA_OVERVIEW", "IDEA_PROVENANCE", "IDEA_HISTORY"),
            "aichat/AiChatScreen" to listOf("CHAT_MESSAGES", "CHAT_SESSIONS"),
            "unifiednews/HotNewsDetailScreen" to listOf("HOT_NEWS_SUMMARY", "HOT_NEWS_BODY"),
            "book/BooksScreen" to listOf("BOOK_VIEWPOINT"),
            "myspace/NewsOpportunityScreens" to listOf("OPPORTUNITY_FILTER"),
        )
        for ((file, tabs) in families) {
            val source = File("src/main/kotlin/com/dailysatori/ui/feature/$file.kt").readText()
            for (tab in tabs) assertTrue("PerformanceScreen.${PerformanceScreen.valueOf(tab)}" in source, "$file/$tab")
        }
    }

    @Test
    fun singleStreamReaderMeasuresDistinctRenderedSections() {
        val expected = mapOf(
            ArticleReadMode.ORIGINAL to PerformanceScreen.ARTICLE_READ_BODY,
            ArticleReadMode.SUMMARY to PerformanceScreen.ARTICLE_READ_SUMMARY,
            ArticleReadMode.TRANSLATION to PerformanceScreen.ARTICLE_READ_TRANSLATION,
        )
        assertEquals(expected, ArticleReadMode.entries.associateWith { it.performanceScreen() })
        val reader = File("src/main/kotlin/com/dailysatori/ui/feature/article/ArticleAiReader.kt").readText()
        expected.keys.forEach { mode ->
            assertTrue("performanceTab(ArticleReadMode.${mode.name}.performanceScreen()," in reader, mode.name)
        }
    }

    private val contentTargets = mapOf(
            PerformanceScreen.HomeRoute to "home/HomeScreen",
            PerformanceScreen.LifeArchiveRoute to "lifearchive/LifeArchiveScreen",
            PerformanceScreen.LedgerRoute to "ledger/LedgerScreen",
            PerformanceScreen.LedgerEntryRoute to "ledger/LedgerEntryScreen",
            PerformanceScreen.PhoneHistoryRoute to "phone/PhoneAssistantScreen",
            PerformanceScreen.ProfileRoute to "settings/SettingsScreen",
            PerformanceScreen.MyThoughtsRoute to "diary/DiaryThoughtScreen",
            PerformanceScreen.MyOpportunitiesRoute to "myspace/NewsOpportunityScreens",
            PerformanceScreen.MyOpportunityRoute to "myspace/NewsOpportunityScreens",
            PerformanceScreen.PersonalChatRoute to "aichat/AiChatScreen",
            PerformanceScreen.AssistantRoute to "aichat/AiChatScreen",
            PerformanceScreen.DataPrivacyRoute to "profile/DataPrivacyScreen",
            PerformanceScreen.ProfileFavoritesRoute to "article/ArticleListScreen",
            PerformanceScreen.ProfileExternalFavoritesRoute to "settings/externalfavorites/ExternalFavoritesSettingsScreen",
            PerformanceScreen.RemoteNewsSettingsRoute to "settings/remotenews/RemoteNewsSettingsScreen",
            PerformanceScreen.TaskCenterRoute to "settings/taskcenter/TaskCenterScreen",
            PerformanceScreen.ArticleDetailRoute to "article/ArticleDetailScreen",
            PerformanceScreen.NewsBriefingRoute to "unifiednews/UnifiedNewsBriefingScreen",
            PerformanceScreen.BookSearchRoute to "book/BookSearchScreen",
            PerformanceScreen.BookContentSearchRoute to "book/BookContentSearchScreen",
            PerformanceScreen.AiConfigRoute to "aiconfig/AiConfigScreen",
            PerformanceScreen.SpeechSettingsRoute to "settings/speech/SpeechSettingsScreen",
            PerformanceScreen.AiConfigEditRoute to "aiconfig/AiConfigEditScreen",
            PerformanceScreen.SettingsRoute to "settings/SettingsScreen",
            PerformanceScreen.ReminderListRoute to "reminder/ReminderListScreen",
            PerformanceScreen.ReminderSettingsRoute to "settings/reminder/ReminderSettingsScreen",
            PerformanceScreen.SmsSettingsRoute to "settings/sms/SmsSettingsScreen",
            PerformanceScreen.ReminderDetailRoute to "reminder/ReminderDetailScreen",
            PerformanceScreen.ReminderEditRoute to "reminder/ReminderEditScreen",
            PerformanceScreen.ReminderRoute to "reminder/ReminderDetailScreen",
            PerformanceScreen.ReminderAiBatchRoute to "reminder/ReminderAiBatchScreen",
            PerformanceScreen.ShareDialogRoute to "share/ShareDialogScreen",
            PerformanceScreen.IdeaTopicListRoute to "ideatopic/IdeaTopicListScreen",
            PerformanceScreen.IdeaTopicDetailRoute to "ideatopic/IdeaTopicDetailScreen",
            PerformanceScreen.IdeaTopicSessionRoute to "ideatopic/IdeaTopicSessionScreen",
        )
}
