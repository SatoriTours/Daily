package com.dailysatori.service.skill

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class SkillSchemaSourceTest {
    @Test
    fun skillConfigTableHasRequiredColumnsAndQueries() {
        val schema = File("src/commonMain/sqldelight/com/dailysatori/shared/db/DailySatori.sq").readText()

        assertTrue(schema.contains("CREATE TABLE skill_config"))
        listOf(
            "name TEXT NOT NULL",
            "description TEXT NOT NULL DEFAULT ''",
            "gateway_url TEXT NOT NULL",
            "api_token TEXT NOT NULL DEFAULT ''",
            "skill_version TEXT NOT NULL DEFAULT ''",
            "enabled INTEGER NOT NULL DEFAULT 0",
            "builtin INTEGER NOT NULL DEFAULT 0",
            "provider TEXT NOT NULL DEFAULT ''",
            "template_id TEXT NOT NULL DEFAULT ''",
            "tool_schema_json TEXT NOT NULL DEFAULT ''",
        ).forEach { assertTrue(schema.contains(it), "Missing schema fragment: $it") }
        assertTrue(schema.contains("selectAllSkillConfigs:"))
        assertTrue(schema.contains("selectSkillConfigById:"))
        assertTrue(schema.contains("selectSkillConfigByTemplateId:"))
        assertTrue(schema.contains("selectEnabledSkillConfigs:"))
        assertTrue(schema.contains("insertSkillConfig:"))
        assertTrue(schema.contains("updateSkillConfig:"))
        assertTrue(schema.contains("deleteSkillConfig:"))
    }

    @Test
    fun databaseMigrationCreatesWeReadBuiltInSkill() {
        val config = File("src/commonMain/kotlin/com/dailysatori/config/Config.kt").readText()
        val migration = File("src/commonMain/kotlin/com/dailysatori/service/migration/DatabaseMigration.kt").readText()

        assertTrue(config.contains("currentSchemaVersion ="))
        assertTrue(migration.contains("migrateV8ToV9()"))
        assertTrue(migration.contains("CREATE TABLE IF NOT EXISTS skill_config"))
        assertTrue(migration.contains("template_id"))
        assertTrue(migration.contains("weread"))
        assertTrue(migration.contains("weread_api_key"))
    }

    @Test
    fun databaseMigrationPreservesLegacyWeReadTokenWithoutFieldEncryption() {
        val driver = app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver(app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver.IN_MEMORY)
        try {
            com.dailysatori.shared.db.DailySatoriDatabase.Schema.create(driver)
            val db = com.dailysatori.shared.db.DailySatoriDatabase(driver)
            val settings = com.dailysatori.data.repository.SettingRepository(db)
            settings.upsert("schema_version", "8")
            settings.upsert("weread_api_key", "ordinary-token")
            val cipher = object : com.dailysatori.service.security.SecretValueCipher {
                override fun encrypt(value: String): String = error("must not encrypt")
                override fun decrypt(value: String): String = error("must not decrypt")
                override fun isEncrypted(value: String): Boolean = error("must not inspect")
            }
            val migration = com.dailysatori.service.migration.DatabaseMigration(driver, settings)
            migration.javaClass.getDeclaredMethod("migrateV8ToV9").apply { isAccessible = true }.invoke(migration)
            kotlin.test.assertEquals("ordinary-token", db.dailySatoriQueries.selectBuiltInSkillConfigByTemplateId("weread").executeAsOne().api_token)
        } finally { driver.close() }
    }
}
