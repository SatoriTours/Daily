package com.dailysatori.core.storage

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts

// DocumentsUI remembers the last directory per app, not per feature. Always provide a location.
private fun defaultPickerLocation(): Uri =
    DocumentsContract.buildRootUri("com.android.externalstorage.documents", "primary")

class BackupDirectoryPicker : ActivityResultContract<String, Uri?>() {
    private val delegate = ActivityResultContracts.OpenDocumentTree()

    override fun createIntent(context: Context, input: String): Intent {
        val savedUri = input.takeIf { it.isNotBlank() }?.let(Uri::parse)
        // A tree grant URI alone is not a document URI that DocumentsUI can launch into.
        val initialUri = when {
            savedUri == null -> defaultPickerLocation()
            DocumentsContract.isTreeUri(savedUri) ->
                DocumentsContract.buildDocumentUriUsingTree(savedUri, DocumentsContract.getTreeDocumentId(savedUri))
            else -> savedUri
        }
        return delegate.createIntent(context, initialUri)
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? = delegate.parseResult(resultCode, intent)
}

/** Shared by normal and recovery log exports, without depending on the app database. */
class DiagnosticCreateDocument(context: Context) : ActivityResultContract<String, Uri?>() {
    private val delegate = ActivityResultContracts.CreateDocument("text/plain")
    private val preferences = context.applicationContext.getSharedPreferences("diagnostic_export_location", Context.MODE_PRIVATE)

    override fun createIntent(context: Context, input: String): Intent {
        val initialUri = preferences.getString("last_document_uri", null)
            ?.takeIf { it.isNotBlank() }?.let(Uri::parse) ?: defaultPickerLocation()
        return delegate.createIntent(context, input).putExtra(DocumentsContract.EXTRA_INITIAL_URI, initialUri)
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? {
        val uri = delegate.parseResult(resultCode, intent)
        // DocumentsUI resolves a file's parent directory; do not infer provider-specific URI paths.
        if (uri != null) preferences.edit().putString("last_document_uri", uri.toString()).apply()
        return uri
    }
}
