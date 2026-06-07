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

        assertTrue(config.contains("currentSchemaVersion = 13L"))
        assertTrue(migration.contains("migrateV8ToV9()"))
        assertTrue(migration.contains("CREATE TABLE IF NOT EXISTS skill_config"))
        assertTrue(migration.contains("template_id"))
        assertTrue(migration.contains("weread"))
        assertTrue(migration.contains("weread_api_key"))
    }

    @Test
    fun databaseMigrationEncryptsLegacyWeReadToken() {
        val migration = File("src/commonMain/kotlin/com/dailysatori/service/migration/DatabaseMigration.kt").readText()
        val sharedModule = File("src/commonMain/kotlin/com/dailysatori/di/SharedModule.kt").readText()

        assertTrue(migration.contains("private val secretCipher: SecretCipher"))
        assertTrue(migration.contains("migratedWeReadTokenValue(legacyToken)"))
        assertTrue(migration.contains("secretCipher.isEncrypted"))
        assertTrue(migration.contains("secretCipher.encrypt"))
        assertTrue(sharedModule.contains("DatabaseMigration(get(), get(), get())"))
    }
}
