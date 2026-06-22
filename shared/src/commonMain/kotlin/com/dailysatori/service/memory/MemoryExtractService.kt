package com.dailysatori.service.memory

import co.touchlab.kermit.Logger
import com.dailysatori.data.repository.ArticleRepository
import com.dailysatori.data.repository.BookRepository
import com.dailysatori.data.repository.BookViewpointRepository
import com.dailysatori.data.repository.DiaryRepository
import com.dailysatori.data.repository.MemoryRepository
import com.dailysatori.service.ai.AiConfigService
import com.dailysatori.service.ai.AiService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import kotlin.coroutines.coroutineContext

class MemoryExtractService(
    private val aiService: AiService,
    private val aiConfigService: AiConfigService,
    private val memoryRepo: MemoryRepository,
) {
    private val log = Logger.withTag("MemoryExtract")

    suspend fun extractAndSave(
        sourceType: String,
        sourceId: Long,
        title: String,
        content: String,
    ) {
        try {
            coroutineContext.ensureActive()
            val config = aiConfigService.getDefaultConfig() ?: return
            if (config.api_address.isBlank()) return

            val truncatedContent = if (content.length > 3000) content.take(3000) + "..." else content

            val messages = listOf(
                buildJsonObject {
                    put("role", "system")
                    put("content", buildSystemPrompt())
                },
                buildJsonObject {
                    put("role", "user")
                    put("content", "请将以下内容提取为一条简洁的记忆摘要（不超过200字）。\n\n标题: $title\n\n内容:\n$truncatedContent")
                },
            )

            val response = aiService.chatCompletion(
                messages = messages,
                apiAddress = config.api_address,
                apiToken = config.api_token,
                modelName = config.model_name,
                provider = config.provider,
                temperature = 0.5,
            )
            coroutineContext.ensureActive()

            val summary = response?.let {
                val choice = it["choices"]?.jsonArray?.firstOrNull()?.jsonObject
                val msg = choice?.get("message")?.jsonObject
                msg?.get("content")?.jsonPrimitive?.contentOrNull
            } ?: ""

            if (summary.isNotBlank()) {
                val existing = memoryRepo.getBySource(sourceType, sourceId)
                coroutineContext.ensureActive()
                if (existing != null) {
                    memoryRepo.update(
                        id = existing.id,
                        title = title,
                        content = summary,
                        tags = null,
                    )
                    log.d { "Updated memory for $sourceType:$sourceId" }
                } else {
                    memoryRepo.insert(
                        type = "content",
                        sourceType = sourceType,
                        sourceId = sourceId,
                        title = title,
                        content = summary,
                    )
                    log.d { "Created memory for $sourceType:$sourceId" }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.e(e) { "Failed to extract memory for $sourceType:$sourceId" }
        }
    }

    suspend fun deleteBySource(sourceType: String, sourceId: Long) {
        try {
            memoryRepo.deleteBySource(sourceType, sourceId)
        } catch (e: Exception) {
            log.e(e) { "Failed to delete memory for $sourceType:$sourceId" }
        }
    }

    suspend fun rebuildAll(
        articleRepo: ArticleRepository,
        diaryRepo: DiaryRepository,
        bookRepo: BookRepository,
        viewpointRepo: BookViewpointRepository,
        onProgress: (String) -> Unit,
    ) {
        try {
            coroutineContext.ensureActive()
            withContext(NonCancellable) {
                onProgress("清除旧记忆...")
                memoryRepo.deleteAllByType("content")

                val articles = (articleRepo.getLocalSync() + articleRepo.getFavoritesSync()).distinctBy { it.id }
                articles.forEachIndexed { index, article ->
                    coroutineContext.ensureActive()
                    onProgress("处理文章 (${index + 1}/${articles.size})...")
                    val text = article.ai_markdown_content ?: ""
                    val t = article.ai_title ?: article.title ?: "未命名"
                    coroutineContext.ensureActive()
                    extractAndSave("article", article.id, t, text)
                }

                val diaries = diaryRepo.getAllSync()
                diaries.forEachIndexed { index, diary ->
                    coroutineContext.ensureActive()
                    onProgress("处理日记 (${index + 1}/${diaries.size})...")
                    coroutineContext.ensureActive()
                    extractAndSave("diary", diary.id, "日记", diary.content)
                }

                val books = bookRepo.getAllSync()
                books.forEachIndexed { index, book ->
                    coroutineContext.ensureActive()
                    onProgress("处理书籍 (${index + 1}/${books.size})...")
                    coroutineContext.ensureActive()
                    extractAndSave("book", book.id, book.title, book.introduction)
                }

                val viewpoints = viewpointRepo.getAllSync()
                viewpoints.forEachIndexed { index, vp ->
                    coroutineContext.ensureActive()
                    onProgress("处理读书观点 (${index + 1}/${viewpoints.size})...")
                    coroutineContext.ensureActive()
                    extractAndSave("book_viewpoint", vp.id, vp.title, vp.content)
                }

                onProgress("重建完成")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.e(e) { "Failed to rebuild all memories" }
            onProgress("重建失败: ${e.message}")
        }
    }

    private fun buildSystemPrompt(): String = """
你是一个个人知识管理助手。你的任务是将用户提供的内容提取为简洁的记忆摘要。
要求：
1. 摘要不超过200字
2. 只提取关键事实、观点或信息
3. 使用中文
4. 直接返回摘要文本，不要添加前缀或后缀
    """.trimIndent()
}
