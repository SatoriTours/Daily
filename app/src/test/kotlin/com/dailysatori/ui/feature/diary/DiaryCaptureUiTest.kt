package com.dailysatori.ui.feature.diary

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DiaryCaptureUiTest {
    @Test
    fun captureMenuKeepsFourOrderedCompactActionsAndAddButtonSizing() {
        val menu = source("DiaryCaptureMenu.kt")
        val screen = source("DiaryScreen.kt")
        val labels = listOf("语音日记", "文字日记", "拍摄", "添加文件")

        assertEquals(labels, labels.sortedBy { menu.indexOf("\"$it\"") })
        labels.forEach { assertTrue(menu.contains("\"$it\"")) }
        assertTrue(menu.contains("heightIn(min = 44.dp)"))
        assertTrue(menu.contains("onDismissRequest"))
        assertTrue(screen.contains(".size(48.dp)"))
        assertTrue(screen.contains("Modifier.size(36.dp)"))
    }

    @Test
    fun recordingUiProvidesStatusAndCompactControlsWithoutComposer() {
        val controller = source("DiaryRecordingController.kt")
        val screen = source("DiaryScreen.kt")

        assertTrue(controller.contains("onPauseResume"))
        assertTrue(controller.contains("onStop"))
        assertTrue(controller.contains("onOpenDiary"))
        assertTrue(controller.contains("width(56.dp)"))
        assertTrue(screen.contains("RecordingStatusStrip"))
        assertTrue(screen.contains("DiaryRecordingControls"))
        assertTrue(!screen.contains("写点什么"))
    }

    private fun source(name: String) = File("src/main/kotlin/com/dailysatori/ui/feature/diary/$name").readText()
}
