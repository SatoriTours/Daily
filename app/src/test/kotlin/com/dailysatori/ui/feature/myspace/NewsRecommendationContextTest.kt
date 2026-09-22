package com.dailysatori.ui.feature.myspace

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.data.repository.SettingRepository
import com.dailysatori.service.diary.DiaryThoughtArchive
import com.dailysatori.service.diary.DiaryThoughtState
import com.dailysatori.service.opportunity.*
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class NewsRecommendationContextTest {
    @Test
    fun completedThoughtsUnlockRecommendationsWithoutReenteringThePage() = withService { service, context ->
        val thoughts = MutableStateFlow(DiaryThoughtState(isUpdating = true))
        var scheduled = 0
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            observeRecommendationContext(thoughts, service) { scheduled++ }
        }
        assertFalse(service.state.value.hasAnalysisContext)
        assertEquals(0, scheduled)

        context.summary = "有原文依据的个人思想"
        thoughts.value = DiaryThoughtState(archive = DiaryThoughtArchive(fingerprint = "ready", generatedAt = 1))
        runCurrent()

        assertTrue(service.state.value.hasAnalysisContext)
        assertEquals(1, scheduled)
        thoughts.value = thoughts.value.copy(progress = "新的进度通知", isUpdating = true)
        runCurrent()
        assertEquals(1, scheduled, "进度变化不能反复创建推荐任务")
    }

    @Test
    fun permissionChangesAndStaleThoughtsRefreshEligibilityWithoutBypassingPrivacy() = withService { service, context ->
        context.summary = "有依据的思想"
        val thoughts = MutableStateFlow(DiaryThoughtState(archive = DiaryThoughtArchive(fingerprint = "ready")))
        var scheduled = 0
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            observeRecommendationContext(thoughts, service) { scheduled++ }
        }
        assertTrue(service.state.value.hasAnalysisContext)
        context.enabled = false
        thoughts.value = thoughts.value.copy(useInChat = false)
        runCurrent()
        assertFalse(service.state.value.hasAnalysisContext)
        assertEquals(1, scheduled)

        context.enabled = true
        context.summary = null
        thoughts.value = thoughts.value.copy(useInChat = true, isStale = true)
        runCurrent()
        assertFalse(service.state.value.hasAnalysisContext)
        assertEquals(1, scheduled)

        service.saveFocus("用户自己填写的关注点")
        thoughts.value = thoughts.value.copy(corrections = "新补充")
        runCurrent()
        assertTrue(service.state.value.hasAnalysisContext)
        assertEquals(2, scheduled)
    }

    @Test
    fun missingContextOffersSetupInsteadOfDisablingTheUpdateButton() {
        assertEquals(RecommendationAction.SET_UP_CONTEXT, recommendationAction(false, false, null))
        assertEquals(RecommendationAction.SET_UP_CONTEXT, recommendationAction(false, false, "succeeded"))
        assertEquals(RecommendationAction.UPDATE, recommendationAction(true, false, "failed"))
        assertEquals(RecommendationAction.WAIT, recommendationAction(true, true, null))
        listOf("queued", "running", "retrying").forEach { status ->
            assertEquals(RecommendationAction.WAIT, recommendationAction(false, false, status))
        }
    }

    private fun withService(block: suspend TestScope.(NewsOpportunityService, Context) -> Unit) = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            DailySatoriDatabase.Schema.create(driver)
            val context = Context()
            val service = NewsOpportunityService(NewsOpportunityStore(SettingRepository(DailySatoriDatabase(driver))), OpportunityAnalyzer { null }, context)
            block(service, context)
        } finally { driver.close() }
    }

    private class Context : NewsOpportunityContext {
        override var enabled = true
        var summary: String? = null
        override fun verifiedContext() = summary
    }
}
