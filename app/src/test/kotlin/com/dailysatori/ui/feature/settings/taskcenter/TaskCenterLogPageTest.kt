package com.dailysatori.ui.feature.settings.taskcenter

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TaskCenterLogPageTest {
    private fun event(
        at: Long?,
        kind: TaskCenterLifecycleEventKind,
        summary: String = kind.name,
    ) = TaskCenterLifecycleEvent(atMs = at, summary = summary, kind = kind)

    private fun detail(
        at: Long? = null,
        status: String = "200",
        label: String = "AI 整理",
        url: String = "https://api.example.com/v1/chat/completions",
        body: TaskCenterLogField = TaskCenterLogField.Missing,
        headers: List<Pair<String, String>> = emptyList(),
        requestId: String = "",
        attemptNumber: Int = 1,
    ) = TaskCenterRequestDetail(
        label = label,
        attemptNumber = attemptNumber,
        requestId = requestId,
        method = "POST",
        url = url,
        queryRows = emptyList(),
        requestHeaders = headers,
        requestBody = body,
        responseStatus = status,
        responseHeaders = emptyList(),
        responseBody = TaskCenterLogField.Missing,
        errorLines = emptyList(),
        requestedAtMs = at,
        respondedAtMs = at?.plus(16_000L),
        durationMsOverride = 16_000L,
        attemptKnown = true,
    )

    private fun http(text: String) = taskCenterHttpLogParse(text)

    @Test
    fun lifecycleRoundsSplitOnRealStartedEvents() {
        val rounds = taskCenterLifecycleRounds(
            listOf(
                event(100L, TaskCenterLifecycleEventKind.Started),
                event(110L, TaskCenterLifecycleEventKind.Progress),
                event(120L, TaskCenterLifecycleEventKind.Failed),
                event(200L, TaskCenterLifecycleEventKind.Started),
                event(210L, TaskCenterLifecycleEventKind.Progress),
            ),
        )
        assertEquals(2, rounds.size)
        assertEquals(100L, rounds[0].startMs)
        assertEquals(120L, rounds[0].endMs)
        assertEquals(200L, rounds[1].startMs)
        assertEquals(2, rounds[1].events.size)
    }

    @Test
    fun roundsFallBackToASingleRoundWhenTheStartEventIsGone() {
        val rounds = taskCenterLifecycleRounds(listOf(event(10L, TaskCenterLifecycleEventKind.Progress)))
        assertEquals(1, rounds.size)
        assertEquals(10L, rounds.single().startMs)
    }

    @Test
    fun outcomeCountsComeOnlyFromRealOrganizeCheckpoints() {
        val checkpoint = """{"processed":6,"failed":2,"deferred":1,"itemId":9}"""
        assertEquals(
            TaskCenterOutcomeCounts(succeeded = 3L, failed = 2L, deferred = 1L),
            taskCenterOutcomeCounts("external_favorite_organize", checkpoint),
        )
        assertNull(taskCenterOutcomeCounts("external_favorite_sync", checkpoint))
        assertNull(taskCenterOutcomeCounts("external_favorite_organize", """{"processed":1,"failed":2}"""))
        assertNull(taskCenterOutcomeCounts("external_favorite_organize", ""))
    }

    @Test
    fun modelIsReadOnlyFromACapturedRequestBody() {
        val captured = detail(body = TaskCenterLogField("""{"model":"gpt-5.1","messages":[]}""", collected = true))
        assertEquals("gpt-5.1", taskCenterRequestModel(captured))
        assertNull(taskCenterRequestModel(detail(body = TaskCenterLogField.Missing)))
        assertNull(taskCenterRequestModel(detail(body = TaskCenterLogField("plain text", collected = true))))
    }

    @Test
    fun fullRequestJsonIsRedactedBeforeItCanBeCopied() {
        val item = detail(
            headers = listOf("Authorization" to "Bearer secret-token", "Accept" to "application/json"),
            body = TaskCenterLogField("""{"model":"m1","messages":[{"role":"user","content":"hi"}]}""", collected = true),
        )
        val json = taskCenterRequestFullJson(item)
        assertFalse(json.contains("secret-token"))
        assertTrue(json.contains("m1"))
        assertTrue(json.contains("\"messages\""))
        assertTrue(json.contains("responseBodyState"))
        assertTrue(json.contains("\"durationMs\": 16000"))
        assertTrue(json.contains("\"attempt\": 1"))
    }

    @Test
    fun logGroupsSortNewestFirstAndKeepUntimedRowsLast() {
        val zone = TimeZone.UTC
        val dayOne = Instant.parse("2026-11-11T08:00:00Z").toEpochMilliseconds()
        val dayTwo = Instant.parse("2026-11-12T09:00:00Z").toEpochMilliseconds()
        val entries = taskCenterLogEntries(
            listOf(
                event(dayOne, TaskCenterLifecycleEventKind.Started),
                event(dayTwo, TaskCenterLifecycleEventKind.Succeeded),
            ),
            http(""),
            "request",
            capturedRequests = listOf(detail(at = null)),
        )
        val newest = taskCenterLogGroups(entries, TaskCenterLogFilter.All, TaskCenterLogSort.Newest, "", zone)
        assertEquals(listOf(LocalDate(2026, 11, 12), LocalDate(2026, 11, 11), null), newest.map { it.date })
        assertTrue(newest.last().entries.single().request != null)
        val oldest = taskCenterLogGroups(entries, TaskCenterLogFilter.All, TaskCenterLogSort.Oldest, "", zone)
        assertEquals(listOf(LocalDate(2026, 11, 11), LocalDate(2026, 11, 12), null), oldest.map { it.date })
    }

    @Test
    fun abnormalFilterKeepsFailuresAndFailedResponsesOnly() {
        val parsed = http(
            """
            2026-11-12T09:00:00.000Z HTTP request {method=GET, service=api.example.com, route=/v1/items} params=[omitted] requestId=99999999-9999-9999-9999-999999999999 attempt=1
            2026-11-12T09:00:01.000Z HTTP response status=500 headers=[omitted] body=[omitted] requestId=99999999-9999-9999-9999-999999999999 attempt=1
            2026-11-12T09:00:02.000Z HTTP response status=204 headers=[omitted] body=[omitted]
            2026-11-12T09:00:03.000Z X content fetch summary {fx_success=2, fx_errors=0}
            """.trimIndent(),
        )
        val entries = taskCenterLogEntries(
            listOf(
                event(1L, TaskCenterLifecycleEventKind.Started),
                event(2L, TaskCenterLifecycleEventKind.Failed),
                event(3L, TaskCenterLifecycleEventKind.Succeeded),
            ),
            parsed,
            "request",
        )
        val kept = taskCenterLogGroups(entries, TaskCenterLogFilter.Abnormal, TaskCenterLogSort.Newest, "", TimeZone.UTC)
            .flatMap { it.entries }
        assertEquals(2, kept.size, "one failed event and one failed request")
        assertTrue(kept.any { it.request != null })
        assertFalse(kept.any { it.info })
    }

    @Test
    fun searchMatchesRealRequestFieldsAndEventText() {
        val entries = taskCenterLogEntries(
            listOf(event(1L, TaskCenterLifecycleEventKind.Progress, "已整理 2 条")),
            http(
                """
                2026-11-12T09:00:00.000Z HTTP request {method=POST, service=api.example.com, route=/v1/chat/completions} params=[omitted] label=external_favorite_ai requestId=aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa attempt=1
                """.trimIndent(),
            ),
            "request",
        )
        fun count(query: String) = taskCenterLogGroups(
            entries,
            TaskCenterLogFilter.All,
            TaskCenterLogSort.Newest,
            query,
            TimeZone.UTC,
        ).sumOf { it.entries.size }
        assertEquals(1, count("chat/completions"))
        assertEquals(1, count("已整理"))
        assertEquals(1, count("external_favorite_ai"))
        assertEquals(2, count(""))
        assertEquals(0, count("nothing-matches"))
    }

    @Test
    fun nonHttpLinesStayVisibleAndAreNeverMergedAway() {
        val parsed = http(
            """
            2026-11-12T09:00:00.000Z TASK started type=external_favorite_sync
            2026-11-12T09:00:01.000Z TASK progress current=1 total=2 message=正在读取
            2026-11-12T09:00:01.000Z TASK progress current=1 total=2 message=正在读取
            2026-11-12T09:00:02.000Z X content fetch summary {fx_success=2, budget_deferred=0}
            """.trimIndent(),
        )
        assertEquals(1, parsed.infoLines.size)
        assertTrue(parsed.infoLines.single().text.contains("fx_success=2"))
        val events = taskCenterLifecycleEvents(
            """
            2026-11-12T09:00:01.000Z TASK progress current=1 total=2 message=正在读取
            2026-11-12T09:00:01.000Z TASK progress current=1 total=2 message=正在读取
            """.trimIndent(),
            testStrings(),
        )
        assertEquals(2, events.size, "consecutive real lines are not folded away")
        val entries = taskCenterLogEntries(events, parsed, "request")
        assertTrue(entries.any { it.info && it.title.contains("X content fetch summary") })
    }

    @Test
    fun requestTitlesUseReliableLabelMethodAndRouteWithoutOpaqueIds() {
        val parsed = http(
            """
            2026-11-12T09:00:00.000Z HTTP request {method=GET, service=api.example.com, route=/v1/items} params=[omitted] requestId=bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb attempt=1
            2026-11-12T09:00:01.000Z HTTP request {method=GET, service=custom-host, route=[dynamic-path-omitted]} params=[omitted] requestId=cccccccc-cccc-cccc-cccc-cccccccccccc attempt=1
            2026-11-12T09:00:02.000Z HTTP request {method=GET, service=custom-host, route=[dynamic-path-omitted]} params=[omitted]
            """.trimIndent(),
        )
        val details = taskCenterRequestDetails(
            """
            2026-11-12T09:00:00.000Z HTTP request {method=GET, service=api.example.com, route=/v1/items} params=[omitted] requestId=bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb attempt=1
            2026-11-12T09:00:01.000Z HTTP request {method=GET, service=custom-host, route=[dynamic-path-omitted]} params=[omitted] requestId=cccccccc-cccc-cccc-cccc-cccccccccccc attempt=1
            """.trimIndent(),
        )
        assertEquals("GET api.example.com /v1/items", taskCenterRequestTitle(details[0], "request"))
        assertEquals("GET custom-host", taskCenterRequestTitle(details[1], "request"))
        assertFalse(taskCenterRequestTitle(details[0], "request").contains("bbbbbbbb"))
        assertEquals(3, parsed.entries.size)
    }

    @Test
    fun newCaptureTimesSortByRealTimeWhileOldRecordsStayUntimed() {
        val entries = taskCenterLogEntries(
            emptyList(),
            http(""),
            "request",
            capturedRequests = listOf(
                detail(at = Instant.parse("2026-11-12T09:00:00Z").toEpochMilliseconds(), label = "new"),
                detail(at = null, label = "old"),
            ),
        )
        val groups = taskCenterLogGroups(entries, TaskCenterLogFilter.All, TaskCenterLogSort.Newest, "", TimeZone.UTC)
        assertEquals(LocalDate(2026, 11, 12), groups.first().date)
        assertEquals(null, groups.last().date)
        assertEquals("old", groups.last().entries.single().title)
    }

    @Test
    fun retryLinksRequireTheSameExplicitRequestIdNeverLabelOrOrder() {
        val first = detail(at = 1L, requestId = "call-a", attemptNumber = 1)
        val second = detail(at = 2L, requestId = "call-a", attemptNumber = 2)
        val sameLabelOtherCall = detail(at = 3L, requestId = "call-b", attemptNumber = 1)
        assertEquals(listOf(second), taskCenterRelatedAttempts(first, listOf(first, second, sameLabelOtherCall)))
        val noIdentity = first.copy(requestId = "")
        assertTrue(taskCenterRelatedAttempts(noIdentity, listOf(noIdentity, second, sameLabelOtherCall)).isEmpty())
    }

    @Test
    fun dateAndClockLabelsOnlyClaimTodayForTheRealDate() {
        assertEquals(
            "today 11/12",
            taskCenterLogDateText(LocalDate(2026, 11, 12), LocalDate(2026, 11, 12), "today %1\$s", "%1\$d/%2\$d"),
        )
        assertEquals(
            "11/11",
            taskCenterLogDateText(LocalDate(2026, 11, 11), LocalDate(2026, 11, 12), "today %1\$s", "%1\$d/%2\$d"),
        )
        assertEquals(
            "08:40:05",
            taskCenterLogClockText(Instant.parse("2026-11-12T08:40:05Z").toEpochMilliseconds(), TimeZone.UTC),
        )
        assertEquals("", taskCenterLogClockText(null, TimeZone.UTC))
    }
}
