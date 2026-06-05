package com.dailysatori.ui.feature.book

import androidx.lifecycle.ViewModel

data class BookReflectionMessageUi(
    val id: String,
    val role: String,
    val content: String,
    val createdAt: Long,
    val status: String,
    val errorMessage: String,
    val isStreaming: Boolean = false,
)

data class BookReflectionSessionUi(
    val id: Long,
    val viewpointId: Long,
    val title: String,
    val summary: String,
    val summaryStatus: String,
    val summaryError: String,
    val updatedAt: Long,
    val summarizedAt: Long?,
)

data class BookReflectionState(
    val viewpointId: Long? = null,
    val bookTitle: String = "",
    val author: String = "",
    val viewpointTitle: String = "",
    val viewpointContent: String = "",
    val viewpointExample: String = "",
    val activeSession: BookReflectionSessionUi? = null,
    val sessions: List<BookReflectionSessionUi> = emptyList(),
    val messages: List<BookReflectionMessageUi> = emptyList(),
    val isLoading: Boolean = false,
    val isProcessing: Boolean = false,
    val isSummarizing: Boolean = false,
    val showHistory: Boolean = false,
    val error: String? = null,
)

class BookReflectionViewModel : ViewModel()

fun bookReflectionSummaryActionText(summary: String): String =
    if (summary.isBlank()) "沉淀这一段" else "更新沉淀"

fun bookReflectionStartingPrompts(): List<String> = listOf(
    "这个观点我可能漏掉了哪些角度？",
    "帮我用更具体的例子解释一下",
    "你反问我几个问题，帮我想清楚",
)

fun bookReflectionTitleFromQuestion(question: String): String {
    val firstLine = question.trim().lineSequence().firstOrNull()?.trim().orEmpty()
        .trimEnd('？', '?', '。', '.', '！', '!')
    return firstLine.take(20).ifBlank { "新的思考" }
}

fun bookReflectionTitleFromSummary(summary: String): String {
    val core = summary.lineSequence()
        .map { it.trim() }
        .firstOrNull { it.startsWith("我理解到的核心：") }
        ?.removePrefix("我理解到的核心：")
        ?.trim()
        ?.trimEnd('。', '.', '？', '?', '！', '!')
        .orEmpty()
    return core.take(20).ifBlank { "新的思考" }
}

fun bookReflectionCanRetryLatest(messages: List<BookReflectionMessageUi>): Boolean {
    val last = messages.lastOrNull() ?: return false
    return last.role == "user" || (last.role == "assistant" && last.status == "failed")
}
