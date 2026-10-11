package com.dailysatori.service.hotnews

import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import org.jsoup.nodes.Element
import com.dailysatori.service.parser.articleHtmlToMarkdown

internal actual fun parseGithubHotNews(html: String): List<HotNewsItem> =
    Jsoup.parse(html, "https://github.com").select("article.Box-row").mapIndexedNotNull { index, article ->
        val link = article.selectFirst("h2 a") ?: return@mapIndexedNotNull null
        val id = link.attr("href").trim('/')
        if (!Regex("[\\w.-]+/[\\w.-]+").matches(id)) return@mapIndexedNotNull null
        val description = article.selectFirst("p")?.text().orEmpty()
        val growth = article.selectFirst("span.float-sm-right")?.text()?.let {
            Regex("[\\d,]+").find(it)?.value?.replace(",", "")
        }
        HotNewsItem(id, HotNewsPlatform.GITHUB, index + 1, id, "https://github.com/$id", description, heat = growth)
    }

internal actual fun parseHotNewsFeed(body: String, feedUrl: String, platform: HotNewsPlatform): List<HotNewsItem> {
    val document = Jsoup.parse(body, feedUrl, Parser.xmlParser())
    require(document.children().any { it.tagName() in setOf("rss", "feed", "rdf:RDF") }) { "Invalid news feed" }
    return document.select("item, entry").mapIndexedNotNull { index, entry ->
        feedItem(entry, platform, index + 1)
    }.distinctBy { it.id }
}

private fun feedItem(entry: Element, platform: HotNewsPlatform, rank: Int): HotNewsItem? {
    val title = entry.children().firstOrNull { it.tagName() == "title" }?.text()?.trim()
        ?.takeIf(String::isNotBlank) ?: return null
    val links = entry.children().filter { it.tagName() == "link" }
    val link = links.firstOrNull { it.attr("rel") == "alternate" }
        ?: links.firstOrNull { it.attr("rel").isBlank() } ?: return null
    val url = safeHotNewsUrl(if (link.hasAttr("href")) link.absUrl("href") else link.text().trim()) ?: return null
    val id = entry.children().firstOrNull { it.tagName() in setOf("guid", "id") }
        ?.text()?.trim()?.takeIf(String::isNotBlank) ?: url
    val content = listOf("content:encoded", "content", "description", "summary").firstNotNullOfOrNull { tag ->
        entry.children().firstOrNull { it.tagName() == tag }?.let { node ->
            (if (node.children().isEmpty()) node.text() else node.html()).takeIf(String::isNotBlank)
        }
    }.orEmpty()
    val markdown = articleHtmlToMarkdown(content, url).take(12_000)
    return HotNewsItem(id, platform, rank, title, url, description = markdown.take(1000), originalContent = markdown, fromFeed = true)
}
