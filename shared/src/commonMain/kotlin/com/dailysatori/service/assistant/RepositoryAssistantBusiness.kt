package com.dailysatori.service.assistant

import app.cash.sqldelight.db.SqlDriver
import com.dailysatori.data.repository.*
import com.dailysatori.service.reminder.*
import com.dailysatori.service.assistant.data.AssistantDataGateway
import com.dailysatori.shared.db.DailySatoriDatabase
import io.ktor.http.Url
import kotlinx.datetime.*
import kotlinx.serialization.json.*

/** The prepare methods are database-only and share the caller's action transaction. */
class RepositoryAssistantBusiness(
    driver: SqlDriver,
    private val dispatch: (kind: String, target: String) -> Unit,
) {
    private val store = AssistantStore(driver)
    private val db = DailySatoriDatabase(driver)
    private val articles = ArticleRepository(db)
    private val reminders = ReminderRepository(db)
    private val tasks = AsyncTaskRepository(db)
    private val settings = SettingRepository(db)

    internal fun prepare(id: String, turn: AssistantTurn, request: AssistantActionRequest, input: String): AssistantActionView {
        return when (request.kind) {
            "article" -> prepareArticle(id, turn, request, input)
            "reminder" -> prepareReminder(id, turn, request)
            else -> error("Unsupported action")
        }
    }

    private fun prepareArticle(id: String, turn: AssistantTurn, request: AssistantActionRequest, input: String): AssistantActionView {
        val url = inputLinks(input)[request.urlRef] ?: error("Unknown local link handle")
        val parsed = Url(url)
        require(parsed.protocol.name in setOf("http", "https") && parsed.user.isNullOrEmpty() && parsed.password.isNullOrEmpty())
        val host = parsed.host.lowercase()
        require(host.contains('.') && !host.endsWith(".local") && !host.endsWith(".localhost") &&
            host != "localhost" && host.any(Char::isLetter) && host != "metadata.google.internal")
        val articleId = articles.recordManualIntake(url)
        // The durable original is the authoritative annotation; preserve the article's old comment as well.
        val note = AssistantDataGateway(store.connection).redact(input).take(32_000)
        val old = articles.getById(articleId)?.comment.orEmpty()
        if (!old.contains(note)) store.exec("UPDATE article SET comment = ? WHERE id = ?", listOf(old, note).filter(String::isNotBlank).joinToString("\n\n"), articleId)
        val existing = articles.getById(articleId)
        val completed = existing?.status in setOf("completed", "success", "done")
        val taskId = if (completed) null else tasks.enqueue("save_article", buildJsonObject { put("url", url) }.toString(), "save_article:$articleId")
        return AssistantActionView(id, "article", if (completed) "succeeded" else "submitted", articleId.toString(), taskId)
    }

    private fun prepareReminder(id: String, turn: AssistantTurn, request: AssistantActionRequest): AssistantActionView {
        require(request.content.isNotBlank() && request.content.length <= 2000)
        val zone = TimeZone.of(turn.timeZoneId)
        val today = Instant.fromEpochMilliseconds(turn.receivedAtMs).toLocalDateTime(zone).date
        val deadline = request.deadlineAt?.let(Instant::parse)
        require(deadline == null || request.recurrence == "once")
        val deadlineLocal = deadline?.toLocalDateTime(zone)
        val date = request.startDate?.let(LocalDate::parse) ?: deadlineLocal?.date ?: today
        val end = request.endDate?.let(LocalDate::parse) ?: deadlineLocal?.date ?: date
        val explicitTime = hasExplicitReminderTime(request.sourceQuote)
        val time = if (explicitTime) request.time?.let(LocalTime::parse) ?: deadlineLocal?.time else null
        require(end >= date)
        val recurrence = when {
            request.recurrence == "once" -> ReminderRecurrence.Once
            request.recurrence.startsWith("monthly:") -> ReminderRecurrence.Monthly(request.recurrence.substringAfter(':').toInt())
            request.recurrence.startsWith("yearly:") -> request.recurrence.split(':').let {
                require(it.size == 4); ReminderRecurrence.Yearly(it[1].toInt(), it[2].toInt(), LeapDayPolicy.valueOf(it[3]))
            }
            else -> error("Unsupported recurrence")
        }
        val activeDays = when {
            request.activeDays == "daily" -> ReminderActiveDayRule.Daily
            request.activeDays == "weekdays" -> ReminderActiveDayRule.Weekdays
            request.activeDays == "range" -> ReminderActiveDayRule.ConsecutiveDateRange
            request.activeDays.startsWith("selected:") -> ReminderActiveDayRule.SelectedWeekdays(request.activeDays.substringAfter(':').split(',').map(DayOfWeek::valueOf).toSet())
            else -> error("Invalid active days")
        }
        val profile = resolveDefaultReminderProfile(reminders, settings).snapshot
        val targetId = if (time == null) "assistant:todo:$id" else "assistant:$id"
        val draft = ReminderDraft(targetId, request.content, date, end, time ?: LocalTime(0, 0),
            timeZone = zone, recurrence = recurrence, activeDayRule = activeDays,
            deadlineAt = if (time != null) deadline else null, notes = "助手输入 chat/${turn.id}")
        reminders.createConfirmedOnce(draft, profile)
        if (time == null) reminders.pause(targetId)
        return AssistantActionView(id, "reminder", "submitted", targetId)
    }

    internal fun canRefine(action: AssistantActionView): Boolean =
        action.kind == "reminder" && action.targetId?.let(reminders::get)?.isUnscheduledSmsTodo == true

    internal fun refine(action: AssistantActionView, request: AssistantActionRequest): AssistantActionView {
        require(canRefine(action) && request.time != null && hasExplicitReminderTime(request.sourceQuote))
        val old = requireNotNull(action.targetId?.let(reminders::get))
        val start = request.startDate?.let(LocalDate::parse) ?: old.startDate
        val end = request.endDate?.let(LocalDate::parse) ?: start
        require(reminders.update(old.id, ReminderEdit(old.version, content = request.content.ifBlank { old.content },
            startDate = start, endDate = end, firstReminderTime = LocalTime.parse(request.time))))
        return action.copy(status = "submitted", message = "")
    }

    internal fun dispatch(action: AssistantActionView) {
        if (action.kind == "article") action.taskId?.let { dispatch("article", it.toString()) }
        else action.targetId?.let { dispatch("reminder", it) }
    }

    internal fun current(action: AssistantActionView): AssistantActionView {
        if (action.targetId == null) return action.copy(message = when (action.status) {
            "cancelled" -> "动作已取消，尚未创建业务对象"
            "needs_input" -> "信息不完整或参数无效，请补充后继续"
            else -> "已记录动作计划，尚未创建业务对象"
        })
        val exists = if (action.kind == "article") action.targetId.toLongOrNull()?.let(articles::getById) != null
            else reminders.get(action.targetId) != null
        if (!exists) return action.copy(status = "failed", message = "目标业务对象已删除，未重新创建")
        if (action.kind == "article") {
            val status = action.targetId?.toLongOrNull()?.let(articles::getById)?.status
            val task = action.taskId?.let(tasks::getById)
            return action.copy(status = when {
                status == "error" || task?.status == "failed" -> "failed"
                status in setOf("completed", "success", "done") || task?.status == "succeeded" -> "succeeded"
                else -> "submitted"
            }, message = when (status) {
                "webContentFetched" -> "原文已保存，AI 整理中"
                "error" -> "收录或整理失败，可在任务中心查看"
                "retrying" -> "收录重试中"
                else -> if (task?.status == "succeeded") "文章处理已结束" else "链接已接收，处理中"
            })
        }
        val reminder = action.targetId?.let(reminders::get)
        return action.copy(message = if (reminder?.status == ReminderStatus.PAUSED) "已保存无定时待办，不发送通知"
            else if (action.message.isNotBlank()) action.message else "提醒已保存；通知仍受系统权限与后台限制")
    }
}

