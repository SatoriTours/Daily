package com.dailysatori.core.service

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class NewsRefreshOutcomeTest {
    @Test
    fun aRunningRefreshIsAcceptedWithItsTaskId() {
        for (status in listOf("queued", "running", "retrying", "")) {
            val outcome = newsRefreshOutcome(7L, status, "", null, null)
            assertIs<NewsRefreshOutcome.Accepted>(outcome)
            assertEquals(7L, outcome.taskId)
            assertTrue(outcome.status == NEWS_REFRESH_ACCEPTED_QUEUED || outcome.status == NEWS_REFRESH_ACCEPTED_RUNNING)
        }
        assertEquals(NEWS_REFRESH_ACCEPTED_RUNNING, newsRefreshAcceptedStatus("running"))
    }

    @Test
    fun aFinishedRefreshReportsTheSummaryItProduced() {
        assertEquals(NewsRefreshOutcome.Completed("success"),
            newsRefreshOutcome(1L, "succeeded", "", "success", null))
        assertEquals(NewsRefreshOutcome.Completed("empty"),
            newsRefreshOutcome(1L, "succeeded", "", "empty", null))
        assertEquals(NewsRefreshOutcome.Failed("AI 超时"),
            newsRefreshOutcome(1L, "succeeded", "", "failed", "AI 超时"))
        assertIs<NewsRefreshOutcome.Failed>(
            newsRefreshOutcome(1L, "succeeded", "", null, null),
            "a finished task without a summary is not a success")
    }

    @Test
    fun cancelledOrFailedWorkIsNeverReportedAsSuccess() {
        assertEquals(NewsRefreshOutcome.Failed("已由更新的新闻窗口取代"),
            newsRefreshOutcome(1L, "cancelled", "已由更新的新闻窗口取代", "success", null))
        assertEquals(NewsRefreshOutcome.Failed("新闻汇总生成已取消"),
            newsRefreshOutcome(1L, "cancelled", "", null, null))
        assertEquals(NewsRefreshOutcome.Failed("网络不可用"),
            newsRefreshOutcome(1L, "failed", "网络不可用", null, null))
        assertIs<NewsRefreshOutcome.Failed>(newsRefreshOutcome(1L, "unknown_terminate", "", null, null))
    }

    @Test
    fun theWebClientShowsAcceptedInsteadOfAFalseUpdate() {
        val script = File("src/main/assets/website/js/app.js").readText()
        assertTrue(script.contains(
            "const refreshPending = refreshResult && (refreshResult.status === 'queued' || refreshResult.status === 'running')",
        ))
        assertTrue(script.contains("showToast(refreshPending ? '已受理，正在生成' : '今日汇总已更新')"),
            "an accepted refresh must not be announced as an update")
    }
}
