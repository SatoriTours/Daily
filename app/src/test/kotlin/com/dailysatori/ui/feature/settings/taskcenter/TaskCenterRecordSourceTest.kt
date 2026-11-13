package com.dailysatori.ui.feature.settings.taskcenter

import com.dailysatori.service.diagnostics.TASK_HTTP_KIND_BUSINESS_RESULT
import com.dailysatori.service.diagnostics.TaskHttpDirection
import com.dailysatori.service.diagnostics.TaskHttpRecord
import com.dailysatori.service.diagnostics.TaskHttpRecordCodec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TaskCenterRecordSourceTest {
    private val copy = TaskCenterSourceCopy(
        httpBadge = "HTTP",
        businessBadge = "BUSINESS",
        unknownBadge = "UNKNOWN",
        sourceLabel = "source",
        responseOk = "HTTP %1\$s ok",
        responseFailed = "HTTP %1\$s failed",
        responseMissing = "no response",
        responseIncomplete = "HTTP %1\$s incomplete",
        unknownAssociation = "unlinked",
        businessSucceeded = "biz ok",
        businessFailed = "biz failed",
        businessNoStatus = "no provider status",
        providerStatus = "provider %1\$s",
        unknownStatus = "unknown %1\$s",
        requestTitleHttp = "http request",
        responseTitleHttp = "http response",
        errorTitleHttp = "http error",
        requestTitleBusiness = "business request",
        responseTitleBusiness = "organized result",
        errorTitleBusiness = "business error",
        requestTitleUnknown = "unknown request",
        responseTitleUnknown = "unknown response",
        errorTitleUnknown = "unknown error",
        startedHttp = "http start",
        finishedHttp = "http finish",
        durationHttp = "http duration",
        startedBusiness = "business start",
        finishedBusiness = "business finish",
        durationBusiness = "business duration",
        startedUnknown = "unknown start",
        finishedUnknown = "unknown finish",
        durationUnknown = "unknown duration",
        attemptHttp = "HTTP attempt %1\$d",
        attemptBusiness = "logical call %1\$d",
        tabResponseHttp = "http response tab",
        tabErrorHttp = "http error tab",
        tabResponseBusiness = "result tab",
        tabErrorBusiness = "business error tab",
        tabResponseUnknown = "unknown response tab",
        tabErrorUnknown = "unknown error tab",
        hintHttpCaptured = "captured hint",
        hintHttpLog = "log hint",
        hintBusiness = "business hint",
        hintUnknown = "unknown hint",
    )

    private fun detail(
        source: TaskCenterRecordSource,
        status: String = "200",
        attemptKnown: Boolean = false,
        attemptNumber: Int = 1,
        fromCapture: Boolean = false,
        requestId: String = "",
    ) = TaskCenterRequestDetail(
        label = "external_favorite_ai",
        attemptNumber = attemptNumber,
        requestId = requestId,
        method = "POST",
        url = "https://api.example.com/v1/chat/completions",
        queryRows = emptyList(),
        requestHeaders = emptyList(),
        requestBody = TaskCenterLogField.Missing,
        responseStatus = status,
        responseHeaders = emptyList(),
        responseBody = TaskCenterLogField.Missing,
        errorLines = emptyList(),
        requestedAtMs = 1_000L,
        respondedAtMs = if (status.isBlank()) null else 2_000L,
        durationMsOverride = if (status.isBlank()) null else 1_000L,
        attemptKnown = attemptKnown,
        associationUnknown = status.isBlank(),
        source = source,
        fromCapture = fromCapture,
    )

    @Test
    fun realHttpKeepsHttpVocabulary() {
        val http = taskCenterSourceView(detail(TaskCenterRecordSource.Http, "200", attemptKnown = true, fromCapture = true), copy)
        assertEquals("HTTP", http.badge)
        assertEquals("HTTP 200 ok", http.status)
        assertFalse(http.statusIsError)
        assertEquals("HTTP attempt 1", http.attempt)
        assertEquals("http request", http.requestBodyTitle)
        assertEquals("http response", http.responseBodyTitle)
        assertEquals("captured hint", http.hint)
        assertEquals("http", http.jsonSource)
        val failed = taskCenterSourceView(detail(TaskCenterRecordSource.Http, "599"), copy)
        assertEquals("HTTP 599 failed", failed.status)
        assertTrue(failed.statusIsError)
        assertNull(failed.attempt)
    }

    @Test
    fun businessSuccessIsNeverPresentedAsHttp() {
        val view = taskCenterSourceView(
            detail(TaskCenterRecordSource.BusinessResult, "200", attemptKnown = true),
            copy,
        )
        assertEquals("BUSINESS", view.badge)
        assertEquals("biz ok", view.status)
        assertFalse(view.status.contains("HTTP"))
        assertFalse(view.statusIsError)
        assertEquals("logical call 1", view.attempt)
        assertEquals("business request", view.requestBodyTitle)
        assertEquals("organized result", view.responseBodyTitle)
        assertEquals("business error", view.errorTitle)
        assertEquals("business hint", view.hint)
        assertEquals("business_result", view.jsonSource)
    }

    @Test
    fun syntheticBusinessFailureIsALocalErrorNotAServerStatus() {
        val view = taskCenterSourceView(detail(TaskCenterRecordSource.BusinessResult, "599"), copy)
        assertEquals("biz failed · no provider status", view.status)
        assertFalse(view.status.contains("HTTP"))
        assertFalse(view.status.contains("server", ignoreCase = true))
        assertTrue(view.statusIsError)
        assertFalse(view.status.contains("599"))
    }

    @Test
    fun providerReportedStatusIsLabelledAsSuch() {
        val view = taskCenterSourceView(detail(TaskCenterRecordSource.BusinessResult, "429"), copy)
        assertEquals("biz failed · provider 429", view.status)
        assertFalse(view.status.contains("HTTP"))
    }

    @Test
    fun missingSourceStaysUnknownAndNeverClaimsHttp() {
        val view = taskCenterSourceView(detail(TaskCenterRecordSource.Unknown, "200"), copy)
        assertEquals("UNKNOWN", view.badge)
        assertEquals("unknown 200", view.status)
        assertFalse(view.status.contains("HTTP"))
        assertNull(view.attempt)
        assertEquals("unknown request", view.requestBodyTitle)
        assertEquals("unknown response", view.responseBodyTitle)
        assertEquals("unknown hint", view.hint)
        assertEquals("unknown", view.jsonSource)
        val blank = taskCenterSourceView(detail(TaskCenterRecordSource.Unknown, ""), copy)
        assertEquals("UNKNOWN", blank.status)
    }

    @Test
    fun copyAndFullJsonCarryTheSourceAndStayRedacted() {
        val business = detail(
            TaskCenterRecordSource.BusinessResult,
            "200",
            attemptKnown = true,
            requestId = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee",
        )
        val json = taskCenterRequestFullJson(business)
        assertTrue(json.contains("\"source\": \"business_result\""))
        assertTrue(json.contains("\"fromCapture\": false"))
        assertFalse(json.contains("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"))
        assertTrue(taskCenterRequestCopyText(business).startsWith("# source=business_result"))
        val unknown = detail(TaskCenterRecordSource.Unknown, "200")
        assertTrue(taskCenterRequestCopyText(unknown).startsWith("# source=unknown"))
        val http = detail(TaskCenterRecordSource.Http, "200")
        assertTrue(taskCenterRequestCopyText(http).startsWith("# source=http"))
    }

    @Test
    fun textLogKeepsBusinessAndHttpSourcesDistinct() {
        val businessLine = """
            2026-11-12T09:00:00.000Z HTTP request {method=POST, service=api.example.com, route=/v1/chat/completions} params=[omitted] label=external_favorite_ai requestId=11111111-1111-1111-1111-111111111111 attempt=1 kind=business_result
            2026-11-12T09:00:05.000Z HTTP response status=200 headers=[omitted] body=[omitted] requestId=11111111-1111-1111-1111-111111111111 attempt=1 kind=business_result
        """.trimIndent()
        val business = taskCenterRequestDetails(businessLine).single()
        assertEquals(TaskCenterRecordSource.BusinessResult, business.source)
        assertFalse(business.fromCapture)
        val httpLine = """
            2026-11-12T09:00:00.000Z HTTP request {method=POST, service=api.example.com, route=/v1/chat/completions} params=[omitted] label=bookmarks requestId=22222222-2222-2222-2222-222222222222 attempt=1 kind=http
        """.trimIndent()
        assertEquals(TaskCenterRecordSource.Http, taskCenterRequestDetails(httpLine).single().source)
        val legacy = """
            2026-11-12T09:00:00.000Z HTTP request [chat] POST https://api.example.com/v1/chat/completions params=stream=true
        """.trimIndent()
        assertEquals(TaskCenterRecordSource.Unknown, taskCenterRequestDetails(legacy).single().source)
    }

    @Test
    fun captureCodecKeepsBusinessSourceAndOldRecordsStayUnknown() {
        val businessRecord = TaskHttpRecord(
            requestId = "req-biz",
            attempt = 1,
            direction = TaskHttpDirection.RESPONSE,
            status = 200,
            body = """{"title":"t"}""",
            bodyState = "CAPTURED",
            kind = TASK_HTTP_KIND_BUSINESS_RESULT,
        )
        val encoded = TaskHttpRecordCodec.encode(businessRecord)
        val decoded = taskCenterCapturedRequestDetails(encoded).single()
        assertEquals(TaskCenterRecordSource.BusinessResult, decoded.source)
        assertTrue(decoded.fromCapture)

        val oldRecord = TaskHttpRecord(
            requestId = "req-old",
            attempt = 1,
            direction = TaskHttpDirection.REQUEST,
            method = "GET",
            url = "https://api.example.com/v1/items",
            bodyState = "NOT_CAPTURED",
        )
        val old = taskCenterCapturedRequestDetails(TaskHttpRecordCodec.encode(oldRecord)).single()
        assertEquals(TaskCenterRecordSource.Unknown, old.source)
        assertTrue(old.fromCapture)
    }

    @Test
    fun logRowsCarryTheSourceOfTheirRecord() {
        val http = taskCenterHttpLogParse(
            """
            2026-11-12T09:00:00.000Z HTTP request {method=POST, service=api.example.com, route=/v1/chat/completions} params=[omitted] label=external_favorite_ai requestId=33333333-3333-3333-3333-333333333333 attempt=1 kind=business_result
            2026-11-12T09:00:05.000Z HTTP response status=200 headers=[omitted] body=[omitted] requestId=33333333-3333-3333-3333-333333333333 attempt=1 kind=business_result
            """.trimIndent(),
        )
        val rows = taskCenterLogEntries(emptyList(), http, "request")
        assertEquals(TaskCenterRecordSource.BusinessResult, rows.single().source)
        assertEquals(TaskCenterRecordSource.BusinessResult, rows.single().request?.source)
    }
}
