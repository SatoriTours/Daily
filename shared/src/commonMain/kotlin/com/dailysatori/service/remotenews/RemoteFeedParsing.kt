package com.dailysatori.service.remotenews

import com.dailysatori.service.externalfavorites.sha256Hex
import com.dailysatori.service.articleai.ArticleBodyKind
import com.dailysatori.service.parser.articleHtmlToMarkdown

/** One raw entry read from an RSS 2.0, Atom or RSS 1.0 document. */
internal data class RemoteFeedEntry(
    val title: String?,
    val link: String?,
    val guid: String?,
    val descriptionHtml: String?,
    val contentHtml: String?,
    val publishedAt: String?,
    val summaryLanguage: String? = null,
)

/** The configured address answered with something that is not a usable RSS/Atom document. */
class RemoteFeedFormatException(message: String) : IllegalArgumentException(message)

/**
 * Entry bound for read-only display paths (article recovery, connection probe), never for a sync: a
 * downloaded document is stored completely or the run fails, so a tail is never dropped silently.
 */
const val REMOTE_FEED_DISPLAY_LIMIT = 500

/** Reads the entries and the RFC 5005 head relations of one feed document. */
internal expect fun parseRemoteFeedDocument(body: String, feedUrl: String): RemoteFeedDocument

internal fun parseRemoteFeedEntries(body: String, feedUrl: String): List<RemoteFeedEntry> =
    parseRemoteFeedDocument(body, feedUrl).entries

/**
 * Maps a feed document to articles. Entries keep the feed order (newest first for standard feeds),
 * duplicate identities are collapsed and only http(s) article links are kept.
 */
internal fun parseRemoteFeedArticles(body: String, feedUrl: String, limit: Int): List<RemoteArticle> =
    remoteFeedArticles(parseRemoteFeedEntries(body, feedUrl), limit)

/**
 * Maps one downloaded document. Sync passes no entry limit (a document is stored completely within the
 * byte budget) and the real decoded size, so a document can never be silently truncated.
 */
internal fun parseRemoteFeedPage(
    body: String,
    feedUrl: String,
    pageUrl: String,
    bytes: Int = body.encodeToByteArray().size,
    limit: Int = Int.MAX_VALUE,
): RemoteFeedPage {
    val document = parseRemoteFeedDocument(body, feedUrl)
    return RemoteFeedPage(
        url = pageUrl,
        articles = remoteFeedArticles(document.entries, limit),
        title = document.title,
        links = document.links,
        isArchive = document.isArchive,
        isComplete = document.isComplete,
        bytes = bytes,
    )
}

internal fun remoteFeedArticles(entries: List<RemoteFeedEntry>, limit: Int): List<RemoteArticle> {
    if (limit <= 0) return emptyList()
    val seen = HashSet<Long>()
    val articles = ArrayList<RemoteArticle>()
    for (entry in entries) {
        val article = entry.toRemoteArticle() ?: continue
        if (!seen.add(article.id)) continue
        articles += article
        if (articles.size >= limit) break
    }
    return articles
}

/**
 * Stable identity for one feed entry: the guid when the feed provides one, otherwise its link.
 * Standard JSON feeds used numeric ids; RSS ids are whatever the publisher wrote, so they are
 * hashed to a stable positive [Long] that the sync mapping can deduplicate on.
 */
internal fun remoteFeedArticleId(guid: String?, url: String?, title: String?): Long {
    val identity = guid?.trim()?.takeIf(String::isNotEmpty)?.let { "guid:$it" }
        ?: url?.let { "url:$it" }
        ?: "title:${title.orEmpty()}"
    return sha256Hex(identity).take(ID_HEX_LENGTH).toLong(16)
}

internal fun remoteFeedHttpUrl(value: String?): String? {
    val trimmed = value?.trim().orEmpty()
    return trimmed.takeIf { it.startsWith("http://") || it.startsWith("https://") }
}

private fun RemoteFeedEntry.toRemoteArticle(): RemoteArticle? {
    val url = remoteFeedHttpUrl(link) ?: return null
    val cleanTitle = title?.trim()?.takeIf(String::isNotEmpty)
    fun markdown(html: String?) = html?.takeIf(String::isNotBlank)
        ?.let { articleHtmlToMarkdown(it, url) }?.trim()?.takeIf(String::isNotBlank)
    // The fetched document is already byte bounded. Preserve the complete parsed body here;
    // only the separate AI budget is allowed to choose and label an incomplete prefix.
    val body = markdown(contentHtml)
    val description = markdown(descriptionHtml)
    val markdown = body ?: description
    val sameText = body != null && feedComparisonText(body) == description?.let(::feedComparisonText)
    val duplicatedTeaser = sameText && isTeaser(requireNotNull(body))
    val summary = description?.takeIf { body != null && !sameText && it.length <= MAX_SUMMARY_CHARS }
    if (cleanTitle == null && markdown == null) return null
    return RemoteArticle(
        id = remoteFeedArticleId(guid, url, cleanTitle),
        title = cleanTitle,
        url = url,
        summary = summary,
        summaryLanguage = summary?.let { summaryLanguage },
        content = markdown,
        bodyKind = when {
            body != null && !duplicatedTeaser -> ArticleBodyKind.BODY
            markdown != null -> ArticleBodyKind.PREVIEW
            else -> ArticleBodyKind.MISSING
        },
        publishedAt = normalizeRemoteFeedTime(publishedAt),
    )
}

private fun feedComparisonText(value: String): String = value.replace(Regex("\\s+"), " ").trim()

private val teaserTail = Regex(
    """(?i)(?:…+|\.{3,}|(?:\[|\b)(?:read more|continue reading|阅读全文|阅读更多|查看全文)(?:[\]）】»›]|\b).*)\s*$""",
)

private fun isTeaser(value: String): Boolean =
    value.length <= 600 && teaserTail.containsMatchIn(feedComparisonText(value))

private const val MAX_SUMMARY_CHARS = 500
private const val ID_HEX_LENGTH = 15
