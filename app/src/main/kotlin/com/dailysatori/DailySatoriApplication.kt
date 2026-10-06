package com.dailysatori

import android.app.Application
import com.dailysatori.core.diagnostics.DiagnosticRuntime
import android.os.Build
import java.io.File
import com.dailysatori.core.di.appModule
import com.dailysatori.core.di.platformModule
import com.dailysatori.core.di.viewModelModule
import com.dailysatori.di.sharedModule
import com.dailysatori.core.service.I18nInitializer
import com.dailysatori.core.service.WebServerService
import com.dailysatori.core.worker.ArticleProcessingScheduler
import com.dailysatori.core.worker.AsyncTaskScheduler
import com.dailysatori.core.worker.BackupScheduler
import com.dailysatori.core.worker.DiaryThoughtScheduler
import com.dailysatori.core.worker.ExternalFavoriteSyncScheduler
import com.dailysatori.core.worker.UnifiedNewsScheduler
import com.dailysatori.core.worker.WeeklySummaryScheduler
import com.dailysatori.data.repository.ExternalFavoriteSourceRepository
import com.dailysatori.data.repository.SettingRepository
import com.dailysatori.service.i18n.I18nService
import com.dailysatori.service.migration.DatabaseMigration
import com.dailysatori.service.security.SecretFieldProcessor
import com.dailysatori.ui.feature.settings.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import com.dailysatori.service.diary.DiaryThoughtService
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.java.KoinJavaComponent.get

class DailySatoriApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // The launcher/export process must remain usable even when DI, SQLite or workers crash.
        if (isDiagnosticProcess()) return
        DiagnosticRuntime.initialize(this)
        startKoin {
            androidLogger()
            androidContext(this@DailySatoriApplication)
            modules(sharedModule, platformModule, appModule, viewModelModule)
        }
        get<DatabaseMigration>(DatabaseMigration::class.java).runMigrations()
        encryptStoredSecrets()
        get<AsyncTaskScheduler>(AsyncTaskScheduler::class.java).recoverAfterProcessStart()
        I18nInitializer.init(this, get<I18nService>(I18nService::class.java))
        applicationScope.launch { initializeBackgroundServices() }
        if (com.dailysatori.BuildConfig.DEBUG) {
            applicationScope.launch {
                try {
                    val settingRepo = get<SettingRepository>(SettingRepository::class.java)
                    if (settingRepo.get("web_server_token") == null) {
                        settingRepo.upsert("web_server_token", SettingsViewModel.generateWebServerToken())
                    }
                    get<WebServerService>(WebServerService::class.java).start()
                } catch (_: Exception) {}
            }
        }
    }

    private fun initializeBackgroundServices() {
        get<AsyncTaskScheduler>(AsyncTaskScheduler::class.java).recoverAndEnqueueRunnable()
        get<DiaryThoughtService>(DiaryThoughtService::class.java).start(applicationScope) {
            DiaryThoughtScheduler(this).enqueue()
        }
        get<com.dailysatori.service.diary.DiaryTagCoordinator>(com.dailysatori.service.diary.DiaryTagCoordinator::class.java)
            .start(applicationScope) { get<AsyncTaskScheduler>(AsyncTaskScheduler::class.java).enqueue(it) }
        get<ExternalFavoriteSyncScheduler>(ExternalFavoriteSyncScheduler::class.java).recover()
        get<ArticleProcessingScheduler>(ArticleProcessingScheduler::class.java).enqueueResume()
        BackupScheduler(this).ensureScheduled()
        UnifiedNewsScheduler(this).ensureScheduled()
        get<ExternalFavoriteSyncScheduler>(ExternalFavoriteSyncScheduler::class.java).enqueuePeriodic(
            get<ExternalFavoriteSourceRepository>(ExternalFavoriteSourceRepository::class.java).getEnabled(),
        )
        WeeklySummaryScheduler(this).ensureScheduled()
    }

    private fun isDiagnosticProcess(): Boolean {
        val processName = if (Build.VERSION.SDK_INT >= 28) getProcessName() else
            File("/proc/self/cmdline").inputStream().use { stream ->
                String(stream.readBytes(), Charsets.UTF_8).trimEnd('\u0000')
            }
        return processName == "$packageName:diagnostics"
    }

    private fun encryptStoredSecrets() {
        try {
            get<SecretFieldProcessor>(SecretFieldProcessor::class.java).encryptPlaintextSecrets()
        } catch (_: Exception) {}
    }
}
