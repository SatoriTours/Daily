package com.dailysatori.data.repository

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.service.skill.BuiltInSkillTemplates
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class SkillConfigRepositoryBehaviorTest {
    @Test
    fun insertStoresEncryptedTokenButReadsDecrypted() = withRepository { db, repository ->
        repository.insert(
            name = "Custom",
            description = "",
            gatewayUrl = "https://example.com/gateway",
            apiToken = "plain-token",
            skillVersion = "1.0.0",
            enabled = 1,
        )

        val raw = db.dailySatoriQueries.selectAllSkillConfigs().executeAsList().single()
        val decrypted = repository.getById(raw.id)

        assertNotEquals("plain-token", raw.api_token)
        assertEquals("test-encrypted:plain-token", raw.api_token)
        assertEquals("plain-token", decrypted?.api_token)
    }

    @Test
    fun updateReEncryptsTokenButReadsDecrypted() = withRepository { db, repository ->
        repository.insert("Custom", "", "https://example.com/gateway", "first", "1.0.0", enabled = 1)
        val id = db.dailySatoriQueries.selectAllSkillConfigs().executeAsList().single().id

        repository.update(
            id = id,
            name = "Updated",
            description = "Updated description",
            gatewayUrl = "https://example.com/updated",
            apiToken = "second",
            skillVersion = "1.0.1",
            enabled = 0,
            provider = "custom",
            templateId = "custom-template",
            toolSchemaJson = "{}",
        )

        val raw = db.dailySatoriQueries.selectSkillConfigById(id).executeAsOne()
        val decrypted = repository.getById(id)

        assertEquals("test-encrypted:second", raw.api_token)
        assertEquals("second", decrypted?.api_token)
        assertEquals("Updated", decrypted?.name)
    }

    @Test
    fun builtInDeleteIsRefusedButCustomDeleteSucceeds() = withRepository { db, repository ->
        repository.insert("Built-in", "", "https://example.com/builtin", "token", "1.0.0", 1, builtin = 1)
        repository.insert("Custom", "", "https://example.com/custom", "token", "1.0.0", 1, builtin = 0)
        val rows = db.dailySatoriQueries.selectAllSkillConfigs().executeAsList()
        val builtInId = rows.single { it.builtin == 1L }.id
        val customId = rows.single { it.builtin == 0L }.id

        repository.delete(builtInId)
        repository.delete(customId)

        val remaining = db.dailySatoriQueries.selectAllSkillConfigs().executeAsList()
        assertEquals(listOf(builtInId), remaining.map { it.id })
    }

    @Test
    fun ensureBuiltInWeReadIgnoresCustomWeReadRowsAndIsIdempotent() = withRepository { db, repository ->
        repository.insert(
            name = "Custom WeRead",
            description = "",
            gatewayUrl = "https://example.com/custom-weread",
            apiToken = "custom-token",
            skillVersion = "1.0.0",
            enabled = 1,
            builtin = 0,
            templateId = BuiltInSkillTemplates.weRead,
        )

        repository.ensureBuiltInWeRead()
        repository.ensureBuiltInWeRead()

        val wereadRows = db.dailySatoriQueries.selectAllSkillConfigs().executeAsList()
            .filter { it.template_id == BuiltInSkillTemplates.weRead }
        assertEquals(2, wereadRows.size)
        assertEquals(1, wereadRows.count { it.builtin == 1L })
        assertTrue(wereadRows.any { it.builtin == 0L && it.name == "Custom WeRead" })
    }

    @Test
    fun builtInLookupIgnoresCustomRowsWithSameTemplateId() = withRepository { _, repository ->
        repository.insert(
            name = "Custom WeRead",
            description = "",
            gatewayUrl = "https://example.com/custom-weread",
            apiToken = "",
            skillVersion = "1.0.0",
            enabled = 0,
            builtin = 0,
            templateId = BuiltInSkillTemplates.weRead,
        )
        repository.insert(
            name = "Built-in WeRead",
            description = "",
            gatewayUrl = "https://example.com/builtin-weread",
            apiToken = "built-in-token",
            skillVersion = "1.0.3",
            enabled = 1,
            builtin = 1,
            templateId = BuiltInSkillTemplates.weRead,
        )

        val skill = repository.getBuiltInByTemplateId(BuiltInSkillTemplates.weRead)

        assertEquals("Built-in WeRead", skill?.name)
        assertEquals("built-in-token", skill?.api_token)
        assertEquals(1L, skill?.builtin)
        assertEquals(1L, skill?.enabled)
    }

    private fun withRepository(test: (DailySatoriDatabase, SkillConfigRepository) -> Unit) {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        DailySatoriDatabase.Schema.create(driver)
        try {
            val db = DailySatoriDatabase(driver)
            val repository = SkillConfigRepository(
                db = db,
                encryptSecret = { value -> if (value.isBlank()) value else "test-encrypted:$value" },
                decryptSecret = { value -> value.removePrefix("test-encrypted:") },
            )
            test(db, repository)
        } finally {
            driver.close()
        }
    }
}
