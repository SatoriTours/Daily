package com.dailysatori.service.parser

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WebpageParserLoggingTest {
    @Test
    fun extractionFailureLogOmitsCredentialsPathQueryFragmentAndExceptionMessage() {
        val sensitiveUrl = "https://user:password@example.com/PrivatePath?token=query-secret#fragment-secret"
        val message = webpageExtractionFailureLogMessage(
            sensitiveUrl,
            IllegalStateException("upstream repeated $sensitiveUrl and api-secret"),
        )

        assertTrue(message.contains("https://example.com"))
        listOf("user", "password", "PrivatePath", "query-secret", "fragment-secret", "api-secret").forEach {
            assertFalse(message.contains(it))
        }
    }
}
