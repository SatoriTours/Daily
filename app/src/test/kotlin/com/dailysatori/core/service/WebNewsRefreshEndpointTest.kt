package com.dailysatori.core.service

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WebNewsRefreshEndpointTest {
    @Test
    fun lanRefreshUsesTheUnifiedManualTaskInsteadOfGeneratingInline() {
        val source = File("src/main/kotlin/com/dailysatori/core/service/WebNewsTaskRoutes.kt").readText()
        val endpoint = source.substringAfter("post(\"/summary/refresh\")").substringBefore("internal fun Route.setupTaskRoutes")

        assertTrue(endpoint.contains("enqueueManualNewsRefresh(tasks"))
        assertTrue(endpoint.contains("observeTaskById(taskId)"))
        assertTrue(endpoint.contains("withTimeoutOrNull(NEWS_REFRESH_WAIT_MS)"))
        assertFalse(endpoint.contains("generateDaily(force = true)"), "no side-channel generation")
        assertFalse(endpoint.contains("generateDaily("), "generation belongs to the task handler")
    }
}

