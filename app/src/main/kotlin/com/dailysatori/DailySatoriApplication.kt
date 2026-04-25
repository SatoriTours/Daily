package com.dailysatori

import android.app.Application
import com.dailysatori.di.appModule
import com.dailysatori.di.platformModule
import com.dailysatori.di.sharedModule
import com.dailysatori.di.viewModelModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class DailySatoriApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger()
            androidContext(this@DailySatoriApplication)
            modules(sharedModule, platformModule, appModule, viewModelModule)
        }
    }
}
