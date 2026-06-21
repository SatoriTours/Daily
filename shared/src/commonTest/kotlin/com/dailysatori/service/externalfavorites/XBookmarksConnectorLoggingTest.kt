package com.dailysatori.service.externalfavorites

import kotlin.test.Test
import kotlin.test.assertTrue

class XBookmarksConnectorLoggingTest {
    @Test
    fun xApiResponsesAreLoggedInChunksForDebuggingSyncContent() {
        val source = kotlin.io.path.Path("src/commonMain/kotlin/com/dailysatori/service/externalfavorites/XBookmarksConnector.kt")
            .toFile()
            .readText()

        assertTrue(source.contains("logXApiResponseBody("))
        assertTrue(source.contains("""label = "bookmarks""""))
        assertTrue(source.contains("""label = "post_lookup""""))
        assertTrue(source.contains("chunked(X_API_LOG_CHUNK_SIZE)"))
    }
}
