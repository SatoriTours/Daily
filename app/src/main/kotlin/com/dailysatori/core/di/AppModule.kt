package com.dailysatori.core.di

import android.app.AlarmManager
import android.os.Build
import com.dailysatori.core.reminder.AndroidReminderNotification
import com.dailysatori.core.reminder.ExactAlarmReminderScheduler
import com.dailysatori.core.reminder.HybridReminderScheduler
import com.dailysatori.core.reminder.ReminderCoordinator
import com.dailysatori.core.reminder.ReminderDeliveryStore
import com.dailysatori.core.reminder.ReminderNotifier
import com.dailysatori.core.reminder.ReminderScheduler
import com.dailysatori.core.reminder.RepositoryReminderDeliveryStore
import com.dailysatori.core.reminder.WorkManagerReminderScheduler
import com.dailysatori.core.service.AppUpgradeService
import com.dailysatori.core.service.ClipboardMonitorService
import com.dailysatori.core.service.WebServerService
import com.dailysatori.core.task.AsyncTaskHttpLogWriter
import com.dailysatori.core.task.AsyncTaskLogStore
import com.dailysatori.core.task.BookViewpointGenerateTaskHandler
import com.dailysatori.core.task.ArticleMemoryExtractTaskHandler
import com.dailysatori.core.task.ArticlePostProcessingScheduler
import com.dailysatori.core.task.ExternalFavoriteSyncTaskHandler
import com.dailysatori.core.task.RemoteArticleReprocessTaskHandler
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
import com.dailysatori.service.diary.DiaryKnowledgeCoordinator
import com.dailysatori.service.diary.DiaryTranscriptionCoordinator
import com.dailysatori.service.externalfavorites.FavoriteSyncHttpLogger
import com.dailysatori.service.reminder.ReminderScheduleEngine
import kotlinx.datetime.Clock
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module
import java.io.File

val appModule: Module = module {
    single<Clock> { Clock.System }
    single { AsyncTaskLogStore(File(androidContext().cacheDir, "async-task-logs")) }
    single<FavoriteSyncHttpLogger> { AsyncTaskHttpLogWriter(get()) }
    single { ClipboardMonitorService(androidContext()) }
    single { AsyncTaskScheduler(androidContext()) }
    single { SaveArticleTaskHandler(get()) }
    single { ArticleMemoryExtractTaskHandler(get(), get()) }
    single { RemoteArticleReprocessTaskHandler(get(), get()) }
    single { ArticlePostProcessingScheduler(androidContext(), get()) }
    single { ExternalFavoriteSyncTaskHandler(get(), get()) }
    single { BookViewpointGenerateTaskHandler(get(), get(), get()) }
    single { RemoteArticleSyncTaskHandler(get(), get(), get(), get()) }
    single { UnifiedNewsGenerateTaskHandler(get()) }
    single {
        AsyncTaskHandlerRegistry(
            listOf(
                get<SaveArticleTaskHandler>(),
                get<ArticleMemoryExtractTaskHandler>(),
                get<RemoteArticleReprocessTaskHandler>(),
                get<ExternalFavoriteSyncTaskHandler>(),
                get<BookViewpointGenerateTaskHandler>(),
                get<RemoteArticleSyncTaskHandler>(),
                get<UnifiedNewsGenerateTaskHandler>(),
                get<DiaryTranscriptionCoordinator>(),
                get<DiaryKnowledgeCoordinator>(),
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
    single { ReminderScheduleEngine() }
    single<ReminderDeliveryStore> { RepositoryReminderDeliveryStore(get()) }
    single<ReminderScheduler> {
        val context = androidContext()
        val alarms = context.getSystemService(AlarmManager::class.java)
        HybridReminderScheduler(
            exactAllowed = { Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms() },
            exact = ExactAlarmReminderScheduler(context, alarms),
            fallback = WorkManagerReminderScheduler(context, get()),
        )
    }
    single<ReminderNotifier> { AndroidReminderNotification(androidContext()) }
    single { ReminderCoordinator(get(), get(), get(), get(), get(), androidContext()) }
}
