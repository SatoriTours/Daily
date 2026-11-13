package com.dailysatori.ui.feature.settings.taskcenter

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.dailysatori.R
import com.dailysatori.service.diagnostics.TASK_HTTP_KIND_BUSINESS_RESULT
import com.dailysatori.service.diagnostics.TASK_HTTP_KIND_HTTP

/** Explicit record source. Old records carry no marker, so they stay unknown instead of claiming HTTP. */
internal enum class TaskCenterRecordSource { Http, BusinessResult, Unknown }

internal fun taskCenterRecordSource(kind: String?): TaskCenterRecordSource = when (kind) {
    TASK_HTTP_KIND_HTTP -> TaskCenterRecordSource.Http
    TASK_HTTP_KIND_BUSINESS_RESULT -> TaskCenterRecordSource.BusinessResult
    else -> TaskCenterRecordSource.Unknown
}

internal fun taskCenterSourceKey(source: TaskCenterRecordSource): String = when (source) {
    TaskCenterRecordSource.Http -> TASK_HTTP_KIND_HTTP
    TaskCenterRecordSource.BusinessResult -> TASK_HTTP_KIND_BUSINESS_RESULT
    TaskCenterRecordSource.Unknown -> "unknown"
}

/** One source-dependent vocabulary, so no screen has to guess from a status code or a label. */
internal data class TaskCenterSourceCopy(
    val httpBadge: String,
    val businessBadge: String,
    val unknownBadge: String,
    val sourceLabel: String,
    val responseOk: String,
    val responseFailed: String,
    val responseMissing: String,
    val responseIncomplete: String,
    val unknownAssociation: String,
    val businessSucceeded: String,
    val businessFailed: String,
    val businessNoStatus: String,
    val providerStatus: String,
    val unknownStatus: String,
    val requestTitleHttp: String,
    val responseTitleHttp: String,
    val errorTitleHttp: String,
    val requestTitleBusiness: String,
    val responseTitleBusiness: String,
    val errorTitleBusiness: String,
    val requestTitleUnknown: String,
    val responseTitleUnknown: String,
    val errorTitleUnknown: String,
    val startedHttp: String,
    val finishedHttp: String,
    val durationHttp: String,
    val startedBusiness: String,
    val finishedBusiness: String,
    val durationBusiness: String,
    val startedUnknown: String,
    val finishedUnknown: String,
    val durationUnknown: String,
    val attemptHttp: String,
    val attemptBusiness: String,
    val tabResponseHttp: String,
    val tabErrorHttp: String,
    val tabResponseBusiness: String,
    val tabErrorBusiness: String,
    val tabResponseUnknown: String,
    val tabErrorUnknown: String,
    val hintHttpCaptured: String,
    val hintHttpLog: String,
    val hintBusiness: String,
    val hintUnknown: String,
)

internal data class TaskCenterSourceView(
    val badge: String,
    val status: String,
    val statusIsError: Boolean,
    /** Null means the record has no reliable attempt number or the source should not claim one. */
    val attempt: String?,
    val requestBodyTitle: String,
    val responseBodyTitle: String,
    val errorTitle: String,
    val startedLabel: String,
    val finishedLabel: String,
    val durationLabel: String,
    val responseTab: String,
    val errorTab: String,
    val hint: String,
    val jsonSource: String,
)

internal fun taskCenterSourceView(detail: TaskCenterRequestDetail, copy: TaskCenterSourceCopy): TaskCenterSourceView {
    val status = detail.responseStatus.trim()
    return when (detail.source) {
        TaskCenterRecordSource.Http -> TaskCenterSourceView(
            badge = copy.httpBadge,
            status = when {
                detail.associationUnknown -> copy.unknownAssociation
                detail.responseIncomplete -> String.format(copy.responseIncomplete, status.ifBlank { copy.responseMissing })
                detail.httpOk == false -> String.format(copy.responseFailed, status)
                detail.httpOk == true -> String.format(copy.responseOk, status)
                else -> copy.responseMissing
            },
            statusIsError = detail.httpOk == false || detail.responseIncomplete,
            attempt = if (detail.attemptKnown) String.format(copy.attemptHttp, detail.attemptNumber) else null,
            requestBodyTitle = copy.requestTitleHttp,
            responseBodyTitle = copy.responseTitleHttp,
            errorTitle = copy.errorTitleHttp,
            startedLabel = copy.startedHttp,
            finishedLabel = copy.finishedHttp,
            durationLabel = copy.durationHttp,
            responseTab = copy.tabResponseHttp,
            errorTab = copy.tabErrorHttp,
            hint = if (detail.fromCapture) copy.hintHttpCaptured else copy.hintHttpLog,
            jsonSource = TASK_HTTP_KIND_HTTP,
        )
        TaskCenterRecordSource.BusinessResult -> {
            val succeeded = status.toIntOrNull()?.let { it in 200..299 } == true
            val statusText = when {
                succeeded -> copy.businessSucceeded
                detail.associationUnknown -> copy.unknownAssociation
                status == "599" || status.isBlank() -> copy.businessFailed + " · " + copy.businessNoStatus
                else -> copy.businessFailed + " · " + String.format(copy.providerStatus, status)
            }
            TaskCenterSourceView(
                badge = copy.businessBadge,
                status = statusText,
                statusIsError = !succeeded && !detail.associationUnknown,
                attempt = if (detail.attemptKnown) String.format(copy.attemptBusiness, detail.attemptNumber) else null,
                requestBodyTitle = copy.requestTitleBusiness,
                responseBodyTitle = copy.responseTitleBusiness,
                errorTitle = copy.errorTitleBusiness,
                startedLabel = copy.startedBusiness,
                finishedLabel = copy.finishedBusiness,
                durationLabel = copy.durationBusiness,
                responseTab = copy.tabResponseBusiness,
                errorTab = copy.tabErrorBusiness,
                hint = copy.hintBusiness,
                jsonSource = TASK_HTTP_KIND_BUSINESS_RESULT,
            )
        }
        TaskCenterRecordSource.Unknown -> TaskCenterSourceView(
            badge = copy.unknownBadge,
            status = if (status.isBlank()) copy.unknownBadge else String.format(copy.unknownStatus, status),
            statusIsError = false,
            attempt = null,
            requestBodyTitle = copy.requestTitleUnknown,
            responseBodyTitle = copy.responseTitleUnknown,
            errorTitle = copy.errorTitleUnknown,
            startedLabel = copy.startedUnknown,
            finishedLabel = copy.finishedUnknown,
            durationLabel = copy.durationUnknown,
            responseTab = copy.tabResponseUnknown,
            errorTab = copy.tabErrorUnknown,
            hint = copy.hintUnknown,
            jsonSource = "unknown",
        )
    }
}

