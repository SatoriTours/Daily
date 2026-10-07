package com.dailysatori.ui.feature.ideatopic

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.data.repository.IdeaTopicRepository
import com.dailysatori.service.ideatopic.IdeaAiContext
import com.dailysatori.service.ideatopic.IdeaCaptureInput
import com.dailysatori.service.ideatopic.IdeaDraftContent
import com.dailysatori.service.ideatopic.IdeaDraftState
import com.dailysatori.service.ideatopic.IdeaMessageRoles
import com.dailysatori.service.ideatopic.IdeaMessageStatus
import com.dailysatori.service.ideatopic.IdeaSourceKey
import com.dailysatori.service.ideatopic.IdeaSourceSnapshot
import com.dailysatori.service.ideatopic.IdeaSourceTypes
import com.dailysatori.service.ideatopic.IdeaTopicAiPort
import com.dailysatori.service.ideatopic.IdeaTopicAiWorkflow
import com.dailysatori.service.ideatopic.IdeaTopicContent
import com.dailysatori.service.ideatopic.IdeaTopicError
import com.dailysatori.service.ideatopic.IdeaTopicService
import com.dailysatori.service.ideatopic.IdeaTopicStatus
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IdeaTopicViewModelTest {
    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun listSortsFiltersAndSearchesMainTopicsOnly() = runBlocking {
        val fixture = ViewModelFixture()
        try {
            val vm = IdeaTopicListViewModel(fixture.service)
            withTimeout(5_000) { vm.state.first { !it.isLoading } }
            assertEquals(emptyList(), vm.state.value.topics)

            val first = fixture.captureTopic("甲 主题")
            fixture.tick()
            val second = fixture.captureTopic("乙 主题")
            fixture.tick()
            val third = fixture.captureTopic("丙 主题")
            fixture.service.setStatus(third, IdeaTopicStatus.Researching)

            withTimeout(5_000) { vm.state.first { it.topics.size == 3 } }
            assertEquals(listOf(third, second, first), vm.state.value.topics.map { it.id })

            vm.setStatusFilter(IdeaTopicStatus.Researching)
            withTimeout(5_000) { vm.state.first { it.statusFilter == IdeaTopicStatus.Researching } }
            assertEquals(listOf(third), vm.state.value.topics.map { it.id })

            vm.setStatusFilter(null)
            vm.setQuery("乙")
            withTimeout(5_000) { vm.state.first { it.query == "乙" } }
            assertEquals(listOf(second), vm.state.value.topics.map { it.id })
        } finally {
            fixture.close()
        }
    }

    @Test
    fun listLoadFailureIsReportedInsteadOfLookingEmpty() = runBlocking {
        val fixture = ViewModelFixture()
        try {
            fixture.driver.execute(null, "DROP TABLE idea_topic", 0)
            val vm = IdeaTopicListViewModel(fixture.service)
            withTimeout(5_000) { vm.state.first { it.error != null } }
            assertEquals(IdeaTopicError.StorageFailure, vm.state.value.error)
            assertFalse(vm.state.value.isLoading)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun detailRedirectsMergedTopicAndUnavailableAfterDelete() = runBlocking {
        val fixture = ViewModelFixture()
        try {
            val fromTopic = fixture.captureTopic("被合并主题")
            val intoTopic = fixture.captureTopic("主主题")
            fixture.service.merge(fromTopic, intoTopic)

            val mergedVm = IdeaTopicDetailViewModel(fromTopic, fixture.service, fixture.workflow)
            withTimeout(5_000) { mergedVm.state.first { it.detail != null } }
            assertEquals(intoTopic, mergedVm.state.value.topicId)
            assertEquals(intoTopic, mergedVm.state.value.mergedInto)

            val deletedVm = IdeaTopicDetailViewModel(fromTopic, fixture.service, fixture.workflow)
            fixture.service.delete(intoTopic)
            withTimeout(5_000) { deletedVm.state.first { it.deleted } }
            assertEquals(IdeaTopicError.NotFound, deletedVm.state.value.error)
            assertFalse(deletedVm.state.value.canMergeOrDelete)
            assertNull(deletedVm.state.value.detail)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun generatingDisablesMergeAndDeleteUntilCancelled() = runBlocking {
        val fixture = ViewModelFixture()
        try {
            val topicId = fixture.captureTopic("生成中主题")
            val gate = CompletableDeferred<Unit>()
            fixture.port.proposeGate = gate
            val job = launch { fixture.workflow.propose(topicId) }
            withTimeout(5_000) { while (!fixture.workflow.isBusy(topicId)) delay(10) }

            val vm = IdeaTopicDetailViewModel(topicId, fixture.service, fixture.workflow)
            withTimeout(5_000) { vm.state.first { it.detail != null } }
            assertTrue(vm.state.value.busy)
            assertFalse(vm.state.value.canMergeOrDelete)

            fixture.workflow.cancel(topicId)
            job.join()
            fixture.port.proposeGate = null
            vm.cancel()
            withTimeout(5_000) { vm.state.first { !it.busy && it.detail != null } }
            assertTrue(vm.state.value.canMergeOrDelete)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun pendingDraftShowsChangePreviewAndStaysUnconfirmed() = runBlocking {
        val fixture = ViewModelFixture()
        try {
            val topicId = fixture.captureTopic("草稿主题", description = "原始描述")
            val vm = IdeaTopicDetailViewModel(topicId, fixture.service, fixture.workflow)
            withTimeout(5_000) { vm.state.first { it.detail != null } }

            fixture.port.proposeResult = IdeaDraftContent(
                content = IdeaTopicContent(title = "AI 新标题", description = "原始描述"),
                referenceIds = emptyList(),
            )
            vm.propose()
            withTimeout(5_000) { vm.state.first { it.draftPreviews.isNotEmpty() } }

            val preview = vm.state.value.draftPreviews.single()
            assertEquals(IdeaDraftState.Pending, preview.state)
            assertEquals(listOf("title"), preview.changes.map { it.field })
            assertEquals("AI 新标题", preview.changes.single().after)
            assertEquals("草稿主题", vm.state.value.detail!!.topic.content.title, "AI must not overwrite content")
        } finally {
            fixture.close()
        }
    }

    @Test
    fun sessionLoadsRecentMessagesWithoutAiRequestAndMergesOlderPages() = runBlocking {
        val fixture = ViewModelFixture()
        try {
            val topicId = fixture.captureTopic("会话主题")
            val sessionId = fixture.service.createSession(topicId, "会话")
            repeat(40) { index ->
                fixture.db.dailySatoriQueries.insertIdeaTopicMessage(
                    id = "m-${index.toString().padStart(2, '0')}",
                    session_id = sessionId,
                    role = IdeaMessageRoles.User,
                    content = "消息 $index",
                    status = IdeaMessageStatus.Complete,
                    error = null,
                    created_at = 9_000L,
                )
            }

            val vm = IdeaTopicSessionViewModel(sessionId, fixture.service, fixture.workflow)
            withTimeout(5_000) { vm.state.first { it.messages.size == 30 } }
            assertEquals(0, fixture.port.replyCalls, "opening a session must not call the AI")
            assertEquals(30, vm.state.value.messages.map { it.id }.toSet().size)

            vm.loadOlder()
            withTimeout(5_000) { vm.state.first { it.messages.size == 40 } }
            assertEquals(40, vm.state.value.messages.map { it.id }.toSet().size)
            val ids = vm.state.value.messages.map { it.id }
            assertEquals(ids.sorted(), ids)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun captureViewModelReusesExistingTopicForDuplicateSource() = runBlocking {
        val fixture = ViewModelFixture()
        try {
            val diaryId = 1L
            fixture.db.dailySatoriQueries.insertDiary("来源内容", null, null, null, 1_000L, 1_000L)
            val snapshot = IdeaSourceSnapshot(
                key = IdeaSourceKey(IdeaSourceTypes.Diary, diaryId.toString()),
                originalTitle = "来源内容",
                originalContent = "来源内容",
                originalRecordId = diaryId.toString(),
            )
            val vm = IdeaTopicCaptureViewModel(fixture.service)
            vm.lookupExisting(snapshot.key)
            assertNull(vm.state.value.existingTopicId)

            vm.submit(IdeaCaptureInput(source = snapshot, content = IdeaTopicContent(title = "收录标题")))
            withTimeout(5_000) { vm.state.first { it.capturedTopicId != null } }
            val captured = assertNotNull(vm.state.value.capturedTopicId)

            val second = IdeaTopicCaptureViewModel(fixture.service)
            second.lookupExisting(snapshot.key)
            assertEquals(captured, second.state.value.existingTopicId)

            second.submit(IdeaCaptureInput(source = snapshot, content = IdeaTopicContent(title = "重复标题")))
            withTimeout(5_000) { second.state.first { it.capturedTopicId != null } }
            assertEquals(captured, second.state.value.capturedTopicId)
            assertEquals(1, fixture.service.observeSummaries().first().size)
        } finally {
            fixture.close()
        }
    }
}

private class ViewModelFixture {
    val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    val db: DailySatoriDatabase
    val repository: IdeaTopicRepository
    val service: IdeaTopicService
    val port = ViewModelAiPort()
    val workflow: IdeaTopicAiWorkflow
    private var clockMs = 1_000L
    private var idSeq = 0

    init {
        DailySatoriDatabase.Schema.create(driver)
        db = DailySatoriDatabase(driver)
        repository = IdeaTopicRepository(db)
        service = IdeaTopicService(repository, now = { clockMs }, newId = { "gen-${++idSeq}" })
        workflow = IdeaTopicAiWorkflow(service, port, newId = { "ai-${++idSeq}" })
    }

    fun tick() {
        clockMs += 1
    }

    suspend fun captureTopic(title: String, description: String = ""): String {
        val id = db.dailySatoriQueries.let {
            it.insertDiary("来源-$title", null, null, null, clockMs, clockMs)
            lastDiaryId()
        }
        return service.capture(
            IdeaCaptureInput(
                source = IdeaSourceSnapshot(
                    key = IdeaSourceKey(IdeaSourceTypes.Diary, id.toString()),
                    originalTitle = "来源-$title",
                    originalContent = "来源-$title",
                    originalCreatedAt = clockMs,
                    originalRecordId = id.toString(),
                ),
                content = IdeaTopicContent(title = title, description = description),
            ),
        ).topicId
    }

    private fun lastDiaryId(): Long = driver.executeQuery(
        0,
        "SELECT last_insert_rowid()",
        { cursor ->
            cursor.next()
            app.cash.sqldelight.db.QueryResult.Value(cursor.getLong(0)!!)
        },
        0,
    ).value

    fun close() {
        driver.close()
    }
}

private class ViewModelAiPort : IdeaTopicAiPort {
    var replyCalls = 0
    var proposeGate: CompletableDeferred<Unit>? = null
    var proposeResult: IdeaDraftContent = IdeaDraftContent(IdeaTopicContent(title = "草稿"))

    override suspend fun reply(context: IdeaAiContext, onChunk: suspend (String) -> Unit): String {
        replyCalls++
        return "回复"
    }

    override suspend fun summarize(context: IdeaAiContext): String = "摘要"

    override suspend fun propose(context: IdeaAiContext): IdeaDraftContent {
        proposeGate?.await()
        return proposeResult
    }
}
