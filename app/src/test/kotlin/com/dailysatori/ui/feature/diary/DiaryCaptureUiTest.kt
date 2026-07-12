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
        val editor = source("DiaryEditorSheet.kt")

        assertTrue(controller.contains("onPauseResume"))
        assertTrue(controller.contains("onStop"))
        assertTrue(controller.contains("onOpenDiary"))
        assertTrue(controller.contains("width(48.dp)"))
        assertTrue(screen.contains("DiaryRecordingControls"))
        assertTrue(!screen.contains("Modifier.align(Alignment.BottomCenter).padding(bottom = 88.dp)"))
        assertTrue(editor.contains("recordingState: DiaryRecordingState?"))
        assertTrue(editor.contains("DiaryRecordingControls("))
        assertTrue(screen.contains("editingDiary?.id == state.recordingState.diaryId"))
        assertTrue(!screen.contains("写点什么"))
    }

    @Test
    fun voiceCaptureRequestsRuntimePermissionsBeforeCreatingDiary() {
        val screen = source("DiaryScreen.kt")

        assertTrue(screen.contains("ActivityResultContracts.RequestMultiplePermissions"))
        assertTrue(screen.contains("Manifest.permission.RECORD_AUDIO"))
        assertTrue(screen.contains("Manifest.permission.POST_NOTIFICATIONS"))
        assertTrue(screen.contains("permissionLauncher.launch(missingPermissions.toTypedArray())"))
        assertTrue(screen.contains("if (missingPermissions.isEmpty()) startVoiceDiary()"))
        assertTrue(screen.contains("state.error?.let"))
    }

    @Test
    fun recordingStatusAndDeletionFollowRuntimeState() {
        val controller = source("DiaryRecordingController.kt")
        val screen = source("DiaryScreen.kt")
        val viewModel = source("DiaryViewModel.kt")

        assertTrue(controller.contains("DiaryRecordingState.PersistenceFailed -> \"录音已停止 · 保存失败\""))
        assertTrue(controller.contains("DiaryRecordingState.Stopping -> \"录音已停止 · 正在保存\""))
        assertTrue(screen.contains("viewModel.deleteDiary(diary.id, recordingController::stop)"))
        assertTrue(viewModel.contains("store.state.first"))
        assertTrue(viewModel.contains("等待录音停止超时，未删除日记"))
    }

    private fun source(name: String) = File("src/main/kotlin/com/dailysatori/ui/feature/diary/$name").readText()
}
