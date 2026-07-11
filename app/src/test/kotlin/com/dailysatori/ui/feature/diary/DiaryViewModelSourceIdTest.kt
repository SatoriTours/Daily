package com.dailysatori.ui.feature.diary

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import androidx.lifecycle.ViewModelStore
import com.dailysatori.data.repository.AIConfigRepository
import com.dailysatori.data.repository.DiaryMonthSummaryRepository
import com.dailysatori.data.repository.DiaryRepository
import com.dailysatori.data.repository.MemoryRepository
import com.dailysatori.service.ai.AiConfigService
import com.dailysatori.service.ai.AiService
import com.dailysatori.service.diary.DiaryMonthSummaryService
import com.dailysatori.service.memory.MemoryExtractService
import com.dailysatori.service.security.SecretCipher
import com.dailysatori.shared.db.DailySatoriDatabase
import io.ktor.client.HttpClient
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        val httpClient = HttpClient()
        try {
            DailySatoriDatabase.Schema.create(driver)
            val database = DailySatoriDatabase(driver)
            val diaryRepository = DiaryRepository(database, driver)
            repeat(41) { diaryRepository.create(content = "existing diary $it") }
            val extractor = RecordingMemoryExtractService()
            val monthSummaryRepository = DiaryMonthSummaryRepository(database)
            val aiConfigService = AiConfigService(AIConfigRepository(database, unsafeAllocate()))
            val viewModel = DiaryViewModel(
                diaryRepo = diaryRepository,
                memoryExtractService = extractor,
                monthSummaryRepo = monthSummaryRepository,
                monthSummaryService = DiaryMonthSummaryService(
                    diaryRepo = diaryRepository,
                    summaryRepo = monthSummaryRepository,
                    aiConfigService = aiConfigService,
                    aiService = AiService(httpClient),
                ),
            )

            assertEquals(42L, viewModel.saveDiary(content = "new diary"))

            assertEquals(RecordedExtraction("diary", 42L), extractor.extraction)
            clearViewModel(viewModel)
        } finally {
            httpClient.close()
            driver.close()
        }
    }

    @Test
    fun existingDiaryExtractsMemoryUsingItsExistingId() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        val httpClient = HttpClient()
        try {
            DailySatoriDatabase.Schema.create(driver)
            val database = DailySatoriDatabase(driver)
            val diaryRepository = DiaryRepository(database, driver)
            val existingId = diaryRepository.create(content = "existing diary")
            val extractor = RecordingMemoryExtractService()
            val viewModel = newViewModel(database, diaryRepository, extractor, httpClient)

            assertEquals(existingId, viewModel.saveDiary(content = "updated diary", existingId = existingId))

            assertEquals(RecordedExtraction("diary", existingId), extractor.extraction)
            clearViewModel(viewModel)
        } finally {
            httpClient.close()
            driver.close()
        }
    }

    @Test
    fun failedDiarySaveDoesNotExtractMemory() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        val httpClient = HttpClient()
        try {
            DailySatoriDatabase.Schema.create(driver)
            val database = DailySatoriDatabase(driver)
            val diaryRepository = DiaryRepository(database, driver)
            val extractor = RecordingMemoryExtractService()
            val viewModel = newViewModel(database, diaryRepository, extractor, httpClient)
            driver.execute(
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

            assertNull(viewModel.saveDiary(content = "cannot persist"))

            assertNull(extractor.extraction)
            clearViewModel(viewModel)
        } finally {
            httpClient.close()
            driver.close()
        }
    }

    private fun newViewModel(
        database: DailySatoriDatabase,
        diaryRepository: DiaryRepository,
        extractor: RecordingMemoryExtractService,
        httpClient: HttpClient,
    ): DiaryViewModel {
        val monthSummaryRepository = DiaryMonthSummaryRepository(database)
        val aiConfigService = AiConfigService(AIConfigRepository(database, unsafeAllocate()))
        return DiaryViewModel(
            diaryRepo = diaryRepository,
            memoryExtractService = extractor,
            monthSummaryRepo = monthSummaryRepository,
            monthSummaryService = DiaryMonthSummaryService(
                diaryRepo = diaryRepository,
                summaryRepo = monthSummaryRepository,
                aiConfigService = aiConfigService,
                aiService = AiService(httpClient),
            ),
        )
    }

    private fun clearViewModel(viewModel: DiaryViewModel) {
        ViewModelStore().run {
            put("test", viewModel)
            clear()
        }
    }

    private class RecordingMemoryExtractService : MemoryExtractService(
        aiService = unsafeAllocate(),
        aiConfigService = unsafeAllocate(),
        memoryRepo = unsafeAllocate<MemoryRepository>(),
    ) {
        var extraction: RecordedExtraction? = null

        override suspend fun extractAndSave(sourceType: String, sourceId: Long, title: String, content: String) {
            extraction = RecordedExtraction(sourceType, sourceId)
        }
    }

    private data class RecordedExtraction(
        val sourceType: String,
        val sourceId: Long,
    )

    private companion object {
        private inline fun <reified T> unsafeAllocate(): T {
            val unsafeClass = Class.forName("sun.misc.Unsafe")
            val unsafe = unsafeClass.getDeclaredField("theUnsafe").run {
                isAccessible = true
                get(null)
            }
            return T::class.java.cast(
                unsafeClass.getMethod("allocateInstance", Class::class.java).invoke(unsafe, T::class.java),
            )
        }
    }
}
