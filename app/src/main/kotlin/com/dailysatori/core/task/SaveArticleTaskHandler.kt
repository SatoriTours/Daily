package com.dailysatori.core.task

import com.dailysatori.service.asynctask.AsyncTaskExecutionResult
import com.dailysatori.service.asynctask.AsyncTaskHandler
import com.dailysatori.service.asynctask.AsyncTaskProgressReporter
import com.dailysatori.service.asynctask.AsyncTaskType
import com.dailysatori.service.parser.WebpageParserService
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class SaveArticleTaskPayload(
    val url: String,
)

class SaveArticleTaskHandler(
    private val parser: WebpageParserService,
) : AsyncTaskHandler {
    override val type: String = AsyncTaskType.save_article.name

    override suspend fun execute(
        taskId: Long,
        payloadJson: String,
        checkpointJson: String,
        reporter: AsyncTaskProgressReporter,
    ): AsyncTaskExecutionResult {
        val payload = runCatching { Json.decodeFromString<SaveArticleTaskPayload>(payloadJson) }
            .getOrElse {
                return AsyncTaskExecutionResult.PermanentFailure("invalid_payload", "保存文章任务参数无效")
            }
        if (payload.url.isBlank()) {
            return AsyncTaskExecutionResult.PermanentFailure("invalid_url", "文章链接为空")
        }

        reporter.report(1, 3, "正在处理文章", checkpointJson = """{"stage":"started"}""")
        parser.saveWebpage(url = payload.url, comment = null, title = null, tags = null)
        reporter.report(3, 3, "文章已保存", checkpointJson = """{"stage":"completed"}""")
        return AsyncTaskExecutionResult.Success()
    }
}

fun saveArticleTaskPayloadJson(url: String): String =
    Json.encodeToString(SaveArticleTaskPayload(url = url))
