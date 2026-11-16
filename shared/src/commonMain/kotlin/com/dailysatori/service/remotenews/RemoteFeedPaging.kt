package com.dailysatori.service.remotenews

import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.takeFrom

/**
 * RFC 5005 link relations read from one feed document head. Relative references are already
 * resolved against the final document address (and any xml:base) by the parser.
 */
data class RemoteFeedLinks(
    val next: String? = null,
    val previous: String? = null,
    val first: String? = null,
    val last: String? = null,
    val prevArchive: String? = null,
    val nextArchive: String? = null,
    val current: String? = null,
) {
    /** Paged feed relations of RFC 5005 section 3; "prev-archive" is not part of this set. */
    val hasPaging: Boolean get() = next != null || previous != null || first != null || last != null
}

/** One fetched feed document together with the RFC 5005 relations of its head section. */
internal data class RemoteFeedDocument(
    val entries: List<RemoteFeedEntry>,
    /** Feed-level (channel/feed) title; entry titles are article titles and are never used. */
    val title: String? = null,
    val links: RemoteFeedLinks = RemoteFeedLinks(),
    val isArchive: Boolean = false,
    val isComplete: Boolean = false,
)

/** One downloaded feed document mapped to articles, ready to be stored or traversed. */
data class RemoteFeedPage(
    /** Final document address after redirects; relative links inside the document use it as base. */
    val url: String,
    val articles: List<RemoteArticle>,
    /** Feed-level title of this document, when the publisher set one. */
    val title: String? = null,
    val links: RemoteFeedLinks = RemoteFeedLinks(),
    val isArchive: Boolean = false,
    val isComplete: Boolean = false,
    /** Real decoded byte size of the downloaded document, used by the run budget. */
    val bytes: Int = 0,
)

/**
 * How the subscription is split across documents. RFC 5005 does not define a feed that mixes paged
 * and archived documents, so one document mode is chosen and then kept for the whole chain.
 */
enum class RemoteFeedPagingMode { PLAIN, PAGED, ARCHIVE }

internal fun remoteFeedPagingModeOfName(value: String?): RemoteFeedPagingMode =
    RemoteFeedPagingMode.entries.firstOrNull { it.name.equals(value?.trim(), ignoreCase = true) }
        ?: RemoteFeedPagingMode.PLAIN

internal fun remoteFeedPagingMode(page: RemoteFeedPage): RemoteFeedPagingMode = when {
    // An fh:complete document is the whole logical feed, even if it carries a stray relation.
    page.isComplete -> RemoteFeedPagingMode.PLAIN
    page.isArchive || page.links.prevArchive != null -> RemoteFeedPagingMode.ARCHIVE
    page.links.hasPaging -> RemoteFeedPagingMode.PAGED
    else -> RemoteFeedPagingMode.PLAIN
}

/**
 * The relation that leads to *less recent* documents. RFC 5005 section 4 chains archives through
 * "prev-archive"; section 3 chains pages through "next" ("first" precedes, "last" follows,
 * "previous" points back to newer documents). Following "previous"/"prev" would walk towards the
 * entries that are already synced, never into older documents.
 */
internal fun remoteFeedOlderDocumentUrl(page: RemoteFeedPage, mode: RemoteFeedPagingMode): String? = when (mode) {
    RemoteFeedPagingMode.ARCHIVE -> page.links.prevArchive
    RemoteFeedPagingMode.PAGED -> page.links.next
    RemoteFeedPagingMode.PLAIN -> null
}

/** Absolute http(s) address made of [base] plus the relative [reference], or null when unusable. */
internal fun resolveRemoteFeedUrl(base: String, reference: String): String? {
    val trimmedReference = reference.trim()
    if (trimmedReference.isEmpty() || trimmedReference.startsWith("#")) return null
    val resolved = runCatching { URLBuilder(base).takeFrom(trimmedReference).buildString() }.getOrNull()
        ?: return null
    return resolved.takeIf(::isRemoteFeedHttpUrl)
}

internal fun isRemoteFeedHttpUrl(value: String): Boolean {
    val trimmed = value.trim()
    if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) return false
    return runCatching { Url(trimmed).host.isNotEmpty() }.getOrDefault(false)
}

