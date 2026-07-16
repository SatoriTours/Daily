package com.dailysatori.ui.feature.diary

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class DiaryFeedRegressionTest {
    @Test
    fun feedHierarchyRemainsAndAttachmentsStayBetweenBodyAndFooter() {
        val screen = File("src/main/kotlin/com/dailysatori/ui/feature/diary/DiaryScreen.kt").readText()
        val card = File("src/main/kotlin/com/dailysatori/ui/component/card/DiaryCard.kt").readText()
        val attachmentList = File("src/main/kotlin/com/dailysatori/ui/feature/diary/DiaryAttachmentList.kt").readText()

        assertTrue(screen.contains("DiaryMonthHeader("))
        assertTrue(screen.contains("DiaryDateHeader("))
        assertTrue(screen.contains("DiaryCard("))
        assertTrue(!screen.contains("待整理"))
        assertTrue(!screen.contains("已入库"))
        assertTrue(card.indexOf("DiaryBody(") < card.indexOf("DiaryAttachmentList("))
        assertTrue(card.indexOf("DiaryAttachmentList(") < card.indexOf("DiaryCardFooter("))
        assertTrue(attachmentList.contains("displayableAttachments.take(2)"))
        assertTrue(attachmentList.contains("查看全部"))
    }

    @Test
    fun voiceDiaryIsBroughtIntoViewAndRecordedAudioCanBePlayed() {
        val screen = File("src/main/kotlin/com/dailysatori/ui/feature/diary/DiaryScreen.kt").readText()
        val attachmentList = File("src/main/kotlin/com/dailysatori/ui/feature/diary/DiaryAttachmentList.kt").readText()

        assertTrue(screen.contains("animateScrollToItem(0)"))
        assertTrue(screen.contains("state.diaries.firstOrNull()?.id"))
        assertTrue(screen.contains("state.recordingState is DiaryRecordingState.Idle"))
        assertTrue(attachmentList.contains("DiaryAudioPlaybackButton"))
        assertTrue(attachmentList.contains("MediaPlayer"))
        assertTrue(attachmentList.contains("attachment.local_path.isNotBlank()"))
        assertTrue(attachmentList.contains("Slider("))
        assertTrue(attachmentList.contains("player.seekTo"))
        assertTrue(attachmentList.contains("delay(250)"))
        assertTrue(attachmentList.contains("formatPlaybackTime"))
        assertTrue(attachmentList.contains("AudioFocusRequest.Builder"))
        assertTrue(attachmentList.contains("requestAudioFocus"))
        assertTrue(attachmentList.contains("abandonAudioFocusRequest"))
        assertTrue(attachmentList.contains("player.reset()"))
        assertTrue(attachmentList.contains("error_message.startsWith(\"recording_\")"))
        assertTrue(attachmentList.contains("\"录音失败\""))
    }
}
