package com.dailysatori

internal fun extractFirstUrl(text: String?): String? {
    if (text.isNullOrBlank()) return null
    return Regex("""https?://\S+""", RegexOption.IGNORE_CASE)
        .find(text)
        ?.value
        ?.trimEnd('.', ',', ';', '，', '。', ')', '）')
}

internal fun normalizeArticleUrl(url: String?): String {
    return url.orEmpty().trim().trimEnd('/')
}

internal fun articleUrlExists(url: String, existingUrls: List<String>): Boolean {
    val normalized = normalizeArticleUrl(url)
    return existingUrls.any { normalizeArticleUrl(it) == normalized }
}

internal fun shouldRetryExistingSharedArticle(status: String?): Boolean = when (status) {
    "pending", "webContentFetched", "aiProcessing", "error" -> true
    else -> false
}

internal fun shouldCheckClipboardOnForeground(launchedFromShare: Boolean): Boolean = !launchedFromShare

internal fun duplicateUrlSnackbarMessage(): String = "链接已存在"

internal fun shareSaveStartedToastMessage(): String = "已开始保存文章"

internal fun shareInvalidUrlToastMessage(): String = "未找到链接"

internal fun clipboardPromptTitle(): String = "检测到剪切板链接"

internal fun shouldScrollToTopAfterArticleAdded(scrollRequest: Long): Boolean = scrollRequest > 0

internal fun countNewLeadingArticles(articleIds: List<Long>, rememberedTopArticleId: Long?): Int {
    if (rememberedTopArticleId == null) return 0
    val rememberedIndex = articleIds.indexOf(rememberedTopArticleId)
    return if (rememberedIndex > 0) rememberedIndex else 0
}

internal fun shouldShowNewArticlesIndicator(newCount: Int, isAtTop: Boolean): Boolean {
    return newCount > 0 && !isAtTop
}

internal class ClipboardUrlPromptState {
    private var handledUrl: String = ""

    fun shouldPrompt(url: String): Boolean {
        return normalizeArticleUrl(url).isNotBlank() && normalizeArticleUrl(url) != handledUrl
    }

    fun markHandled(url: String) {
        handledUrl = normalizeArticleUrl(url)
    }
}

internal class ClipboardCheckGate {
    private var suppressNext = false

    fun suppressNextCheck() {
        suppressNext = true
    }

    fun shouldCheck(): Boolean {
        if (!suppressNext) return true
        suppressNext = false
        return false
    }
}

internal class ClipboardReadGate {
    private var lastReadTimestamp: Long? = null

    fun shouldRead(timestamp: Long): Boolean = lastReadTimestamp != timestamp

    fun markRead(timestamp: Long) {
        lastReadTimestamp = timestamp
    }
}
