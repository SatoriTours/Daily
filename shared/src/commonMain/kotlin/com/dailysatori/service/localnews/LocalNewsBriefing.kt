package com.dailysatori.service.localnews

import com.dailysatori.data.repository.UnifiedNewsSummaryRepository
import com.dailysatori.service.hotnews.HotNewsPlatform
import com.dailysatori.shared.db.Unified_news_source
import com.dailysatori.shared.db.Unified_news_summary
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** Which already generated briefing the user asked for. Answering it needs no model and no network. */
enum class LocalNewsIntent { Today, Latest }

/** [NotPrepared] means this date has no briefing row at all; it never borrows another day's content. */
enum class LocalNewsBriefingState { Ready, NotPrepared, Generating, Failed, Empty }

/** Sources are shown by title and platform only: no URL, token or error text ever leaves the database. */
data class LocalNewsSourceView(val title: String, val sourceType: String, val platformName: String)

data class LocalNewsBriefing(
    val intent: LocalNewsIntent,
    val state: LocalNewsBriefingState,
    val summaryDate: String = "",
    val window: String = "",
    val windowEndMs: Long = 0L,
    val title: String = "",
    val content: String = "",
    val sources: List<LocalNewsSourceView> = emptyList(),
    val isToday: Boolean = false,
    val latestDate: String = "",
)

/**
 * Reads briefings the app already generated. It never triggers a refresh, a task or a model request, and
 * the caller decides whether the result stays on the device.
 */
fun interface LocalNewsBriefingReader {
    /**
     * [receivedAtMs] and [timeZoneId] fix "today" to the instant the question arrived, so a read that
     * crosses midnight cannot silently change the answered date.
     */
    suspend fun read(intent: LocalNewsIntent, receivedAtMs: Long, timeZoneId: String): LocalNewsBriefing
}

