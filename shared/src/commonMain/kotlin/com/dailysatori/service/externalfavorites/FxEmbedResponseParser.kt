package com.dailysatori.service.externalfavorites

import kotlinx.datetime.Instant
import kotlinx.serialization.json.*

/** Adapts FxEmbed's public response to the same normalized draft used by the official API. */
internal fun parseFxEmbedPost(rawJson: String, expectedPostId: String): ExternalFavoriteItemDraft? {
    val root = Json.parseToJsonElement(rawJson) as? JsonObject ?: return null
    if (root.textValue("code") != "200") return null
    val tweet = root.objectValue("tweet") ?: return null
    if (tweet.textValue("id") != expectedPostId) return null
    val article = tweet.objectValue("article")
    val body = article?.let(::xArticleBody)
    if (article != null && body == null) return null
    val text = tweet.textValue("text").orEmpty()
    if (body == null && text.isBlank()) return null
    if (body == null && text.contains(Regex("""(?:x|twitter)\.com/i/article/\d+"""))) return null
    val media = fxEmbedMedia(tweet, article)
    val author = tweet.objectValue("author")
    val authorId = author?.textValue("id") ?: "fx-author"
    val response = buildJsonObject {
        put("data", buildJsonObject {
            put("id", expectedPostId)
            put("text", text)
            put("author_id", authorId)
            tweet.textValue("lang")?.let { put("lang", it) }
            tweet.textValue("created_timestamp")?.toLongOrNull()?.let { put("created_at", Instant.fromEpochSeconds(it).toString()) }
            if (article != null) put("article", buildJsonObject {
                article.textValue("id")?.let { put("id", it) }
                article.textValue("title")?.let { put("title", it) }
                (article.textValue("modified_at") ?: article.textValue("updated_at"))?.let { put("modified_at", it) }
                put("plain_text", body.orEmpty())
                article.textValue("preview_text")?.let { put("preview_text", it) }
            })
            if (tweet.textValue("is_note_tweet") == "true") put("note_tweet", buildJsonObject { put("text", text) })
            put("attachments", buildJsonObject {
                put("media_keys", JsonArray(media.mapNotNull { it["media_key"] }))
            })
        })
        put("includes", buildJsonObject {
            put("users", buildJsonArray { add(buildJsonObject {
                put("id", authorId)
                author?.textValue("screen_name")?.let { put("username", it) }
                author?.textValue("name")?.let { put("name", it) }
                author?.textValue("avatar_url")?.let { put("profile_image_url", it) }
            }) })
            put("media", JsonArray(media))
        })
    }
    return XBookmarksResponseParser.parsePostLookup(response.toString())
}

private fun fxEmbedMedia(tweet: JsonObject, article: JsonObject?): List<JsonObject> {
    val articleImages = listOfNotNull(article?.objectValue("cover_media")) + article?.arrayValue("media_entities").orEmpty()
        .mapNotNull { it as? JsonObject }
    val images = articleImages.mapNotNull { item ->
        val info = item.objectValue("media_info") ?: return@mapNotNull null
        val url = info.textValue("original_img_url")?.takeIf(::isArticleHttpUrl) ?: return@mapNotNull null
        fxMediaObject(item.textValue("media_key") ?: "3_${item.textValue("media_id") ?: url}", "photo", url, null)
    }
    val postMedia = tweet.objectValue("media")
    val photos = postMedia?.arrayValue("photos").orEmpty().mapNotNull { item ->
        val obj = item as? JsonObject ?: return@mapNotNull null
        obj.textValue("url")?.takeIf(::isArticleHttpUrl)?.let { fxMediaObject(it, "photo", it, null) }
    }
    val videos = postMedia?.arrayValue("videos").orEmpty().mapNotNull { item ->
        val obj = item as? JsonObject ?: return@mapNotNull null
        val url = obj.textValue("url")?.takeIf(::isArticleHttpUrl) ?: return@mapNotNull null
        fxMediaObject(url, "video", url, obj.textValue("thumbnail_url"))
    }
    return (images + photos + videos).distinctBy { it.textValue("media_key") }
}

private fun fxMediaObject(key: String, type: String, url: String, preview: String?): JsonObject = buildJsonObject {
    put("media_key", key)
    put("type", type)
    put("url", url)
    preview?.takeIf(::isArticleHttpUrl)?.let { put("preview_image_url", it) }
}
