package com.dailysatori.core.task

import com.dailysatori.data.repository.BookRepository
import com.dailysatori.data.repository.BookViewpointRepository
import com.dailysatori.service.asynctask.AsyncTaskExecutionResult
import com.dailysatori.service.asynctask.AsyncTaskHandler
import com.dailysatori.service.asynctask.AsyncTaskProgressReporter
import com.dailysatori.service.book.BookIntelligenceService
import com.dailysatori.service.book.BookSearchResult
import com.dailysatori.service.book.parseBookViewpointRetryContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class BookViewpointGenerateTaskPayload(
    val bookId: Long,
)

class BookViewpointGenerateTaskHandler(
    private val bookRepo: BookRepository,
    private val viewpointRepo: BookViewpointRepository,
    private val bookIntelligenceService: BookIntelligenceService,
) : AsyncTaskHandler {
    override val type: String = TYPE

    override suspend fun execute(
        taskId: Long,
        payloadJson: String,
        checkpointJson: String,
        reporter: AsyncTaskProgressReporter,
    ): AsyncTaskExecutionResult {
        val payload = runCatching { Json.decodeFromString<BookViewpointGenerateTaskPayload>(payloadJson) }
            .getOrElse {
                return AsyncTaskExecutionResult.PermanentFailure("invalid_payload", "书籍观点生成任务参数无效")
            }
        val book = bookRepo.getById(payload.bookId)
            ?: return AsyncTaskExecutionResult.PermanentFailure("book_missing", "书籍不存在")

        return try {
            reporter.report(0, 3, "正在准备《${book.title}》资料", checkpointJson = """{"stage":"started"}""")
            val result = BookSearchResult(
                title = book.title,
                author = book.author,
                category = book.category,
                coverUrl = book.cover_image,
                introduction = book.introduction,
                sourceUrl = refreshSourceUrlFromViewpoints(payload.bookId),
            )
            reporter.report(1, 3, "正在生成《${book.title}》观点", checkpointJson = """{"stage":"generating"}""")
            val drafts = bookIntelligenceService.generateViewpoints(result).drafts.take(BOOK_VIEWPOINT_IMPORT_LIMIT)
            viewpointRepo.deleteByBook(payload.bookId)
            drafts.forEachIndexed { index, draft ->
                viewpointRepo.insert(
                    bookId = payload.bookId,
                    title = draft.title,
                    content = draft.content,
                    example = draft.example,
                    status = draft.status,
                    errorMessage = draft.errorMessage,
                    outlineJson = draft.outlineJson,
                    sourceNotes = draft.sourceNotes,
                )
                reporter.report(
                    current = (index + 1).toLong(),
                    total = drafts.size.toLong().coerceAtLeast(1),
                    message = "已生成 ${index + 1} 个观点",
                    checkpointJson = """{"stage":"saving","count":${index + 1}}""",
                )
            }
            reporter.report(3, 3, "《${book.title}》观点已更新", checkpointJson = """{"stage":"completed"}""")
            AsyncTaskExecutionResult.Success()
        } catch (error: Exception) {
            AsyncTaskExecutionResult.RetryableFailure(
                code = "book_viewpoint_generate_failed",
                message = error.message.orEmpty().ifBlank { "书籍观点生成失败" },
            )
        }
    }

    private fun refreshSourceUrlFromViewpoints(bookId: Long): String {
        val sourceBookId = viewpointRepo.getByBookSync(bookId)
            .asSequence()
            .mapNotNull { parseBookViewpointRetryContext(it.outline_json)?.info?.bookId?.trim() }
            .firstOrNull { it.isNotBlank() }
        return sourceBookId?.let { "weread://reading?bId=${it.trim()}" }.orEmpty()
    }

    companion object {
        const val TYPE = "book_viewpoint_generate"
        private const val BOOK_VIEWPOINT_IMPORT_LIMIT = 20
    }
}

fun bookViewpointGenerateTaskPayloadJson(bookId: Long): String =
    Json.encodeToString(BookViewpointGenerateTaskPayload(bookId = bookId))
