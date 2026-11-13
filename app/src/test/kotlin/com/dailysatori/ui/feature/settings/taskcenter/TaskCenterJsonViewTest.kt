package com.dailysatori.ui.feature.settings.taskcenter

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TaskCenterJsonViewTest {
    private val document = taskCenterJsonDocument(
        """{"model":"deepseek-chat","stream":true,"temperature":0.3,"messages":[{"role":"system"}]}""",
    )

    @Test
    fun nestedJsonIsPrettyPrintedByDefaultWithTwoSpaceIndent() {
        assertTrue(document.formatted)
        assertNull(document.errorMessage)
        val lines = document.text.lines()
        assertEquals("{", lines.first())
        assertTrue(lines.any { it == "  \"model\": \"deepseek-chat\"," })
        assertTrue(lines.any { it == "      \"role\": \"system\"" })
    }

    @Test
    fun rootAndNestedObjectsAreFoldable() {
        val lines = document.text.lines()
        val folds = taskCenterJsonFolds(lines)
        val root = folds[0]
        assertNotNull(root)
        assertEquals(lines.lastIndex, root.endLine)
        val visible = taskCenterJsonVisibleLines(lines, collapsed = setOf(0))
        assertEquals(1, visible.size)
        assertEquals(lines.lastIndex, visible.single().foldEndLine)
        assertEquals(lines.lastIndex, visible.single().hiddenLineCount)
    }

    @Test
    fun tokensMarkKeysStringsNumbersAndLiterals() {
        val modelLine = document.text.lines().first { it.contains("\"model\"") }
        val tokens = taskCenterJsonTokens(modelLine)
        assertEquals(TaskCenterJsonTokenType.Key, tokens.first { it.text == "\"model\"" }.type)
        assertEquals(TaskCenterJsonTokenType.Text, tokens.first { it.text == "\"deepseek-chat\"" }.type)
        val temperature = taskCenterJsonTokens(document.text.lines().first { it.contains("temperature") })
        assertEquals(TaskCenterJsonTokenType.Number, temperature.first { it.text == "0.3" }.type)
        val stream = taskCenterJsonTokens(document.text.lines().first { it.contains("\"stream\"") })
        assertEquals(TaskCenterJsonTokenType.Literal, stream.first { it.text == "true" }.type)
    }

    @Test
    fun highlightedLineKeepsEveryCharacterOfTheCapturedBytes() {
        val line = """  "remoteNewsSourceId": 1,"""
        val red = androidx.compose.ui.graphics.Color(0xFFFF0000)
        val annotated = taskCenterJsonAnnotatedLine(line, red, red, red, red)
        assertEquals(line, annotated.text)
        val broken = """  "choices": ["""
        assertEquals(broken, taskCenterJsonAnnotatedLine(broken, red, red, red, red).text)
    }

    @Test
    fun searchFindsMatchingLines() {
        val lines = document.text.lines()
        val matches = taskCenterJsonMatchLines(lines, "model")
        assertEquals(1, matches.size)
        assertEquals(lines.indexOfFirst { it.contains("model") }, matches.single())
        assertTrue(taskCenterJsonMatchLines(lines, "").isEmpty())
    }

    @Test
    fun invalidJsonKeepsRawTextAndReportsErrorButNeverRepairsIt() {
        val raw = """{"a": [1, 2"""
        val invalid = taskCenterJsonDocument(raw)
        assertFalse(invalid.formatted)
        assertEquals(raw, invalid.text)
        assertNotNull(invalid.errorMessage)
    }

    @Test
    fun oversizedBodyIsNotFormattedAndKeepsRawText() {
        val raw = "{\"a\":\"" + "x".repeat(2_000) + "\"}"
        val bounded = taskCenterJsonDocument(raw, maxFormatChars = 100)
        assertFalse(bounded.formatted)
        assertEquals(raw, bounded.text)
        assertNotNull(bounded.errorMessage)
    }

    @Test
    fun sseChunksKeepBoundariesAndFormatEachJsonChunk() {
        val body = """
            data: {"id":"a","choices":[{"delta":{"content":"今日"}}]}

            data: {"id":"a","choices":[{"delta":{"content":"要闻"}}]}

            data: [DONE]
        """.trimIndent()
        val stream = taskCenterSseView(body)
        assertEquals(3, stream.chunks.size)
        assertEquals("data: {\"id\":\"a\",\"choices\":[{\"delta\":{\"content\":\"今日\"}}]}", stream.chunks.first().raw)
        assertTrue(stream.chunks.first().pretty!!.contains("\n  \"id\": \"a\","))
        assertNull(stream.chunks.first().parseErrorMessage)
        assertTrue(stream.chunks.last().done)
        assertTrue(stream.doneReceived)
        assertFalse(stream.incomplete)
    }

    @Test
    fun truncatedSseIsMarkedIncompleteWithoutFabricatingDoneMarker() {
        val body = "data: {\"choices\":[{\"delta\":{\"content\":\"部分\"}}]}"
        val stream = taskCenterSseView(body)
        assertEquals(1, stream.chunks.size)
        assertFalse(stream.doneReceived)
        assertTrue(stream.incomplete)
        assertFalse(stream.chunks.any { it.done })
    }

    @Test
    fun invalidSseChunkKeepsRawTextAndFlagsError() {
        val stream = taskCenterSseView("data: {\"choices\": [")
        assertEquals("data: {\"choices\": [", stream.chunks.single().raw)
        assertNull(stream.chunks.single().pretty)
        assertNotNull(stream.chunks.single().parseErrorMessage)
    }

    @Test
    fun redactionHidesCredentialsEverywhereButKeepsDebugContext() {
        val raw = listOf(
            "Authorization: Bearer abcdefghijklmnop",
            "Cookie: session=deadbeef",
            """{"api_key":"sk-live-1234567890","prompt":"hello"}""",
            "https://api.example.com/v1?token=zzz&page=2",
        ).joinToString("\n")
        val redacted = taskCenterRedactSensitive(raw)
        assertFalse(redacted.contains("abcdefghijklmnop"))
        assertFalse(redacted.contains("deadbeef"))
        assertFalse(redacted.contains("sk-live-1234567890"))
        assertFalse(redacted.contains("token=zzz"))
        assertTrue(redacted.contains("hello"))
        assertTrue(redacted.contains("page=2"))
    }

    @Test
    fun requestDetailsExposeMethodQueryHeadersBodyAndTiming() {
        val run = taskCenterRequestDetails(
            """
            2026-06-28T10:00:00.000Z HTTP request [chat] POST https://api.deepseek.com/chat/completions params=stream=true label=chat requestId=11111111-1111-1111-1111-111111111111 attempt=1
            2026-06-28T10:00:00.010Z Content-Type: application/json
            2026-06-28T10:00:00.020Z Authorization: Bearer sk-secret1234567890
            2026-06-28T10:00:00.030Z {"model":"deepseek-chat","stream":true}
            2026-06-28T10:00:00.250Z HTTP response [chat] status=200 headers=content-type=text/event-stream body=data: {"choices":[]} requestId=11111111-1111-1111-1111-111111111111 attempt=1
            """.trimIndent(),
        )
        val detail = run.single()
        assertEquals("POST", detail.method)
        assertEquals("https://api.deepseek.com/chat/completions", detail.url)
        assertEquals(listOf("stream" to "true"), detail.queryRows)
        assertTrue(detail.requestHeaders.contains("Content-Type" to "application/json"))
        assertTrue(detail.requestHeaders.any { it.first == "Authorization" && it.second == "[已隐藏]" })
        assertTrue(detail.requestBody.collected)
        assertTrue(detail.requestBody.text.contains("deepseek-chat"))
        assertEquals("200", detail.responseStatus)
        assertTrue(detail.responseBody.collected)
        assertEquals("250ms", detail.durationLabel(testStrings()))
        assertEquals(true, detail.httpOk)
        assertEquals(true, detail.attemptKnown)
        assertFalse(detail.associationUnknown)
        assertFalse(detail.collectsSensitivePlaintext)
    }

    @Test
    fun omittedBodiesAreReportedAsNotCollectedInsteadOfFakeContent() {
        val detail = taskCenterRequestDetails(
            """
            2026-06-28T10:00:00.000Z HTTP request {method=GET, url=https://api.x.com/2/users/me/bookmarks} params=[omitted] label=bookmarks requestId=22222222-2222-2222-2222-222222222222 attempt=1
            2026-06-28T10:00:01.000Z HTTP response status=200 headers=[omitted] body=[omitted] requestId=22222222-2222-2222-2222-222222222222 attempt=1
            """.trimIndent(),
        ).single()
        assertEquals("GET", detail.method)
        assertEquals("https://api.x.com/2/users/me/bookmarks", detail.url)
        assertTrue(detail.requestHeaders.isEmpty())
        assertFalse(detail.requestBody.collected)
        assertFalse(detail.responseBody.collected)
        assertTrue(detail.responseHeaders.isEmpty())
        assertNull(detail.stream)
        assertEquals("1.0s", detail.durationLabel(testStrings()))
        assertEquals("bookmarks", detail.label)
    }

    @Test
    fun repeatedRequestsOfTheSameTargetKeepExplicitAttemptsWithoutGuessing() {
        val run = taskCenterRequestDetails(
            """
            2026-06-28T10:00:00.000Z HTTP request [external_favorite_ai] POST https://api.openai.com/v1/chat/completions params=[omitted] requestId=33333333-3333-3333-3333-333333333333 attempt=1
            2026-06-28T10:00:01.000Z HTTP response [external_favorite_ai] status=500 body={"error":"boom"} requestId=33333333-3333-3333-3333-333333333333 attempt=1
            2026-06-28T10:00:05.000Z HTTP request [external_favorite_ai] POST https://api.openai.com/v1/chat/completions params=[omitted] requestId=33333333-3333-3333-3333-333333333333 attempt=2
            2026-06-28T10:00:06.000Z HTTP response [external_favorite_ai] status=200 body={"ok":true} requestId=33333333-3333-3333-3333-333333333333 attempt=2
            """.trimIndent(),
        )
        assertEquals(2, run.size)
        assertEquals(listOf(1, 2), run.map { it.attemptNumber })
        assertTrue(run.all { it.attemptKnown })
        assertEquals(false, run.first().httpOk)
        assertEquals(true, run.last().httpOk)
    }

    @Test
    fun legacyLinesWithoutIdentityStayIndependentWithUnknownAssociation() {
        val run = taskCenterRequestDetails(
            """
            2026-06-28T10:00:00.000Z HTTP request [chat] POST https://api.deepseek.com/chat/completions params=stream=true
            2026-06-28T10:00:00.250Z HTTP response [chat] status=200 body={"ok":true}
            """.trimIndent(),
        )
        val detail = run.single()
        assertEquals("", detail.responseStatus)
        assertTrue(detail.associationUnknown)
        assertFalse(detail.attemptKnown)
        assertNull(detail.respondedAtMs)
        assertEquals("unknown", detail.durationLabel(testStrings()))
    }

    @Test
    fun curlCopyDoesNotInventBodyThatWasNeverCollected() {
        val detail = taskCenterRequestDetails(
            """
            2026-06-28T10:00:00.000Z HTTP request {method=POST, url=https://api.example.com/v1} params=[omitted]
            2026-06-28T10:00:01.000Z HTTP response status=200 headers=[omitted] body=[omitted]
            """.trimIndent(),
        ).single()
        val curl = taskCenterCurlCommand(detail)
        assertFalse(curl.contains("--data-raw"))
        assertTrue(curl.contains("https://api.example.com/v1"))
    }
}
