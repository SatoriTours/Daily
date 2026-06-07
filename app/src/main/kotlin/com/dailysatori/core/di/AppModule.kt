package com.dailysatori.core.di

import com.dailysatori.core.service.AppUpgradeService
import com.dailysatori.core.service.ClipboardMonitorService
import com.dailysatori.core.service.WebServerService
import com.dailysatori.core.worker.ArticleProcessingScheduler
import com.dailysatori.core.worker.ExternalFavoriteSyncScheduler
import com.dailysatori.config.SettingKeys
import com.dailysatori.data.repository.SettingRepository
import com.dailysatori.service.externalfavorites.SharedPreferencesXOAuthSessionStore
import com.dailysatori.service.externalfavorites.XOAuthCoordinator
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

val appModule: Module = module {
    single { ClipboardMonitorService(androidContext()) }
    single { ArticleProcessingScheduler(androidContext()) }
    single { ExternalFavoriteSyncScheduler(androidContext()) }
    single { SharedPreferencesXOAuthSessionStore(androidContext()) }
    single {
        XOAuthCoordinator(
            clientId = com.dailysatori.BuildConfig.X_OAUTH_CLIENT_ID,
            redirectUri = "dailysatori://oauth/x",
            httpClient = get(),
            sourceRepo = get(),
            sessionStore = get<SharedPreferencesXOAuthSessionStore>(),
            clientIdProvider = {
                get<SettingRepository>().get(SettingKeys.xOAuthClientId)
                    ?.takeIf { it.isNotBlank() }
                    ?: com.dailysatori.BuildConfig.X_OAUTH_CLIENT_ID
            },
        )
    }
    single { WebServerService(androidContext()) }
    single { AppUpgradeService(get()) }
}
