package com.dailysatori.ui.feature.diary

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import androidx.lifecycle.ViewModelStore
import com.dailysatori.data.repository.AIConfigRepository
import com.dailysatori.data.repository.DiaryMonthSummaryRepository
import com.dailysatori.data.repository.DiaryAttachmentDraft
import com.dailysatori.data.repository.DiaryAttachmentKind
import com.dailysatori.data.repository.DiaryAttachmentRepository
import com.dailysatori.data.repository.DiaryRepository
import com.dailysatori.service.ai.AiConfigService
import com.dailysatori.service.ai.AiService
import com.dailysatori.service.diary.DiaryMonthSummaryService
import com.dailysatori.service.memory.MemoryExtractor
import com.dailysatori.service.security.SecretValueCipher
import com.dailysatori.shared.db.DailySatoriDatabase
import io.ktor.client.HttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
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

    @Test
    fun attachmentsStayReactiveAndFollowTheVisibleDiaryFilter() = runBlocking {
        val fixture = diaryFixture()
        try {
            val firstId = fixture.diaryRepository.create(content = "first diary")
            val secondId = fixture.diaryRepository.create(content = "second diary")
            fixture.attachmentRepository.create(
                firstId,
                DiaryAttachmentDraft(DiaryAttachmentKind.audio, "/first.m4a"),
            )
            fixture.attachmentRepository.create(
                secondId,
                DiaryAttachmentDraft(DiaryAttachmentKind.image, "/second.jpg"),
            )

            val all = withTimeout(5_000) {
                fixture.viewModel.state.first {
                    it.attachmentsByDiary[firstId]?.singleOrNull()?.local_path == "/first.m4a" &&
                        it.attachmentsByDiary[secondId]?.singleOrNull()?.local_path == "/second.jpg"
                }
            }
            assertEquals("/first.m4a", all.attachmentsByDiary.getValue(firstId).single().local_path)
            assertEquals("/second.jpg", all.attachmentsByDiary.getValue(secondId).single().local_path)

            fixture.viewModel.search("first diary")
            val filtered = withTimeout(5_000) {
                fixture.viewModel.state.first {
                    it.diaries.map { diary -> diary.id } == listOf(firstId) &&
                        it.attachmentsByDiary.keys == setOf(firstId) &&
                        it.attachmentsByDiary[firstId]?.singleOrNull()?.local_path == "/first.m4a"
                }
            }
            assertEquals("/first.m4a", filtered.attachmentsByDiary.getValue(firstId).single().local_path)
        } finally {
            fixture.close()
        }
    }

    private fun diaryFixture(extractorFailure: Throwable? = null): DiaryFixture {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        DailySatoriDatabase.Schema.create(driver)
        val database = DailySatoriDatabase(driver)
        val diaryRepository = DiaryRepository(database, driver)
        val attachmentRepository = DiaryAttachmentRepository(database, driver)
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
            attachmentRepo = attachmentRepository,
        )
        return DiaryFixture(driver, diaryRepository, attachmentRepository, viewModel, extractor, httpClient)
    }

    private data class DiaryFixture(
        val driver: JdbcSqliteDriver,
        val diaryRepository: DiaryRepository,
        val attachmentRepository: DiaryAttachmentRepository,
        val viewModel: DiaryViewModel,
        val extractor: RecordingMemoryExtractor,
        val httpClient: HttpClient,
    ) {
        fun close() {
            ViewModelStore().run {
                put("test", viewModel)
                clear()
            }
            runBlocking { delay(50) }
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
