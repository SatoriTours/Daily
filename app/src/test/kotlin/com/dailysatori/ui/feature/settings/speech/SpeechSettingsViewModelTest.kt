package com.dailysatori.ui.feature.settings.speech

import androidx.lifecycle.viewModelScope
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.data.repository.AIConfigRepository
import com.dailysatori.data.repository.SettingRepository
import com.dailysatori.service.ai.AiConfigService
import com.dailysatori.service.diary.SpeechConfig
import com.dailysatori.service.diary.SpeechSettingsService
import com.dailysatori.service.diary.SpeechTranscriptionApi
import com.dailysatori.service.i18n.I18nService
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpHeaders
import java.io.File
import com.dailysatori.service.security.SecretValueCipher
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class SpeechSettingsViewModelTest {
    @Test fun testUsesUnsavedKeyAndModelWithoutSavingOrCreatingDiary() = withModel(
        engine = MockEngine { request ->
            assertEquals("Bearer draft-key", request.headers[HttpHeaders.Authorization])
            respond("{\"text\":\"语音测试成功\"}", HttpStatusCode.OK)
        },
    ) { model, service ->
        model.selectProvider("minimax")
        model.setApiKey("draft-key")
        model.testConfiguration { byteArrayOf(1, 2) to "test.wav" }
        val result = withTimeout(5_000) { model.state.first { !it.testing && it.message != null } }
        assertFalse(result.isError)
        assertTrue(result.message.orEmpty().contains("语音测试成功"))
        assertNull(service.load())
        assertTrue(result.hasChanges)
        model.setApiKey("updated-key")
        assertNull(model.state.value.message)
    }

    @Test fun deniedKeyHasActionableErrorWithoutServerEchoedCredentials() = withModel(
        engine = MockEngine { respond("invalid draft-key", HttpStatusCode.Unauthorized) },
    ) { model, service ->
        model.setApiKey("draft-key")
        model.testConfiguration { byteArrayOf(1) to "test.wav" }
        val result = withTimeout(5_000) { model.state.first { !it.testing && it.message != null } }
        assertTrue(result.isError)
        assertTrue(result.message.orEmpty().contains("API Key"))
        assertFalse(result.message.orEmpty().contains("draft-key"))
        assertTrue(result.canTest)
        assertNull(service.load())
    }

    @Test fun inFlightTestPreventsDuplicateCallsEditingAndSaving() = withModel(
        engine = MockEngine { awaitCancellation() },
    ) { model, service ->
        model.setApiKey("draft-key")
        val loaded = CompletableDeferred<Unit>()
        model.testConfiguration { loaded.complete(Unit); byteArrayOf(1) to "test.wav" }
        loaded.await()
        assertTrue(model.state.value.testing)
        assertFalse(model.state.value.canSave)
        model.setApiKey("other-key")
        model.selectProvider("minimax")
        model.save()
        model.testConfiguration { error("Duplicate test must not load audio") }
        assertEquals("draft-key", model.state.value.config.apiKey)
        assertNull(service.load())
        model.viewModelScope.coroutineContext[Job]!!.cancelAndJoin()
        assertFalse(model.state.value.testing)
    }

    @Test fun emptyOrOversizedAudioDoesNotSendRequest() = withModel { model, _ ->
        model.setApiKey("draft-key")
        for (bytes in listOf(byteArrayOf(), ByteArray(SPEECH_TEST_MAX_BYTES + 1))) {
            model.testConfiguration { bytes to "test.wav" }
            val result = withTimeout(5_000) { model.state.first { !it.testing && it.message != null } }
            assertTrue(result.isError)
            assertTrue(result.message.orEmpty().contains("1 MB"))
        }
    }

    @Test fun invalidConfigurationNeverReadsOrUploadsAudio() = withModel { model, _ ->
        assertFalse(model.state.value.canTest)
        model.testConfiguration { error("Missing Key must not start a test") }
        assertFalse(model.state.value.testing)
        assertNull(model.state.value.message)
    }

    @Test fun modelAndServiceFailuresAreDistinguishable() {
        for ((status, expected) in listOf(HttpStatusCode.NotFound to "模型", HttpStatusCode.ServiceUnavailable to "暂不可用")) {
            withModel(engine = MockEngine { respond("echoed-key", status) }) { model, _ ->
                model.setApiKey("echoed-key")
                model.testConfiguration { byteArrayOf(1) to "test.wav" }
                val result = withTimeout(5_000) { model.state.first { !it.testing && it.message != null } }
                assertTrue(result.isError)
                assertTrue(result.message.orEmpty().contains(expected))
                assertFalse(result.message.orEmpty().contains("echoed-key"))
            }
        }
    }


    @Test fun discardingChangesRestoresSavedValuesAndClearsProviderDrafts() = withModel(
        SpeechConfig("minimax", "asr-1.0", "https://api.minimax.cn/v1", "saved-key"),
    ) { model, service ->
        model.setApiKey("unsaved-key")
        model.selectProvider("dashscope")
        model.setApiKey("other-key")
        model.discardChanges()
        assertEquals("saved-key", model.state.value.config.apiKey)
        assertFalse(model.state.value.hasChanges)
        model.selectProvider("dashscope")
        assertEquals("", model.state.value.config.apiKey)
        assertEquals("saved-key", service.load()?.apiKey)
    }

    @Test fun endpointDependentCustomProviderCannotBeSelected() = withModel { model, _ ->
        val original = model.state.value.config
        model.selectProvider("compatible")
        assertEquals(original, model.state.value.config)
    }

    @Test fun modelSelectionOnlyAcceptsProviderModels() = withModel { model, _ ->
        model.selectProvider("gemini")
        val original = model.state.value.config.model
        model.setModel("arbitrary-chat-model")
        assertEquals(original, model.state.value.config.model)
    }

    @Test fun oldProxyCredentialsAreNotMovedToTheOfficialEndpoint() = withModel(
        SpeechConfig("minimax", "asr-1.0", "https://proxy.example.com/v1", "proxy-key"),
    ) { model, service ->
        assertEquals("https://api.minimax.cn/v1", model.state.value.config.apiAddress)
        assertEquals("", model.state.value.config.apiKey)
        assertEquals("https://proxy.example.com/v1", service.load()?.apiAddress)
        assertFalse(model.state.value.canSave)
    }

    @Test fun switchingProvidersKeepsDraftsSeparateAndDoesNotSaveWithoutAnExplicitSave() = withModel { model, service ->
        model.setApiKey("aliyun-key")
        model.selectProvider("minimax")
        assertEquals("", model.state.value.config.apiKey)
        assertEquals("asr-1.0", model.state.value.config.model)
        model.setApiKey("minimax-key")
        model.selectProvider("dashscope")
        assertEquals("aliyun-key", model.state.value.config.apiKey)
        model.selectProvider("minimax")
        assertEquals("minimax-key", model.state.value.config.apiKey)
        assertNull(service.load())
        assertTrue(model.state.value.canSave)
    }

    @Test fun savePersistsSelectedProviderAndCanBeReloaded() = withModel { model, service ->
        model.selectProvider("siliconflow")
        model.setModel("TeleAI/TeleSpeechASR")
        model.setApiKey("silicon-key")
        val savedCallback = CompletableDeferred<Unit>()
        model.save {
            assertFalse(model.state.value.hasChanges)
            savedCallback.complete(Unit)
        }
        withTimeout(5_000) { savedCallback.await() }
        val saved = withTimeout(5_000) { model.state.first { !it.saving && it.message != null } }
        assertFalse(saved.isError)
        assertEquals("siliconflow", service.load()?.provider)
        assertEquals("TeleAI/TeleSpeechASR", service.load()?.model)
        assertEquals("silicon-key", service.load()?.apiKey)
        assertFalse(saved.hasChanges)
        assertFalse(saved.canSave)
    }

    @Test fun invalidKeyCannotBeSavedAndEditingClearsValidationMessage() = withModel { model, service ->
        assertFalse(model.state.value.canSave)
        model.save()
        assertTrue(model.state.value.isError)
        assertNull(service.load())
        model.setApiKey("valid-key")
        assertNull(model.state.value.message)
        assertTrue(model.state.value.canSave)
    }

    @Test fun openingPageLoadsSavedModelAndCredentials() = withModel(
        SpeechConfig("minimax", "asr-1.0", "https://api.minimax.cn/v1", "saved-key"),
    ) { model, _ ->
        assertEquals("minimax", model.state.value.config.provider)
        assertEquals("saved-key", model.state.value.config.apiKey)
    }

    private fun withModel(
        initial: SpeechConfig? = null,
        engine: MockEngine = MockEngine { error("Unexpected speech request") },
        test: suspend (SpeechSettingsViewModel, SpeechSettingsService) -> Unit,
    ) = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        var model: SpeechSettingsViewModel? = null
        val client = HttpClient(engine)
        try {
            DailySatoriDatabase.Schema.create(driver)
            val db = DailySatoriDatabase(driver)
            val service = SpeechSettingsService(SettingRepository(db), TestCipher, AiConfigService(AIConfigRepository(db, TestCipher)))
            initial?.let(service::save)
            val i18n = I18nService(SettingRepository(db)).apply {
                loadTranslation("zh", File("../shared/src/commonMain/resources/i18n/zh.yaml").readText())
                init("zh")
            }
            val viewModel = SpeechSettingsViewModel(service, SpeechTranscriptionApi(client), i18n).also { model = it }
            withTimeout(5_000) { viewModel.state.first { it.loaded } }
            test(viewModel, service)
        } finally {
            model?.viewModelScope?.coroutineContext?.get(Job)?.cancelAndJoin()
            driver.close()
            client.close()
            Dispatchers.resetMain()
        }
    }

    private object TestCipher : SecretValueCipher {
        override fun encrypt(value: String) = "encrypted:$value"
        override fun decrypt(value: String) = value.removePrefix("encrypted:")
        override fun isEncrypted(value: String) = value.startsWith("encrypted:")
    }
}
