package com.dailysatori.service.ai

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.data.repository.AIConfigRepository
import com.dailysatori.data.repository.SettingRepository
import com.dailysatori.service.security.SecretValueCipher
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlin.test.*

class AiPurposeRoutingTest {
    @Test fun independentAssignmentsPersistAndUnassignedUsesDefault() = withDatabase { db ->
        val repo = configs(db)
        val defaultId = repo.getDefault()!!.id
        val fastId = add(repo, "deepseek", "https://api.deepseek.com", "fast-model")
        val backgroundId = add(repo, "opencode-go", "https://opencode.ai/zen/go/v1", "background-model")
        val deepId = add(repo, "opencode-go", "https://opencode.ai/zen/go/v1", "deep-model")
        assertEquals(defaultId, repo.getForPurpose(AiPurpose.INTERACTIVE)!!.id)
        repo.setPurposeConfig(AiPurpose.INTERACTIVE, fastId)
        repo.setPurposeConfig(AiPurpose.EXTERNAL_CONTENT, backgroundId)
        repo.setPurposeConfig(AiPurpose.REFLECTION, deepId)
        val reopened = AIConfigRepository(db, PlainCipher)
        val service = AiConfigService(reopened)
        assertEquals("fast-model", service.getConfig(AiPurpose.INTERACTIVE)!!.model_name)
        assertEquals("background-model", service.getConfig(AiPurpose.EXTERNAL_CONTENT)!!.model_name)
        assertEquals("deep-model", service.getConfig(AiPurpose.REFLECTION)!!.model_name)
        assertEquals(defaultId, service.getDefaultConfig()!!.id)
        assertEquals(defaultId, service.getSpeechConfig()!!.id)
        repo.setPurposeConfig(AiPurpose.INTERACTIVE, null)
        assertEquals(defaultId, reopened.getForPurpose(AiPurpose.INTERACTIVE)!!.id)
        assertEquals(backgroundId, reopened.getPurposeAssignments()[AiPurpose.EXTERNAL_CONTENT])
    }

    @Test fun followingDefaultTracksDefaultChangesWithoutReassigningOtherPurposes() = withDatabase { db ->
        val repo = configs(db)
        val id = add(repo, "deepseek", "https://api.deepseek.com", "new-default")
        repo.setPurposeConfig(AiPurpose.REFLECTION, repo.getDefault()!!.id)
        val config = repo.getById(id)!!
        repo.update(id, config.provider, config.api_address, config.api_token, config.model_name, 1)
        assertEquals("new-default", repo.getForPurpose(AiPurpose.INTERACTIVE)!!.model_name)
        assertEquals("default-model", repo.getForPurpose(AiPurpose.REFLECTION)!!.model_name)
    }

    @Test fun deletingAssignedConfigClearsEveryReferenceButProtectsDefault() = withDatabase { db ->
        val repo = configs(db)
        val id = add(repo, "deepseek", "https://api.deepseek.com", "selected")
        AiPurpose.entries.forEach { repo.setPurposeConfig(it, id) }
        repo.delete(id)
        assertTrue(repo.getPurposeAssignments().isEmpty())
        AiPurpose.entries.forEach { assertEquals("default-model", repo.getForPurpose(it)!!.model_name) }
        val defaultId = repo.getDefault()!!.id
        repo.setPurposeConfig(AiPurpose.INTERACTIVE, defaultId)
        repo.delete(defaultId)
        assertEquals(defaultId, repo.getForPurpose(AiPurpose.INTERACTIVE)!!.id)
        assertEquals(defaultId, repo.getPurposeAssignments()[AiPurpose.INTERACTIVE])
    }

    @Test fun invalidOrRestoredStaleAssignmentsNeverDisableTheDefault() = withDatabase { db ->
        val repo = configs(db)
        val settings = SettingRepository(db)
        settings.upsert("ai.purpose.interactive.config_id", "not-a-number")
        settings.upsert("ai.purpose.external_content.config_id", "99999")
        assertTrue(repo.getPurposeAssignments().isEmpty())
        assertEquals("default-model", repo.getForPurpose(AiPurpose.INTERACTIVE)!!.model_name)
        assertEquals("default-model", repo.getForPurpose(AiPurpose.EXTERNAL_CONTENT)!!.model_name)
        assertFailsWith<IllegalArgumentException> { repo.setPurposeConfig(AiPurpose.REFLECTION, 99999) }
        assertTrue(repo.getPurposeAssignments().isEmpty())
    }

    @Test fun explicitAssignmentWorksWithoutAGlobalDefault() = withDatabase { db ->
        val repo = AIConfigRepository(db, PlainCipher)
        val id = add(repo, "deepseek", "https://api.deepseek.com", "fast-model")
        repo.setPurposeConfig(AiPurpose.INTERACTIVE, id)
        assertEquals(id, repo.getForPurpose(AiPurpose.INTERACTIVE)!!.id)
        assertNull(repo.getForPurpose(AiPurpose.REFLECTION))
        repo.setPurposeConfig(AiPurpose.INTERACTIVE, null)
        assertNull(repo.getForPurpose(AiPurpose.INTERACTIVE))
    }

    private fun configs(db: DailySatoriDatabase) = AIConfigRepository(db, PlainCipher).also {
        it.insert("openai", "https://example.com/v1", "test-token", "default-model", 1)
    }

    private fun add(repo: AIConfigRepository, provider: String, host: String, model: String): Long {
        repo.insert(provider, host, "test-token", model)
        return repo.getAllSync().single { it.model_name == model }.id
    }

    private fun withDatabase(test: (DailySatoriDatabase) -> Unit) {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            DailySatoriDatabase.Schema.create(driver)
            test(DailySatoriDatabase(driver))
        }
    }

    private object PlainCipher : SecretValueCipher {
        override fun encrypt(value: String) = value
        override fun decrypt(value: String) = value
        override fun isEncrypted(value: String) = false
    }
}
