package com.dailysatori.service.externalfavorites

import kotlinx.serialization.json.*

/** Titles and previews are never evidence that the complete Article body was fetched. */
internal fun xArticleNeedsBody(canonicalUrl: String?, normalizedJson: String): Boolean {
    val metadata = runCatching { Json.parseToJsonElement(normalizedJson) as? JsonObject }.getOrNull()
    return xIsArticle(canonicalUrl, metadata) && metadata?.textValue("article_content_complete") != "true"
}

internal fun xIsArticle(canonicalUrl: String?, metadata: JsonObject?): Boolean =
    canonicalUrl?.let(::isXArticleUrl) == true || metadata?.textValue("is_article") == "true" ||
        metadata?.textValue("primary_url")?.let(::isXArticleUrl) == true

internal fun JsonObject.textValue(key: String): String? =
    (get(key) as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

internal fun JsonObject.objectValue(key: String): JsonObject? = get(key) as? JsonObject
internal fun JsonObject.arrayValue(key: String): JsonArray = get(key) as? JsonArray ?: JsonArray(emptyList())

internal fun xArticleBody(article: JsonObject): String? {
    listOf("plain_text", "body", "content", "text").firstNotNullOfOrNull { article.textValue(it) }?.let { return it }
    val state = article.objectValue("content") ?: article.objectValue("content_state") ?: return null
    val blocks = state.arrayValue("blocks").mapNotNull { it as? JsonObject }
    val entities = articleEntities(state)
    val media = article.arrayValue("media_entities").mapNotNull { it as? JsonObject }
        .associateBy { it.textValue("media_id").orEmpty() }
    return blocks.map { articleBlockMarkdown(it, entities, media) }
        .filter(String::isNotBlank).joinToString("\n\n").takeIf(String::isNotBlank)
}

private fun articleEntities(state: JsonObject): Map<String, JsonObject> {
    val entries = state["entityMap"] ?: state["entities"]
    if (entries is JsonObject) return entries.mapNotNull { (key, value) -> (value as? JsonObject)?.let { key to it } }.toMap()
    return (entries as? JsonArray).orEmpty().mapNotNull { entry ->
        val obj = entry as? JsonObject ?: return@mapNotNull null
        val key = obj.textValue("key") ?: return@mapNotNull null
        obj.objectValue("value")?.let { key to it }
    }.toMap()
}

private fun articleBlockMarkdown(block: JsonObject, entities: Map<String, JsonObject>, media: Map<String, JsonObject>): String {
    if (block.textValue("type") == "atomic") return articleAtomicMarkdown(block, entities, media)
    val text = articleInlineMarkdown(block, entities)
    if (text.isBlank()) return ""
    return when (block.textValue("type")) {
        "header-one" -> "# $text"
        "header-two" -> "## $text"
        "header-three" -> "### $text"
        "unordered-list-item" -> "- $text"
        "ordered-list-item" -> "1. $text"
        "blockquote" -> text.lines().joinToString("\n") { "> $it" }
        "code-block" -> "```\n$text\n```"
        else -> text
    }
}

private data class ArticleSpan(val start: Int, val end: Int, val open: String, val close: String)

private fun articleInlineMarkdown(block: JsonObject, entities: Map<String, JsonObject>): String {
    val text = block.textValue("text").orEmpty()
    val styles = (block["inlineStyleRanges"] ?: block["inline_style_ranges"]) as? JsonArray
    val links = (block["entityRanges"] ?: block["entity_ranges"]) as? JsonArray
    val spans = styles.orEmpty().mapNotNull { (it as? JsonObject)?.articleSpan(text.length) }
        .plus(links.orEmpty().mapNotNull { entry ->
            val range = entry as? JsonObject ?: return@mapNotNull null
            val entity = entities[range.textValue("key")] ?: return@mapNotNull null
            val url = entity.objectValue("data")?.textValue("url")?.takeIf(::isArticleHttpUrl) ?: return@mapNotNull null
            if (entity.textValue("type")?.lowercase() != "link") return@mapNotNull null
            range.articleSpan(text.length, "[", "](${url.replace(")", "%29")})")
        })
    val opens = spans.groupBy { it.start }
    val closes = spans.groupBy { it.end }
    return buildString {
        for (index in 0..text.length) {
            closes[index]?.sortedByDescending { it.start }?.forEach { append(it.close) }
            opens[index]?.sortedByDescending { it.end }?.forEach { append(it.open) }
            if (index < text.length) append(text[index])
        }
    }
}

private fun JsonObject.articleSpan(textLength: Int, open: String? = null, close: String? = null): ArticleSpan? {
    val start = textValue("offset")?.toIntOrNull() ?: return null
    val length = textValue("length")?.toIntOrNull() ?: return null
    if (start < 0 || length <= 0 || start > textLength || length > textLength - start) return null
    val marker = when (textValue("style")?.lowercase()) {
        "bold" -> "**"
        "italic" -> "*"
        "strikethrough" -> "~~"
        "code" -> "`"
        else -> null
    }
    return ArticleSpan(start, start + length, open ?: marker ?: return null, close ?: marker ?: return null)
}

private fun articleAtomicMarkdown(block: JsonObject, entities: Map<String, JsonObject>, media: Map<String, JsonObject>): String {
    val ranges = (block["entityRanges"] ?: block["entity_ranges"]) as? JsonArray
    return ranges.orEmpty().mapNotNull { (it as? JsonObject)?.textValue("key")?.let(entities::get) }
        .joinToString("\n\n") { entity ->
            val data = entity.objectValue("data") ?: JsonObject(emptyMap())
            when (entity.textValue("type")?.lowercase()) {
                "media", "image" -> articleMediaMarkdown(data, media)
                "markdown" -> data.textValue("markdown").orEmpty()
                "divider" -> "---"
                "tweet", "post" -> (data.textValue("tweetId") ?: data.textValue("post_id"))
                    ?.takeIf { it.all(Char::isDigit) }?.let { "[嵌入推文](https://x.com/i/status/$it)" }.orEmpty()
                "latex" -> "$$\n${block.textValue("text").orEmpty()}\n$$"
                else -> block.textValue("text").orEmpty().trim()
            }
        }
}

private fun articleMediaMarkdown(data: JsonObject, media: Map<String, JsonObject>): String {
    val items = (data["mediaItems"] ?: data["media_items"]) as? JsonArray
    return items.orEmpty().mapNotNull { item ->
        val obj = item as? JsonObject ?: return@mapNotNull null
        val id = obj.textValue("mediaId") ?: obj.textValue("media_id") ?: return@mapNotNull null
        val url = media[id]?.objectValue("media_info")?.textValue("original_img_url")?.takeIf(::isArticleHttpUrl)
            ?: return@mapNotNull null
        val caption = data.textValue("caption").orEmpty().replace("]", "\\]")
        "![$caption]($url)"
    }.joinToString("\n\n")
}

internal fun isArticleHttpUrl(url: String): Boolean = url.startsWith("https://") || url.startsWith("http://")
