package com.dailysatori.service.remotenews

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser

/**
 * Reads RSS 2.0 / Atom / RSS 1.0 entries. Parsing happens through the same jsoup XML parser the
 * existing feed reader uses: it never resolves DOCTYPE entities or fetches external resources, so
 * hostile feeds cannot trigger XXE, and unknown entities stay literal text.
 */
internal actual fun parseRemoteFeedEntries(body: String, feedUrl: String): List<RemoteFeedEntry> {
    if (body.isBlank()) throw RemoteFeedFormatException("RSS 内容为空")
    val document = runCatching {
        Jsoup.parse(body, feedUrl.ifBlank { "" }, Parser.xmlParser())
    }.getOrElse { throw RemoteFeedFormatException("RSS 内容不是有效的 XML") }
    val root = document.children().firstOrNull() ?: throw RemoteFeedFormatException("RSS 内容不是有效的 XML")
    val rootName = root.normalName()
    val entries = when {
        rootName == "rss" -> root.children().firstOrNull { it.normalName() == "channel" }
            ?.children().orEmpty().filter { it.normalName() == "item" }
        rootName == "feed" -> root.children().filter { it.normalName() == "entry" }
        rootName.endsWith("rdf") -> root.select("item")
        else -> throw RemoteFeedFormatException("返回内容不是有效的 RSS/Atom 订阅")
    }
    return entries.mapNotNull(::feedEntry)
}

private fun feedEntry(entry: Element): RemoteFeedEntry? {
    val children = entry.children()
    fun node(vararg names: String): Element? = children.firstOrNull { it.normalName() in names }
    fun text(vararg names: String): String? = node(*names)?.text()?.trim()?.takeIf(String::isNotEmpty)
    fun html(vararg names: String): String? = node(*names)?.let { element ->
        (if (element.children().isEmpty()) element.text() else element.html()).trim().takeIf(String::isNotEmpty)
    }
    val about = entry.attr("rdf:about").ifBlank { entry.attr("about") }.takeIf(String::isNotBlank)
    val links = children.filter { it.normalName() == "link" }
    val preferredLink = links.firstOrNull { it.attr("rel") == "alternate" }
        ?: links.firstOrNull { it.attr("rel").isBlank() }
    val link = preferredLink?.let { element ->
        if (element.hasAttr("href")) element.absUrl("href").ifBlank { element.attr("href") } else element.text()
    } ?: about
    return RemoteFeedEntry(
        title = text("title"),
        link = link,
        guid = text("guid", "id") ?: about,
        descriptionHtml = html("description", "summary"),
        contentHtml = html("content:encoded", "content"),
        publishedAt = text("pubdate", "published", "updated", "dc:date", "date"),
    )
}
