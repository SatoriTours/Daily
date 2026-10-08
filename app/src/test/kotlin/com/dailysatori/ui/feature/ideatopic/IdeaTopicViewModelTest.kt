package com.dailysatori.ui.feature.ideatopic

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
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

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class IdeaTopicViewModelTest {
    @Test
    fun openingATopicStartsAnEmptyDiscussionWithoutCreatingOrCallingAi() = runBlocking<Unit> {
        val fixture = ViewModelFixture()
        try {
            val topic = fixture.captureTopic("直接反馈")
            val vm = IdeaTopicSessionViewModel(topic, fixture.service, fixture.workflow).also(fixture::track)
            assertNull(vm.state.value.error)
            assertEquals(topic, vm.state.value.topicId)
            assertTrue(fixture.service.sessionsSync(topic).isEmpty())
            assertEquals(0, fixture.port.replyCalls)
            assertEquals(0, fixture.port.summaryCalls)
        } finally { fixture.close() }
    }

    @Test
    fun topicFeedbackCreatesOneDiscussionAndReopeningRetainsItsSummaryAndContext() = runBlocking<Unit> {
        val fixture = ViewModelFixture()
        try {
            val topic = fixture.captureTopic("持续反馈")
            val vm = IdeaTopicSessionViewModel(topic, fixture.service, fixture.workflow).also(fixture::track)
            vm.send("访谈整理有用，但权限边界还没验证")
            withTimeout(5_000) { vm.state.first { it.messages.size == 2 && !it.busy } }
            val session = vm.state.value.sessionId
            vm.summarize()
            withTimeout(5_000) { vm.state.first { it.summary == "摘要" && !it.busy } }
            fixture.tick()
            vm.send("我补充了权限限制，还是不合适，先搁置")
            withTimeout(5_000) { vm.state.first { it.messages.size == 4 && !it.busy } }
            assertTrue(fixture.port.lastReplyContext!!.messages.any { it.content == "访谈整理有用，但权限边界还没验证" })
            assertTrue(fixture.port.lastReplyContext!!.userPrompt.contains("摘要"))
            val reopened = IdeaTopicSessionViewModel(topic, fixture.service, fixture.workflow).also(fixture::track)
            withTimeout(5_000) { reopened.state.first { it.messages.size == 4 } }
            assertEquals(session, reopened.state.value.sessionId)
            assertEquals("摘要", reopened.state.value.summary)
            assertEquals(com.dailysatori.service.ideatopic.IdeaSessionSummaryStatus.NeedsUpdate, reopened.state.value.summaryStatus)
            assertEquals(1, fixture.service.sessionsSync(topic).size)
            assertEquals(2, fixture.port.replyCalls)
            assertEquals(1, fixture.port.summaryCalls)
        } finally { fixture.close() }
    }

    @Test
    fun openingATopicContinuesLatestUserDiscussionNotLatestSummaryOrEmptySession() = runBlocking<Unit> {
        val fixture = ViewModelFixture()
        try {
            val topic = fixture.captureTopic("多个旧讨论")
            val first = fixture.service.createSession(topic, "旧讨论")
            fixture.workflow.send(first, "旧反馈")
            fixture.tick()
            val latest = fixture.service.createSession(topic, "最近讨论")
            fixture.workflow.send(latest, "最近反馈")
            fixture.tick()
            fixture.workflow.summarize(first)
            fixture.tick()
            fixture.service.createSession(topic, "更晚的空讨论")
            val vm = IdeaTopicSessionViewModel(topic, fixture.service, fixture.workflow).also(fixture::track)
            assertEquals(latest, vm.state.value.sessionId)
            withTimeout(5_000) { vm.state.first { it.messages.isNotEmpty() } }
            assertEquals("最近反馈", vm.state.value.messages.single { it.role == IdeaMessageRoles.User }.content)
            assertEquals(3, fixture.service.sessionsSync(topic).size)
        } finally { fixture.close() }
    }

    @Test
    fun retryingAFailedReplyKeepsOneSavedFeedbackAndOneReply() = runBlocking<Unit> {
        val fixture = ViewModelFixture()
        try {
            val topic = fixture.captureTopic("反馈不会丢")
            val session = fixture.service.createSession(topic, "讨论")
            val vm = IdeaTopicSessionViewModel(session, fixture.service, fixture.workflow).also(fixture::track)
            fixture.port.failReply = true
            vm.send("这个方向暂时行不通")
            withTimeout(5_000) { vm.state.first { it.messages.any { m -> m.status == IdeaMessageStatus.Failed } && !it.busy } }
            val failedReplyId = vm.state.value.messages.last().id
            assertEquals("这个方向暂时行不通", fixture.service.messagesSync(session).first().content)
            fixture.port.failReply = false
            vm.retry(failedReplyId)
            withTimeout(5_000) { vm.state.first { fixture.port.replyCalls == 2 && !it.busy } }
            val saved = fixture.service.messagesSync(session)
            assertEquals(2, saved.size)
            assertEquals(1, saved.count { it.role == IdeaMessageRoles.User })
            assertEquals(failedReplyId, saved.last().id)
            assertEquals(IdeaMessageStatus.Complete, saved.last().status)
        } finally { fixture.close() }
    }

    @Test
    fun feedbackAndRepliesKeepTheirCausalOrderWhenTheClockDoesNotAdvance() = runBlocking<Unit> {
        val fixture = ViewModelFixture()
        try {
            val topic = fixture.captureTopic("同一时刻继续聊")
            val session = fixture.service.createSession(topic, "讨论")
            repeat(6) { fixture.workflow.send(session, "反馈 $it") }
            val saved = fixture.service.messagesSync(session)
            assertEquals(List(6) { listOf(IdeaMessageRoles.User, IdeaMessageRoles.Assistant) }.flatten(), saved.map { it.role })
            assertEquals((0..5).map { "反馈 $it" }, saved.filter { it.role == IdeaMessageRoles.User }.map { it.content })
        } finally { fixture.close() }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun openingAnotherCaptureClearsThePreviousCompletion() = runBlocking<Unit> {
        val fixture = ViewModelFixture()
        try {
            val vm = IdeaTopicCaptureViewModel(fixture.service).also(fixture::track)
            val source = IdeaSourceSnapshot(IdeaSourceKey(IdeaSourceTypes.Diary, "101"), "来源一", "正文一")
            vm.lookupExisting(source.key)
            vm.submit(IdeaCaptureInput(source, IdeaTopicContent(title = "主题一")))
            withTimeout(5_000) { vm.state.first { it.capturedTopicId != null } }
            vm.lookupExisting(IdeaSourceKey(IdeaSourceTypes.Diary, "102"))
            assertNull(vm.state.value.capturedTopicId, "another sheet must not navigate to the previous topic")
            assertNull(vm.state.value.existingTopicId)
        } finally { fixture.close() }
    }

    @Test
    fun mergingAnOpenDetailFollowsTheTargetAndItsFutureUpdates() = runBlocking<Unit> {
        val fixture = ViewModelFixture()
        try {
            val from = fixture.captureTopic("合并前")
            val target = fixture.captureTopic("目标内容")
            val vm = IdeaTopicDetailViewModel(from, fixture.service, fixture.workflow).also(fixture::track)
            withTimeout(5_000) { vm.state.first { it.detail != null } }
            vm.merge(target)
            withTimeout(5_000) { vm.state.first { it.mergedInto == target } }
            assertEquals(target, vm.state.value.topicId)
            withTimeout(5_000) { vm.state.first { it.detail?.topic?.id == target } }
            fixture.service.updateContent(target, IdeaTopicContent(title = "继续更新目标"))
            withTimeout(5_000) { vm.state.first { it.detail?.topic?.content?.title == "继续更新目标" } }
            assertEquals(from, vm.state.value.requestedTopicId)
        } finally { fixture.close() }
    }

    @Test
    fun anAlreadyOpenDetailObservesRequestsStartedElsewhere() = runBlocking<Unit> {
        val fixture = ViewModelFixture()
        val gate = CompletableDeferred<Unit>()
        try {
            val topicId = fixture.captureTopic("活跃请求")
            val vm = IdeaTopicDetailViewModel(topicId, fixture.service, fixture.workflow).also(fixture::track)
            withTimeout(5_000) { vm.state.first { it.detail != null } }
            fixture.port.proposeGate = gate
            val job = launch { fixture.workflow.propose(topicId) }
            withTimeout(5_000) { while (!fixture.workflow.isBusy(topicId)) delay(10) }
            delay(30)
            val observedBusy = vm.state.value.busy
            fixture.workflow.cancel(topicId)
            gate.complete(Unit)
            job.join()
            assertTrue(observedBusy, "busy state must update without reopening the detail")
            withTimeout(5_000) { vm.state.first { !it.busy } }
        } finally { gate.complete(Unit); fixture.close() }
    }

    @Test
    fun theFirstConversationExposesSummaryActionBeforeAnySummaryExists() = runBlocking<Unit> {
        val fixture = ViewModelFixture()
        try {
            val topic = fixture.captureTopic("首次总结")
            val session = fixture.service.createSession(topic, "沟通")
            val vm = IdeaTopicSessionViewModel(session, fixture.service, fixture.workflow).also(fixture::track)
            assertFalse(vm.state.value.canSummarize)
            vm.send("先讨论问题")
            withTimeout(5_000) { vm.state.first { it.messages.size == 2 && !it.busy } }
            assertEquals("", vm.state.value.summary)
            assertTrue(vm.state.value.canSummarize)
            vm.summarize()
            withTimeout(5_000) { vm.state.first { it.summary.isNotBlank() } }
        } finally { fixture.close() }
    }

    @Test
    fun listSortsFiltersAndSearchesMainTopicsOnly() = runBlocking {
        val fixture = ViewModelFixture()
        try {
            val vm = IdeaTopicListViewModel(fixture.service).also(fixture::track)
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
            val vm = IdeaTopicListViewModel(fixture.service).also(fixture::track)
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

            val mergedVm = IdeaTopicDetailViewModel(fromTopic, fixture.service, fixture.workflow).also(fixture::track)
            withTimeout(5_000) { mergedVm.state.first { it.detail != null } }
            assertEquals(intoTopic, mergedVm.state.value.topicId)
            assertEquals(intoTopic, mergedVm.state.value.mergedInto)

            val deletedVm = IdeaTopicDetailViewModel(fromTopic, fixture.service, fixture.workflow).also(fixture::track)
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

            val vm = IdeaTopicDetailViewModel(topicId, fixture.service, fixture.workflow).also(fixture::track)
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
            val vm = IdeaTopicDetailViewModel(topicId, fixture.service, fixture.workflow).also(fixture::track)
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
            assertNotNull(preview.proposal)
            assertEquals("AI 新标题", preview.proposal?.content?.title)

            // User edits the proposal before applying
            val edited = preview.proposal!!.content.copy(title = "人工修改后标题")
            vm.applyDraft(preview.draftId, edited)
            withTimeout(5_000) { vm.state.first { it.detail?.topic?.content?.title == "人工修改后标题" } }
            assertEquals("人工修改后标题", vm.state.value.detail!!.topic.content.title)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun detailExposesMergeCandidatesAndCreateSession() = runBlocking {
        val fixture = ViewModelFixture()
        try {
            val mainTopic = fixture.captureTopic("主主题")
            val targetTopic = fixture.captureTopic("候选合并目标")
            val vm = IdeaTopicDetailViewModel(mainTopic, fixture.service, fixture.workflow).also(fixture::track)
            withTimeout(5_000) { vm.state.first { it.detail != null && it.mergeCandidates.isNotEmpty() } }

            assertEquals(listOf(targetTopic), vm.state.value.mergeCandidates.map { it.id })

            var createdSessionId: String? = null
            vm.createSession("新沟通") { id -> createdSessionId = id }
            withTimeout(5_000) { while (createdSessionId == null) delay(10) }
            val created = assertNotNull(createdSessionId)

            val sessions = fixture.service.sessionsSync(mainTopic)
            assertTrue(sessions.any { it.id == created && it.title == "新沟通" })
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

            val vm = IdeaTopicSessionViewModel(sessionId, fixture.service, fixture.workflow).also(fixture::track)
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
            val vm = IdeaTopicCaptureViewModel(fixture.service).also(fixture::track)
            vm.lookupExisting(snapshot.key)
            assertNull(vm.state.value.existingTopicId)

            vm.submit(IdeaCaptureInput(source = snapshot, content = IdeaTopicContent(title = "收录标题")))
            withTimeout(5_000) { vm.state.first { it.capturedTopicId != null } }
            val captured = assertNotNull(vm.state.value.capturedTopicId)

            val second = IdeaTopicCaptureViewModel(fixture.service).also(fixture::track)
            second.lookupExisting(snapshot.key)
            assertEquals(captured, second.state.value.existingTopicId)

            second.submit(IdeaCaptureInput(source = snapshot, content = IdeaTopicContent(title = "重复标题")))
            withTimeout(5_000) { second.state.first { it.capturedTopicId != null } }
            assertEquals(captured, second.state.value.capturedTopicId)
            assertEquals(1, fixture.service.observeSummaries().first().size)
            assertTrue(second.state.value.existingTopics.any { it.id == captured })
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
    private val models = mutableListOf<ViewModel>()

    fun track(model: ViewModel) { models += model }
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
        val jobs = models.mapNotNull { it.viewModelScope.coroutineContext[Job] }
        ViewModelStore().run {
            models.forEachIndexed { index, model -> put(index.toString(), model) }
            clear()
        }
        runBlocking { withTimeout(5_000) { jobs.forEach { it.join() } } }
        driver.close()
    }
}

private class ViewModelAiPort : IdeaTopicAiPort {
    var replyCalls = 0
    var summaryCalls = 0
    var failReply = false
    var lastReplyContext: IdeaAiContext? = null
    var proposeGate: CompletableDeferred<Unit>? = null
    var proposeResult: IdeaDraftContent = IdeaDraftContent(IdeaTopicContent(title = "草稿"))

    override suspend fun reply(context: IdeaAiContext, onChunk: suspend (String) -> Unit): String {
        replyCalls++
        lastReplyContext = context
        if (failReply) throw com.dailysatori.service.ideatopic.IdeaTopicException(IdeaTopicError.AiNotConfigured)
        return "回复"
    }

    override suspend fun summarize(context: IdeaAiContext): String {
        summaryCalls++
        return "摘要"
    }

    override suspend fun propose(context: IdeaAiContext): IdeaDraftContent {
        proposeGate?.await()
        return proposeResult
    }
}
