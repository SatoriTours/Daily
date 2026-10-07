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
}
