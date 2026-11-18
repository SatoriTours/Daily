package com.dailysatori.service.remotenews

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser

private const val ATOM_NAMESPACE = "http://www.w3.org/2005/Atom"
private const val HISTORY_NAMESPACE = "http://purl.org/syndication/history/1.0"

/**
 * Reads RSS 2.0 / Atom / RSS 1.0 entries plus the RFC 5005 head relations. Parsing happens through the
 * same jsoup XML parser the existing feed reader uses: it never resolves DOCTYPE entities or fetches
 * external resources, so hostile feeds cannot trigger XXE, and unknown entities stay literal text.
 */
internal actual fun parseRemoteFeedDocument(body: String, feedUrl: String): RemoteFeedDocument {
    if (body.isBlank()) throw RemoteFeedFormatException("RSS 内容为空")
    val document = runCatching {
        Jsoup.parse(body, feedUrl.ifBlank { "" }, Parser.xmlParser())
    }.getOrElse { throw RemoteFeedFormatException("RSS 内容不是有效的 XML") }
    val root = document.children().firstOrNull() ?: throw RemoteFeedFormatException("RSS 内容不是有效的 XML")
    val rootName = root.normalName()
    val isAtom = rootName == "feed"
    val head: Element
    val entryElements: List<Element>
    when {
        rootName == "rss" -> {
            val channel = root.children().firstOrNull { it.normalName() == "channel" }
            head = channel ?: root
            entryElements = channel?.children().orEmpty().filter { it.normalName() == "item" }
        }
        isAtom -> {
            head = root
            entryElements = root.children().filter { it.normalName() == "entry" }
        }
        rootName.endsWith("rdf") -> {
            val channel = root.children().firstOrNull { it.normalName() == "channel" }
            head = channel ?: root
            entryElements = root.select("item").toList()
        }
        else -> throw RemoteFeedFormatException("返回内容不是有效的 RSS/Atom 订阅")
    }
    val documentBase = feedUrl.ifBlank { "" }
    return RemoteFeedDocument(
        entries = entryElements.mapNotNull(::feedEntry),
        title = head.children().firstOrNull { it.normalName() == "title" }?.text()?.trim()?.takeIf(String::isNotEmpty),
        links = feedHeadLinks(head, isAtom, documentBase),
        isArchive = head.children().any { isHistoryElement(it, "archive") },
        isComplete = head.children().any { isHistoryElement(it, "complete") },
    )
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
        summaryLanguage = node("description", "summary")?.attr("xml:lang")?.takeIf(String::isNotBlank),
        publishedAt = text("pubdate", "published", "updated", "dc:date", "date"),
    )
}

/**
 * RFC 5005 relations live in the head section of the document: the feed element itself for Atom, the
 * channel element for RSS. A prefixed link only counts when its prefix is bound to the Atom namespace,
 * so an unrelated vendor element named "link" is never mistaken for a paging relation.
 */
private fun feedHeadLinks(head: Element, isAtom: Boolean, documentBase: String): RemoteFeedLinks {
    var next: String? = null
    var previous: String? = null
    var first: String? = null
    var last: String? = null
    var prevArchive: String? = null
    var nextArchive: String? = null
    var current: String? = null
    for (element in head.children()) {
        if (element.normalName().substringAfterLast(':') != "link") continue
        val prefix = element.normalName().substringBeforeLast(':', missingDelimiterValue = "").ifEmpty { null }
        if (prefix == null && !isAtom && !element.hasAttr("rel")) continue
        if (prefix != null && namespaceOf(element, prefix) != ATOM_NAMESPACE) continue
        val relation = remoteFeedLinkRelation(element.attr("rel")) ?: continue
        val resolved = resolveRemoteFeedUrl(elementBaseUrl(element, documentBase), element.attr("href")) ?: continue
        when (relation) {
            "next" -> if (next == null) next = resolved
            "prev", "previous" -> if (previous == null) previous = resolved
            "first" -> if (first == null) first = resolved
            "last" -> if (last == null) last = resolved
            "prev-archive" -> if (prevArchive == null) prevArchive = resolved
            "next-archive" -> if (nextArchive == null) nextArchive = resolved
            "current" -> if (current == null) current = resolved
        }
    }
    return RemoteFeedLinks(
        next = next,
        previous = previous,
        first = first,
        last = last,
        prevArchive = prevArchive,
        nextArchive = nextArchive,
        current = current,
    )
}

/** True when [element] is the RFC 5005 history child [localName] bound to its own namespace. */
private fun isHistoryElement(element: Element, localName: String): Boolean {
    val name = element.normalName()
    if (name.substringAfterLast(':') != localName) return false
    val prefix = name.substringBeforeLast(':', missingDelimiterValue = "").ifEmpty { return false }
    return namespaceOf(element, prefix) == HISTORY_NAMESPACE
}

/** Resolves [prefix] through the xmlns declarations in scope; null when nothing is declared. */
private fun namespaceOf(element: Element, prefix: String): String? {
    var node: Element? = element
    while (node != null) {
        val declared = node.attr("xmlns:$prefix").trim()
        if (declared.isNotEmpty()) return declared
        node = node.parent()
    }
    return null
}

/** Applies the xml:base declarations from the document root down to [element]. */
private fun elementBaseUrl(element: Element, documentBase: String): String {
    var base = documentBase
    for (node in generateSequence(element) { it.parent() }.toList().asReversed()) {
        val declared = node.attr("xml:base").trim()
        if (declared.isNotEmpty()) base = resolveRemoteFeedUrl(base, declared) ?: base
    }
    return base
}
