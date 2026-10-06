package com.dailysatori.ui.feature.myspace

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.data.repository.SettingRepository
import com.dailysatori.data.repository.AsyncTaskRepository
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
    fun interruptionErrorsAreHiddenAndDismissalOnlyAppliesToThatTask() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            DailySatoriDatabase.Schema.create(driver)
            val tasks = AsyncTaskRepository(DailySatoriDatabase(driver))
            val id = tasks.enqueue("news_opportunity_analysis", "{}")
            tasks.claimForRun(id, "test", Long.MAX_VALUE)
            tasks.finishFailure(id, "interrupted", "Job was cancelled")
            assertNull(recommendationError(OpportunityState(), tasks.getById(id), "分析失败"))
            val failure = tasks.getById(id)!!.copy(last_error_code = "opportunity_analysis_failed", last_error_message = "private error")
            assertEquals("分析失败", recommendationError(OpportunityState(), failure, "分析失败"))
            assertNull(recommendationError(OpportunityState(dismissedErrorTaskId = id), failure, "分析失败"))
            assertEquals("分析失败", recommendationError(OpportunityState(dismissedErrorTaskId = id - 1), failure, "分析失败"))
            assertNull(recommendationError(OpportunityState(error = "Job was cancelled"), null, "分析失败"))
        } finally { driver.close() }
    }

    @Test
    fun automaticRecommendationsWaitForInProgressThoughtsEvenWhenCachedContextExists() = withService { service, context ->
        context.summary = "上一次整理的思想"
        val thoughts = MutableStateFlow(DiaryThoughtState(
            archive = DiaryThoughtArchive(fingerprint = "cached"), isUpdating = true,
        ))
        var scheduled = 0
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            observeRecommendationContext(thoughts, service) { scheduled++ }
        }
        assertTrue(service.state.value.hasAnalysisContext)
        assertEquals(0, scheduled, "不能用正在更新的思想启动自动分析")
        thoughts.value = thoughts.value.copy(progress = "正在整理")
        runCurrent()
        assertEquals(0, scheduled)

        context.summary = "本次整理完成的思想"
        thoughts.value = thoughts.value.copy(isUpdating = false)
        runCurrent()
        assertEquals(1, scheduled, "即使归档未变化，完成更新也应恢复自动分析")
    }

    @Test
    fun headlineRefreshChecksContextAndWaitsOnlyWhenThoughtsAreUsed() = withService { service, context ->
        var scheduled = 0
        refreshRecommendationsIfReady(DiaryThoughtState(), service) { scheduled++ }
        assertEquals(0, scheduled, "没有分析条件时，首页更新不能创建失败任务")

        service.saveFocus("用户填写的关注点")
        refreshRecommendationsIfReady(DiaryThoughtState(isUpdating = true), service) { scheduled++ }
        assertEquals(0, scheduled)

        context.enabled = false
        refreshRecommendationsIfReady(DiaryThoughtState(isUpdating = true, useInChat = false), service) { scheduled++ }
        assertEquals(1, scheduled, "不用思想时，关注点应足以启动自动分析")
        context.enabled = true
        refreshRecommendationsIfReady(DiaryThoughtState(isUpdating = false), service) { scheduled++ }
        assertEquals(2, scheduled)
    }

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
