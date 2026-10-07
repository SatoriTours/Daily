package com.dailysatori.service.diary

import com.dailysatori.shared.db.Diary
import com.dailysatori.shared.db.Diary_attachment
import kotlinx.datetime.Instant

/** 汇总状态与数据库 `diary_thread_summary.status` 一一对应。 */
object DiaryThreadSummaryStatus {
    const val pending = "pending"
    const val running = "running"
    const val ready = "ready"
    const val failed = "failed"
}

/** 仍在进行、尚未产出真实正文的转写状态；占位正文不得进入统一原文。 */
private val PENDING_TRANSCRIPT_STATUSES = setOf("queued", "processing")

data class DiaryThreadSummary(
    val text: String,
    val sourceRevision: Long,
    val summaryRevision: Long,
    val status: String,
    val errorMessage: String?,
    val generatedAt: Long?,
)

data class DiaryThreadSnapshot(
    val root: Diary,
    val entries: List<Diary>,
    val attachments: List<Diary_attachment>,
    val revision: Long,
    val pendingAttachmentCount: Long,
    val summary: DiaryThreadSummary?,
)

data class DiaryThreadSource(
    val rootId: Long,
    val content: String,
    val createdAt: Long,
    val updatedAt: Long,
    val revision: Long,
)

data class DiaryThreadOverview(
    val rootId: Long,
    val replyCount: Long,
    val pendingAttachmentCount: Long,
    val summary: DiaryThreadSummary?,
    /** 当前原文版本；卡片据此判断旧汇总是否待更新（summary.summaryRevision != revision）。 */
    val revision: Long = 0L,
)

/** 真实正文判定：排除空的以及录音自动写入的占位文案，但保留真实转写与自动标题。 */
fun Diary.hasRenderableThreadContent(): Boolean =
    content.isNotBlank() && content.trim() != DiaryTranscriptionCoordinator.AUTO_TRANSCRIBING_BODY

fun Diary_attachment.isPendingThreadTranscription(): Boolean =
    kind == "audio" && transcript_status in PENDING_TRANSCRIPT_STATUSES

fun pendingThreadAttachmentCount(attachments: List<Diary_attachment>): Long =
    attachments.count { it.isPendingThreadTranscription() }.toLong()

/** 单个记录的统一段落：带记录类型、ID 与时间边界。 */
internal fun diaryThreadEntrySegment(entries: List<Diary>, diary: Diary, includeBody: Boolean = true): String {
    val kind = if (diary.id == entries.firstOrNull()?.id) "最初记录" else "续写"
    val header = "【$kind #${diary.id} · ${Instant.fromEpochMilliseconds(diary.created_at)}】"
    return if (includeBody) "$header\n${diary.content.trim()}" else header
}

/**
 * 统一原文：单条记录保持原样以便沿用既有指纹与展示；
 * 有续写时按记录 ID 和时间加边界，占位正文不再作为原文参与分析。
 */
fun renderDiaryThreadContent(entries: List<Diary>): String {
    if (entries.size <= 1) {
        return entries.firstOrNull()?.takeIf { it.hasRenderableThreadContent() }?.content?.trim().orEmpty()
    }
    return entries.joinToString("\n\n") { diary ->
        diaryThreadEntrySegment(entries, diary, includeBody = diary.hasRenderableThreadContent())
    }
}
