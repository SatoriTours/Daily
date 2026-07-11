package com.dailysatori.ui.feature.diary

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import androidx.lifecycle.ViewModelStore
import com.dailysatori.data.repository.AIConfigRepository
import com.dailysatori.data.repository.DiaryMonthSummaryRepository
import com.dailysatori.data.repository.DiaryRepository
import com.dailysatori.service.ai.AiConfigService
import com.dailysatori.service.ai.AiService
import com.dailysatori.service.diary.DiaryMonthSummaryService
import com.dailysatori.service.memory.MemoryExtractor
import com.dailysatori.service.security.SecretValueCipher
import com.dailysatori.shared.db.DailySatoriDatabase
import io.ktor.client.HttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DiaryViewModelSourceIdTest {
    private val mainDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        kotlinx.coroutines.Dispatchers.setMain(mainDispatcher)
    }

    @After
    fun tearDown() {
        kotlinx.coroutines.Dispatchers.resetMain()
    }

    @Test
    fun newDiaryExtractsMemoryUsingItsPersistedId() = runTest {
        val fixture = diaryFixture()
        try {
            repeat(41) { fixture.diaryRepository.create(content = "existing diary $it") }

            assertEquals(42L, fixture.viewModel.saveDiaryAndGetId(content = "new diary"))

            assertEquals(listOf(RecordedExtraction("diary", 42L)), fixture.extractor.extractions)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun existingDiaryExtractsMemoryUsingItsExistingId() = runTest {
        val fixture = diaryFixture()
        try {
            val existingId = fixture.diaryRepository.create(content = "existing diary")

            assertEquals(existingId, fixture.viewModel.saveDiaryAndGetId(content = "updated diary", existingId = existingId))

            assertEquals(listOf(RecordedExtraction("diary", existingId)), fixture.extractor.extractions)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun failedDiarySaveDoesNotExtractMemory() = runTest {
        val fixture = diaryFixture()
        try {
            fixture.driver.execute(
                null,
                """
                    CREATE TRIGGER fail_diary_insert
                    BEFORE INSERT ON diary
                    WHEN NEW.content = 'cannot persist'
                    BEGIN
                        SELECT RAISE(ABORT, 'save failed');
                    END
                """.trimIndent(),
                0,
            )

            assertNull(fixture.viewModel.saveDiaryAndGetId(content = "cannot persist"))

            assertTrue(fixture.extractor.extractions.isEmpty())
        } finally {
            fixture.close()
        }
    }

    @Test
    fun extractionFailureStillReturnsPersistedId() = runTest {
        val fixture = diaryFixture(extractorFailure = IllegalStateException("extraction failed"))
        try {
            assertEquals(1L, fixture.viewModel.saveDiaryAndGetId(content = "persisted diary"))
            assertEquals(1, fixture.diaryRepository.getAllSync().size)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun cancellationFromExtractionIsRethrown() = runTest {
        val fixture = diaryFixture(extractorFailure = CancellationException("cancelled"))
        try {
            assertFailsWith<CancellationException> {
                fixture.viewModel.saveDiaryAndGetId(content = "cancelled diary")
            }
            assertEquals(1, fixture.diaryRepository.getAllSync().size)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun diaryScreenDelegatesSavingToTheViewModelLifecycle() {
        val screenSource = File("src/main/kotlin/com/dailysatori/ui/feature/diary/DiaryScreen.kt").readText()
        val viewModelSource = File("src/main/kotlin/com/dailysatori/ui/feature/diary/DiaryViewModel.kt").readText()

        assertTrue(screenSource.contains("viewModel.saveDiary("))
        assertTrue(!screenSource.contains("rememberCoroutineScope"))
        assertTrue(!screenSource.contains("saveScope.launch"))
        assertTrue(viewModelSource.contains("fun saveDiary("))
        assertTrue(viewModelSource.contains("viewModelScope.launch(Dispatchers.IO)"))
        assertTrue(viewModelSource.contains("suspend fun saveDiaryAndGetId("))
    }

    private fun diaryFixture(extractorFailure: Throwable? = null): DiaryFixture {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        DailySatoriDatabase.Schema.create(driver)
        val database = DailySatoriDatabase(driver)
        val diaryRepository = DiaryRepository(database, driver)
        val monthSummaryRepository = DiaryMonthSummaryRepository(database)
        val httpClient = HttpClient()
        val extractor = RecordingMemoryExtractor(extractorFailure)
        val viewModel = DiaryViewModel(
            diaryRepo = diaryRepository,
            memoryExtractor = extractor,
            monthSummaryRepo = monthSummaryRepository,
            monthSummaryService = DiaryMonthSummaryService(
                diaryRepo = diaryRepository,
                summaryRepo = monthSummaryRepository,
                aiConfigService = AiConfigService(
                    AIConfigRepository(database, PlainSecretCipher),
                ),
                aiService = AiService(httpClient),
            ),
        )
        return DiaryFixture(driver, diaryRepository, viewModel, extractor, httpClient)
    }

    private data class DiaryFixture(
        val driver: JdbcSqliteDriver,
        val diaryRepository: DiaryRepository,
        val viewModel: DiaryViewModel,
        val extractor: RecordingMemoryExtractor,
        val httpClient: HttpClient,
    ) {
        fun close() {
            ViewModelStore().run {
                put("test", viewModel)
                clear()
            }
            httpClient.close()
            driver.close()
        }
    }

    private class RecordingMemoryExtractor(
        private val failure: Throwable? = null,
    ) : MemoryExtractor {
        val extractions = mutableListOf<RecordedExtraction>()

        override suspend fun extractAndSave(sourceType: String, sourceId: Long, title: String, content: String) {
            failure?.let { throw it }
            extractions += RecordedExtraction(sourceType, sourceId)
        }
    }

    private data class RecordedExtraction(
        val sourceType: String,
        val sourceId: Long,
    )

    private object PlainSecretCipher : SecretValueCipher {
        override fun encrypt(value: String): String = value

        override fun decrypt(value: String): String = value

        override fun isEncrypted(value: String): Boolean = false
    }
}
