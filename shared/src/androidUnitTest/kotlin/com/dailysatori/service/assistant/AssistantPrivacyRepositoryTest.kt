package com.dailysatori.service.assistant

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.config.DatabaseConfig
import com.dailysatori.config.SettingKeys
import com.dailysatori.data.repository.ChatConversationRepository
import com.dailysatori.data.repository.SettingRepository
import com.dailysatori.service.migration.DatabaseMigration
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlin.test.*

class AssistantPrivacyRepositoryTest {
    @Test
    fun excludingAnInputKeepsOriginalAndRemovesItsDerivedMemory() = withDatabase { driver, db ->
        val chat = ChatConversationRepository(db)
        chat.insert("one", "user", "保留这条原始输入", createdAt = 10)
        val id = chat.getBySession("one").single().id
        db.dailySatoriQueries.insertMemory("content", "chat", id, "归纳", "旧归纳", null, 10, 10)
        val privacy = AssistantPrivacyRepository(driver)
        privacy.exclude("chat", id.toString())
        privacy.exclude("chat", id.toString())
        assertTrue(privacy.isExcluded("chat", id.toString()))
        assertEquals("保留这条原始输入", chat.getBySession("one").single().content)
        assertEquals(0, db.dailySatoriQueries.selectMemoryBySource("chat", id).executeAsList().size)
        assertTrue(privacy.history("one").isEmpty())
    }

    @Test
    fun textIdsAndSessionHistoryRemainSeparate() = withDatabase { driver, db ->
        val chat = ChatConversationRepository(db)
        chat.insert("one", "user", "第一会话", createdAt = 10)
        chat.insert("two", "user", "第二会话", createdAt = 11)
        val privacy = AssistantPrivacyRepository(driver)
        privacy.exclude("reminder", "todo-uuid")
        assertTrue(privacy.isExcluded("reminder", "todo-uuid"))
        assertEquals(listOf("第一会话"), privacy.history("one").map { it.content })
    }

    @Test
    fun version35UpgradeCreatesExclusionsWithoutChangingInputsOrSecrets() = withDatabase { driver, db ->
        val chat = ChatConversationRepository(db)
        chat.insert("old-session", "user", "升级前原文", createdAt = 10)
        val settings = SettingRepository(db)
        settings.upsert("unrelated.setting", "unchanged")
        driver.execute(null, "DROP TABLE assistant_memory_exclusion", 0)
        repeat(2) {
            com.dailysatori.service.migration.DiaryLegacyFieldsFixture.install(driver)
            settings.upsert(SettingKeys.schemaVersion, "35")
            DatabaseMigration(driver, settings).runMigrations()
            AssistantPrivacyRepository(driver).exclude("chat", "999")
            assertEquals("升级前原文", chat.getBySession("old-session").single().content)
            assertEquals("unchanged", settings.get("unrelated.setting"))
            assertEquals(DatabaseConfig.currentSchemaVersion.toString(), settings.get(SettingKeys.schemaVersion))
        }
    }

    private fun withDatabase(block: (JdbcSqliteDriver, DailySatoriDatabase) -> Unit) {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use {
            DailySatoriDatabase.Schema.create(it)
            block(it, DailySatoriDatabase(it))
        }
    }
}
