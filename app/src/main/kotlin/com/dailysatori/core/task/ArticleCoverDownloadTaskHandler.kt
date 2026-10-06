package com.dailysatori.core.task

import com.dailysatori.core.worker.AsyncTaskScheduler
import com.dailysatori.data.repository.ArticleRepository
import com.dailysatori.data.repository.AsyncTaskRepository
import com.dailysatori.service.asynctask.*
import com.dailysatori.service.diagnostics.DiagnosticLog
import com.dailysatori.service.parser.ArticleCoverScheduler
import com.dailysatori.service.parser.WebpageParserService
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
internal data class ArticleCoverPayload(val articleId: Long)

class PersistentArticleCoverScheduler(
    private val articles: ArticleRepository,
    private val tasks: AsyncTaskRepository,
    private val scheduler: AsyncTaskScheduler,
) : ArticleCoverScheduler {
    override fun enqueue(articleId: Long) {
        val url = articles.getById(articleId)?.cover_image_url?.takeIf { it.isNotBlank() } ?: return
        val taskId = tasks.enqueue(AsyncTaskType.article_cover_download.name,
            Json.encodeToString(ArticleCoverPayload(articleId)),
            uniqueKey = "article_cover_download:$articleId:${DiagnosticLog.fingerprint(url)}", maxAttempts = 3)
        scheduler.enqueue(taskId)
    }
}

class ArticleCoverDownloadTaskHandler(private val parser: WebpageParserService) : AsyncTaskHandler {
    override val type = AsyncTaskType.article_cover_download.name

    override suspend fun execute(taskId: Long, payloadJson: String, checkpointJson: String,
        reporter: AsyncTaskProgressReporter): AsyncTaskExecutionResult {
        val payload = runCatching { Json.decodeFromString<ArticleCoverPayload>(payloadJson) }.getOrNull()
            ?: return AsyncTaskExecutionResult.PermanentFailure("invalid_payload", "封面任务参数无效")
        if (payload.articleId <= 0) return AsyncTaskExecutionResult.PermanentFailure("invalid_payload", "文章编号无效")
        reporter.report(0, 1, "正在下载文章封面")
        parser.downloadArticleCover(payload.articleId)
        reporter.report(1, 1, "文章封面已保存")
        return AsyncTaskExecutionResult.Success()
    }
}
