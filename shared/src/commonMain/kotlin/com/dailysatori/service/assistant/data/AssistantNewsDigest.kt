package com.dailysatori.service.assistant.data

import app.cash.sqldelight.TransacterImpl
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.serialization.json.*

/**
 * Read-only projection of briefings the app already generated.
 *
 * It never fetches upstream: the assistant must answer "what matters today" from the local unified-news
 * result. "Today" is fixed by the turn's received instant and zone, so re-validating a checkpoint resolves
 * exactly the same date. A date without its own readable summary is reported as such; a historical
 * briefing is never presented as today's.
 *
 * Privacy is decided over the *complete* source set with one aggregate query, not over the bounded excerpt
 * page: a private or excluded source anywhere in the briefing withholds the derived prose and the title.
 */
internal class AssistantNewsDigest(
    private val driver: SqlDriver,
    private val today: String,
    private val zone: TimeZone = TimeZone.UTC,
    private val redact: (String) -> String = { it },
) {
    private val transactions = object : TransacterImpl(driver) {}
    fun read(date: String = "", window: String = ""): JsonObject = try {
        val requested = date.trim()
        val key = window.trim()
        require(key.isEmpty() || key in WINDOWS) { "invalid window" }
        // All reads below share one snapshot, so an exclusion or a source deletion cannot land halfway.
        consistent {
            when {
                requested.isEmpty() || requested.equals("today", ignoreCase = true) -> forDate(today, key, "today")
                requested.equals("latest", ignoreCase = true) -> latest(key)
                else -> runCatching { LocalDate.parse(requested) }.getOrNull()
                    ?.let { forDate(it.toString(), key, it.toString()) } ?: failure("invalid_date")
            }
        }
    } catch (_: Exception) {
        failure("invalid_or_unavailable_read")
    }

    private fun forDate(date: String, key: String, requested: String): JsonObject {
        val rows = summaries("WHERE summary_date = ?", listOf(date)).filter { key.isEmpty() || it.window == key }
        val best = rows.firstOrNull { it.status == STATUS_OK && it.content.isNotBlank() }
        return payload(requested, date, best, rows)
    }

    private fun latest(key: String): JsonObject {
        val clause = "WHERE status = ? AND length(trim(content)) > 0" + if (key.isEmpty()) "" else " AND window_key = ?"
        val row = summaries(clause, listOfNotNull(STATUS_OK, key.ifEmpty { null }), "summary_date DESC, window_end_ms DESC, id DESC")
            .firstOrNull()
        return payload("latest", row?.date ?: "", row, listOfNotNull(row))
    }

    /**
     * [best] is the readable version of [date]; when null the observed rows still describe the real state
     * (pending/failed/empty) instead of pretending there is no news. [rows] are ordered newest-first.
     */
    private fun payload(requested: String, date: String, best: Row?, rows: List<Row>): JsonObject {
        val observed = rows.firstOrNull()
        val sources = best?.let { sourcePrivacy(it.id) }
        val released = sources != null && sources.total > 0 && sources.publicCount == sources.total
        return buildJsonObject {
            put("ok", true)
            put("requested_date", requested)
            put("resolved_date", date)
            put("is_today", date == today)
            put("today", today)
            put("found", best != null)
            put("state", when {
                best != null && released -> "ready"
                // A briefing exists but nothing in it is proven releasable for cloud use. This is not
                // "no news": the user can still read the full briefing inside the app.
                best != null -> "restricted"
                observed == null -> "no_summary_for_date"
                observed.status == STATUS_PENDING -> "generating"
                observed.status == STATUS_FAILED -> "failed"
                observed.status == STATUS_EMPTY -> "empty"
                else -> "unreadable"
            })
            if (best == null) {
                observed?.let {
                    put("window", it.window)
                    put("window_end_ms", it.endMs)
                    put("observed_status", it.status)
                }
                put("latest_available_date", latestSuccessfulDate())
                refresh(date)?.let { (status, scope) ->
                    put("upstream_refresh_task", status)
                    put("upstream_refresh_scope", scope)
                }
                return@buildJsonObject
            }
            put("summary_date", best.date)
            put("window", best.window)
            put("window_start_ms", best.startMs)
            put("window_end_ms", best.endMs)
            best.generatedAt?.let { put("generated_at", it) }
            put("source_count", sources!!.total)
            put("prose_allowed", released)
            if (!released) {
                put("restricted_reason", sources.reason)
                put("read_in_app", true)
            }
            // The prose and its title can each paraphrase any source, so an unreleased briefing exposes
            // neither; only excerpts this code can prove public are ever listed.
            put("title", if (released) bound(best.title, TITLE_CHARS) else "")
            put("content", if (released) bound(best.content, CONTENT_CHARS) else "")
            put("content_truncated", released && best.content.length > CONTENT_CHARS)
            val excerpts = publicExcerpts(best.id)
            put("sources", JsonArray(excerpts))
            put("sources_omitted", (sources.total - excerpts.size).coerceAtLeast(0))
        }
    }

    /**
     * Only an observed queued/running/retrying refresh is reported, and only a tick whose own local date is
     * the requested one is claimed as that day's; anything else is reported as a global refresh.
     */
    private fun refresh(date: String): Pair<String, String>? {
        val active = rows(
            "SELECT status, json_extract(payload_json,'\$.tickDueAtMs') FROM async_task " +
                "WHERE type = 'remote_news_fetch' AND status IN ('queued','running','retrying') ORDER BY id ASC LIMIT 1",
            emptyList(), 2,
        ).firstOrNull() ?: return null
        val status = active[0].orEmpty()
        val dayRange = dayRange(date) ?: return status to "global"
        val scoped = rows(
            "SELECT 1 FROM async_task WHERE type = 'remote_news_fetch' AND status IN ('queued','running','retrying') " +
                "AND CAST(json_extract(payload_json,'\$.tickDueAtMs') AS INTEGER) BETWEEN ? AND ? LIMIT 1",
            listOf(dayRange.first, dayRange.second), 1,
        ).isNotEmpty()
        return status to if (scoped) "requested_date" else "global"
    }

    private fun dayRange(date: String): Pair<Long, Long>? = runCatching {
        val day = LocalDate.parse(date)
        day.atStartOfDayIn(zone).toEpochMilliseconds() to
            day.plus(DatePeriod(days = 1)).atStartOfDayIn(zone).toEpochMilliseconds() - 1
    }.getOrNull()

    private data class SourcePrivacy(val total: Int, val publicCount: Int, val reason: String)

    /**
     * Counts the complete source set; [publicCount] only covers rows this code can prove are public.
     * A briefing is released only when every one of its sources is proven public.
     */
    private fun sourcePrivacy(summaryId: Long): SourcePrivacy {
        val cells = rows(
            "SELECT COUNT(*), SUM(CASE WHEN $PROVEN_PUBLIC THEN 1 ELSE 0 END) " +
                "FROM unified_news_source s WHERE s.summary_id = ?",
            listOf(summaryId), 2,
        ).firstOrNull().orEmpty()
        val total = cells.getOrNull(0)?.toIntOrNull() ?: 0
        val publicCount = cells.getOrNull(1)?.toIntOrNull() ?: 0
        return SourcePrivacy(total, publicCount, when {
            total <= 0 -> "briefing has no attributable source"
            publicCount < total -> "briefing contains sources that are not proven public for cloud release"
            else -> ""
        })
    }

    private fun publicExcerpts(summaryId: Long): List<JsonObject> = rows(
        "SELECT s.source_type, s.title, s.summary FROM unified_news_source s WHERE s.summary_id = ? " +
            "AND ($PROVEN_PUBLIC) ORDER BY s.id ASC LIMIT ?",
        listOf(summaryId, MAX_SOURCES.toLong()), 3,
    ).map { row ->
        buildJsonObject {
            put("source_type", row[0].orEmpty())
            put("title", bound(row[1].orEmpty(), TITLE_CHARS))
            put("summary", bound(row[2].orEmpty(), SOURCE_SUMMARY_CHARS))
        }
    }

    private fun latestSuccessfulDate(): String? = rows(
        "SELECT summary_date FROM unified_news_summary WHERE status = ? AND length(trim(content)) > 0 " +
            "ORDER BY summary_date DESC, window_end_ms DESC LIMIT 1",
        listOf(STATUS_OK), 1,
    ).firstOrNull()?.get(0)

    private fun summaries(clause: String, args: List<String>, order: String = "window_end_ms DESC, id DESC"): List<Row> = rows(
        "SELECT id, summary_date, window_key, window_start_ms, window_end_ms, title, content, status, generated_at " +
            "FROM unified_news_summary $clause ORDER BY $order LIMIT ?",
        args + MAX_ROWS.toString(), 9,
    ).map { cells ->
        Row(cells[0]!!.toLong(), cells[1].orEmpty(), cells[2].orEmpty(), cells[3]?.toLong() ?: 0L, cells[4]?.toLong() ?: 0L,
            cells[5].orEmpty(), cells[6].orEmpty(), cells[7].orEmpty(), cells[8]?.toLong())
    }

    private fun bound(text: String, max: Int): String = redact(text).let { if (it.length > max) it.take(max) else it }

    private fun failure(code: String): JsonObject = buildJsonObject { put("ok", false); put("error", code) }

    private fun consistent(block: () -> JsonObject): JsonObject = transactions.transactionWithResult { block() }

    private fun rows(sql: String, args: List<Any?>, columns: Int): List<List<String?>> =
        driver.executeQuery(null, sql.trimIndent(), { cursor ->
            val result = mutableListOf<List<String?>>()
            while (cursor.next().value) result += (0 until columns).map { cursor.getString(it) }
            QueryResult.Value(result)
        }, args.size) {
            args.forEachIndexed { index, value ->
                if (value is Number) bindLong(index, value.toLong()) else bindString(index, value as String?)
            }
        }.value

    private data class Row(
        val id: Long, val date: String, val window: String, val startMs: Long, val endMs: Long,
        val title: String, val content: String, val status: String, val generatedAt: Long?,
    )

    private companion object {
        val WINDOWS = setOf("daily", "0800", "1330", "1800", "2100", "final")
        const val STATUS_OK = "success"
        const val STATUS_PENDING = "pending"
        const val STATUS_FAILED = "failed"
        const val STATUS_EMPTY = "empty"
        const val MAX_ROWS = 8
        const val MAX_SOURCES = 8
        const val CONTENT_CHARS = 4000
        const val TITLE_CHARS = 200
        const val SOURCE_SUMMARY_CHARS = 400

        /**
         * Provenance gate for cloud release. No stored source currently carries verifiable public-release
         * metadata, so this denies everything and keeps the briefing readable only inside the app:
         * - remote_article comes from a user-configured feed; its URL may embed a token and an independent
         *   token may be sent as Authorization (docs/08-remote-news-api.md), and remote_news_source.api_token
         *   is mandatory. source_type/source_filename only name the channel.
         * - hot_news may come from the user-editable per-platform feedUrl, and the X board is fetched with
         *   the user's own bearer token (XHotNewsClient), so a built-in platform key or a token-free URL is
         *   not proof of public content.
         * - local_favorite is local private content.
         * Replace this predicate only with an explicit, verifiable per-source public authorization; a
         * missing token, a domain or a source name must never be treated as that proof.
         */
        private const val PROVEN_PUBLIC = "0 = 1"
    }
}
