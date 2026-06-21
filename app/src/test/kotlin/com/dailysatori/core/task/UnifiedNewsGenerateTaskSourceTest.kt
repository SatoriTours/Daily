package com.dailysatori.core.task

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UnifiedNewsGenerateTaskSourceTest {
    @Test
    fun payloadJsonCarriesGenerationOptions() {
        val json = unifiedNewsGenerateTaskPayloadJson(force = true, ignoreSourceTimeFilter = false, mode = "due")

        assertTrue(json.contains("\"force\":true"))
        assertTrue(json.contains("\"ignoreSourceTimeFilter\":false"))
        assertTrue(json.contains("\"mode\":\"due\""))
    }

    @Test
    fun taskHandlerUsesRemoteNewsFetchTaskType() {
        assertEquals("remote_news_fetch", UnifiedNewsGenerateTaskHandler.TYPE)
    }

    @Test
    fun workerEnqueuesUnifiedNewsGenerationIntoAsyncTaskFramework() {
        val source = File("src/main/kotlin/com/dailysatori/core/worker/UnifiedNewsWorker.kt").readText()

        assertTrue(source.contains("AsyncTaskType.remote_news_fetch.name"))
        assertTrue(source.contains("unifiedNewsGenerateTaskPayloadJson("))
        assertTrue(source.contains("\"remote_news_fetch:\${mode.name.lowercase()}\""))
        assertTrue(source.contains("asyncTaskRepo.enqueue("))
        assertTrue(source.contains("asyncTaskScheduler.enqueue(taskId)"))
    }

    @Test
    fun appModuleRegistersUnifiedNewsGenerateHandler() {
        val source = File("src/main/kotlin/com/dailysatori/core/di/AppModule.kt").readText()

        assertTrue(source.contains("single { UnifiedNewsGenerateTaskHandler(get(), androidContext()) }"))
        assertTrue(source.contains("get<UnifiedNewsGenerateTaskHandler>()"))
    }
}
