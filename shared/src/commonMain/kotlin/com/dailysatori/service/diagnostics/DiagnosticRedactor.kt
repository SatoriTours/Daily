package com.dailysatori.service.diagnostics

import io.ktor.http.Url

/** Allow-list projection, not a best-effort replacement of secrets in arbitrary text. */
object DiagnosticRedactor {
    private val numeric = setOf("status", "durationMs", "bytes", "count", "attempt", "reason", "exitTimestampMs", "sdk", "versionCode", "firstChunkMs", "inputTokens", "outputTokens", "translatedCount", "pendingCount", "taskId", "predecessorTaskId", "blockingTaskId",
        "notificationId", "foregroundServiceType", "runAttemptCount")
    private val foregroundStages = setOf("request", "request_ok", "request_failed", "worker_started", "worker_cancelled")
    private val foregroundWorkers = setOf("async_task", "external_favorite_queue", "diary_thought")
    private val foregroundStartFailures = setOf("start_not_allowed", "illegal_state", "other")
    private val foregroundOutcomes = setOf("completed", "failed", "skipped", "retry_scheduled",
        "nothing_pending", "sources_not_ready", "cancelled")
    /** 思想整理自身阶段：只放行固定值，未知值一律丢弃（不放行任意 stage 文本）。 */
    private val diaryStages = setOf("sources_observation_failed")
    /** 前台任务类型：仅本 App 已知的异步任务类型白名单，其它记为 unknown。 */
    private val foregroundTaskTypes = setOf("assistant_turn", "sms_reminder_parse", "external_favorite_organize",
        "save_article", "external_favorite_sync", "remote_article_reprocess", "remote_article_sync",
        "remote_news_fetch", "reminder_ai_parse", "diary_attachment_transcribe", "diary_thread_summarize",
        "article_memory_extract", "book_viewpoint_generate", "diary_knowledge_extract", "unknown")
    private val foregroundComponents = setOf("workmanager_system_foreground", "diary_recording", "unknown")
    private val foregroundTypeNames = setOf("data_sync", "microphone", "camera", "location", "media_playback",
        "connectivity", "short_service", "special_use", "unknown")
    private val taskTypes = setOf("remote_news_fetch", "remote_article_sync", "hot_news_refresh")
    private val taskStages = setOf("started", "waiting_predecessor", "claim_rejected", "not_runnable",
        "succeeded", "failed", "retry_scheduled", "skipped", "cancelled")
    private val newsSummaryStages = setOf("persisted", "skipped_superseded", "empty", "failed", "already_present")
    private val methods = setOf("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS")
    private val reminderFields = setOf("content", "start_date", "end_date", "first_reminder_time",
        "active_day_rule", "recurrence_rule", "selected_weekdays", "profile", "timezone", "other")
    private val providers = setOf("openai", "anthropic", "gemini", "deepseek")
    private val aiPurposes = setOf("interactive", "external_content", "reflection")
    private val hotNewsPlatforms = setOf("github", "hackernews", "x", "xiaohongshu", "toutiao", "zhihu", "v2ex", "linuxdo", "chinanews", "ithome")
    private val hotNewsStages = setOf("retry_queued", "retry_running", "cache", "translation_batch", "translation_request", "translation_result", "translation_failure")
    private val publicModels = setOf("gpt-4o", "gpt-4o-mini", "gpt-5", "gpt-5-mini", "gpt-5.1", "gpt-5.2",
        "claude-sonnet-4-5", "claude-opus-4-5", "gemini-2.5-pro", "gemini-2.5-flash",
        "deepseek-chat", "deepseek-reasoner", "deepseek-v4-flash", "deepseek-v4-pro", "deepseek-v4.1-flash", "deepseek-v4-flash-vision-exp")
    private val tools = setOf("get_latest_diary", "get_diary_by_date", "search_diary_by_content", "get_diary_by_tag",
        "get_diary_count", "get_latest_articles", "search_articles", "get_favorite_articles", "get_article_count",
        "get_latest_books", "search_books", "search_book_notes", "get_book_viewpoints", "get_book_count", "get_statistics",
        "query_local_database", "search_web_with_mcp", "search_memory", "get_memory_source", "create_reminder_draft")
    private val routes = listOf("/chat/completions", "/messages", "/models", "/responses", "/graphql")
    private val hosts = setOf("api.github.com", "api.x.com", "api.twitter.com", "api.openai.com", "api.anthropic.com", "generativelanguage.googleapis.com", "api.deepseek.com")