/** True when [url] is https and [entryUrl] is too; used to refuse silent https -> http downgrades. */
internal fun remoteFeedDowngradesTransport(url: String, entryUrl: String): Boolean =
    entryUrl.trim().startsWith("https://") && url.trim().startsWith("http://")

/**
 * Scheme/host/port of [url]; null when the address is not a usable http(s) URL. Used to decide whether
 * a followed document may receive the configured credential at all.
 */
internal fun remoteFeedOrigin(url: String): String? {
    if (!isRemoteFeedHttpUrl(url)) return null
    val parsed = runCatching { Url(url.trim()) }.getOrNull() ?: return null
    val scheme = parsed.protocol.name.lowercase()
    val port = parsed.specifiedPort.let { specified ->
        when {
            specified > 0 -> specified
            scheme == "https" -> 443
            else -> 80
        }
    }
    return "$scheme://${parsed.host.lowercase()}:$port"
}

/** True only when both addresses share scheme, host and port, so a token is never sent elsewhere. */
internal fun remoteFeedSameOrigin(first: String, second: String): Boolean {
    val firstOrigin = remoteFeedOrigin(first) ?: return false
    return firstOrigin == remoteFeedOrigin(second)
}

/**
 * Ordered overlap between the confirmed checkpoint and one page.
 *
 * [runStart] is the index in the page of the first entry of the matched run; every entry above it is
 * not covered by the confirmed region yet (gap content), everything at or below it is confirmed or
 * older than it.
 */
data class RemoteFeedOverlap(val runStart: Int, val runLength: Int)

/**
 * The single closure judgement used by the entry document, the gap scan and the blocked re-linkage.
 *
 * The confirmed checkpoint is the ordered list of the oldest entries of the entry document that was
 * current when the last increment was completely closed. A page proves that the hole is closed only if
 * it contains a contiguous, order-preserving run that ends at the checkpoint's oldest entry ("the
 * confirmed region's bottom"): the run may start anywhere inside the checkpoint (a pinned outlier above
 * the real content must not become the boundary), but it must reach the checkpoint's end, because only
 * then are the entries above it provably newer than everything already confirmed.
 *
 * A run of one entry is only accepted for a one-entry checkpoint whose match reaches the end of the page,
 * so a truncated window or an isolated old entry never degrades into single-id matching.
 */
internal fun remoteFeedOverlap(checkpoint: List<Long>, pageIds: List<Long>): RemoteFeedOverlap? {
    if (checkpoint.isEmpty() || pageIds.isEmpty()) return null
    val longestRun = minOf(checkpoint.size, pageIds.size)
    for (runLength in longestRun downTo 1) {
        val tail = checkpoint.subList(checkpoint.size - runLength, checkpoint.size)
        var index = 0
        while (index + runLength <= pageIds.size) {
            if (pageIds[index] == tail[0] && matchesRun(pageIds, index, tail)) {
                val reachesPageEnd = index + runLength == pageIds.size
                if (runLength >= 2 || (checkpoint.size == 1 && reachesPageEnd)) {
                    return RemoteFeedOverlap(runStart = index, runLength = runLength)
                }
            }
            index += 1
        }
    }
    return null
}

private fun matchesRun(pageIds: List<Long>, start: Int, run: List<Long>): Boolean {
    run.forEachIndexed { offset, id -> if (pageIds[start + offset] != id) return false }
    return true
}

/**
 * A document address a sync may follow. Cross-origin documents are allowed (some publishers host
 * archives on another host), but a transport downgrade is refused and non-http(s) references are
 * silently unusable, so a feed cannot redirect the client into a local file or a plaintext downgrade.
 */
internal fun remoteFeedFollowableUrl(value: String?, entryUrl: String): String? {
    val trimmed = value?.trim()?.takeIf(String::isNotEmpty) ?: return null
    if (!isRemoteFeedHttpUrl(trimmed)) return null
    if (remoteFeedDowngradesTransport(trimmed, entryUrl)) return null
    return trimmed
}

/** RFC 5005 relations this client understands; anything else in a head link is ignored. */
internal fun remoteFeedLinkRelation(rel: String?): String? = rel?.trim()?.lowercase()?.let { value ->
    value.takeIf {
        it in setOf("next", "prev", "previous", "first", "last", "prev-archive", "next-archive", "current")
    }
}
