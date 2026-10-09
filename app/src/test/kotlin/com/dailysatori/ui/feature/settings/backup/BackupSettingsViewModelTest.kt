package com.dailysatori.ui.feature.settings.backup

import com.dailysatori.service.backup.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class BackupSettingsViewModelTest {
    @Test
    fun verificationRequiresFreshInputAndNeverSavesOrReusesIt() = runTest {
        val client = Client()
        var checks = 0
        client.verificationBody = { password, _ ->
            assertEquals("manually entered password", password)
            checks++
            verificationResult()
        }
        val vm = BackupSettingsViewModel(client, backgroundScope, StandardTestDispatcher(testScheduler))
        runCurrent()
        vm.verifyLatestBackup()
        runCurrent()
        assertEquals(0, checks)
        assertFalse(vm.state.value.isVerifying)
        vm.updatePasswordInput("unchanged saved-password editor")
        vm.updateVerificationPassword("manually entered password")
        vm.verifyLatestBackup()
        assertEquals("", vm.state.value.verificationPasswordInput)
        runCurrent()
        assertEquals(1, checks)
        assertEquals(BackupVerificationStatus.PASSED, vm.state.value.verificationResult?.status)
        assertEquals("unchanged saved-password editor", vm.state.value.passwordInput)
        assertTrue(client.savedPasswords.isEmpty())
        vm.verifyLatestBackup()
        runCurrent()
        assertEquals(1, checks)
    }

    @Test
    fun queuedVerificationImmediatelyBlocksBackupAndDirectoryChanges() = runTest {
        val client = Client()
        val vm = BackupSettingsViewModel(client, backgroundScope, StandardTestDispatcher(testScheduler))
        runCurrent()
        vm.verifyWithPassword()
        assertTrue(vm.state.value.isVerifying)
        vm.startBackup()
        vm.saveBackupDirectory("new", {}, {})
        runCurrent()
        assertTrue(client.backupDirectories.isEmpty())
        assertEquals("old", vm.state.value.backupDirectory)
        assertEquals(BackupVerificationStatus.PASSED, vm.state.value.verificationResult?.status)
        assertFalse(vm.state.value.isVerifying)
    }

    @Test
    fun passwordRetryUsesFailedFileAndClearsTransientPasswordWithoutSavingIt() = runTest {
        val client = Client()
        client.verificationBody = { _, _ -> verificationResult(BackupVerificationStatus.FAILED, BackupVerificationStage.DECRYPTING) }
        val vm = BackupSettingsViewModel(client, backgroundScope, StandardTestDispatcher(testScheduler))
        runCurrent()
        vm.verifyWithPassword()
        runCurrent()
        client.verificationBody = { password, name ->
            assertEquals("previous password", password)
            assertEquals("latest.zip.enc", name)
            verificationResult()
        }
        vm.updateVerificationPassword("previous password")
        vm.retryVerification()
        assertEquals("", vm.state.value.verificationPasswordInput)
        runCurrent()
        assertEquals(BackupVerificationStatus.PASSED, vm.state.value.verificationResult?.status)
        assertTrue(client.savedPasswords.isEmpty())
    }

    @Test
    fun cancellationIsIncompleteAndReleasesUiBusyState() = runTest {
        val client = Client()
        client.verificationBody = { _, _ -> awaitCancellation() }
        val vm = BackupSettingsViewModel(client, backgroundScope, StandardTestDispatcher(testScheduler))
        runCurrent()
        vm.verifyWithPassword()
        runCurrent()
        vm.cancelVerification()
        runCurrent()
        assertFalse(vm.state.value.isVerifying)
        assertEquals(BackupVerificationStatus.INCOMPLETE, vm.state.value.verificationResult?.status)
        assertEquals(BackupVerificationIssue.CANCELLED, vm.state.value.verificationResult?.issue)
        vm.startBackup()
        runCurrent()
        assertEquals(listOf("old"), client.backupDirectories)
    }

    @Test
    fun unexpectedVerificationExceptionCannotLeakExceptionTextOrLeaveBusyState() = runTest {
        val client = Client()
        client.verificationBody = { _, _ -> error("private diary content") }
        val vm = BackupSettingsViewModel(client, backgroundScope, StandardTestDispatcher(testScheduler))
        runCurrent()
        vm.verifyWithPassword()
        runCurrent()
        assertFalse(vm.state.value.isVerifying)
        assertEquals(BackupVerificationStatus.FAILED, vm.state.value.verificationResult?.status)
        assertNull(vm.state.value.error)
        assertFalse(vm.state.value.verificationResult.toString().contains("private diary"))
    }

    @Test
    fun newBackupAndDirectoryChangeInvalidatePreviousVerificationResult() = runTest {
        val client = Client()
        val vm = BackupSettingsViewModel(client, backgroundScope, StandardTestDispatcher(testScheduler))
        runCurrent()
        vm.verifyWithPassword(); runCurrent()
        assertNotNull(vm.state.value.verificationResult)
        vm.startBackup(); runCurrent()
        assertNull(vm.state.value.verificationResult)
        vm.verifyWithPassword(); runCurrent()
        vm.saveBackupDirectory("new", {}, {}); runCurrent()
        assertNull(vm.state.value.verificationResult)
    }

    @Test
    fun cancellationBeforeDispatchStillReleasesBusyState() = runTest {
        val vm = BackupSettingsViewModel(Client(), backgroundScope, StandardTestDispatcher(testScheduler))
        runCurrent()
        vm.verifyWithPassword()
        vm.cancelVerification()
        runCurrent()
        assertFalse(vm.state.value.isVerifying)
        assertEquals(BackupVerificationIssue.CANCELLED, vm.state.value.verificationResult?.issue)
    }

    @Test
    fun verificationDoesNotStartWhileDirectorySelectionIsBeingSaved() = runTest {
        val vm = BackupSettingsViewModel(Client(), backgroundScope, StandardTestDispatcher(testScheduler))
        runCurrent()
        vm.saveBackupDirectory("new", {}, {})
        vm.verifyWithPassword()
        assertFalse(vm.state.value.isVerifying)
        runCurrent()
        assertNull(vm.state.value.verificationResult)
    }

    @Test
    fun queuedPasswordSavingCannotChangeThePasswordDuringVerification() = runTest {
        val client = Client()
        client.verificationBody = { _, _ -> awaitCancellation() }
        val vm = BackupSettingsViewModel(client, backgroundScope, StandardTestDispatcher(testScheduler))
        runCurrent()
        vm.updatePasswordInput("new backup password")
        vm.saveBackupPassword()
        vm.verifyWithPassword()
        runCurrent()
        assertTrue(client.savedPasswords.isEmpty())
        assertEquals("new backup password", vm.state.value.passwordInput)
        vm.cancelVerification()
        runCurrent()
    }

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

    private fun BackupSettingsViewModel.verifyWithPassword() {
        updateVerificationPassword("test verification password")
        verifyLatestBackup()
    }

    private companion object {
        fun verificationResult(status: BackupVerificationStatus = BackupVerificationStatus.PASSED,
            stage: BackupVerificationStage = BackupVerificationStage.COMPLETE) =
            BackupVerificationResult(status, stage, "2026-05-21T12:00:00Z", "latest.zip.enc")
    }

    private class Client : BackupSettingsClient {
        override val isBackingUp = MutableStateFlow(false)
        override val progress = MutableStateFlow(0.0)
        override val verificationStage = MutableStateFlow(BackupVerificationStage.SELECTING)
        override val verificationFileName = MutableStateFlow<String?>(null)
        var verificationBody: suspend (String?, String?) -> BackupVerificationResult = { _, _ -> verificationResult() }
        val savedPasswords = mutableListOf<String>()
        var directory = "old"
        val backupDirectories = mutableListOf<String>()
        override fun loadDirectory() = directory
        override fun saveDirectory(path: String) { directory = path }
        override fun displayName(path: String) = path
        override fun hasPassword() = true
        override fun savePassword(password: String) { savedPasswords += password }
        override suspend fun verifyLatestBackup(password: String?, expectedName: String?) = verificationBody(password, expectedName)
        override suspend fun backupNow(): Boolean {
            backupDirectories += directory
            return true
        }
    }
}
