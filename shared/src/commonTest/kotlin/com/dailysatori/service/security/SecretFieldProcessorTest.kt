package com.dailysatori.service.security

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.config.SettingKeys
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SecretFieldProcessorTest {
    @Test
    fun registryContainsAllPersistedSecretFields() {
        val fields = SecretFieldRegistry.fields.map { "${it.table}.${it.column}:${it.whereClause.orEmpty()}" }

        assertTrue("ai_config.api_token:" in fields)
        assertTrue("mcp_server.api_key:" in fields)
        assertTrue("remote_news_source.api_token:" in fields)
        assertTrue("external_favorite_source.auth_json:" in fields)
        assertTrue("skill_config.api_token:" in fields)
        assertTrue("setting.value:key = '${SettingKeys.weReadApiKey}'" in fields)
    }

    @Test
    fun encryptPlaintextSecretsEncryptsEveryRegisteredField() {
        val fixture = secretFixture()
        fixture.insertPlaintextRows()

        val result = fixture.processor.encryptPlaintextSecrets()

        assertEquals(6, result.updated)
        fixture.allSecretValues().forEach { value ->
            assertTrue(value.startsWith(SecretCipherPrefix), "Expected encrypted value, got $value")
        }
    }

    @Test
    fun decryptSecretsForBackupTurnsEncryptedFieldsIntoPlaintextInTargetDatabase() {
        val fixture = secretFixture()
        fixture.insertPlaintextRows()
        fixture.processor.encryptPlaintextSecrets()

        val result = fixture.processor.decryptSecretsForBackup()

        assertEquals(6, result.updated)
        assertEquals(
            listOf("ai-token", "mcp-key", "remote-token", """{"access_token":"x"}""", "skill-token", "weread-key"),
            fixture.allSecretValues(),
        )
    }

    @Test
    fun prepareRestoredSecretsClearsUnrecoverableCiphertextAndEncryptsPlaintext() {
        val fixture = secretFixture(
            cipher = TestSecretCipher(
                canDecrypt = { value -> !value.contains("old-device") },
            ),
        )
        fixture.insertPlaintextRows()
        fixture.updateSecretValue("ai_config", "api_token", "enc:v1:old-device-ai")
        fixture.updateSecretValue("mcp_server", "api_key", "plain-mcp-after-restore")

        val result = fixture.processor.prepareRestoredSecrets()

        assertEquals(1, result.cleared)
        assertEquals(5, result.encrypted)
        assertEquals("", fixture.value("ai_config", "api_token"))
        assertTrue(fixture.value("mcp_server", "api_key").startsWith(SecretCipherPrefix))
        assertFalse(fixture.value("mcp_server", "api_key").contains("plain-mcp-after-restore"))
    }

    private fun secretFixture(cipher: TestSecretCipher = TestSecretCipher()): SecretFixture {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        DailySatoriDatabase.Schema.create(driver)
        val db = DailySatoriDatabase(driver)
        return SecretFixture(driver, db, SecretFieldProcessor(driver, cipher))
    }
}

private class SecretFixture(
    private val driver: JdbcSqliteDriver,
    private val db: DailySatoriDatabase,
    val processor: SecretFieldProcessor,
) {
    fun insertPlaintextRows() {
        val now = 1L
        db.dailySatoriQueries.insertAiConfig("openai", "https://ai", "ai-token", "model", 1, now, now)
        db.dailySatoriQueries.insertMcpServer("mcp", "https://mcp", "mcp-key", 1, now, now)
        db.dailySatoriQueries.insertRemoteNewsSource("remote", "https://remote", "remote-token", 1, now, now)
        db.dailySatoriQueries.insertExternalFavoriteSource(
            "x",
            "X",
            "account-id",
            "account",
            1,
            720,
            "idle",
            """{"access_token":"x"}""",
            "",
            "",
            now,
            now,
        )
        db.dailySatoriQueries.insertSkillConfig(
            "skill",
            "",
            "https://skill",
            "skill-token",
            "1",
            1,
            0,
            "",
            "",
            "",
            now,
            now,
        )
        db.dailySatoriQueries.upsertSetting(SettingKeys.weReadApiKey, "weread-key", now, now)
    }

    fun allSecretValues(): List<String> = listOf(
        value("ai_config", "api_token"),
        value("mcp_server", "api_key"),
        value("remote_news_source", "api_token"),
        value("external_favorite_source", "auth_json"),
        value("skill_config", "api_token"),
        value("setting", "value", "key = '${SettingKeys.weReadApiKey}'"),
    )

    fun value(table: String, column: String, whereClause: String = "1 = 1"): String =
        driver.executeQuery(0, "SELECT $column FROM $table WHERE $whereClause LIMIT 1", { cursor ->
            cursor.next()
            app.cash.sqldelight.db.QueryResult.Value(cursor.getString(0).orEmpty())
        }, 0).value

    fun updateSecretValue(table: String, column: String, value: String) {
        driver.execute(null, "UPDATE $table SET $column = '${value.replace("'", "''")}'", 0, null)
    }
}

private class TestSecretCipher(
    private val canDecrypt: (String) -> Boolean = { true },
) : SecretValueCipher {
    override fun encrypt(value: String): String =
        if (value.isBlank() || isEncrypted(value)) value else SecretCipherPrefix + value.reversed()

    override fun decrypt(value: String): String =
        if (isEncrypted(value) && canDecrypt(value)) value.removePrefix(SecretCipherPrefix).reversed() else value

    override fun isEncrypted(value: String): Boolean = value.startsWith(SecretCipherPrefix)
}
