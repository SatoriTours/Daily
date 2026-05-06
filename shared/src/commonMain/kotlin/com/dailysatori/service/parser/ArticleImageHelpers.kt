package com.dailysatori.service.parser

private fun extractOgImageUrl(html: String): String? {
    val opts = setOf(RegexOption.IGNORE_CASE)
    val ogImageRegex = Regex("""<meta[^>]*property\s*=\s*["']og:image["'][^>]*content\s*=\s*["']([^"']*)["']""", opts)
    val ogImage2Regex = Regex("""<meta[^>]*content\s*=\s*["']([^"']*)["'][^>]*property\s*=\s*["']og:image["']""", opts)
    return ogImageRegex.find(html)?.groupValues?.get(1)
        ?: ogImage2Regex.find(html)?.groupValues?.get(1)
}

internal fun extractCoverImageUrl(html: String, sourceUrl: String? = null): String? {
    return extractLargestContentImgSrc(html, sourceUrl)
        ?: extractOgImageUrl(html)?.takeUnless { it.hasSkippedKeyword() }?.normalizeTwitterImageUrl()
        ?: extractFirstImgSrc(html, sourceUrl)
}

internal fun extractContentImageUrls(html: String, sourceUrl: String? = null): List<String> {
    val imgRegex = Regex("""<img\b[^>]*>""", setOf(RegexOption.IGNORE_CASE))
    return imgRegex.findAll(html)
        .mapNotNull { imageCandidate(it.value) }
        .filter { it.isLargeEnough }
        .map { it.src.toAbsoluteUrl(sourceUrl).normalizeTwitterImageUrl() }
        .distinct()
        .toList()
}

private fun extractLargestContentImgSrc(html: String, sourceUrl: String?): String? {
    val imgRegex = Regex("""<img\b[^>]*>""", setOf(RegexOption.IGNORE_CASE))
    return imgRegex.findAll(html)
        .mapNotNull { match -> imageCandidate(match.value) }
        .filter { it.isLargeEnough }
        .maxByOrNull { it.area }
        ?.src
        ?.toAbsoluteUrl(sourceUrl)
        ?.normalizeTwitterImageUrl()
}

private fun extractFirstImgSrc(html: String, sourceUrl: String?): String? {
    val imgRegex = Regex("""<img[^>]+src\s*=\s*["']([^"']+)["']""", setOf(RegexOption.IGNORE_CASE))
    return imgRegex.findAll(html)
        .map { it.groupValues[1] }
        .firstOrNull { !it.shouldSkipImage() && !it.hasSkippedKeyword() }
        ?.toAbsoluteUrl(sourceUrl)
        ?.normalizeTwitterImageUrl()
}

private fun imageCandidate(tag: String): ImageCandidate? {
    val src = attrValue(tag, "src") ?: return null
    if (src.shouldSkipImage() || src.hasSkippedKeyword()) return null
    val width = attrValue(tag, "width")?.toLongOrNull() ?: 0L
    val height = attrValue(tag, "height")?.toLongOrNull() ?: 0L
    return ImageCandidate(src, width, height)
}

private fun attrValue(tag: String, name: String): String? {
    val regex = Regex("""\b$name\s*=\s*["']([^"']+)["']""", setOf(RegexOption.IGNORE_CASE))
    return regex.find(tag)?.groupValues?.get(1)
}

private fun String.shouldSkipImage(): Boolean {
    return startsWith("data:image/", ignoreCase = true) || endsWith(".gif", ignoreCase = true)
}

private fun String.hasSkippedKeyword(): Boolean {
    return contains("logo", ignoreCase = true) ||
        contains("avatar", ignoreCase = true) ||
        contains("icon", ignoreCase = true) ||
        contains("placeholder", ignoreCase = true) ||
        contains("default", ignoreCase = true) ||
        contains("blank", ignoreCase = true) ||
        contains("transparent", ignoreCase = true)
}

private fun String.toAbsoluteUrl(sourceUrl: String?): String {
    if (startsWith("http://", ignoreCase = true) || startsWith("https://", ignoreCase = true)) return this
    val origin = sourceUrl?.origin() ?: return this
    return when {
        startsWith("//") -> "https:$this"
        startsWith("/") -> "$origin$this"
        else -> "$origin/$this"
    }
}

internal fun String.normalizeTwitterImageUrl(): String {
    if (!contains("pbs.twimg.com/media/", ignoreCase = true)) return this
    return replace(Regex("([?&]name=)[^&]+", RegexOption.IGNORE_CASE), "$1large")
}

private fun String.origin(): String? {
    val regex = Regex("""^(https?://[^/]+)""", RegexOption.IGNORE_CASE)
    return regex.find(this)?.groupValues?.get(1)
}

private data class ImageCandidate(
    val src: String,
    val width: Long,
    val height: Long,
) {
    val area: Long = width * height
    val isLargeEnough: Boolean = width > 300L || height > 300L
}
