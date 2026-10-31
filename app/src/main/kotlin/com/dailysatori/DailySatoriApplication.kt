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
import com.dailysatori.service.diagnostics.DiagnosticLog
import com.dailysatori.service.diagnostics.DiagnosticSource
import com.dailysatori.service.diagnostics.DiagnosticCode
import com.dailysatori.ui.feature.settings.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import com.dailysatori.service.diary.DiaryThoughtService
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import kotlinx.coroutines.flow.first
import com.dailysatori.core.startup.StartupCoordinator
import com.dailysatori.core.startup.StartupState
import org.koin.java.KoinJavaComponent.get

open class DailySatoriApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val startup = StartupCoordinator(applicationScope, onFailure = {
        com.dailysatori.core.diagnostics.SafeAndroidLog.e("DailySatoriStartup", "Database preparation failed", it)
    }) {
        initializeRuntime()
        DiagnosticLog.diagnostics.emit(DiagnosticCode.OPERATION_PROGRESS, DiagnosticSource.APP,
            fields = mapOf("startupStage" to "runtime_ready"))
    }
    var startupNotice: String? = null
        private set
    var startupLanguage: String? = null
        private set

    @Synchronized
    fun consumeStartupNotice(): String? = startupNotice.also { startupNotice = null }

    override fun onCreate() {
        super.onCreate()
        // The launcher/export process must remain usable even when DI, SQLite or workers crash.
        if (isDiagnosticProcess()) return
        DiagnosticRuntime.initialize(this)
        startup.start()
        applicationScope.launch {
            startup.state.first { it == StartupState.Ready }
            initializeBackgroundServices()
        }
    }

    protected open suspend fun initializeRuntime() {
        startupNotice = startupStep("database_prepare") {
            com.dailysatori.service.security.DatabaseBootstrap.prepare(
                com.dailysatori.platform.PlatformContext(this),
                onPreparationRequired = startup::requireDataPreparation,
            )
        }
        var driver: app.cash.sqldelight.db.SqlDriver? = null
        try {
            startupStep("dependency_graph") {
                startKoin {
                    androidLogger()
                    androidContext(this@DailySatoriApplication)
                    modules(sharedModule, platformModule, appModule, viewModelModule)
                }
            }
            driver = startupStep("database_open") {
                get<app.cash.sqldelight.db.SqlDriver>(app.cash.sqldelight.db.SqlDriver::class.java)
            }
            // Bootstrap already migrates/repairs and validates the installed schema, before DI.
            // Running DatabaseMigration again scans the same columns, identities and foreign keys.
            startupStep("task_recovery") {
                get<AsyncTaskScheduler>(AsyncTaskScheduler::class.java).recoverAfterProcessStart()
            }
            startupStep("translations") {
                I18nInitializer.init(this, get<I18nService>(I18nService::class.java))
                startupLanguage = get<I18nService>(I18nService::class.java).getCurrentLanguage()
            }
        } catch (failure: Exception) {
            try { driver?.close() } finally { stopKoin() }
            throw failure
        }
    }

    private suspend fun <T> startupStep(stage: String, block: suspend () -> T): T =
        DiagnosticLog.diagnostics.operation(DiagnosticSource.APP, fields = mapOf("startupStage" to stage), block = block)

    protected open fun initializeBackgroundServices() {
        initializeSchedulers()
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

    private fun initializeSchedulers() {
        runCatching { com.dailysatori.core.sms.recoverPendingSms(this) }.onFailure {
            com.dailysatori.core.diagnostics.SafeAndroidLog.w("DailySatoriStartup", "Pending SMS recovery deferred", it)
        }
        get<AsyncTaskScheduler>(AsyncTaskScheduler::class.java).recoverAndEnqueueRunnable()
        get<DiaryThoughtService>(DiaryThoughtService::class.java).start(applicationScope) {
            DiaryThoughtScheduler(this).enqueue()
        }
        get<com.dailysatori.service.diary.DiaryTagCoordinator>(com.dailysatori.service.diary.DiaryTagCoordinator::class.java)
            .start(applicationScope) { get<AsyncTaskScheduler>(AsyncTaskScheduler::class.java).enqueue(it) }
        get<com.dailysatori.service.diary.DiaryThreadSummaryCoordinator>(
            com.dailysatori.service.diary.DiaryThreadSummaryCoordinator::class.java,
        ).start(applicationScope) { get<AsyncTaskScheduler>(AsyncTaskScheduler::class.java).enqueue(it) }
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

}
