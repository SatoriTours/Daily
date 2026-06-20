package com.dailysatori.core.di

import com.dailysatori.platform.DatabaseDriverFactory
import com.dailysatori.platform.FileManager
import com.dailysatori.platform.PlatformContext
import com.dailysatori.platform.WebViewLoader
import com.dailysatori.shared.db.DailySatoriDatabase
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

val platformModule: Module = module {
    single { PlatformContext(androidContext()) }
    single { DatabaseDriverFactory(get()) }
    single { get<DatabaseDriverFactory>().createDriver() }
    single { DailySatoriDatabase(get()) }
    single { FileManager().apply { init(get<PlatformContext>().context) } }
    single { WebViewLoader() }
}
