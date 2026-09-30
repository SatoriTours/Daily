package com.dailysatori

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.dailysatori.core.diagnostics.DiagnosticRecoveryReader
import com.dailysatori.ui.feature.settings.diagnostics.DiagnosticRecoveryScreen
import com.dailysatori.ui.feature.settings.diagnostics.DiagnosticRecoveryViewModel
import com.dailysatori.ui.theme.*
import java.io.File

/** Launcher and manual export shortcut run independently of the normal startup pipeline. */
class DiagnosticRecoveryActivity : ComponentActivity() {
    private val viewModel by viewModels<DiagnosticRecoveryViewModel> {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = DiagnosticRecoveryViewModel(
                DiagnosticRecoveryReader(File(noBackupFilesDir, "diagnostics"), File(noBackupFilesDir, "diagnostic-recovery")),
                applicationContext, intent.action == ACTION_EXPORT,
            ) as T
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DailySatoriTheme {
                DiagnosticRecoveryScreen(viewModel) {
                    // Only a foreground launch owns this marker; background workers are not failed UI launches.
                    DiagnosticRecoveryReader.startupStarted(File(noBackupFilesDir, "diagnostics"))
                    startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
                    finish()
                }
            }
        }
    }

    companion object { const val ACTION_EXPORT = "com.dailysatori.action.EXPORT_DIAGNOSTICS" }
}