/** A quoted sentence is data, not an instruction; the complete outer directive must be the evidence. */
internal fun isQuotedAssistantEvidence(input: String, quote: String): Boolean {
    val start = input.indexOf(quote)
    if (start < 0) return true
    val end = start + quote.length
    val ranges = listOf("“[^”]*”", "\"[^\"]*\"", "「[^」]*」", "『[^』]*』", "```[\\s\\S]*?```")
    if (ranges.any { pattern -> Regex(pattern).findAll(input).any { start > it.range.first && end <= it.range.last } }) return true
    val lineStart = input.lastIndexOf('\n', (start - 1).coerceAtLeast(0)) + 1
    return input.substring(lineStart, start).trimStart().startsWith(">") && '\n' !in quote
}

internal fun hasNegatedAssistantAction(input: String, quote: String, kind: String): Boolean {
    val start = input.indexOf(quote).coerceAtLeast(0)
    val prefix = input.substring((start-12).coerceAtLeast(0),start).takeLastWhile { it !in "。！？；\n.!?;" }
    val verb = if (kind == "article") "(保存|收录|收藏)" else "(提醒|待办|闹钟)"
    val english = if (kind == "article") "save" else "remind"
    return Regex("(不要|不用|无需|不必|别)[^，,。！？；\\n]{0,24}$verb|don't $english|do not $english",RegexOption.IGNORE_CASE)
        .containsMatchIn(prefix + quote)
}

internal fun hasExplicitReminderTime(quote: String): Boolean = Regex(
    "([0-9]{1,2}|[一二三四五六七八九十两]+)[点时]|[0-9]{1,2}[:：][0-9]{2}|(分钟|小时).*后|[0-9]+\\s*(min|hour|am|pm)", RegexOption.IGNORE_CASE,
).containsMatchIn(quote)

internal fun inputLinks(input: String): Map<String, String> = Regex("https?://[^\\s<>\"，。；、）)]+")
    .findAll(input).map { it.value.trimEnd('.', ',', ';', '!', '?') }.distinct().take(12)
    .mapIndexed { i, url -> "input_link_$i" to url }.toMap()

internal fun modelInput(input: String): String = inputLinks(input).entries.fold(input) { text, (ref, url) -> text.replace(url, "[$ref]") }
