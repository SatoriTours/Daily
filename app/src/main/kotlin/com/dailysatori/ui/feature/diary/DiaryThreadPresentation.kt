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

/**
 * 校验日记保存按钮启用状态。
 * - 普通日记：必须有非空正文；
 * - 续写日记：允许非空正文或非空图片（F5：空正文照片续写）；完全全空的草稿禁止保存；
 * - 正在保存时（isSaving = true）统一禁用（F3）。
 */
fun canSaveDiaryEntry(
    isContinuation: Boolean,
    content: String,
    hasImages: Boolean,
    isSaving: Boolean = false,
): Boolean {
    if (isSaving) return false
    return if (isContinuation) {
        content.isNotBlank() || hasImages
    } else {
        content.isNotBlank()
    }
}

/**
 * 日记编辑器打开路由目标解析（F2）：
 * 根据实际日记记录的 parent_diary_id 区分主记录与续写子记录，
 * 保证续写打开时 parent_diary_id 正确解析为 continuationRootId，自身 id 为 continuationReplyId。
 */
data class DiaryEditorRouteTarget(
    val continuationRootId: Long?,
    val continuationReplyId: Long?,
    val editingDiary: Diary?,
    val isContinuation: Boolean,
)

fun resolveDiaryEditorRouteTarget(diary: Diary): DiaryEditorRouteTarget {
    val parentId = diary.parent_diary_id
    return if (parentId != null) {
        DiaryEditorRouteTarget(
            continuationRootId = parentId,
            continuationReplyId = diary.id,
            editingDiary = diary,
            isContinuation = true,
        )
    } else {
        DiaryEditorRouteTarget(
            continuationRootId = null,
            continuationReplyId = null,
            editingDiary = diary,
            isContinuation = false,
        )
    }
}

/**
 * 待执行的语音录音动作模型（F1）。
 * 保证麦克风/通知权限授予后分别正确恢复主日记录音与续写录音，避免权限通过后跑错主日记。
 */
sealed interface PendingVoiceRecordingAction {
    data object NewDiary : PendingVoiceRecordingAction
    data class Continuation(val rootId: Long) : PendingVoiceRecordingAction
}

data class VoiceRecordingResolution(
    val action: PendingVoiceRecordingAction,
    val isContinuation: Boolean,
    val targetRootId: Long?,
)

fun resolveVoiceRecordingAction(action: PendingVoiceRecordingAction): VoiceRecordingResolution {
    return when (action) {
        is PendingVoiceRecordingAction.NewDiary -> VoiceRecordingResolution(
            action = action,
            isContinuation = false,
            targetRootId = null,
        )
        is PendingVoiceRecordingAction.Continuation -> VoiceRecordingResolution(
            action = action,
            isContinuation = true,
            targetRootId = action.rootId,
        )
    }
}

/**
 * 续写保存结果解析（F3）：
 * 成功后关闭编辑器并清空 IDs；
 * 失败时保持编辑器打开，保留 rootId、replyId 及未保存输入，并展示错误。
 */
data class DiaryContinuationSaveResolution(
    val shouldClose: Boolean,
    val retainedRootId: Long?,
    val retainedReplyId: Long?,
    val errorMessage: String?,
)

fun resolveContinuationSaveResult(
    rootId: Long,
    replyId: Long?,
    savedReplyId: Long?,
    error: String? = null,
): DiaryContinuationSaveResolution {
    return if (savedReplyId != null) {
        DiaryContinuationSaveResolution(
            shouldClose = true,
            retainedRootId = null,
            retainedReplyId = null,
            errorMessage = null,
        )
    } else {
        DiaryContinuationSaveResolution(
            shouldClose = false,
            retainedRootId = rootId,
            retainedReplyId = replyId,
            errorMessage = error ?: "保存续写失败",
        )
    }
}
