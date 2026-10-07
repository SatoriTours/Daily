package com.dailysatori.service.ai

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.data.repository.SettingRepository
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class AiConversationSessionStoreTest {
    @Test
    fun conversationsReceiveOpaquePersistedIdsAndConcurrentReadsAgree() = runBlocking {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            DailySatoriDatabase.Schema.create(driver)
            val settings = SettingRepository(DailySatoriDatabase(driver))
            val store = AiConversationSessionStore(settings)
            val first = store.getOrCreate("book-reflection:42")
            assertTrue(first.matches(Regex("[0-9a-f-]{36}")))
            assertEquals(first, AiConversationSessionStore(settings).getOrCreate("book-reflection:42"))
            assertNotEquals(first, store.getOrCreate("ai-chat:42"))
            coroutineScope {
                val ids = List(5) { async { store.getOrCreate("ai-chat:new") } }.map { it.await() }
                assertEquals(1, ids.distinct().size)
            }
        }
    }
}
