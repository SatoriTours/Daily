package com.dailysatori.service.parser

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal fun parseFxTwitterTweetPayload(json: String, sourceUrl: String): ExtractedContent? {
    val tweet = Json.parseToJsonElement(json).jsonObject["tweet"]?.jsonObject ?: return null
    val text = tweet.stringValue("text")?.trim()?.takeIf { it.isNotBlank() } ?: return null
    val author = tweet["author"]?.jsonObject
    val authorName = author?.stringValue("name") ?: author?.stringValue("screen_name")
    val imageUrls = tweetImageUrls(tweet)
    val title = text.lineSequence().firstOrNull { it.isNotBlank() }?.take(80) ?: "X Post"
    val html = buildTweetHtml(title, text, sourceUrl, authorName, imageUrls)
    return ExtractedContent(
        title = title.replace(Regex("\\s+"), " "),
        content = text,
        htmlContent = html,
        coverImageUrl = imageUrls.firstOrNull(),
        imageUrls = imageUrls,
    )
}

internal fun twitterStatusId(url: String): String? {
    val regex = Regex("""https?://(?:www\.)?(?:x|twitter)\.com/[^/]+/status/(\d+)""", RegexOption.IGNORE_CASE)
    return regex.find(url)?.groupValues?.get(1)
}

private fun tweetImageUrls(tweet: JsonObject): List<String> {
    val media = tweet["media"]?.jsonObject ?: return emptyList()
    val photos = media["photos"]?.jsonArray ?: media["all"]?.jsonArray ?: return emptyList()
    return photos.mapNotNull { photo ->
        val obj = photo.jsonObject
        val url = obj.stringValue("url") ?: return@mapNotNull null
        val width = obj.longValue("width")
        val height = obj.longValue("height")
        url.takeIf { width > 300L || height > 300L }?.normalizeTwitterImageUrl()
    }.distinct()
}

private fun buildTweetHtml(
    title: String,
    text: String,
    sourceUrl: String,
    authorName: String?,
    imageUrls: List<String>,
): String {
    val escapedText = escapeHtml(text).replace("\n", "<br>")
    val images = imageUrls.joinToString("\n") { "<img src=\"$it\" width=\"1024\" height=\"576\">" }
    val byline = authorName?.let { "<p>作者：${escapeHtml(it)}</p>" }.orEmpty()
    return """
        <html><head><title>${escapeHtml(title)}</title></head><body>
        <article>
        $byline
        <p>$escapedText</p>
        $images
        <p>原文：<a href="$sourceUrl">$sourceUrl</a></p>
        </article>
        </body></html>
    """.trimIndent()
}

private fun JsonObject.stringValue(name: String): String? = this[name]?.jsonPrimitive?.contentOrNull

private fun JsonObject.longValue(name: String): Long = stringValue(name)?.toLongOrNull() ?: 0L

private fun escapeHtml(text: String): String {
    return text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}
