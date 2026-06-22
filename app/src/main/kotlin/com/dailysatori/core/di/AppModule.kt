package com.dailysatori.core.di

import com.dailysatori.core.service.AppUpgradeService
import com.dailysatori.core.service.ClipboardMonitorService
import com.dailysatori.core.service.WebServerService
import com.dailysatori.core.task.BookViewpointGenerateTaskHandler
import com.dailysatori.core.task.ExternalFavoriteSyncTaskHandler
import com.dailysatori.core.task.SaveArticleTaskHandler
import com.dailysatori.core.task.RemoteArticleSyncTaskHandler
import com.dailysatori.core.task.UnifiedNewsGenerateTaskHandler
import com.dailysatori.core.worker.ArticleProcessingScheduler
import com.dailysatori.core.worker.AsyncTaskScheduler
import com.dailysatori.core.worker.ExternalFavoriteSyncScheduler
import com.dailysatori.config.SettingKeys
import com.dailysatori.data.repository.SettingRepository
import com.dailysatori.service.externalfavorites.SharedPreferencesXOAuthSessionStore
import com.dailysatori.service.externalfavorites.XOAuthCoordinator
import com.dailysatori.service.asynctask.AsyncTaskHandlerRegistry
import kotlinx.datetime.Clock
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

val appModule: Module = module {
    single<Clock> { Clock.System }
    single { ClipboardMonitorService(androidContext()) }
    single { AsyncTaskScheduler(androidContext()) }
    single { SaveArticleTaskHandler(get()) }
    single { ExternalFavoriteSyncTaskHandler(get(), get()) }
    single { BookViewpointGenerateTaskHandler(get(), get(), get()) }
    single { RemoteArticleSyncTaskHandler(get(), get(), get(), get()) }
    single { UnifiedNewsGenerateTaskHandler(get(), androidContext()) }
    single {
        AsyncTaskHandlerRegistry(
            listOf(
                get<SaveArticleTaskHandler>(),
                get<ExternalFavoriteSyncTaskHandler>(),
                get<BookViewpointGenerateTaskHandler>(),
                get<RemoteArticleSyncTaskHandler>(),
                get<UnifiedNewsGenerateTaskHandler>(),
            ),
        )
    }
    single { ArticleProcessingScheduler(androidContext(), get(), get()) }
    single { ExternalFavoriteSyncScheduler(androidContext(), get(), get()) }
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