    fun fields(input: Map<String, String>): Map<String, String> = buildMap {
        input.forEach { (key, value) ->
            when {
                key in numeric -> value.toLongOrNull()?.takeIf { it >= 0 }?.let { put(key, it.toString()) }
                key == "startupStage" && value in setOf("runtime_ready", "database_prepare", "dependency_graph",
                    "database_open", "task_recovery", "translations", "first_draw") -> put(key, value)
                key == "articleStage" && value in setOf("queued", "extraction", "overview", "translation", "cover") -> put(key, value)
                key == "aiPurpose" && value in aiPurposes -> put(key, value)
                key == "aiStage" && value in setOf("queued", "requesting", "receiving", "received") -> put(key, value)
                key == "hotNewsPlatform" && value in hotNewsPlatforms -> put(key, value)
                key == "hotNewsStage" && value in hotNewsStages -> put(key, value)
                key == "operation" && value in setOf("hot_news_refresh", "hot_news_retry") -> put(key, value)
                key == "retryState" && value in setOf("accepted", "ignored_running") -> put(key, value)
                key == "unifiedNewsStage" && value in setOf("enqueue", "run", "tick") -> put(key, value)
                key == "unifiedNewsTarget" && value in setOf("window", "due") -> put(key, value)
                key == "unifiedNewsFailure" && value in setOf("timeout", "error", "budget_exhausted") -> put(key, value)
                key == "unifiedNewsDecision" && value in setOf("generate", "skip_stale", "skip_generated") -> put(key, value)
                key == "taskType" && value in taskTypes -> put(key, value)
                key == "taskStage" && value in taskStages -> put(key, value)
                key == "foregroundStage" && value in foregroundStages -> put(key, value)
                key == "diaryStage" && value in diaryStages -> put(key, value)
                key == "foregroundTaskType" && value in foregroundTaskTypes -> put(key, value)
                key == "foregroundWorker" && value in foregroundWorkers -> put(key, value)
                key == "foregroundStartFailure" && value in foregroundStartFailures -> put(key, value)
                key == "workerOutcome" && value in foregroundOutcomes -> put(key, value)
                key == "crashKind" && value in setOf("fgs_stop_timeout") -> put(key, value)
                key == "fgsComponent" && value in foregroundComponents -> put(key, value)
                key == "fgsTypeName" && value in foregroundTypeNames -> put(key, value)
                key == "newsSummaryStage" && value in newsSummaryStages -> put(key, value)
                key == "hotNewsFailure" && value in setOf("network", "timeout", "format", "configuration", "other") -> put(key, value)
                key == "hotNewsValidation" && value in setOf("json", "identity", "title", "summary", "missing_summary") -> put(key, value)
                key == "hotNewsValidationDetail" && value in setOf("json", "identity", "title_length", "title_chinese_count",
                    "title_untranslated", "summary_length", "summary_untranslated", "missing_summary") -> put(key, value)
                key == "method" && value in methods -> put(key, value)
                key == "reminderField" && value in reminderFields -> put(key, value)
                key == "backupAttachmentSource" && value in setOf("diary_attachment.local_path", "diary.images",
                    "image.path", "article.cover_image", "book.cover_image") -> put(key, value)
                key == "provider" && value.lowercase() in providers -> put(key, value.lowercase())
                key == "url" -> putAll(endpoint(value))
                key == "model" -> put(key, value.takeIf { it in publicModels } ?: "custom-${DiagnosticLog.fingerprint(value)}")
                key == "tool" -> put(key, value.takeIf { it in tools } ?: "custom-${DiagnosticLog.fingerprint(value)}")
                key in setOf("foreground", "permission.microphone", "permission.notifications", "cacheUsed") && value in setOf("true", "false") -> put(key, value)
                key == "screen" && value in setOf("MainActivity", "ShareReceiverActivity") -> put(key, value)
                key == "network" && value in setOf("wifi", "cellular", "ethernet", "other", "none") -> put(key, value)
            }
        }
    }

    fun endpoint(raw: String): Map<String, String> = runCatching {
        val url = Url(raw)
        val host = url.host.lowercase()
        val service = if (host in hosts) host else "custom-${DiagnosticLog.fingerprint(host + ":" + url.port)}"
        val route = routes.firstOrNull { url.encodedPath.endsWith(it) } ?: "[dynamic-path-omitted]"
        mapOf("service" to service, "route" to route)
    }.getOrDefault(mapOf("service" to "unknown", "route" to "[omitted]"))
}
