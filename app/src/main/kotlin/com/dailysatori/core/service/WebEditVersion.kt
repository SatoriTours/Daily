package com.dailysatori.core.service

import com.dailysatori.shared.db.Article
import com.dailysatori.shared.db.Book
import com.dailysatori.shared.db.Book_viewpoint
import com.dailysatori.shared.db.Diary_record
import java.security.MessageDigest

/** Length-delimited values distinguish nulls, empty strings and embedded separators. */
internal fun webEditVersion(vararg fields: Any?): String {
    val encoded = fields.joinToString("") { value ->
        value?.toString()?.let { "${it.length}:$it" } ?: "-1:"
    }
    return MessageDigest.getInstance("SHA-256").digest(encoded.toByteArray())
        .joinToString("") { "%02x".format(it) }
}

internal fun webVersion(a: Article): String = webEditVersion(
    a.id, a.updated_at, a.title, a.ai_title, a.ai_content, a.ai_markdown_content,
    a.original_markdown_content, a.url, a.is_favorite, a.comment, a.status,
    a.cover_image, a.cover_image_url, a.pub_date, a.source_type,
)

internal fun webVersion(d: Diary_record): String = webEditVersion(
    d.id, d.updated_at, d.content, d.tags, d.mood, d.images,
)

internal fun webVersion(b: Book): String = webEditVersion(
    b.id, b.updated_at, b.title, b.author, b.category, b.cover_image, b.introduction, b.has_update,
)

internal fun webVersion(v: Book_viewpoint): String = webEditVersion(
    v.id, v.updated_at, v.title, v.content, v.example, v.status, v.error_message,
    v.outline_json, v.source_notes,
)
