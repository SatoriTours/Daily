package com.dailysatori.ui.feature.article

internal val articleProcessingStepLabels = listOf(
    "打开网页",
    "提取正文",
    "优化标题",
    "生成摘要",
    "整理原文",
    "保存封面",
    "完成更新",
)

internal fun isArticleProcessing(status: String?): Boolean = when (status) {
    "pending", "webContentFetched", "aiProcessing", "retrying" -> true
    else -> false
}

internal fun shouldReloadArticleAfterProcessingState(status: String?, progress: String? = null): Boolean = when (status) {
    "completed", "error" -> true
    "webContentFetched" -> progress == "Original saved"
    else -> progress in setOf("Summary ready", "Content ready", "Cover saved")
}

internal fun articleProcessingStepIndex(status: String?, progress: String? = null): Int = when (status) {
    "pending", "retrying" -> 0
    "webContentFetched" -> 1
    "aiProcessing" -> aiProgressStepIndex(progress)
    "completed" -> articleProcessingStepLabels.lastIndex
    else -> -1
}

internal fun articleProcessingProgress(status: String?, progress: String? = null): Float {
    val step = articleProcessingStepIndex(status, progress)
    if (step < 0) return 0f
    return ((step + 1).toFloat() / articleProcessingStepLabels.size).coerceIn(0f, 1f)
}

internal fun articleProcessingCardMessage(status: String?): String? {
    if (status == "error") return "处理失败，点击查看"
    if (!isArticleProcessing(status)) return null
    return articleProcessingMessage(status)
}

internal fun articleProcessingMessage(status: String?, progress: String? = null): String? = when (status) {
    "pending" -> pendingProgressMessage(progress)
    "retrying" -> "等待自动重试..."
    "webContentFetched" -> "网页内容已获取，正在整理..."
    "aiProcessing" -> aiProgressMessage(progress)
    "completed" -> "文章已更新"
    "error" -> "处理失败，请稍后重试"
    else -> null
}

private fun pendingProgressMessage(progress: String?): String = when (progress) {
    "Fetching X post" -> "正在通过 X API 获取内容..."
    else -> "正在打开网页..."
}

private fun aiProgressMessage(progress: String?): String = when (progress) {
    "Translating article" -> "摘要与全文翻译正在处理，原文已可阅读..."
    "Summary ready" -> "摘要已生成，正在整理全文..."
    "Content ready" -> "正文已保存，正在完成 AI 处理..."
    "Generating title" -> "正在优化标题..."
    "Generating summary" -> "正在生成摘要..."
    "Converting to Markdown" -> "正在整理原文排版..."
    "Running AI tasks" -> "正在并行处理标题、摘要和原文..."
    "Downloading cover image" -> "正在保存封面图..."
    else -> "正在处理文章..."
}

private fun aiProgressStepIndex(progress: String?): Int = when (progress) {
    "Translating article", "Summary ready", "Content ready" -> 4
    "Generating title" -> 2
    "Generating summary" -> 3
    "Converting to Markdown" -> 4
    "Downloading cover image" -> 5
    else -> 2
}
