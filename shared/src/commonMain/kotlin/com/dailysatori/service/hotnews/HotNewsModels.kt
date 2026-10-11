package com.dailysatori.service.hotnews

import kotlinx.serialization.Serializable

@Serializable
enum class HotNewsPlatform(
    val key: String,
    val displayName: String,
    val boardUrl: String,
    val defaultFeedUrl: String = "",
    val core: Boolean = true,
) {
    GITHUB("github", "GitHub", "https://github.com/trending?since=daily"),
    HACKER_NEWS("hackernews", "Hacker News", "https://news.ycombinator.com/", "https://rsshub.app/hackernews/index"),
    X("x", "X", "https://x.com/explore", "https://rsshub.app/twitter/trends/1"),
    XIAOHONGSHU("xiaohongshu", "小红书", "https://www.xiaohongshu.com/explore"),
    TOUTIAO("toutiao", "今日头条", "https://www.toutiao.com/", "https://rsshub.app/toutiao/channel/news_hot"),
    ZHIHU("zhihu", "知乎", "https://www.zhihu.com/hot", "https://rsshub.app/zhihu/hot"),
    V2EX("v2ex", "V2EX", "https://www.v2ex.com/?tab=hot", "https://rsshub.app/v2ex/topics/hot"),
    LINUX_DO("linuxdo", "Linux DO", "https://linux.do/top"),
    CHINA_NEWS("chinanews", "中新网", "https://www.chinanews.com.cn/", "https://www.chinanews.com.cn/rss/importnews.xml", false),
    IT_HOME("ithome", "IT之家", "https://www.ithome.com/", "https://www.ithome.com/rss/", false),
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
    val fromFeed: Boolean = false,
) {
    val identity: String get() = "${platform.key}:$id"
    val displayTitle: String? get() = chineseTitle ?: originalTitle.takeIf(::isChineseHotNewsText)
    val displaySummary: String get() = chineseSummary ?: description.takeIf(::isChineseHotNewsText).orEmpty()
    val fingerprint: String get() = "$originalTitle\n$description"
}

@Serializable
enum class HotNewsError { FETCH_FAILED, FEED_NOT_CONFIGURED, AI_NOT_CONFIGURED, TRANSLATION_FAILED }

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
