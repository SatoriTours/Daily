package com.dailysatori.core.di

import com.dailysatori.core.service.AppUpgradeService
import com.dailysatori.core.service.ClipboardMonitorService
import com.dailysatori.core.service.WebServerService
import com.dailysatori.core.worker.ArticleProcessingScheduler
import com.dailysatori.core.worker.ExternalFavoriteSyncScheduler
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

val appModule: Module = module {
    single { ClipboardMonitorService(androidContext()) }
    single { ArticleProcessingScheduler(androidContext()) }
    single { ExternalFavoriteSyncScheduler(androidContext()) }
    single { WebServerService(androidContext()) }
    single { AppUpgradeService(get()) }
}
