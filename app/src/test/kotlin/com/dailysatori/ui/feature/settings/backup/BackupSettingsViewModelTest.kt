package com.dailysatori.ui.feature.settings.backup

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class BackupSettingsViewModelTest {
    @Test
    fun backupCannotUseOldDirectoryWhileNewSelectionIsQueuedForSaving() = runTest {
        val client = Client()
        val vm = BackupSettingsViewModel(client, backgroundScope, StandardTestDispatcher(testScheduler))
        runCurrent()

        vm.saveBackupDirectory("new", {}, {})
        // The save coroutine has not run yet, but the UI must already be busy.
        assertTrue(vm.state.value.isSavingDirectory)
        var rejected: Boolean? = null
        vm.startBackup { rejected = it }
        runCurrent()
        assertEquals(false, rejected)
        assertTrue(client.backupDirectories.isEmpty())
        assertFalse(vm.state.value.isSavingDirectory)
        assertEquals("new", vm.state.value.backupDirectory)

        vm.startBackup()
        runCurrent()
        assertEquals(listOf("new"), client.backupDirectories)
    }

    @Test
    fun backupTriggeredDuringPermissionGrantCannotWriteToOldDirectory() = runTest {
        val client = Client()
        val vm = BackupSettingsViewModel(client, backgroundScope, UnconfinedTestDispatcher(testScheduler))
        runCurrent()
        vm.saveBackupDirectory("new", { vm.startBackup() }, {})
        runCurrent()
        assertTrue(client.backupDirectories.isEmpty())
        vm.startBackup()
        runCurrent()
        assertEquals(listOf("new"), client.backupDirectories)
    }

    @Test
    fun schedulingFailureDoesNotLeaveOldDirectoryOnScreenAfterSuccessfulSave() = runTest {
        val client = Client()
        val vm = BackupSettingsViewModel(client, backgroundScope, StandardTestDispatcher(testScheduler))
        runCurrent()
        vm.saveBackupDirectory("new", {}, { error("scheduler unavailable") })
        runCurrent()

        assertEquals("new", vm.state.value.backupDirectory)
        assertNotNull(vm.state.value.message)
        assertNotNull(vm.state.value.error)
        assertFalse(vm.state.value.isSavingDirectory)
        vm.startBackup()
        runCurrent()
        assertEquals(listOf("new"), client.backupDirectories)
    }

    @Test
    fun queuedBackupIsRejectedIfDirectorySavingStartsBeforeItRuns() = runTest {
        val client = Client()
        val vm = BackupSettingsViewModel(client, backgroundScope, StandardTestDispatcher(testScheduler))
        runCurrent()
        vm.startBackup()
        vm.saveBackupDirectory("new", {}, {})
        runCurrent()
        assertTrue(client.backupDirectories.isEmpty())
        assertEquals("new", vm.state.value.backupDirectory)
    }

    @Test
    fun failedPermissionKeepsOldDirectoryAndAllowsRetry() = runTest {
        val client = Client()
        val vm = BackupSettingsViewModel(client, backgroundScope, StandardTestDispatcher(testScheduler))
        runCurrent()
        vm.saveBackupDirectory("new", { error("permission denied") }, {})
        runCurrent()
        assertEquals("old", vm.state.value.backupDirectory)
        assertNull(vm.state.value.message)
        assertNotNull(vm.state.value.error)
        assertFalse(vm.state.value.isSavingDirectory)

        vm.saveBackupDirectory("new", {}, {})
        runCurrent()
        assertEquals("new", vm.state.value.backupDirectory)
        assertNull(vm.state.value.error)
    }

    private class Client : BackupSettingsClient {
        override val isBackingUp = MutableStateFlow(false)
        override val progress = MutableStateFlow(0.0)
        var directory = "old"
        val backupDirectories = mutableListOf<String>()
        override fun loadDirectory() = directory
        override fun saveDirectory(path: String) { directory = path }
        override fun displayName(path: String) = path
        override fun hasPassword() = true
        override fun savePassword(password: String) = Unit
        override suspend fun backupNow(): Boolean {
            backupDirectories += directory
            return true
        }
    }
}
