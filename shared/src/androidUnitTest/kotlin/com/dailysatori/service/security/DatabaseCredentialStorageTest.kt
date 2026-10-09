package com.dailysatori.service.security

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.data.repository.AIConfigRepository
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlin.test.*

class DatabaseCredentialStorageTest {
    @Test fun databaseCredentialsAreOrdinaryValuesInsideTheProtectedDatabase() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            DailySatoriDatabase.Schema.create(driver)
            val db = DailySatoriDatabase(driver)
            val cipher = object : SecretValueCipher {
                override fun encrypt(value: String) = "enc:v1:$value"
                override fun decrypt(value: String) = value.removePrefix("enc:v1:")
                override fun isEncrypted(value: String) = value.startsWith("enc:v1:")
            }
            val repository = AIConfigRepository(db)
            repository.insert("openai", "https://ai", "original-token", "model")
            assertEquals("original-token", db.dailySatoriQueries.selectAllAiConfigs().executeAsOne().api_token)
            assertEquals("original-token", repository.getAllSync().single().api_token)
        } finally { driver.close() }
    }
}
