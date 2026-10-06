package com.dailysatori.service.parser

/** Converts extracted body HTML locally; never sends HTML to the AI. */
internal expect fun articleHtmlToMarkdown(html: String, baseUrl: String): String

internal fun articleOriginalMarkdown(extracted: ExtractedContent, url: String): String {
    val html = extracted.readableHtmlContent?.trim().orEmpty()
    val body = if (html.isNotBlank()) articleHtmlToMarkdown(html, url) else extracted.content.orEmpty().trim()
    return normalizeArticleMarkdownImages(body, extracted.imageUrls)
}
