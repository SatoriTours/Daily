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
        assertTrue(attachmentList.contains("attachments.take(2)"))
        assertTrue(attachmentList.contains("查看全部"))
    }
}
