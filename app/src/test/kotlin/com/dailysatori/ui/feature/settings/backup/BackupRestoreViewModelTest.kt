package com.dailysatori.ui.feature.settings.backup

import com.dailysatori.service.backup.BackupEntry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class BackupRestoreViewModelTest {
    @Test
    fun legacyBackupWarningSurvivesSuccessfulPreparationAndManualRestart() = runTest {
        val client = Client()
        val vm = BackupRestoreViewModel(client, { it }, backgroundScope, StandardTestDispatcher(testScheduler))
        runCurrent(); vm.selectFile("content://chosen"); runCurrent()
        vm.restoreBackup("password"); runCurrent()
        client.lastMessage.value = "Restore ready: legacy backup; restart manually"
        client.gate.complete(true); runCurrent()

        assertTrue(vm.state.value.successMessage.contains("backup_restore.restart_manual"))
        assertTrue(vm.state.value.successMessage.contains("backup_restore.legacy_life"))
    }

    @Test
    fun manualRestartFeedbackUnlocksNavigationAndPreventsRepeatingPreparedRestore() = runTest {
        val client = Client()
        val vm = BackupRestoreViewModel(client, { it }, backgroundScope, StandardTestDispatcher(testScheduler))
        runCurrent(); vm.selectFile("content://chosen"); runCurrent()
        vm.restoreBackup("password"); runCurrent()
        client.lastMessage.value = "Restore ready: restart manually"
        client.gate.complete(true); runCurrent()
        assertEquals("backup_restore.restart_manual", vm.state.value.successMessage)
        assertFalse(vm.state.value.isRestoring)
        assertTrue(vm.state.value.isRestorePending)
        vm.selectFile("content://another"); vm.restoreBackup("password"); runCurrent()
        assertEquals(listOf("content://chosen"), client.restored)
        assertEquals("content://chosen", vm.state.value.selectedFileUri)
    }

    @Test
    fun newDeviceCanRestoreSelectedFileAndIgnoresDoubleSubmission() = runTest {
        val client = Client()
        val vm = BackupRestoreViewModel(client, { it }, backgroundScope, StandardTestDispatcher(testScheduler))
        runCurrent()
        vm.selectFile("content://chosen")
        runCurrent()
        assertEquals("chosen.zip.enc", vm.state.value.selectedFileName)
        vm.restoreBackup("password"); vm.restoreBackup("password")
        runCurrent()
        assertEquals(listOf("content://chosen"), client.restored)
        client.progress.value = 0.63
        client.lastMessage.value = "Validating backup..."
        runCurrent()
        assertEquals(0.63f, vm.state.value.restoreProgress)
        assertEquals("backup_restore.validating", vm.state.value.statusMessage)
        client.gate.complete(true)
        runCurrent()
        assertEquals("backup_restore.restart", vm.state.value.successMessage)
        assertFalse(vm.state.value.isRestoring)
        assertTrue(vm.state.value.isRestorePending)
        vm.restoreBackup("password"); runCurrent()
        assertEquals(1, client.restored.size)
    }

    @Test
    fun failureIsActionableAndAllowsRetryWithTheSameFile() = runTest {
        val client = Client()
        val vm = BackupRestoreViewModel(client, { it }, backgroundScope, StandardTestDispatcher(testScheduler))
        runCurrent(); vm.selectFile("content://chosen"); runCurrent()
        vm.restoreBackup("wrong"); runCurrent()
        client.lastMessage.value = "Restore failed: Invalid backup password or corrupted backup"
        client.gate.complete(false); runCurrent()
        assertEquals("backup_restore.password_error", vm.state.value.errorMessage)
        assertFalse(vm.state.value.isRestoring)
        assertEquals("content://chosen", vm.state.value.selectedFileUri)
    }

    @Test
    fun newestBackupIsSelectedAndChangingSelectionClearsExternalFile() = runTest {
        val client = Client(listOf("daily_satori_backup_2026-10-06-09-15-30.zip.enc", "older.enc"))
        val vm = BackupRestoreViewModel(client, { it }, backgroundScope, StandardTestDispatcher(testScheduler))
        runCurrent()
        assertEquals(0, vm.state.value.selectedBackupIndex)
        assertEquals("2026-10-06 09:15:30", vm.getBackupTime(client.names.first()))
        vm.selectFile("content://chosen"); runCurrent(); vm.selectBackupIndex(1)
        assertNull(vm.state.value.selectedFileUri)
        assertEquals(1, vm.state.value.selectedBackupIndex)
    }

    private class Client(val names: List<String> = emptyList()) : BackupRestoreClient {
        override val progress = MutableStateFlow(0.0)
        override val lastMessage = MutableStateFlow("")
        val gate = CompletableDeferred<Boolean>()
        val restored = mutableListOf<String>()
        override suspend fun listBackups() = names.map { BackupEntry(it, "", "", 0) }
        override fun displayName(uri: String) = "chosen.zip.enc"
        override suspend fun restore(name: String, password: String): Boolean { restored += name; return gate.await() }
        override suspend fun restoreFile(uri: String, password: String): Boolean { restored += uri; return gate.await() }
    }
}
