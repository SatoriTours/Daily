package com.dailysatori.core.task

import android.content.Context
import com.dailysatori.core.worker.AsyncTaskScheduler
import com.dailysatori.data.repository.ArticleRepository
import com.dailysatori.data.repository.AsyncTaskRepository
import com.dailysatori.service.asynctask.AsyncTaskExecutionResult
import com.dailysatori.service.asynctask.AsyncTaskHandler
import com.dailysatori.service.asynctask.AsyncTaskProgressReporter
import com.dailysatori.service.asynctask.AsyncTaskType
import com.dailysatori.service.memory.MemoryExtractService
import com.dailysatori.service.parser.WebpageParserService
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class ArticlePostProcessingScheduler(
    context: Context,
    private val taskRepo: AsyncTaskRepository,
) {
    private val taskScheduler = AsyncTaskScheduler(context)

    fun enqueueMemoryExtraction(articleId: Long) {
        enqueue(AsyncTaskType.article_memory_extract, articleId)
    }

    fun enqueueRemoteArticlePostProcessing(articleId: Long, needsReprocessing: Boolean) {
        if (articleId <= 0L) return
        val taskIds = buildList {
            if (needsReprocessing) add(createTask(AsyncTaskType.remote_article_reprocess, articleId))
            add(createTask(AsyncTaskType.article_memory_extract, articleId))
        }
        if (taskIds.size == 1) {
            taskScheduler.enqueue(taskIds.single())
        } else {
            taskScheduler.enqueueSequential("remote-article-post-processing:$articleId", taskIds)
        }
    }

    private fun enqueue(type: AsyncTaskType, articleId: Long) {
        if (articleId <= 0L) return
        taskScheduler.enqueue(createTask(type, articleId))
    }

    private fun createTask(type: AsyncTaskType, articleId: Long): Long =
        taskRepo.enqueue(
            type = type.name,
            payloadJson = articlePostProcessingPayloadJson(articleId),
            uniqueKey = "${type.name}:$articleId",
        )
}

@Serializable
private data class ArticlePostProcessingPayload(val articleId: Long)

class ArticleMemoryExtractTaskHandler(
    private val articleRepo: ArticleRepository,
    private val memoryExtractService: MemoryExtractService,
) : AsyncTaskHandler {
    override val type: String = AsyncTaskType.article_memory_extract.name

    override suspend fun execute(
        taskId: Long,
        payloadJson: String,
        checkpointJson: String,
        reporter: AsyncTaskProgressReporter,
    ): AsyncTaskExecutionResult {
        val articleId = decodeArticleId(payloadJson) ?: return invalidArticlePayload()
        val article = articleRepo.getById(articleId) ?: return AsyncTaskExecutionResult.Success()
        if (article.is_favorite != 1L) return AsyncTaskExecutionResult.Success()
        reporter.report(0, 1, "正在提取文章记忆", """{"stage":"extracting"}""")
        memoryExtractService.extractAndSave(
            sourceType = "article",
            sourceId = articleId,
            title = article.ai_title ?: article.title ?: "未命名",
            content = article.ai_markdown_content.orEmpty(),
        )
        reporter.report(1, 1, "文章记忆已提取", """{"stage":"completed"}""")
        return AsyncTaskExecutionResult.Success()
    }
}

class RemoteArticleReprocessTaskHandler(
    private val articleRepo: ArticleRepository,
    private val parser: WebpageParserService,
) : AsyncTaskHandler {
    override val type: String = AsyncTaskType.remote_article_reprocess.name

    override suspend fun execute(
        taskId: Long,
        payloadJson: String,
        checkpointJson: String,
        reporter: AsyncTaskProgressReporter,
    ): AsyncTaskExecutionResult {
        val articleId = decodeArticleId(payloadJson) ?: return invalidArticlePayload()
        if (articleRepo.getById(articleId) == null) return AsyncTaskExecutionResult.Success()
        reporter.report(0, 1, "正在整理收藏文章", """{"stage":"processing"}""")
        try {
            parser.reprocessArticle(articleId)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            return remoteArticleProcessingFailure(error)
        }
        reporter.report(1, 1, "收藏文章已整理", """{"stage":"completed"}""")
        return AsyncTaskExecutionResult.Success()
    }
}

internal fun remoteArticleProcessingFailure(error: Exception): AsyncTaskExecutionResult {
    val message = error.message.orEmpty().ifBlank { "文章整理失败" }
    val normalized = message.lowercase()
    val permanent = normalized.contains("ai config not set") ||
        normalized.contains("api token") ||
        normalized.contains("unauthorized") ||
        normalized.contains("forbidden") ||
        Regex("(^|\\D)(401|403)(\\D|$)").containsMatchIn(normalized)
    return if (permanent) {
        AsyncTaskExecutionResult.PermanentFailure("remote_article_config_error", message)
    } else {
        AsyncTaskExecutionResult.RetryableFailure("remote_article_processing_failed", message)
    }
}

private fun articlePostProcessingPayloadJson(articleId: Long): String =
    Json.encodeToString(ArticlePostProcessingPayload(articleId))

private fun decodeArticleId(payloadJson: String): Long? =
    runCatching { Json.decodeFromString<ArticlePostProcessingPayload>(payloadJson).articleId }
        .getOrNull()
        ?.takeIf { it > 0L }

private fun invalidArticlePayload() =
    AsyncTaskExecutionResult.PermanentFailure("invalid_payload", "文章后台任务参数无效")
