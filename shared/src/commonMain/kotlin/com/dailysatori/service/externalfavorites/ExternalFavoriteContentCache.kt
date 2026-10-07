package com.dailysatori.service.externalfavorites

import com.dailysatori.shared.db.External_favorite_item
import kotlinx.serialization.json.*
import kotlinx.datetime.Clock

private const val CONTENT_CACHE_KEY = "fetched_content"

internal fun favoriteMetadata(raw: String): JsonObject =
    runCatching { Json.parseToJsonElement(raw) as? JsonObject }.getOrNull() ?: JsonObject(emptyMap())

internal fun sourceFavoriteMetadata(raw: String): JsonObject =
    JsonObject(favoriteMetadata(raw) - CONTENT_CACHE_KEY - "public_metrics")

/** Cache is local-only: retain ownership information across updates, but never reuse a different source version. */
internal fun mergeFavoriteContentCache(existing: String, incoming: String, equivalentSourceHash: String? = null): String {
    val cache = favoriteMetadata(existing).objectValue(CONTENT_CACHE_KEY) ?: return incoming
    val retained = if (equivalentSourceHash == null) cache else JsonObject(cache + ("source_hash" to JsonPrimitive(equivalentSourceHash)))
    return JsonObject(favoriteMetadata(incoming) + (CONTENT_CACHE_KEY to retained)).toString()
}

internal fun favoriteContentCacheJson(item: External_favorite_item, supplement: ExternalFavoriteSupplement): String =
    JsonObject(favoriteMetadata(item.normalized_json) + (CONTENT_CACHE_KEY to buildJsonObject {
        favoriteMetadata(item.normalized_json).objectValue(CONTENT_CACHE_KEY)?.get("refresh_only_hash")
            ?.let { put("refresh_only_hash", it) }
        put("fetched_at", Clock.System.now().toEpochMilliseconds())
        supplement.articleModifiedAt?.let { put("article_modified_at", it) }
        put("source_hash", item.content_hash)
        put("ai_hash", item.ai_input_hash)
        put("url", supplement.url)
        supplement.title?.let { put("title", it) }
        put("body", supplement.text)
        put("source_type", supplement.sourceType)
        put("article_content_complete", supplement.articleContentComplete)
    })).toString()

internal fun cachedFavoriteContent(
    item: External_favorite_item,
    allowExpired: Boolean = false,
    nowMs: Long = Clock.System.now().toEpochMilliseconds(),
): ExternalFavoriteSupplement? {
    val cache = favoriteMetadata(item.normalized_json).objectValue(CONTENT_CACHE_KEY) ?: return null
    if (cache.textValue("source_hash") != item.content_hash || cache.textValue("ai_hash") != item.ai_input_hash) return null
    val content = cache.toFavoriteContent() ?: return null
    if (xArticleNeedsBody(item.canonical_url, item.normalized_json) && !content.articleContentComplete) return null
    if (content.articleContentComplete && !allowExpired) {
        val fetchedAt = cache.textValue("fetched_at")?.toLongOrNull() ?: return null
        val age = nowMs - fetchedAt
        if (age < 0 || age >= 24 * 60 * 60 * 1_000L) return null
    }
    return content
}

internal fun favoriteContentRefreshJson(item: External_favorite_item): String {
    val root = favoriteMetadata(item.normalized_json)
    val cache = root.objectValue(CONTENT_CACHE_KEY) ?: return item.normalized_json
    val content = cache.toFavoriteContent() ?: return item.normalized_json
    val marked = JsonObject(cache + ("refresh_only_hash" to JsonPrimitive(favoriteBodyFingerprint(content.title, content.text))))
    return JsonObject(root + (CONTENT_CACHE_KEY to marked)).toString()
}

internal fun favoriteRefreshIsUnchanged(item: External_favorite_item, title: String?, body: String?): Boolean =
    body != null && favoriteMetadata(item.normalized_json).objectValue(CONTENT_CACHE_KEY)?.textValue("refresh_only_hash") ==
        favoriteBodyFingerprint(title, body)

internal fun favoriteContentWithoutRefreshFlag(raw: String): String {
    val root = favoriteMetadata(raw)
    val cache = root.objectValue(CONTENT_CACHE_KEY) ?: return raw
    return JsonObject(root + (CONTENT_CACHE_KEY to JsonObject(cache - "refresh_only_hash"))).toString()
}

private fun favoriteBodyFingerprint(title: String?, body: String): String = sha256Hex("${title.orEmpty()}\n$body")

/** Type evidence survives source revisions; an invalidated body must not turn an Article into a tweet. */
internal fun favoriteHasArticleCache(item: External_favorite_item): Boolean =
    favoriteMetadata(item.normalized_json).objectValue(CONTENT_CACHE_KEY)?.textValue("article_content_complete") == "true"

internal fun previousCachedArticleUrl(item: External_favorite_item): String? =
    if (favoriteHasArticleCache(item)) favoriteMetadata(item.normalized_json).objectValue(CONTENT_CACHE_KEY)
        ?.textValue("url")?.takeIf(::isXArticleUrl) else null

internal fun previousCachedFavoriteBody(item: External_favorite_item): String? =
    favoriteMetadata(item.normalized_json).objectValue(CONTENT_CACHE_KEY)?.textValue("body")

private fun JsonObject.toFavoriteContent(): ExternalFavoriteSupplement? {
    val url = textValue("url") ?: return null
    val body = textValue("body") ?: return null
    val source = textValue("source_type") ?: return null
    return ExternalFavoriteSupplement(url, textValue("title"), body, source, textValue("article_content_complete") == "true", textValue("article_modified_at"))
}

/** Exact metadata/link matches only; no length heuristic is allowed to replace a user's original. */
internal fun xArticleOriginalIsPlaceholder(original: String?, item: External_favorite_item): Boolean {
    if (original.isNullOrBlank()) return true
    val metadata = favoriteMetadata(item.normalized_json)
    val pieces = listOf(item.title, metadata.textValue("url_title"), metadata.textValue("url_description"),
        metadata.textValue("primary_url"), item.canonical_url)
        .mapNotNull { it?.trim()?.takeIf(String::isNotBlank) }.distinct()
    val value = original.trim()
    if (value in pieces || value == listOfNotNull(metadata.textValue("url_title"), metadata.textValue("url_description")).joinToString("\n\n")) return true
    return value.lines().all { line ->
        val text = line.trim().removePrefix("链接：").removePrefix("链接:").trim()
        text.isEmpty() || Regex("""^https?://\S+$""").matches(text)
    }
}

internal fun xArticleItemContainsOnlyMetadata(item: External_favorite_item): Boolean {
    val value = item.text.trim()
    if (value.isEmpty() || Regex("""^(?:链接[：:]\s*)?https?://\S+$""").matches(value)) return true
    val root = favoriteMetadata(item.normalized_json)
    return value in listOfNotNull(root.textValue("text"), root.textValue("note_text"), root.textValue("url_title"), root.textValue("url_description"))
}
