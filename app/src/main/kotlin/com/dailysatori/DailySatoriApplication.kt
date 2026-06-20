package com.dailysatori

import android.app.Application
import com.dailysatori.core.di.appModule
import com.dailysatori.core.di.platformModule
import com.dailysatori.core.di.viewModelModule
import com.dailysatori.di.sharedModule
import com.dailysatori.core.service.I18nInitializer
import com.dailysatori.core.service.WebServerService
import com.dailysatori.core.worker.ArticleProcessingScheduler
import com.dailysatori.core.worker.BackupScheduler
import com.dailysatori.core.worker.UnifiedNewsScheduler
import com.dailysatori.data.repository.SettingRepository
import com.dailysatori.service.i18n.I18nService
import com.dailysatori.service.migration.DatabaseMigration
import com.dailysatori.service.security.SecretFieldProcessor
import com.dailysatori.ui.feature.settings.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.java.KoinJavaComponent.get

class DailySatoriApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger()
            androidContext(this@DailySatoriApplication)
            modules(sharedModule, platformModule, appModule, viewModelModule)
        }
        get<DatabaseMigration>(DatabaseMigration::class.java).runMigrations()
        encryptStoredSecrets()
        get<ArticleProcessingScheduler>(ArticleProcessingScheduler::class.java).enqueueResume()
        BackupScheduler(this).ensureScheduled()
        UnifiedNewsScheduler(this).ensureScheduled()
        I18nInitializer.init(this, get<I18nService>(I18nService::class.java))
        if (com.dailysatori.BuildConfig.DEBUG) {
            GlobalScope.launch(Dispatchers.IO) {
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

    private fun encryptStoredSecrets() {
        try {
            get<SecretFieldProcessor>(SecretFieldProcessor::class.java).encryptPlaintextSecrets()
        } catch (_: Exception) {}
    }
}
