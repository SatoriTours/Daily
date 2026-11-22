package com.dailysatori.di

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.data.repository.ArticleRepository
import com.dailysatori.data.repository.DiaryThoughtRepository
import com.dailysatori.data.repository.DiaryThreadRepository
import com.dailysatori.service.articleai.ArticleAiQueue
import com.dailysatori.service.opportunity.NewsOpportunityContext
import com.dailysatori.service.opportunity.NewsOpportunityService
import com.dailysatori.service.remotenews.RemoteArticleSyncService
import com.dailysatori.shared.db.DailySatoriDatabase
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame

class SharedBusinessModulesTest {
    @Test
    fun sharedEntryPointResolvesBusinessSingletonsAndKeepsQueueOptional() {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            DailySatoriDatabase.Schema.create(driver)
            HttpClient(MockEngine { error("No network needed to compose the modules") }).use { client ->
                val fixtures = module {
                    single<SqlDriver> { driver }
                    single { DailySatoriDatabase(get()) }
                    single { client }
                }
                val app = koinApplication { modules(sharedModule, fixtures) }
                try {
                    val koin = app.koin
                    assertSame(koin.get<ArticleRepository>(), koin.get<ArticleRepository>())
                    assertSame(koin.get<DiaryThreadRepository>(), koin.get<DiaryThreadRepository>())
                    assertSame(koin.get<NewsOpportunityService>(), koin.get<NewsOpportunityService>())
                    assertSame(koin.get<RemoteArticleSyncService>(), koin.get<RemoteArticleSyncService>())
                    assertNull(koin.getOrNull<ArticleAiQueue>())
                    koin.get<DiaryThoughtRepository>().setUseInChat(false)
                    assertFalse(koin.get<NewsOpportunityContext>().enabled)
                } finally {
                    app.close()
                }
            }
        }
    }
}
