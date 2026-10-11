package com.dailysatori.service.hotnews

import kotlinx.serialization.Serializable

@Serializable
enum class HotNewsPlatform(val key: String, val displayName: String, val boardUrl: String) {
    GITHUB("github", "GitHub", "https://github.com/trending?since=daily"),
    HACKER_NEWS("hackernews", "Hacker News", "https://news.ycombinator.com/"),
    ZHIHU("zhihu", "知乎", "https://www.zhihu.com/hot"),
    TOUTIAO("toutiao", "今日头条", "https://www.toutiao.com/"),
    V2EX("v2ex", "V2EX", "https://www.v2ex.com/?tab=hot"),
}

@Serializable
data class HotNewsItem(
    val id: String,
    val platform: HotNewsPlatform,
    val rank: Int,
    val originalTitle: String,
    val url: String,
    val description: String = "",
    val discussionUrl: String? = null,
    val heat: String? = null,
    val comments: Int? = null,
    val chineseTitle: String? = null,
    val chineseSummary: String? = null,
    val originalContent: String = "",
) {
    val identity: String get() = "${platform.key}:$id"
    val displayTitle: String? get() = chineseTitle ?: originalTitle.takeIf(::isChineseHotNewsText)
    val displaySummary: String get() = chineseSummary ?: description.takeIf(::isChineseHotNewsText).orEmpty()
    val fingerprint: String get() = "$originalTitle\n$description"
}

@Serializable
enum class HotNewsError { FETCH_FAILED, AI_NOT_CONFIGURED, TRANSLATION_FAILED }

@Serializable
data class HotNewsBoard(
    val platform: HotNewsPlatform,
    val items: List<HotNewsItem> = emptyList(),
    val fetchedAt: Long = 0,
    val error: HotNewsError? = null,
    val refreshing: Boolean = false,
)

private val hotNewsIdentifiers = Regex("https?://\\S+|[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+|\\b[A-Z][A-Za-z0-9.+-]*\\b")

internal fun isChineseHotNewsText(text: String): Boolean =
    text.count { it in '\u4e00'..'\u9fff' } >= 2 &&
        !com.dailysatori.service.parser.articleNeedsTranslation(hotNewsIdentifiers.replace(text, ""))

internal fun safeHotNewsUrl(value: String): String? {
    if (!value.startsWith("https://") && !value.startsWith("http://")) return null
    return runCatching { io.ktor.http.Url(value) }.getOrNull()
        ?.takeIf { it.host.isNotBlank() }?.toString()
}
