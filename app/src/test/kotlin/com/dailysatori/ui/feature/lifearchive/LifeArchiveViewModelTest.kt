package com.dailysatori.ui.feature.lifearchive

import com.dailysatori.service.lifearchive.*
import com.dailysatori.service.reminder.Reminder
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class LifeArchiveViewModelTest {
    @Test
    fun searchStartsCollapsedAndClosingClearsQuery() = runTest {
        val vm = LifeArchiveViewModel(MemoryRepository(), FakeAi(), EmptySource, EmptyImporter, this)
        advanceUntilIdle()
        assertFalse(vm.state.value.searchVisible)
        vm.toggleSearch(); vm.setQuery("test")
        assertTrue(vm.state.value.searchVisible)
        vm.toggleSearch()
        assertEquals("", vm.state.value.query)
        assertFalse(vm.state.value.searchVisible)
    }

    @Test
    fun lateResponseCannotReplaceChangedInputAndDuplicateSendsAreIgnored() = runTest {
        val remote = FakeAi(CompletableDeferred())
        val vm = LifeArchiveViewModel(MemoryRepository(), remote, EmptySource, EmptyImporter, this)
        advanceUntilIdle(); vm.beginNew(); vm.setInput("original"); vm.organize(); vm.organize()
        runCurrent()
        assertEquals(1, remote.calls)
        vm.setInput("edited")
        remote.gate!!.complete(Unit)
        advanceUntilIdle()
        assertEquals("edited", vm.state.value.input)
        assertEquals("", vm.state.value.draft!!.title)
        assertTrue(vm.state.value.pendingDrafts.isEmpty())
    }

    @Test
    fun optimizationOnlyPersistsOnConfirmationAndCanBeDiscarded() = runTest {
        val repository = MemoryRepository(mutableListOf(record()))
        val vm = LifeArchiveViewModel(repository, FakeAi(), EmptySource, EmptyImporter, this)
        advanceUntilIdle(); vm.open(record()); vm.setInstruction("change title"); vm.optimize()
        advanceUntilIdle()
        assertEquals("optimized", vm.state.value.draft!!.title)
        assertEquals("original", repository.values.single().title)
        vm.discardOptimization()
        assertEquals("original", vm.state.value.draft!!.title)
    }

    @Test
    fun staleSaveDoesNotOverwriteNewerRecord() = runTest {
        val repository = MemoryRepository(mutableListOf(record()))
        val vm = LifeArchiveViewModel(repository, FakeAi(), EmptySource, EmptyImporter, this)
        advanceUntilIdle(); vm.open(record()); vm.setTitle("mine")
        repository.values[0] = record().copy(title = "newer", updatedAt = 2)
        vm.save()
        advanceUntilIdle()
        assertEquals(LifeArchiveError.CONFLICT, vm.state.value.error)
        assertEquals("newer", repository.values.single().title)
    }

    @Test
    fun partialImportDraftsSurviveLaterBatchFailure() = runTest {
        val importer = object : LifeArchiveImporter {
            override suspend fun analyze(reminders: List<Reminder>, existing: List<LifeArchiveRecord>, categories: List<LifeArchiveCategory>,
                onBatch: (List<LifeArchiveRecord>, Int, Int) -> Unit): List<LifeArchiveRecord> {
                onBatch(listOf(record().copy(id = "import", updatedAt = 0)), 1, 2)
                error("offline")
            }
        }
        val vm = LifeArchiveViewModel(MemoryRepository(), FakeAi(), EmptySource, importer, this)
        advanceUntilIdle(); vm.importReminders(); advanceUntilIdle()
        assertEquals(listOf("import"), vm.state.value.pendingDrafts.map { it.id })
        assertFalse(vm.state.value.busy)
        assertEquals(LifeArchiveError.AI_REQUEST, vm.state.value.error)
    }

    @Test
    fun savingImportedDraftChecksSourceVersion() = runTest {
        val repository = MemoryRepository()
        val draft = record().copy(id = "import", updatedAt = 0, sourceReminderId = "missing-source", sourceReminderVersion = 0)
        val importer = object : LifeArchiveImporter {
            override suspend fun analyze(reminders: List<Reminder>, existing: List<LifeArchiveRecord>, categories: List<LifeArchiveCategory>,
                onBatch: (List<LifeArchiveRecord>, Int, Int) -> Unit): List<LifeArchiveRecord> {
                onBatch(listOf(draft), 1, 1); return listOf(draft)
            }
        }
        val vm = LifeArchiveViewModel(repository, FakeAi(), EmptySource, importer, this)
        advanceUntilIdle(); vm.importReminders(); advanceUntilIdle(); vm.openPending(draft.id); vm.save(confirmLocalOnly = true)
        advanceUntilIdle()
        assertEquals(LifeArchiveError.CONFLICT, vm.state.value.error)
        assertTrue(repository.values.isEmpty())
        assertEquals(1, vm.state.value.pendingDrafts.size)
    }

    @Test
    fun storageFailureCannotBeDismissedAsAnEmptyArchive() = runTest {
        val repository = object : LifeArchiveRepository by MemoryRepository() {
            override suspend fun records(): List<LifeArchiveRecord> = throw LifeArchiveStorageFailure()
        }
        val vm = LifeArchiveViewModel(repository, FakeAi(), EmptySource, EmptyImporter, this)
        advanceUntilIdle()
        assertEquals(LifeArchiveError.STORAGE, vm.state.value.error)
        vm.dismissError()
        assertEquals(LifeArchiveError.STORAGE, vm.state.value.error)
    }

    private fun record() = LifeArchiveRecord("saved", "original", "domain", createdAt = 1, updatedAt = 1)

    private class FakeAi(val gate: CompletableDeferred<Unit>? = null) : LifeArchiveAi {
        var calls = 0
        override suspend fun organize(text: String, categories: List<LifeArchiveCategory>, fieldNames: List<String>): List<LifeArchiveRecord> {
            calls++
            if (gate != null) withContext(NonCancellable) { gate.await() }
            return listOf(LifeArchiveRecord("new", "generated", "domain"))
        }
        override suspend fun optimize(record: LifeArchiveRecord, instruction: String, categories: List<LifeArchiveCategory>) = record.copy(title = "optimized")
    }

    private object EmptySource : LifeArchiveReminderSource {
        override suspend fun all() = emptyList<Reminder>()
        override suspend fun get(id: String): Reminder? = null
    }

    private object EmptyImporter : LifeArchiveImporter {
        override suspend fun analyze(reminders: List<Reminder>, existing: List<LifeArchiveRecord>, categories: List<LifeArchiveCategory>,
            onBatch: (List<LifeArchiveRecord>, Int, Int) -> Unit) = emptyList<LifeArchiveRecord>()
    }

    private class MemoryRepository(val values: MutableList<LifeArchiveRecord> = mutableListOf()) : LifeArchiveRepository {
        override suspend fun records() = values.toList()
        override suspend fun categories() = defaultLifeArchiveCategories()
        override suspend fun save(record: LifeArchiveRecord, expectedUpdatedAt: Long?): LifeArchiveRecord {
            if (values.find { it.id == record.id }?.updatedAt != expectedUpdatedAt) throw LifeArchiveConflict()
            val saved = record.copy(updatedAt = (expectedUpdatedAt ?: 0) + 1)
            values.removeAll { it.id == record.id }; values += saved
            return saved
        }
        override suspend fun delete(id: String) { values.removeAll { it.id == id } }
        override suspend fun addCategory(name: String) = LifeArchiveCategory("custom", name)
        override suspend fun renameCategory(id: String, name: String) = Unit
        override suspend fun deleteCategory(id: String, replacementId: String) = Unit
    }
}
