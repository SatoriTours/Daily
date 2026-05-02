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
    "pending", "webContentFetched", "aiProcessing" -> true
    else -> false
}

internal fun shouldReloadArticleAfterProcessingState(status: String?): Boolean = when (status) {
    "completed", "error" -> true
    else -> false
}

internal fun articleProcessingStepIndex(status: String?, progress: String? = null): Int = when (status) {
    "pending" -> 0
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
    if (!isArticleProcessing(status)) return null
    return articleProcessingMessage(status)
}

internal fun articleProcessingMessage(status: String?, progress: String? = null): String? = when (status) {
    "pending" -> "正在打开网页..."
    "webContentFetched" -> "网页内容已获取，正在整理..."
    "aiProcessing" -> aiProgressMessage(progress)
    "completed" -> "文章已更新"
    "error" -> "处理失败，请稍后重试"
    else -> null
}

private fun aiProgressMessage(progress: String?): String = when (progress) {
    "Generating title" -> "正在优化标题..."
    "Generating summary" -> "正在生成摘要..."
    "Converting to Markdown" -> "正在整理原文排版..."
    "Downloading cover image" -> "正在保存封面图..."
    else -> "正在处理文章..."
}

private fun aiProgressStepIndex(progress: String?): Int = when (progress) {
    "Generating title" -> 2
    "Generating summary" -> 3
    "Converting to Markdown" -> 4
    "Downloading cover image" -> 5
    else -> 2
}
