package com.dailysatori.service.diary

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.data.repository.AIConfigRepository
import com.dailysatori.service.ai.AiConfigService
import com.dailysatori.service.security.SecretValueCipher
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlin.test.Test
import kotlin.test.assertEquals

class SpeechConfigSelectionTest {
    @Test
    fun speechFallsBackFromUnsupportedDefaultToConfiguredGemini() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            DailySatoriDatabase.Schema.create(driver)
            val repository = AIConfigRepository(db = DailySatoriDatabase(driver), secretCipher = PlainCipher)
            repository.insert("deepseek", "https://api.deepseek.com", "deep-token", "deepseek-chat", 1)
            repository.insert("gemini", "https://generativelanguage.googleapis.com", "gemini-token", "gemini-2.5-flash", 0)

            val config = AiConfigService(repository).getSpeechConfig()

            assertEquals("gemini", config?.provider)
            assertEquals("gemini-2.5-flash", config?.model_name)
        } finally {
            driver.close()
        }
    }

    private object PlainCipher : SecretValueCipher {
        override fun encrypt(value: String) = value
        override fun decrypt(value: String) = value
        override fun isEncrypted(value: String) = true
    }
}
