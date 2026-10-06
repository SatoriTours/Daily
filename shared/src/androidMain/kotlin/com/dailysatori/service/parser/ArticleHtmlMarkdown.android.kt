package com.dailysatori.service.parser

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

internal actual fun articleHtmlToMarkdown(html: String, baseUrl: String): String {
    val body = Jsoup.parseBodyFragment(html, baseUrl).body()
    body.select("script,style,nav,button,iframe,noscript").remove()
    return renderArticleNodes(body.childNodes()).trim()
}

private fun renderArticleNodes(nodes: List<Node>): String = nodes.joinToString("") { node ->
    when (node) {
        is TextNode -> node.text().replace(Regex("[\\t\\r\\n ]+"), " ")
        is Element -> renderArticleElement(node)
        else -> ""
    }
}

private fun renderArticleElement(element: Element): String {
    val tag = element.normalName()
    if (tag == "pre") return renderArticleCode(element)
    if (tag == "table") return renderArticleTable(element)
    if (tag == "ul" || tag == "ol") return "\n\n${renderArticleList(element, 0)}\n\n"
    val content = renderArticleNodes(element.childNodes())
    return when (tag) {
        "h1", "h2", "h3", "h4", "h5", "h6" -> "\n\n${"#".repeat(tag.last().digitToInt())} ${content.trim()}\n\n"
        "p", "div", "section", "article", "figure", "figcaption" -> "\n\n${content.trim()}\n\n"
        "br" -> "\n"
        "hr" -> "\n\n---\n\n"
        "strong", "b" -> "**$content**"
        "em", "i" -> "*$content*"
        "s", "del" -> "~~$content~~"
        "code" -> "`${element.wholeText()}`"
        "a" -> articleHtmlLink(element, content)
        "img" -> articleHtmlImage(element)
        "blockquote" -> "\n\n${content.trim().lines().joinToString("\n") { "> $it" }}\n\n"
        else -> content
    }
}

private fun articleHtmlLink(element: Element, content: String): String {
    val url = element.absUrl("href").takeIf { it.startsWith("https://") || it.startsWith("http://") }
    return if (url == null) content else "[$content](${url.replace(" ", "%20").replace(")", "%29")})"
}

private fun articleHtmlImage(element: Element): String {
    val attribute = listOf("data-src", "data-original", "src").firstOrNull {
        element.absUrl(it).let { url -> url.startsWith("https://") || url.startsWith("http://") }
    } ?: return ""
    val url = element.absUrl(attribute).replace(" ", "%20").replace(")", "%29")
    val alt = element.attr("alt").replace("[", "").replace("]", "")
    return "\n\n![$alt]($url)\n\n"
}

private fun renderArticleCode(element: Element): String {
    val code = element.selectFirst("code") ?: element
    val language = code.classNames().firstOrNull { it.startsWith("language-") }?.removePrefix("language-").orEmpty()
    val text = code.wholeText().trimEnd('\n', '\r')
    val fence = "`".repeat(maxOf(3, (Regex("`+").findAll(text).maxOfOrNull { it.value.length } ?: 0) + 1))
    return "\n\n$fence$language\n$text\n$fence\n\n"
}

private fun renderArticleList(list: Element, depth: Int): String = list.children()
    .filter { it.normalName() == "li" }.mapIndexed { index, item ->
        val nested = item.children().filter { it.normalName() in setOf("ul", "ol") }
        val body = renderArticleNodes(item.childNodes().filterNot { it in nested }).trim()
        val marker = if (list.normalName() == "ol") "${index + 1}. " else "- "
        val prefix = "  ".repeat(depth)
        val lines = body.lines().joinToString("\n$prefix  ")
        "$prefix$marker$lines" + nested.joinToString("") { "\n${renderArticleList(it, depth + 1)}" }
    }.joinToString("\n")

private fun renderArticleTable(table: Element): String {
    val rows = table.select("tr").map { row -> row.children().filter { it.normalName() in setOf("td", "th") }
        .map { renderArticleNodes(it.childNodes()).trim().replace("|", "\\|").replace(Regex("\\s*\\n\\s*"), "<br>") } }
        .filter { it.isNotEmpty() }
    if (rows.isEmpty()) return ""
    val width = rows.maxOf { it.size }
    fun row(cells: List<String>) = "| " + (cells + List(width - cells.size) { "" }).joinToString(" | ") + " |"
    return "\n\n" + (listOf(row(rows.first()), row(List(width) { "---" })) + rows.drop(1).map(::row)).joinToString("\n") + "\n\n"
}
