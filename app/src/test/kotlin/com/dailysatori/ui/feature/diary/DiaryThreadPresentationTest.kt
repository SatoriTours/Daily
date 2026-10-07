package com.dailysatori.ui.feature.diary

import com.dailysatori.service.diary.DiaryThreadOverview
import com.dailysatori.service.diary.DiaryThreadSummary
import com.dailysatori.service.diary.DiaryThreadSummaryStatus
import com.dailysatori.service.diary.DiaryTranscriptionCoordinator
import java.util.Locale
import java.util.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DiaryThreadPresentationTest {

    @Test
    fun staleSummaryIsClearlyMarkedWhenSummaryRevisionDiffersFromRevision() {
        // sourceRevision is 3, summaryRevision is 2, snapshot revision is 3.
        // It must compare summaryRevision != revision, NOT sourceRevision!
        val staleSummary = DiaryThreadSummary(
            text = "旧汇总正文",
            sourceRevision = 3L,
            summaryRevision = 2L,
            status = DiaryThreadSummaryStatus.ready,
            errorMessage = null,
            generatedAt = 1000L,
        )

        val state = resolveDiaryThreadSummaryUiState(
            summary = staleSummary,
            revision = 3L,
            pendingAttachmentCount = 0L,
        )

        assertTrue(state is DiaryThreadSummaryUiState.Ready)
        assertTrue(state.isStale, "旧汇总必须标记为待更新")
        assertEquals("旧汇总正文", state.text)
        assertFalse(state.hasPendingAudio)
    }

    @Test
    fun upToDateSummaryIsNotMarkedStale() {
        val freshSummary = DiaryThreadSummary(
            text = "最新汇总正文",
            sourceRevision = 3L,
            summaryRevision = 3L,
            status = DiaryThreadSummaryStatus.ready,
            errorMessage = null,
            generatedAt = 1000L,
        )

        val state = resolveDiaryThreadSummaryUiState(
            summary = freshSummary,
            revision = 3L,
            pendingAttachmentCount = 0L,
        )

        assertTrue(state is DiaryThreadSummaryUiState.Ready)
        assertFalse(state.isStale)
        assertEquals("最新汇总正文", state.text)
    }

    @Test
    fun failedSummaryKeepsOldTextAndAllowsRetry() {
        val failedSummary = DiaryThreadSummary(
            text = "之前成功的汇总",
            sourceRevision = 4L,
            summaryRevision = 2L,
            status = DiaryThreadSummaryStatus.failed,
            errorMessage = "网络超时",
            generatedAt = 1000L,
        )

        val state = resolveDiaryThreadSummaryUiState(
            summary = failedSummary,
            revision = 4L,
            pendingAttachmentCount = 0L,
        )

        assertTrue(state is DiaryThreadSummaryUiState.Failed)
        assertEquals("之前成功的汇总", state.oldText)
        assertEquals("网络超时", state.errorMessage)
        assertTrue(state.isStale)
        assertTrue(state.canRetry)
    }

    @Test
    fun failedSummaryWithoutOldTextHasNullOldText() {
        val failedSummary = DiaryThreadSummary(
            text = "",
            sourceRevision = 1L,
            summaryRevision = 0L,
            status = DiaryThreadSummaryStatus.failed,
            errorMessage = "AI 未配置",
            generatedAt = null,
        )

        val state = resolveDiaryThreadSummaryUiState(
            summary = failedSummary,
            revision = 1L,
            pendingAttachmentCount = 0L,
        )

        assertTrue(state is DiaryThreadSummaryUiState.Failed)
        assertNull(state.oldText)
        assertEquals("AI 未配置", state.errorMessage)
        assertTrue(state.canRetry)
    }

    @Test
    fun generatingSummaryKeepsOldTextAndMarksStale() {
        val runningSummary = DiaryThreadSummary(
            text = "之前已生成的汇总",
            sourceRevision = 5L,
            summaryRevision = 2L,
            status = DiaryThreadSummaryStatus.running,
            errorMessage = null,
            generatedAt = 1000L,
        )

        val state = resolveDiaryThreadSummaryUiState(
            summary = runningSummary,
            revision = 5L,
            pendingAttachmentCount = 1L,
        )

        assertTrue(state is DiaryThreadSummaryUiState.Generating)
        assertEquals("之前已生成的汇总", state.oldText)
        assertTrue(state.isStale)
        assertTrue(state.hasPendingAudio)
    }

    @Test
    fun initialPendingSummaryWithoutOldText() {
        val pendingSummary = DiaryThreadSummary(
            text = "",
            sourceRevision = 1L,
            summaryRevision = 0L,
            status = DiaryThreadSummaryStatus.pending,
            errorMessage = null,
            generatedAt = null,
        )

        val state = resolveDiaryThreadSummaryUiState(
            summary = pendingSummary,
            revision = 1L,
            pendingAttachmentCount = 0L,
        )

        assertTrue(state is DiaryThreadSummaryUiState.Generating)
        assertNull(state.oldText)
        assertFalse(state.isStale)
    }

    @Test
    fun cardBodyUsesSummaryWhenAvailableAndMarksStale() {
        val overview = DiaryThreadOverview(
            rootId = 1L,
            replyCount = 2L,
            pendingAttachmentCount = 0L,
            summary = DiaryThreadSummary(
                text = "AI 汇总故事线",
                sourceRevision = 3L,
                summaryRevision = 2L,
                status = DiaryThreadSummaryStatus.ready,
                errorMessage = null,
                generatedAt = 1000L,
            ),
            revision = 3L,
        )

        val cardPresentation = resolveDiaryCardBodyPresentation(
            originalContent = "原始第一篇日记",
            overview = overview,
        )

        assertEquals("AI 汇总故事线", cardPresentation.text)
        assertTrue(cardPresentation.isAiSummary)
        assertTrue(cardPresentation.isStale)
        assertEquals(2L, cardPresentation.replyCount)
        assertFalse(cardPresentation.hasPendingAudio)
    }

    @Test
    fun cardBodyFallsBackToOriginalWhenSummaryBlankOrNull() {
        val overview = DiaryThreadOverview(
            rootId = 1L,
            replyCount = 1L,
            pendingAttachmentCount = 1L,
            summary = null,
            revision = 2L,
        )

        val cardPresentation = resolveDiaryCardBodyPresentation(
            originalContent = "原始第一篇日记",
            overview = overview,
        )

        assertEquals("原始第一篇日记", cardPresentation.text)
        assertFalse(cardPresentation.isAiSummary)
        assertFalse(cardPresentation.isStale)
        assertEquals(1L, cardPresentation.replyCount)
        assertTrue(cardPresentation.hasPendingAudio)
    }

    @Test
    fun noRepliesDoesNotUseAiSummaryEvenIfSummaryExists() {
        val overview = DiaryThreadOverview(
            rootId = 1L,
            replyCount = 0L,
            pendingAttachmentCount = 0L,
            summary = DiaryThreadSummary(
                text = "单篇误生成的汇总",
                sourceRevision = 1L,
                summaryRevision = 1L,
                status = DiaryThreadSummaryStatus.ready,
                errorMessage = null,
                generatedAt = 1000L,
            ),
            revision = 1L,
        )

        val cardPresentation = resolveDiaryCardBodyPresentation(
            originalContent = "单篇无续写日记",
            overview = overview,
        )

        assertEquals("单篇无续写日记", cardPresentation.text)
        assertFalse(cardPresentation.isAiSummary)
        assertEquals(0L, cardPresentation.replyCount)
    }

    @Test
    fun autoTranscribingBodyIsNotTreatedAsRealContent() {
        assertEquals("", filterDisplayableDiaryContent(DiaryTranscriptionCoordinator.AUTO_TRANSCRIBING_BODY))
        assertEquals("真实用户记录", filterDisplayableDiaryContent("真实用户记录"))
    }

    @Test
    fun threadEntryTimeUsesLocalTimeZone() {
        // 2026-01-01 00:00:00 UTC = 1767225600000L
        val timestamp = 1767225600000L
        val utcFormatted = formatDiaryThreadEntryTime(
            timestamp = timestamp,
            timeZone = TimeZone.getTimeZone("UTC"),
            locale = Locale.CHINA,
        )
        val shanghaiFormatted = formatDiaryThreadEntryTime(
            timestamp = timestamp,
            timeZone = TimeZone.getTimeZone("Asia/Shanghai"),
            locale = Locale.CHINA,
        )

        assertTrue(utcFormatted.contains("00:00") || utcFormatted.contains("0:00"))
        assertTrue(shanghaiFormatted.contains("08:00") || shanghaiFormatted.contains("8:00"))
    }

    @Test
    fun canSaveDiaryEntryAllowsImagesOnlyForContinuation() {
        // Continuation mode with photos but empty text should be allowed to save (F5)
        assertTrue(
            canSaveDiaryEntry(
                isContinuation = true,
                content = "",
                hasImages = true,
                isSaving = false,
            ),
        )
        // Continuation mode with blank text and no photos must not be allowed (prevents empty history)
        assertFalse(
            canSaveDiaryEntry(
                isContinuation = true,
                content = "   ",
                hasImages = false,
                isSaving = false,
            ),
        )
        // Continuation mode while isSaving must be disabled
        assertFalse(
            canSaveDiaryEntry(
                isContinuation = true,
                content = "续写正文",
                hasImages = false,
                isSaving = true,
            ),
        )
    }

    @Test
    fun canSaveDiaryEntryRequiresContentForNormalDiary() {
        // Normal diary requires content
        assertTrue(
            canSaveDiaryEntry(
                isContinuation = false,
                content = "今天发生的事情",
                hasImages = false,
                isSaving = false,
            ),
        )
        // Normal diary does not allow empty text even if images exist
        assertFalse(
            canSaveDiaryEntry(
                isContinuation = false,
                content = "",
                hasImages = true,
                isSaving = false,
            ),
        )
    }

    @Test
    fun resolveDiaryEditorRouteTargetSeparatesRootAndChild() {
        val rootDiary = com.dailysatori.shared.db.Diary(
            id = 100L,
            content = "主日记",
            tags = "日常",
            mood = "开心",
            images = null,
            created_at = 1000L,
            updated_at = 1000L,
            parent_diary_id = null,
        )
        val childDiary = com.dailysatori.shared.db.Diary(
            id = 200L,
            content = "续写回复",
            tags = null,
            mood = null,
            images = null,
            created_at = 2000L,
            updated_at = 2000L,
            parent_diary_id = 100L,
        )

        val rootRoute = resolveDiaryEditorRouteTarget(rootDiary)
        assertFalse(rootRoute.isContinuation)
        assertNull(rootRoute.continuationRootId)
        assertNull(rootRoute.continuationReplyId)
        assertEquals(100L, rootRoute.editingDiary?.id)

        val childRoute = resolveDiaryEditorRouteTarget(childDiary)
        assertTrue(childRoute.isContinuation)
        assertEquals(expected = 100L, actual = childRoute.continuationRootId, message = "子记录的 parent_diary_id 必须正确恢复为 rootId")
        assertEquals(expected = 200L, actual = childRoute.continuationReplyId, message = "子记录的 id 必须恢复为 replyId")
        assertEquals(expected = 200L, actual = childRoute.editingDiary?.id)
    }

    @Test
    fun resolveVoiceRecordingActionPreservesIntent() {
        val newDiaryAction = PendingVoiceRecordingAction.NewDiary
        val continuationAction = PendingVoiceRecordingAction.Continuation(rootId = 555L)

        val newResolution = resolveVoiceRecordingAction(newDiaryAction)
        assertFalse(newResolution.isContinuation)
        assertNull(newResolution.targetRootId)

        val contResolution = resolveVoiceRecordingAction(continuationAction)
        assertTrue(contResolution.isContinuation)
        assertEquals(555L, contResolution.targetRootId)
    }

    @Test
    fun resolveContinuationSaveResultKeepsDraftOnFailureAndClearsOnSuccess() {
        // When save succeeds (returns non-null ID), editor should close and clear IDs
        val successResolution = resolveContinuationSaveResult(
            rootId = 100L,
            replyId = 200L,
            savedReplyId = 200L,
        )
        assertTrue(successResolution.shouldClose)
        assertNull(successResolution.retainedRootId)
        assertNull(successResolution.retainedReplyId)

        // When save fails (returns null), editor should STAY OPEN and RETAIN rootId/replyId (F3)
        val failureResolution = resolveContinuationSaveResult(
            rootId = 100L,
            replyId = 200L,
            savedReplyId = null,
            error = "网络错误或日记已删除",
        )
        assertFalse(failureResolution.shouldClose, "保存失败时不能关闭编辑器丢草稿")
        assertEquals(100L, failureResolution.retainedRootId)
        assertEquals(200L, failureResolution.retainedReplyId)
        assertEquals("网络错误或日记已删除", failureResolution.errorMessage)
    }
}
