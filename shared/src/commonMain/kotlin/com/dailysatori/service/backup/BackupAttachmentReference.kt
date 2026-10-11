package com.dailysatori.service.backup

enum class BackupAttachmentSource(val table: String, val column: String, internal val ownerColumn: String? = null) {
    DIARY_ATTACHMENT("diary_attachment", "local_path", "diary_id"),
    DIARY_IMAGE("diary", "images"),
    ARTICLE_IMAGE("image", "path", "article_id"),
    ARTICLE_COVER("article", "cover_image"),
    BOOK_COVER("book", "cover_image");

    val diagnosticName: String get() = "$table.$column"
}

internal const val BackupAttachmentLogDirectory = "backup_logs"

/** Private, on-device evidence. Paths and record IDs must never enter exported diagnostic logs. */
data class BackupAttachmentReference(
    val source: BackupAttachmentSource,
    val rowId: Long,
    val recordId: Long?,
    val originalPath: String,
    val relativePath: String,
    val isImage: Boolean = source != BackupAttachmentSource.DIARY_ATTACHMENT,
)
