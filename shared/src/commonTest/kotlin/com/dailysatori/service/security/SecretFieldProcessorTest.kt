package com.dailysatori.service.security

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.config.SettingKeys
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class SecretFieldProcessorTest {
    @Test fun legacyConversionIsStrictAtomicAndSkipsMissingColumns() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            driver.execute(null, "CREATE TABLE old(value TEXT)", 0)
            driver.execute(null, "INSERT INTO old VALUES ('enc:v1:good'), ('enc:v1:lost')", 0)
            val cipher = object : SecretValueCipher {
                override fun encrypt(value: String) = error("must not re-encrypt")
                override fun isEncrypted(value: String) = value.startsWith("enc:v1:")
                override fun decrypt(value: String) = if (value.endsWith("good")) "plain'good" else value
            }
            val processor = SecretFieldProcessor(driver, cipher, listOf(SecretFieldSpec("old", "missing"), SecretFieldSpec("old", "value")))
            assertFailsWith<IllegalStateException> { processor.decryptLegacyFields() }
            val value = driver.executeQuery(null, "SELECT value FROM old LIMIT 1", { c -> c.next(); app.cash.sqldelight.db.QueryResult.Value(c.getString(0)) }, 0).value
            assertEquals("enc:v1:good", value)
            driver.execute(null, "DELETE FROM old WHERE value='enc:v1:lost'", 0)
            assertEquals(1, processor.decryptLegacyFields().updated)
        } finally { driver.close() }
    }
    @Test
    fun backupProcessesDistinctSecretsWithAndroidStyleStatementCaching() {
        val database = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            DailySatoriDatabase.Schema.create(database)
            val q = DailySatoriDatabase(database).dailySatoriQueries
            q.insertAiConfig("openai", "https://ai", "enc:v1:nekot-ia", "model", 1, 1, 1)
            q.insertMcpServer("mcp", "https://mcp", "enc:v1:yek-pcm", 1, 1, 1)
            q.upsertSetting(SettingKeys.weReadApiKey, "enc:v1:yek-daerew", 1, 1)
            q.upsertSetting(SettingKeys.speechConfig, "enc:v1:hceeps", 1, 1)

            val processor = SecretFieldProcessor(IdentifierCachingDriver(database), TestSecretCipher())
            processor.decryptSecretsForBackup()

            assertEquals("ai-token", q.selectAllAiConfigs().executeAsOne().api_token)
            assertEquals("mcp-key", q.selectAllMcpServers().executeAsOne().api_key)
            assertEquals("weread-key", q.selectSettingByKey(SettingKeys.weReadApiKey).executeAsOne().value_)
            assertEquals("speech", q.selectSettingByKey(SettingKeys.speechConfig).executeAsOne().value_)
            processor.prepareRestoredSecrets(strict = true)
            assertEquals("enc:v1:nekot-ia", q.selectAllAiConfigs().executeAsOne().api_token)
            assertEquals("enc:v1:yek-pcm", q.selectAllMcpServers().executeAsOne().api_key)
            assertEquals("enc:v1:yek-daerew", q.selectSettingByKey(SettingKeys.weReadApiKey).executeAsOne().value_)
            assertEquals("enc:v1:hceeps", q.selectSettingByKey(SettingKeys.speechConfig).executeAsOne().value_)
        } finally { database.close() }
    }

    @Test
    fun backupRejectsUnrecoverableSecretsInsteadOfProducingAnIncompleteBackup() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            DailySatoriDatabase.Schema.create(driver)
            driver.execute(null, "INSERT INTO setting(key, value, created_at, updated_at) VALUES ('weread_api_key', 'enc:v1:lost', 1, 1)", 0)
            val cipher = object : com.dailysatori.service.security.SecretValueCipher {
                override fun encrypt(value: String) = "enc:v1:$value"
                override fun decrypt(value: String) = value
                override fun isEncrypted(value: String) = value.startsWith("enc:v1:")
            }
            assertFailsWith<IllegalStateException> { SecretFieldProcessor(driver, cipher).decryptSecretsForBackup() }
        } finally { driver.close() }
    }
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

/** AndroidSqliteDriver caches prepared SQL by identifier, not by SQL text; JDBC does not. */
private class IdentifierCachingDriver(private val delegate: app.cash.sqldelight.db.SqlDriver) :
    app.cash.sqldelight.db.SqlDriver by delegate {
    private val queries = mutableMapOf<Int, String>()
    override fun <R> executeQuery(identifier: Int?, sql: String,
        mapper: (app.cash.sqldelight.db.SqlCursor) -> app.cash.sqldelight.db.QueryResult<R>,
        parameters: Int, binders: (app.cash.sqldelight.db.SqlPreparedStatement.() -> Unit)?): app.cash.sqldelight.db.QueryResult<R> =
        delegate.executeQuery(null, identifier?.let { queries.getOrPut(it) { sql } } ?: sql, mapper, parameters, binders)
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
