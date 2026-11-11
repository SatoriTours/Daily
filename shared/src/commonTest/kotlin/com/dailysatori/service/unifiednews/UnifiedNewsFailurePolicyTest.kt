package com.dailysatori.service.unifiednews

import com.dailysatori.service.ai.AiHttpFailure
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UnifiedNewsFailurePolicyTest {
    @Test
    fun rateLimitsTimeoutsServerAndTransportErrorsRetry() {
        assertTrue(unifiedNewsAiFailureIsRetryable(AiHttpFailure(429, 120_000)))
        assertTrue(unifiedNewsAiFailureIsRetryable(AiHttpFailure(408)))
        assertTrue(unifiedNewsAiFailureIsRetryable(AiHttpFailure(500)))
        assertTrue(unifiedNewsAiFailureIsRetryable(AiHttpFailure(503)))
        assertTrue(unifiedNewsAiFailureIsRetryable(IllegalStateException("Request timeout has expired [request_timeout=120000]")))
        assertTrue(unifiedNewsAiFailureIsRetryable(IllegalStateException("Connection reset by peer")))
        assertTrue(unifiedNewsAiFailureIsRetryable(IllegalStateException("OpenCode Go HTTP 429: usage limit reached")))
    }

    @Test
    fun bareNumbersTimingsAndUrlsAreNotHttpStatusEvidence() {
        assertTrue(unifiedNewsAiFailureIsRetryable(IllegalStateException("Response cached for 403ms")))
        assertTrue(unifiedNewsAiFailureIsRetryable(IllegalStateException("retry in 400 ms")))
        assertTrue(unifiedNewsAiFailureIsRetryable(IllegalStateException("GET https://example.test/http401/v1 failed")))
        assertTrue(unifiedNewsAiFailureIsRetryable(IllegalStateException("TLS certificate code4030123 rejected")))
        assertTrue(unifiedNewsAiFailureIsRetryable(IllegalStateException("GET https://status401.example.test failed")))
        assertTrue(unifiedNewsAiFailureIsRetryable(IllegalStateException("request_timeout=404000 expired")))
    }

    @Test
    fun explicitHttpStatusTextStaysPermanent() {
        assertFalse(unifiedNewsAiFailureIsRetryable(IllegalStateException("OpenCode Go HTTP 401: check API key")))
        assertFalse(unifiedNewsAiFailureIsRetryable(IllegalStateException("server returned status 403")))
        assertFalse(unifiedNewsAiFailureIsRetryable(IllegalStateException("HTTP/1.1 404 Not Found")))
        assertFalse(unifiedNewsAiFailureIsRetryable(IllegalStateException("status=400")))
        assertFalse(unifiedNewsAiFailureIsRetryable(IllegalStateException("status code: 401 unauthorized")))
    }

    @Test
    fun configurationAuthenticationAndUnusableAnswersStayPermanent() {
        assertFalse(unifiedNewsAiFailureIsRetryable(AiHttpFailure(401)))
        assertFalse(unifiedNewsAiFailureIsRetryable(AiHttpFailure(403)))
        assertFalse(unifiedNewsAiFailureIsRetryable(AiHttpFailure(400)))
        assertFalse(unifiedNewsAiFailureIsRetryable(AiHttpFailure(404)))
        assertFalse(unifiedNewsAiFailureIsRetryable(IllegalStateException("AI config not set")))
        assertFalse(unifiedNewsAiFailureIsRetryable(IllegalStateException("API token missing")))
        assertFalse(unifiedNewsAiFailureIsRetryable(IllegalArgumentException("JsonParseException: unexpected token")))
        assertFalse(unifiedNewsAiFailureIsRetryable(IllegalStateException("OpenCode Go HTTP 401: check API key")))
    }
}
