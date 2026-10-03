package com.dailysatori.service.diary

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.config.SettingKeys
import com.dailysatori.data.repository.AIConfigRepository
import com.dailysatori.data.repository.SettingRepository
import com.dailysatori.service.ai.AiConfigService
import com.dailysatori.service.security.SecretFieldProcessor
import com.dailysatori.service.security.SecretValueCipher
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlin.test.*

class SpeechSettingsTest {
    private val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    private val db = DailySatoriDatabase(driver).also { DailySatoriDatabase.Schema.create(driver) }
    private val settings = SettingRepository(db)
    private val aiConfigs = AIConfigRepository(db, TestCipher)
    private val service = SpeechSettingsService(settings, TestCipher, AiConfigService(aiConfigs))

    @AfterTest fun close() = driver.close()

    @Test fun independentSpeechConfigIsEncryptedAndDoesNotChangeChatDefault() {
        aiConfigs.insert("deepseek", "https://api.deepseek.com", "chat-key", "deepseek-flash", 1)
        val config = SpeechConfig("minimax", "asr-1.0", "https://api.minimax.cn/v1", "speech-key")
        service.save(config)
        assertEquals(config, service.load())
        assertTrue(settings.get(SettingKeys.speechConfig)!!.startsWith("encrypted:"))
        assertEquals("deepseek", aiConfigs.getDefault()?.provider)
        assertFalse(config.toString().contains("speech-key"))
    }

    @Test fun legacyConfigurationWorksUntilIndependentConfigurationIsSaved() {
        aiConfigs.insert("gemini", "https://generativelanguage.googleapis.com", "old-key", "gemini-2.5-flash", 1)
        assertEquals("gemini-2.5-flash", service.load()?.model)
        service.save(SpeechConfig("siliconflow", "FunAudioLLM/SenseVoiceSmall", "https://api.siliconflow.cn/v1", "new-key"))
        assertEquals("siliconflow", service.load()?.provider)
    }

    @Test fun corruptExplicitSettingsNeverSilentlyUseAnotherProvidersKey() {
        aiConfigs.insert("openai", "https://api.openai.com/v1", "old-key", "chat-model", 1)
        settings.upsert(SettingKeys.speechConfig, "encrypted:invalid json")
        assertNull(service.load())
    }

    @Test fun legacyCustomTranscriptionModelIsPreserved() {
        aiConfigs.insert("openai", "https://example.com/v1", "old-key", "chat-model", 1)
        settings.upsert(SettingKeys.speechModel, "custom-asr")
        val config = service.load()!!
        assertEquals("compatible", config.provider)
        assertEquals("custom-asr", config.model)
        assertNull(config.validationError())
    }

    @Test fun copiedWhitespaceIsTrimmedBeforeSaving() {
        service.save(SpeechConfig("minimax", " asr-1.0 ", " https://api.minimax.cn/v1/ ", " key\n"))
        assertEquals(SpeechConfig("minimax", "asr-1.0", "https://api.minimax.cn/v1", "key"), service.load())
    }

    @Test fun secretBackupProcessingIncludesIndependentSpeechConfiguration() {
        service.save(SpeechConfig("minimax", "asr-1.0", "https://api.minimax.cn/v1", "speech-key"))
        SecretFieldProcessor(driver, TestCipher).decryptSecretsForBackup()
        assertFalse(settings.get(SettingKeys.speechConfig)!!.startsWith("encrypted:"))
        SecretFieldProcessor(driver, TestCipher).prepareRestoredSecrets()
        assertEquals("speech-key", service.load()?.apiKey)
    }

    @Test fun validationRejectsMissingCredentialsInsecureAddressesAndWrongModelFamilies() {
        val valid = SpeechConfig("dashscope", "qwen-audio-3.1-asr-flash", "https://dashscope.aliyuncs.com", "key")
        assertNull(valid.validationError())
        assertNotNull(valid.copy(apiKey = " ").validationError())
        assertNotNull(valid.copy(apiAddress = "http://example.com").validationError())
        assertNotNull(valid.copy(model = "qwen-audio-3.1-asr-flash-message").validationError())
        assertNotNull(valid.copy(model = "qwen-audio-3.1-asr-flash-filetrans").validationError())
        assertNotNull(valid.copy(provider = "deepseek").validationError())
        assertFailsWith<IllegalArgumentException> { service.save(valid.copy(apiKey = "")) }
    }

    private object TestCipher : SecretValueCipher {
        override fun encrypt(value: String) = "encrypted:$value"
        override fun decrypt(value: String) = value.removePrefix("encrypted:")
        override fun isEncrypted(value: String) = value.startsWith("encrypted:")
    }
}
