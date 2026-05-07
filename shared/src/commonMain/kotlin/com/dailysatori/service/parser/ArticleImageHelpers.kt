package com.dailysatori.service.parser

private fun extractMetadataImageUrl(html: String): String? {
    val opts = setOf(RegexOption.IGNORE_CASE)
    val names = "(?:og:image|og:image:secure_url|twitter:image|twitter:image:src)"
    val imageRegex = Regex("""<meta[^>]*(?:property|name)\s*=\s*["']$names["'][^>]*content\s*=\s*["']([^"']*)["']""", opts)
    val image2Regex = Regex("""<meta[^>]*content\s*=\s*["']([^"']*)["'][^>]*(?:property|name)\s*=\s*["']$names["']""", opts)
    return imageRegex.find(html)?.groupValues?.get(1)
        ?: image2Regex.find(html)?.groupValues?.get(1)
}

internal fun extractCoverImageUrl(html: String, sourceUrl: String? = null): String? {
    return extractLargestContentImgSrc(html, sourceUrl)
        ?: extractMetadataImageUrl(html)?.takeUnless { it.hasSkippedKeyword() }?.toAbsoluteUrl(sourceUrl)?.normalizeTwitterImageUrl()
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
    val imgRegex = Regex("""<img\b[^>]*>""", setOf(RegexOption.IGNORE_CASE))
    return imgRegex.findAll(html)
        .mapNotNull { imageSrc(it.value) }
        .firstOrNull { !it.shouldSkipImage() && !it.hasSkippedKeyword() }
        ?.toAbsoluteUrl(sourceUrl)
        ?.normalizeTwitterImageUrl()
}

private fun imageCandidate(tag: String): ImageCandidate? {
    val src = imageSrc(tag) ?: return null
    if (src.shouldSkipImage() || src.hasSkippedKeyword()) return null
    val width = attrValue(tag, "width")?.toLongOrNull() ?: 0L
    val height = attrValue(tag, "height")?.toLongOrNull() ?: 0L
    return ImageCandidate(src, width, height)
}

private fun imageSrc(tag: String): String? =
    attrValue(tag, "data-src")
        ?: attrValue(tag, "data-original")
        ?: attrValue(tag, "data-lazy-src")
        ?: bestSrcsetUrl(attrValue(tag, "data-srcset"))
        ?: bestSrcsetUrl(attrValue(tag, "srcset"))
        ?: attrValue(tag, "src")

private fun bestSrcsetUrl(srcset: String?): String? {
    if (srcset.isNullOrBlank()) return null
    return srcset.split(',')
        .mapNotNull { srcsetCandidate(it.trim()) }
        .maxByOrNull { it.width }
        ?.url
}

private fun srcsetCandidate(candidate: String): SrcsetCandidate? {
    val parts = candidate.split(Regex("\\s+")).filter { it.isNotBlank() }
    val url = parts.firstOrNull() ?: return null
    if (url.shouldSkipImage() || url.hasSkippedKeyword()) return null
    val width = parts.drop(1)
        .firstOrNull { it.endsWith("w", ignoreCase = true) }
        ?.dropLast(1)
        ?.toLongOrNull()
        ?: 0L
    return SrcsetCandidate(url, width)
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
        else -> "${sourceUrl.basePathUrl()}/$this"
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

private fun String.basePathUrl(): String {
    val withoutQuery = substringBefore('?').substringBefore('#')
    return if (withoutQuery.endsWith("/")) withoutQuery.dropLast(1) else withoutQuery.substringBeforeLast('/', missingDelimiterValue = origin().orEmpty())
}

private data class ImageCandidate(
    val src: String,
    val width: Long,
    val height: Long,
) {
    val area: Long = width * height
    val isLargeEnough: Boolean = width > 300L || height > 300L
}

private data class SrcsetCandidate(
    val url: String,
    val width: Long,
)
