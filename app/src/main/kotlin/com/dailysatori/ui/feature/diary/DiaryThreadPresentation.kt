package com.dailysatori.ui.feature.diary

import com.dailysatori.service.diary.DiaryThreadOverview
import com.dailysatori.service.diary.DiaryThreadSummary
import com.dailysatori.service.diary.DiaryThreadSummaryStatus
import com.dailysatori.service.diary.DiaryTranscriptionCoordinator
import com.dailysatori.shared.db.Diary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * 日记串 AI 汇总的展示状态模型。
 * 纯逻辑与展示文案判定，可在 JVM 单元测试中完整覆盖。
 */
sealed interface DiaryThreadSummaryUiState {
    data object None : DiaryThreadSummaryUiState

    data class Generating(
        val oldText: String? = null,
        val isStale: Boolean = false,
        val hasPendingAudio: Boolean = false,
    ) : DiaryThreadSummaryUiState

    data class Failed(
        val errorMessage: String? = null,
        val oldText: String? = null,
        val isStale: Boolean = false,
        val canRetry: Boolean = true,
        val hasPendingAudio: Boolean = false,
    ) : DiaryThreadSummaryUiState

    data class Ready(
        val text: String,
        val isStale: Boolean = false,
        val hasPendingAudio: Boolean = false,
    ) : DiaryThreadSummaryUiState
}

/**
 * 根据快照汇总、当前原文版本以及待转写录音数解析汇总展示状态。
 *
 * 核心规则：
 * 1. 判断旧汇总必须是 summary.text.isNotBlank() && summary.summaryRevision != revision。
 *    不能使用 sourceRevision，因为 markSummaryState 会把 sourceRevision 更新为当前请求版本而保留旧 summaryRevision。
 * 2. summary.text 为空表示从未生成成功；failed/running/pending 时若有旧 text 则保留展示并标待更新。
 * 3. pendingAttachmentCount > 0 时标明有录音待转写。
 */
fun resolveDiaryThreadSummaryUiState(
    summary: DiaryThreadSummary?,
    revision: Long,
    pendingAttachmentCount: Long,
): DiaryThreadSummaryUiState {
    val hasOldText = summary != null && summary.text.isNotBlank()
    val isStale = hasOldText && summary.summaryRevision != revision
    val hasPendingAudio = pendingAttachmentCount > 0
    val oldText = summary?.text?.takeIf { it.isNotBlank() }

    return when {
        summary == null -> DiaryThreadSummaryUiState.Generating(
            oldText = null,
            isStale = false,
            hasPendingAudio = hasPendingAudio,
        )
        summary.status == DiaryThreadSummaryStatus.failed -> DiaryThreadSummaryUiState.Failed(
            errorMessage = summary.errorMessage,
            oldText = oldText,
            isStale = isStale,
            canRetry = true,
            hasPendingAudio = hasPendingAudio,
        )
        summary.status in listOf(DiaryThreadSummaryStatus.pending, DiaryThreadSummaryStatus.running) -> DiaryThreadSummaryUiState.Generating(
            oldText = oldText,
            isStale = isStale,
            hasPendingAudio = hasPendingAudio,
        )
        summary.status == DiaryThreadSummaryStatus.ready || hasOldText -> DiaryThreadSummaryUiState.Ready(
            text = summary.text,
            isStale = isStale,
            hasPendingAudio = hasPendingAudio,
        )
        else -> DiaryThreadSummaryUiState.None
    }
}

/**
 * 卡片正文展示数据：当存在续写且已有成功 AI 汇总时可优先预览汇总，
 * 否则回退到原始正文。
 */
data class DiaryCardBodyPresentation(
    val text: String,
    val isAiSummary: Boolean,
    val isStale: Boolean,
    val replyCount: Long,
    val hasPendingAudio: Boolean,
)

/**
 * 计算日记卡片正文展示。
 * 只有在 replyCount > 0 且存在非空 summary 时才展示 AI 汇总。
 * 校验过期使用 summary.summaryRevision != overview.revision。
 */
fun resolveDiaryCardBodyPresentation(
    originalContent: String,
    overview: DiaryThreadOverview?,
): DiaryCardBodyPresentation {
    if (overview == null || overview.replyCount <= 0L) {
        return DiaryCardBodyPresentation(
            text = originalContent,
            isAiSummary = false,
            isStale = false,
            replyCount = 0L,
            hasPendingAudio = false,
        )
    }
    val summary = overview.summary
    val hasValidSummary = summary != null && summary.text.isNotBlank()
    return if (hasValidSummary) {
        DiaryCardBodyPresentation(
            text = summary!!.text,
            isAiSummary = true,
            isStale = summary.summaryRevision != overview.revision,
            replyCount = overview.replyCount,
            hasPendingAudio = overview.pendingAttachmentCount > 0,
        )
    } else {
        DiaryCardBodyPresentation(
            text = originalContent,
            isAiSummary = false,
            isStale = false,
            replyCount = overview.replyCount,
            hasPendingAudio = overview.pendingAttachmentCount > 0,
        )
    }
}

/**
 * 过滤不可当作真实正文展示的自动录音占位文本。
 */
fun filterDisplayableDiaryContent(content: String): String {
    return if (content == DiaryTranscriptionCoordinator.AUTO_TRANSCRIBING_BODY) {
        ""
    } else {
        content
    }
}

/**
 * 本地时间格式化：确保标题与记录时间使用本地时区展示，而非直接使用 UTC 字符串。
 */
fun formatDiaryThreadEntryTime(
    timestamp: Long,
    timeZone: TimeZone = TimeZone.getDefault(),
    locale: Locale = Locale.getDefault(),
): String {
    val format = SimpleDateFormat("yyyy-MM-dd HH:mm", locale)
    format.timeZone = timeZone
    return format.format(Date(timestamp))
}

/**
 * 续写编辑器初始正文解析：
 * 绝不能将 AI 汇总带入作为正文！
 */
fun resolveContinuationEditorInitialContent(
    continuationRootId: Long?,
    existingReply: Diary?,
    summary: DiaryThreadSummary?,
): String {
    if (continuationRootId != null) {
        return existingReply?.content?.let { filterDisplayableDiaryContent(it) }.orEmpty()
    }
    return existingReply?.content.orEmpty()
}

/**
 * 续写模式下隐藏标签编辑，避免子记录清空或覆盖主日记标签。
 */
fun shouldShowTagsInEditor(continuationRootId: Long?): Boolean {
    return continuationRootId == null
}

/**
 * 编辑器顶部标题与日期文本解析。
 */
fun diaryEditorHeaderTitle(
    existingDiary: Diary?,
    continuationRootId: Long?,
    locale: Locale = Locale.getDefault(),
    timeZone: TimeZone = TimeZone.getDefault(),
): String {
    if (continuationRootId != null) {
        return if (locale.language.startsWith("zh")) "续写日记" else "Continue Diary"
    }
    val date = Date(existingDiary?.created_at ?: System.currentTimeMillis())
    val format = SimpleDateFormat("M月d日 EEEE", locale)
    format.timeZone = timeZone
    return format.format(date)
}
