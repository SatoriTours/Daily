package com.dailysatori.service.unifiednews

import com.dailysatori.service.ai.AiHttpFailure

/**
 * Explicit HTTP status only: `HTTP 401`, `HTTP/1.1 404`, `status 403`, `status=400`, `status code: 401`.
 * A bare number next to a separator is not status evidence, so timing text (`403ms`, `400 ms`),
 * URLs or hosts (`https://example.test/http401`, `status401.example.test`) and opaque identifiers
 * (`code4030123`) must not mark a failure permanent.
 */
private val ExplicitHttpStatusPattern = Regex(
    """(?<![\w/])(?:http(?:/\d(?:\.\d+)?)?|status(?:\s+code)?|code)(?:\s*[:=]\s*|\s+)(400|401|403|404)(?!\w)""",
    RegexOption.IGNORE_CASE,
)

/**
 * Same policy as the article and favorite AI handlers: configuration, authentication and unusable
 * answers stay permanent, while timeouts, rate limits, transport errors and 5xx may retry
 * (bounded by the task max_attempts).
 *
 * Cancellation never reaches this decision: [UnifiedNewsSummaryService.generate] rethrows
 * CancellationException, the runner's own timeout maps to a retryable timeout result, and Ktor
 * network timeouts surface here as ordinary exceptions and retry.
 */
fun unifiedNewsAiFailureIsRetryable(error: Throwable): Boolean {
    if (error is AiHttpFailure) return error.status !in PERMANENT_AI_STATUSES
    if (error is IllegalArgumentException) return false
    val normalized = error.message.orEmpty().lowercase()
    if (normalized.contains("config not set")) return false
    if (normalized.contains("api token") && normalized.contains("missing")) return false
    if (normalized.contains("jsonparse") || normalized.contains("unexpected json")) return false
    return ExplicitHttpStatusPattern.find(normalized) == null
}

private val PERMANENT_AI_STATUSES = setOf(400, 401, 403, 404)
