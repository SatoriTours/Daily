package com.dailysatori.ui.feature.aiconfig

import androidx.lifecycle.viewModelScope
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.data.repository.AIConfigRepository
import com.dailysatori.data.repository.SettingRepository
import com.dailysatori.service.i18n.I18nService
import java.io.File
import com.dailysatori.service.ai.AiPurpose
import com.dailysatori.service.security.SecretValueCipher
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AiPurposeViewModelTest {

    private object NoOpCipher : SecretValueCipher {
        override fun encrypt(value: String): String = value
        override fun decrypt(value: String): String = value
        override fun isEncrypted(value: String): Boolean = false
    }

    private fun insertConfig(
        repo: AIConfigRepository,
        provider: String,
        modelName: String,
        apiAddress: String = "https://api.example.com",
        apiToken: String = "test-token",
        isDefault: Long = 0L,
    ): Long {
        repo.insert(provider, apiAddress, apiToken, modelName, isDefault)
        return repo.getAllSync().first { it.model_name == modelName && it.provider == provider }.id
    }

    private fun withViewModel(
        block: suspend (AiPurposeViewModel, AIConfigRepository, DailySatoriDatabase) -> Unit,
    ) = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        DailySatoriDatabase.Schema.create(driver)
        val db = DailySatoriDatabase(driver)
        val repo = AIConfigRepository(db, NoOpCipher)
        var vm: AiPurposeViewModel? = null

        try {
            val viewModel = AiPurposeViewModel(repo).also { vm = it }
            block(viewModel, repo, db)
        } finally {
            vm?.viewModelScope?.coroutineContext?.get(Job)?.cancelAndJoin()
            driver.close()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun testInitialStateWithDefaultAndSecondaryModel() = withViewModel { vm, repo, _ ->
        val defaultId = insertConfig(repo, "openai", "gpt-4o", isDefault = 1L)
        val secondaryId = insertConfig(repo, "deepseek", "deepseek-chat", isDefault = 0L)

        val state = withTimeout(5_000) {
            vm.state.first { it.configs.size == 2 }
        }

        assertEquals(2, state.configs.size)
        assertEquals(defaultId, state.defaultConfig?.id)
        assertTrue(state.assignments.isEmpty())
        assertNull(state.errorMessage)
    }

    @Test
    fun testSelectModelForPurposePersistsAndUpdatesState() = withViewModel { vm, repo, _ ->
        val defaultId = insertConfig(repo, "openai", "gpt-4o", isDefault = 1L)
        val fastId = insertConfig(repo, "deepseek", "deepseek-chat", isDefault = 0L)

        withTimeout(5_000) { vm.state.first { it.configs.size == 2 } }

        vm.selectModelForPurpose(AiPurpose.INTERACTIVE, fastId)

        val updated = withTimeout(5_000) {
            vm.state.first { it.assignments[AiPurpose.INTERACTIVE] == fastId }
        }
        assertEquals(fastId, updated.assignments[AiPurpose.INTERACTIVE])
        assertEquals(fastId, repo.getPurposeAssignments()[AiPurpose.INTERACTIVE])

        // Reset to default (null)
        vm.selectModelForPurpose(AiPurpose.INTERACTIVE, null)
        val resetState = withTimeout(5_000) {
            vm.state.first { !it.assignments.containsKey(AiPurpose.INTERACTIVE) }
        }
        assertNull(resetState.assignments[AiPurpose.INTERACTIVE])
        assertNull(repo.getPurposeAssignments()[AiPurpose.INTERACTIVE])
    }

    @Test
    fun testInvalidConfigIdReturnsALocalizableError() = withViewModel { vm, repo, db ->
        insertConfig(repo, "openai", "gpt-4o", isDefault = 1L)
        withTimeout(5_000) { vm.state.first { it.configs.size == 1 } }

        vm.selectModelForPurpose(AiPurpose.REFLECTION, 999999L)

        val errorState = withTimeout(5_000) {
            vm.state.first { it.errorMessage != null }
        }
        val i18n = I18nService(SettingRepository(db)).apply {
            loadTranslation("zh", File("src/main/assets/i18n/zh.yaml").readText())
            init("zh")
        }
        assertEquals("保存模型分配失败，请重试", i18n.t(assertNotNull(errorState.errorMessage)))

        vm.clearErrorMessage()
        val cleared = withTimeout(5_000) {
            vm.state.first { it.errorMessage == null }
        }
        assertNull(cleared.errorMessage)
    }

    @Test
    fun testDeletingAssignedModelCleansAssignmentInState() = withViewModel { vm, repo, _ ->
        insertConfig(repo, "openai", "gpt-4o", isDefault = 1L)
        val batchId = insertConfig(repo, "deepseek", "deepseek-reasoner", isDefault = 0L)

        withTimeout(5_000) { vm.state.first { it.configs.size == 2 } }

        vm.selectModelForPurpose(AiPurpose.EXTERNAL_CONTENT, batchId)
        withTimeout(5_000) { vm.state.first { it.assignments[AiPurpose.EXTERNAL_CONTENT] == batchId } }

        // Deleting the non-default model
        repo.delete(batchId)

        val afterDelete = withTimeout(5_000) {
            vm.state.first { it.configs.size == 1 && !it.assignments.containsKey(AiPurpose.EXTERNAL_CONTENT) }
        }
        assertEquals(1, afterDelete.configs.size)
        assertNull(afterDelete.assignments[AiPurpose.EXTERNAL_CONTENT])
    }
}
