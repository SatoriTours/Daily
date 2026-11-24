package com.dailysatori.ui.feature.settings.taskcenter

import com.dailysatori.service.asynctask.AsyncTaskType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TaskCenterSourceTargetTest {

    @Test
    fun articleTasksResolveToTheArticleTheyOperateOn() {
        listOf(
            AsyncTaskType.save_article,
            AsyncTaskType.article_cover_download,
            AsyncTaskType.article_memory_extract,
            AsyncTaskType.remote_article_reprocess,
            AsyncTaskType.article_ai,
        ).forEach { type ->
            assertEquals(
                TaskCenterSourceTarget.Article(42L),
                taskCenterSourceTarget(type.name, """{"articleId":42}"""),
                type.name,
            )
        }
        assertEquals(
            TaskCenterSourceTarget.Article(7L),
            taskCenterSourceTarget(AsyncTaskType.article_ai.name, """{"article_id":7}"""),
        )
    }

    @Test
    fun articleTasksWithoutAUsableArticleIdHaveNoTarget() {
        assertNull(taskCenterSourceTarget(AsyncTaskType.save_article.name, """{"url":"https://example.com/a"}"""))
        assertNull(taskCenterSourceTarget(AsyncTaskType.article_ai.name, """{"articleId":0}"""))
        assertNull(taskCenterSourceTarget(AsyncTaskType.article_ai.name, """{"articleId":"not-a-number"}"""))
        assertNull(taskCenterSourceTarget(AsyncTaskType.article_memory_extract.name, "invalid"))
        assertNull(taskCenterSourceTarget(AsyncTaskType.article_cover_download.name, ""))
        assertNull(taskCenterArticleId("""{"articleId":-3}"""))
    }

    @Test
    fun newsSyncTasksOpenRemoteNewsSettingsAndAnalysisOpensOpportunities() {
        assertEquals(
            TaskCenterSourceTarget.RemoteNewsSettings,
            taskCenterSourceTarget(AsyncTaskType.remote_article_sync.name, """{"sourceId":1}"""),
        )
        assertEquals(
            TaskCenterSourceTarget.RemoteNewsSettings,
            taskCenterSourceTarget(AsyncTaskType.remote_news_fetch.name, "{}"),
        )
        assertEquals(
            TaskCenterSourceTarget.Opportunities,
            taskCenterSourceTarget(AsyncTaskType.news_opportunity_analysis.name, "{}"),
        )
    }

    @Test
    fun unrelatedTasksHaveNoJumpTarget() {
        assertNull(taskCenterSourceTarget(AsyncTaskType.external_favorite_sync.name, """{"sourceId":1}"""))
        assertNull(taskCenterSourceTarget(AsyncTaskType.book_viewpoint_generate.name, """{"articleId":1}"""))
        assertNull(taskCenterSourceTarget("unknown_type", "{}"))
    }
}
