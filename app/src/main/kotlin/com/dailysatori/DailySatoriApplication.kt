package com.dailysatori

import android.app.Application
import com.dailysatori.core.di.appModule
import com.dailysatori.core.di.platformModule
import com.dailysatori.di.sharedModule
import com.dailysatori.core.di.viewModelModule
import com.dailysatori.core.service.I18nInitializer
import com.dailysatori.service.i18n.I18nService
import com.dailysatori.service.migration.DatabaseMigration
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
        I18nInitializer.init(this, get<I18nService>(I18nService::class.java))
    }
}