@Composable
internal fun taskCenterSourceCopy(): TaskCenterSourceCopy = TaskCenterSourceCopy(
    httpBadge = stringResource(R.string.task_center_source_http),
    businessBadge = stringResource(R.string.task_center_source_business),
    unknownBadge = stringResource(R.string.task_center_source_unknown),
    sourceLabel = stringResource(R.string.task_center_request_source),
    responseOk = stringResource(R.string.task_center_response_ok),
    responseFailed = stringResource(R.string.task_center_response_failed),
    responseMissing = stringResource(R.string.task_center_response_missing),
    responseIncomplete = stringResource(R.string.task_center_response_incomplete),
    unknownAssociation = stringResource(R.string.task_center_badge_unknown_association),
    businessSucceeded = stringResource(R.string.task_center_business_succeeded),
    businessFailed = stringResource(R.string.task_center_business_failed),
    businessNoStatus = stringResource(R.string.task_center_business_no_status),
    providerStatus = stringResource(R.string.task_center_business_provider_status),
    unknownStatus = stringResource(R.string.task_center_record_unknown_status),
    requestTitleHttp = stringResource(R.string.task_center_request_body),
    responseTitleHttp = stringResource(R.string.task_center_response_body),
    errorTitleHttp = stringResource(R.string.task_center_detail_error),
    requestTitleBusiness = stringResource(R.string.task_center_business_request_title),
    responseTitleBusiness = stringResource(R.string.task_center_business_result_title),
    errorTitleBusiness = stringResource(R.string.task_center_business_error_title),
    requestTitleUnknown = stringResource(R.string.task_center_record_unknown_request),
    responseTitleUnknown = stringResource(R.string.task_center_record_unknown_response),
    errorTitleUnknown = stringResource(R.string.task_center_record_unknown_error),
    startedHttp = stringResource(R.string.task_center_error_requested_at),
    finishedHttp = stringResource(R.string.task_center_error_responded_at),
    durationHttp = stringResource(R.string.task_center_request_duration),
    startedBusiness = stringResource(R.string.task_center_business_started),
    finishedBusiness = stringResource(R.string.task_center_business_finished),
    durationBusiness = stringResource(R.string.task_center_business_duration),
    startedUnknown = stringResource(R.string.task_center_record_started),
    finishedUnknown = stringResource(R.string.task_center_record_finished),
    durationUnknown = stringResource(R.string.task_center_record_duration),
    attemptHttp = stringResource(R.string.task_center_request_attempt),
    attemptBusiness = stringResource(R.string.task_center_request_attempt_business),
    tabResponseHttp = stringResource(R.string.task_center_request_tab_response),
    tabErrorHttp = stringResource(R.string.task_center_request_tab_error),
    tabResponseBusiness = stringResource(R.string.task_center_business_tab_result),
    tabErrorBusiness = stringResource(R.string.task_center_business_tab_error),
    tabResponseUnknown = stringResource(R.string.task_center_record_tab_response),
    tabErrorUnknown = stringResource(R.string.task_center_record_tab_error),
    hintHttpCaptured = stringResource(R.string.task_center_requests_capture_source),
    hintHttpLog = stringResource(R.string.task_center_hint_http_log),
    hintBusiness = stringResource(R.string.task_center_hint_business),
    hintUnknown = stringResource(R.string.task_center_hint_unknown),
)