class RepositoryLocalNewsBriefingReader(
    private val summaries: UnifiedNewsSummaryRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : LocalNewsBriefingReader {
    override suspend fun read(intent: LocalNewsIntent, receivedAtMs: Long, timeZoneId: String): LocalNewsBriefing =
        withContext(dispatcher) {
            val today = localDateOf(receivedAtMs, timeZoneId)
            val readable = readableLatest()
            // A generated-but-empty newest briefing still has a real date; it is reported as such and
            // never used as the answer itself.
            val latestDate = readable?.summary_date ?: summaries.getLatestSuccessful()?.summary_date.orEmpty()
            when (intent) {
                LocalNewsIntent.Latest -> readable?.let { ready(LocalNewsIntent.Latest, it, today) }
                    ?: LocalNewsBriefing(LocalNewsIntent.Latest, LocalNewsBriefingState.NotPrepared, latestDate = latestDate)
                LocalNewsIntent.Today -> todayBriefing(today, latestDate)
            }
        }

    /**
     * The newest briefing that really has text. White-space-only "success" rows are skipped in SQL and
     * guarded again here, so an older readable briefing is still found instead of reporting "nothing".
     */
    private fun readableLatest(): Unified_news_summary? =
        summaries.getReadableSummaries(READABLE_CANDIDATES).firstOrNull { it.content.isNotBlank() }

    private fun todayBriefing(today: String, latestDate: String): LocalNewsBriefing {
        val rows = summaries.getByDateAllWindows(today)
        val readable = rows.firstOrNull { it.status == STATUS_SUCCESS && it.content.isNotBlank() }
        if (readable != null) return ready(LocalNewsIntent.Today, readable, today)
        val state = when (rows.firstOrNull()?.status) {
            null -> LocalNewsBriefingState.NotPrepared
            STATUS_PENDING -> LocalNewsBriefingState.Generating
            STATUS_FAILED -> LocalNewsBriefingState.Failed
            STATUS_SUCCESS, STATUS_EMPTY -> LocalNewsBriefingState.Empty
            else -> LocalNewsBriefingState.NotPrepared
        }
        return LocalNewsBriefing(
            intent = LocalNewsIntent.Today,
            state = state,
            summaryDate = today,
            isToday = true,
            latestDate = latestDate,
        )
    }

    private fun ready(intent: LocalNewsIntent, row: Unified_news_summary, today: String): LocalNewsBriefing =
        LocalNewsBriefing(
            intent = intent,
            state = LocalNewsBriefingState.Ready,
            summaryDate = row.summary_date,
            window = row.window_key,
            windowEndMs = row.window_end_ms,
            title = row.title.trim(),
            content = row.content.trim(),
            sources = summaries.getSources(row.id).take(MAX_SOURCES).map(::sourceView),
            isToday = row.summary_date == today,
            latestDate = row.summary_date,
        )

    private fun sourceView(source: Unified_news_source): LocalNewsSourceView = LocalNewsSourceView(
        title = source.title.trim().take(SOURCE_TITLE_CHARS),
        sourceType = source.source_type,
        platformName = if (source.source_type == SOURCE_HOT_NEWS) {
            val key = source.source_filename?.substringBefore(':')
            HotNewsPlatform.entries.firstOrNull { it.key == key }?.displayName.orEmpty()
        } else "",
    )

    private fun localDateOf(receivedAtMs: Long, timeZoneId: String): String {
        val zone = runCatching { TimeZone.of(timeZoneId) }.getOrNull() ?: TimeZone.currentSystemDefault()
        return Instant.fromEpochMilliseconds(receivedAtMs).toLocalDateTime(zone).date.toString()
    }

    private companion object {
        const val STATUS_SUCCESS = "success"
        const val STATUS_PENDING = "pending"
        const val STATUS_FAILED = "failed"
        const val STATUS_EMPTY = "empty"
        const val SOURCE_HOT_NEWS = "hot_news"
        const val MAX_SOURCES = 12
        const val READABLE_CANDIDATES = 8L
        const val SOURCE_TITLE_CHARS = 120
    }
}

/**
 * Recognises only complete overview questions, so a mixed sentence keeps its ordinary assistant path.
 * The patterns are anchored: extra words ("帮我写日报", a topic, a citation or a follow-up) never match.
 */
fun localNewsIntentOf(text: String): LocalNewsIntent? {
    val compact = text.lowercase().filterNot { it.isWhitespace() || it in PUNCTUATION }
    if (compact.isEmpty() || compact.length > MAX_PHRASE_CHARS) return null
    return when {
        compact in ENGLISH_TODAY || TodayQuestion.matches(compact) -> LocalNewsIntent.Today
        compact in ENGLISH_LATEST || LatestQuestion.matches(compact) -> LocalNewsIntent.Latest
        else -> null
    }
}

private const val PUNCTUATION = "?？!！。.,，、;；:：'\"“”‘’()（）[]【】…~～-—"
private const val MAX_PHRASE_CHARS = 30

private val TodayQuestion = Regex(
    "^(请问|问一下)?(今天|今日|本日)(的)?" +
        "(有什么|有哪些|有没有|有啥)?(重点|重要|主要|值得关注|值得看|值得一看)?(的)?" +
        "(新闻简报|要闻简报|新闻|要闻|简报)(有哪些|有什么|吗|呢|啊|嘛|么)?$",
)

private val LatestQuestion = Regex(
    "^(请问|问一下)?(最近|最新|上次|上一次)(的)?(一次|一期)?" +
        "(新闻简报|要闻简报|新闻|要闻|简报)(有哪些|有什么|吗|呢|啊|嘛|么)?$",
)

private val ENGLISH_TODAY = setOf(
    "whatsthenewstoday", "whatisthenewstoday", "whatthenewstoday", "todaynews", "todaysnews",
    "newstoday", "todayheadlines", "todaybriefing", "briefingtoday",
)

private val ENGLISH_LATEST = setOf(
    "latestbriefing", "lastbriefing", "mostrecentbriefing", "latestnews", "recentbriefing", "newestbriefing",
)
