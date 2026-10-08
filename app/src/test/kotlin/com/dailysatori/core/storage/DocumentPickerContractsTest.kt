package com.dailysatori.core.storage

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.result.contract.ActivityResultContract
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = Application::class)
class DocumentPickerContractsTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val logDocument = Uri.parse("content://com.android.externalstorage.documents/document/primary%3ALogs%2Fdiagnostics.txt")
    private val backupTree = "content://com.android.externalstorage.documents/tree/primary%3ABackups"

    @Test
    fun backupPickerStartsInSavedDirectoryInsteadOfSystemRecentDirectory() {
        val intent = backupIntent(backupTree)

        assertEquals(Intent.ACTION_OPEN_DOCUMENT_TREE, intent.action)
        assertEquals(
            "content://com.android.externalstorage.documents/tree/primary%3ABackups/document/primary%3ABackups",
            initialUri(intent)?.toString(),
        )
    }

    @Test
    fun firstBackupSelectionHasExplicitLocationInsteadOfSharedRecentLocation() {
        assertEquals("content://com.android.externalstorage.documents/root/primary", initialUri(backupIntent(""))?.toString())
    }

    @Test
    fun firstLogExportHasExplicitLocationInsteadOfSharedRecentLocation() {
        val intent = logPicker().createIntent(context, "diagnostics.txt")

        assertEquals(Intent.ACTION_CREATE_DOCUMENT, intent.action)
        assertEquals("text/plain", intent.type)
        assertEquals("diagnostics.txt", intent.getStringExtra(Intent.EXTRA_TITLE))
        assertEquals("content://com.android.externalstorage.documents/root/primary", initialUri(intent)?.toString())
    }

    @Test
    fun logLocationSurvivesPickerRecreationAndIsNotUsedForBackup() {
        assertEquals(logDocument, logPicker().parseResult(Activity.RESULT_OK, Intent().setData(logDocument)))

        assertEquals(logDocument, initialUri(logPicker().createIntent(context, "next.txt")))
        assertEquals(
            "content://com.android.externalstorage.documents/tree/primary%3ABackups/document/primary%3ABackups",
            initialUri(backupIntent(backupTree))?.toString(),
        )
    }

    @Test
    fun changingBackupLocationDoesNotChangeLogLocation() {
        logPicker().parseResult(Activity.RESULT_OK, Intent().setData(logDocument))
        val backupPicker = BackupDirectoryPicker()
        val otherBackup = Uri.parse("content://com.android.externalstorage.documents/tree/primary%3AOtherBackups")
        backupPicker.createIntent(context, backupTree)
        assertEquals(otherBackup, backupPicker.parseResult(Activity.RESULT_OK, Intent().setData(otherBackup)))

        assertEquals(logDocument, initialUri(logPicker().createIntent(context, "next.txt")))
    }

    @Test
    fun cancelledExportDoesNotReplaceRememberedLogLocation() {
        logPicker().parseResult(Activity.RESULT_OK, Intent().setData(logDocument))
        assertNull(logPicker().parseResult(Activity.RESULT_CANCELED, Intent().setData(Uri.parse("content://ignored/document/file"))))
        assertNull(logPicker().parseResult(Activity.RESULT_OK, null))

        assertEquals(logDocument, initialUri(logPicker().createIntent(context, "next.txt")))
    }

    @Test
    fun subsequentLogSelectionReplacesOnlyLogLocation() {
        logPicker().parseResult(Activity.RESULT_OK, Intent().setData(logDocument))
        val other = Uri.parse("content://com.android.externalstorage.documents/document/primary%3AOtherLogs%2Fdiagnostics.txt")
        logPicker().parseResult(Activity.RESULT_OK, Intent().setData(other))

        assertEquals(other, initialUri(logPicker().createIntent(context, "next.txt")))
        assertEquals(
            "content://com.android.externalstorage.documents/tree/primary%3ABackups/document/primary%3ABackups",
            initialUri(backupIntent(backupTree))?.toString(),
        )
    }

    private fun backupIntent(savedDirectory: String): Intent =
        BackupDirectoryPicker().createIntent(context, savedDirectory)

    private fun logPicker(): ActivityResultContract<String, Uri?> = DiagnosticCreateDocument(context)

    @Suppress("DEPRECATION")
    private fun initialUri(intent: Intent): Uri? = intent.getParcelableExtra(DocumentsContract.EXTRA_INITIAL_URI)
}
