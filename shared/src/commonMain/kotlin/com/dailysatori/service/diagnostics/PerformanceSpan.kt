package com.dailysatori.service.diagnostics

import kotlinx.coroutines.CancellationException

enum class PerformancePoint {
    DB_PREPARE, DB_LOCK, DB_RECOVER, DB_ENCRYPT, DB_KEY, DB_OPEN, DB_SNAPSHOT, DB_COPY,
    DB_MIGRATE, DB_VALIDATE, DB_INSTALL_STAGE, DB_INSTALL, DB_READ, DB_WRITE, DB_TRANSACTION_BEGIN, DB_TRANSACTION, DB_COMMIT, DB_ROLLBACK,
    ARTICLE_DETAIL_LOAD, ARTICLE_LIST_LOAD, UI_FIRST_DRAW, UI_CONTENT_DRAW, IMAGE_LOAD, IMAGE_DECODE, WEBVIEW_LOAD,
    WEBVIEW_MAIN_FRAME,
}

enum class PerformanceResult { OK, EMPTY, FAILED, CANCELLED, ROLLED_BACK }

enum class PerformanceScreen {
    HomeRoute, LifeArchiveRoute, LedgerRoute, LedgerEntryRoute, PhoneHistoryRoute, ProfileRoute,
    MyThoughtsRoute, MyOpportunitiesRoute, MyOpportunityRoute, PersonalChatRoute, AssistantRoute,
    DataPrivacyRoute, ProfileFavoritesRoute, ProfileExternalFavoritesRoute, RemoteNewsSettingsRoute,
    TaskCenterRoute, ArticleDetailRoute, NewsBriefingRoute, BookSearchRoute, BookContentSearchRoute,
    AiConfigRoute, SpeechSettingsRoute, AiConfigEditRoute, SettingsRoute, ReminderListRoute,
    ReminderSettingsRoute, SmsSettingsRoute, ReminderDetailRoute, ReminderEditRoute, ReminderRoute,
    ReminderAiBatchRoute, ShareDialogRoute, IdeaTopicListRoute, IdeaTopicDetailRoute, IdeaTopicSessionRoute,
    TODAY, DIARY, READING, MY, ARTICLE_LIST, ARTICLE_FAVORITES, ARTICLE_EXTERNAL_LIST,
    LEDGER_DETAIL, LEDGER_ANALYSIS, TASK_CURRENT, TASK_PENDING, TASK_HISTORY, TASK_DETAIL, TASK_LOG,
    TASK_REQUEST, TASK_RESPONSE, TASK_ERROR_TIMING, CHAT_MESSAGES, CHAT_SESSIONS, CHAT_DIALOG,
    REMINDER_RECENT, REMINDER_MONTHS, REMINDER_FINISHED, OPPORTUNITY_FILTER,
    IDEA_OVERVIEW, IDEA_PROVENANCE, IDEA_HISTORY, ARTICLE_READ_SUMMARY, ARTICLE_READ_BODY, ARTICLE_READ_TRANSLATION, IDEA_DISCUSSION,
    HOT_NEWS_SUMMARY, HOT_NEWS_BODY, BOOK_VIEWPOINT,
    SETTINGS_AI_CONFIG, SETTINGS_AI_PURPOSE, SETTINGS_SPEECH, SETTINGS_SKILLS, SETTINGS_PLUGIN_CENTER,
    SETTINGS_MCP_SERVER, SETTINGS_WEB_SERVICE, SETTINGS_HOT_NEWS, SETTINGS_REMOTE_NEWS, SETTINGS_EXTERNAL_FAVORITES,
    SETTINGS_BACKUP_SETTINGS, SETTINGS_BACKUP_RESTORE,
    NEWS_FEED, NEWS_OPPORTUNITIES, NEWS_HOTLISTS, NEWS_SOURCE, NEWS_DETAIL,
}

private val performancePoints = PerformancePoint.entries.mapTo(hashSetOf()) { it.name }
private val performanceResults = PerformanceResult.entries.mapTo(hashSetOf()) { it.name }
private val performanceScreens = PerformanceScreen.entries.mapTo(hashSetOf()) { it.name }

private val localCorrelationId = Regex("[a-f0-9-]{36}")

internal fun allowedPerformanceField(key: String, value: String): Boolean = when (key) {
    "perfPoint" -> value in performancePoints
    "perfResult" -> value in performanceResults
    "perfScreen" -> value in performanceScreens
    "dataTraceId" -> localCorrelationId.matches(value)
    "imageSource" -> value in setOf("MEMORY_CACHE", "MEMORY", "DISK", "NETWORK")
    else -> false
}

/** Only fixed labels and random local correlation IDs cross this boundary. */
class PerformanceSpan(
    val point: PerformancePoint,
    val began: Long = DiagnosticLog.elapsed(),
    val trace: DiagnosticTrace = DiagnosticLog.currentTrace() ?: DiagnosticTrace(DiagnosticLog.newId()),
    private val screen: PerformanceScreen? = trace.screen,
    private val sink: Diagnostics = DiagnosticLog.diagnostics,
    private val clock: () -> Long = DiagnosticLog::elapsed,
) {
    fun finish(result: PerformanceResult = PerformanceResult.OK, imageSource: String? = null, dataTrace: DiagnosticTrace? = null) {
        sink.emit(DiagnosticCode.PERFORMANCE, when (point) {
            PerformancePoint.IMAGE_LOAD, PerformancePoint.IMAGE_DECODE -> DiagnosticSource.IMAGE
            PerformancePoint.WEBVIEW_LOAD, PerformancePoint.WEBVIEW_MAIN_FRAME -> DiagnosticSource.WEBVIEW
            PerformancePoint.UI_FIRST_DRAW, PerformancePoint.UI_CONTENT_DRAW -> DiagnosticSource.APP
            else -> DiagnosticSource.DATABASE
        }, level = if (result == PerformanceResult.FAILED) DiagnosticLevel.WARNING else DiagnosticLevel.INFO,
            fields = buildMap {
                put("perfPoint", point.name)
                put("perfResult", result.name)
                put("durationMs", (clock() - began).coerceAtLeast(0).toString())
                screen?.let { put("perfScreen", it.name) }
                imageSource?.let { put("imageSource", it) }
                dataTrace?.let { put("dataTraceId", it.id) }
            }, trace = trace)
    }

    inline fun <T> measure(block: () -> T): T {
        var result = PerformanceResult.OK
        try {
            return block()
        } catch (failure: Throwable) {
            result = if (failure is CancellationException) PerformanceResult.CANCELLED else PerformanceResult.FAILED
            throw failure
        } finally {
            finish(result)
        }
    }
}
