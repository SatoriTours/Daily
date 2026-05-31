package com.dailysatori.ui.feature.settings

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BackupRestoreScreenFeedbackTest {
    @Test
    fun restoreScreenShowsProgressAndResultMessages() {
        val screen = File("src/main/kotlin/com/dailysatori/ui/feature/settings/backup/BackupRestoreScreen.kt").readText()
        val viewModel = File("src/main/kotlin/com/dailysatori/ui/feature/settings/backup/BackupRestoreViewModel.kt").readText()

        assertTrue(viewModel.contains("restoreProgress"), "Restore state should expose progress")
        assertTrue(viewModel.contains("successMessage"), "Restore state should expose success message")
        assertTrue(viewModel.contains("backupService.lastMessage.collect"), "Restore ViewModel should observe service messages")
        assertTrue(screen.contains("LinearProgressIndicator"), "Restore screen should show restore progress")
        assertTrue(screen.contains("state.errorMessage"), "Restore screen should render restore errors")
        assertTrue(screen.contains("state.successMessage"), "Restore screen should render restore success")
    }

    @Test
    fun restoreScreenReloadsBackupListWhenOpened() {
        val screen = File("src/main/kotlin/com/dailysatori/ui/feature/settings/backup/BackupRestoreScreen.kt").readText()

        assertTrue(screen.contains("LaunchedEffect"), "Restore screen should refresh when opened")
        assertTrue(screen.contains("viewModel.loadBackupFiles()"), "Restore screen should reload backup files")
    }

    @Test
    fun restoreScreenDelegatesRestoreWorkToViewModel() {
        val screen = File("src/main/kotlin/com/dailysatori/ui/feature/settings/backup/BackupRestoreScreen.kt").readText()
        val viewModel = File("src/main/kotlin/com/dailysatori/ui/feature/settings/backup/BackupRestoreViewModel.kt").readText()

        assertFalse(screen.contains("rememberCoroutineScope"), "Restore screen should not own restore coroutine scope")
        assertFalse(screen.contains("scope.launch"), "Restore screen should not launch restore work")
        assertTrue(screen.contains("viewModel.restoreBackup(restorePassword)"), "Restore screen should call ViewModel event")
        assertTrue(viewModel.contains("fun restoreBackup(password: String)"), "Restore should be a ViewModel event")
        assertFalse(viewModel.contains("suspend fun restoreBackup"), "Restore API should not require Composable coroutine ownership")
    }
}
