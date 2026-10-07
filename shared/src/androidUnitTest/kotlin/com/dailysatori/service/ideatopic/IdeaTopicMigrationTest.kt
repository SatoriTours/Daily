package com.dailysatori.service.ideatopic

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.config.DatabaseConfig
import com.dailysatori.config.SettingKeys
import com.dailysatori.data.repository.SettingRepository
import com.dailysatori.service.migration.DatabaseMigration
import com.dailysatori.service.security.SecretValueCipher
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IdeaTopicMigrationTest {

    @Test
    fun oldDatabasePreservesExistingRowsAndGainsIdeaTopicStorage() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            legacySchemaWithoutIdeaTopics(driver)
            driver.execute(
                null,
                "INSERT INTO diary (content, tags, mood, images, created_at, updated_at) VALUES ('旧日记正文', 'work', NULL, NULL, 111, 111)",
                0,
            )
            driver.execute(
                null,
                "INSERT INTO article (title, url, source_type, status, created_at, updated_at) VALUES ('旧文章', 'https://example.com/legacy', 'local', 'done', 222, 222)",
                0,
            )
            driver.execute(
                null,
                "INSERT INTO chat_conversation (session_id, role, content, created_at) VALUES ('global-session', 'user', '旧聊天', 333)",
                0,
            )
            driver.execute(
                null,
                "INSERT INTO reminder (id, content, status, start_date, end_date, first_reminder_time, active_day_rule, time_zone_id, profile_json, version, dismissal_count, created_at, updated_at) VALUES ('legacy-reminder', '旧提醒', 'ACTIVE', '2026-09-01', '2026-09-01', '09:00', 'daily', 'UTC', '{}', 0, 0, 444, 444)",
                0,
            )
            val db = DailySatoriDatabase(driver)
            val settings = SettingRepository(db)
            settings.upsert(SettingKeys.schemaVersion, "32")

            DatabaseMigration(driver, settings, TestCipher).runMigrations()

            assertEquals(DatabaseConfig.currentSchemaVersion.toString(), settings.get(SettingKeys.schemaVersion))
            assertEquals(33L, DatabaseConfig.currentSchemaVersion)

            assertEquals("旧日记正文", db.dailySatoriQueries.selectDiaryById(1L).executeAsOne().content)
            assertEquals("旧聊天", db.dailySatoriQueries.selectChatBySession("global-session").executeAsList().single().content)
            assertEquals("旧提醒", db.dailySatoriQueries.selectReminderById("legacy-reminder").executeAsOne().content)

            // Idea topic storage is usable after the upgrade and starts empty.
            val repository = com.dailysatori.data.repository.IdeaTopicRepository(db)
            var seq = 0
            val service = com.dailysatori.service.ideatopic.IdeaTopicService(
                repository,
                now = { 999L },
                newId = { "mig-${++seq}" },
            )
            val created = runBlocking {
                service.capture(
                    com.dailysatori.service.ideatopic.IdeaCaptureInput(
                        source = diarySnapshot(1L, content = "旧日记正文"),
                        content = IdeaTopicContent(title = "升级后新建"),
                    ),
                )
            }
            assertEquals("升级后新建", service.getDetailSync(created.topicId)!!.topic.content.title)
            kotlin.test.assertNull(
                service.findBySourceSync(IdeaSourceKey(IdeaSourceTypes.NewsOpportunity, "none")),
            )
        } finally {
            driver.close()
        }
    }

    @Test
    fun migrationIsIdempotentWhenIdeaTablesAlreadyExist() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            DailySatoriDatabase.Schema.create(driver)
            val db = DailySatoriDatabase(driver)
            val settings = SettingRepository(db)
            settings.upsert(SettingKeys.schemaVersion, "32")

            DatabaseMigration(driver, settings, TestCipher).runMigrations()
            DatabaseMigration(driver, settings, TestCipher).runMigrations()

            assertTrue(ideaTableNames(driver).containsAll(
                listOf("idea_topic", "idea_topic_source", "idea_topic_event", "idea_topic_session", "idea_topic_message"),
            ))
            assertEquals("33", settings.get(SettingKeys.schemaVersion))
        } finally {
            driver.close()
        }
    }

    private fun legacySchemaWithoutIdeaTopics(driver: JdbcSqliteDriver) {
        DailySatoriDatabase.Schema.create(driver)
        driver.execute(null, "DROP TABLE IF EXISTS idea_topic_message", 0)
        driver.execute(null, "DROP TABLE IF EXISTS idea_topic_session", 0)
        driver.execute(null, "DROP TABLE IF EXISTS idea_topic_event", 0)
        driver.execute(null, "DROP TABLE IF EXISTS idea_topic_source", 0)
        driver.execute(null, "DROP TABLE IF EXISTS idea_topic", 0)
        val ideaTables = ideaTableNames(driver)
        assertTrue(ideaTables.isEmpty(), "legacy sample must not already have idea topic tables: $ideaTables")
    }

    private fun ideaTableNames(driver: JdbcSqliteDriver): List<String> = driver.executeQuery(
        null,
        "SELECT name FROM sqlite_master WHERE type = 'table' AND name LIKE 'idea_topic%'",
        { cursor ->
            val names = mutableListOf<String>()
            while (cursor.next().value) names += cursor.getString(0).orEmpty()
            QueryResult.Value(names)
        },
        0,
    ).value

    private object TestCipher : SecretValueCipher {
        override fun encrypt(value: String) = value
        override fun decrypt(value: String) = value
        override fun isEncrypted(value: String) = false
    }
}
