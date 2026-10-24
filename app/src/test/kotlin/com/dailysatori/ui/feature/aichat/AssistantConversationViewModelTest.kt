package com.dailysatori.ui.feature.aichat

import com.dailysatori.service.assistant.AssistantActionView
import com.dailysatori.service.assistant.AssistantConversation
import com.dailysatori.service.assistant.AssistantMemoryView
import com.dailysatori.service.assistant.AssistantMessage
import com.dailysatori.service.assistant.AssistantReference
import com.dailysatori.service.assistant.AssistantTurn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

open class FakeAssistantConversation : AssistantConversation {
    val sessionsFlow = MutableStateFlow<List<String>>(listOf("s1"))
    val messagesFlow = MutableStateFlow<List<AssistantMessage>>(emptyList())
    val memoriesFlow = MutableStateFlow<List<AssistantMemoryView>>(emptyList())

    var consentValue = false
    val sentCalls = mutableListOf<Triple<String, String, String>>()
    val sentWithContextCalls = mutableListOf<Triple<String, com.dailysatori.service.diary.DiaryThoughtChatContext?, Boolean>>()
    val cancelledTurns = mutableListOf<Long>()
    val retriedTurns = mutableListOf<Long>()
    val excludedMessages = mutableListOf<Long>()
    val deletedMessages = mutableListOf<Long>()
    val deletedSessions = mutableListOf<String>()
    val forgottenMemories = mutableListOf<Long>()
    val correctedMemories = mutableListOf<Pair<Long, String>>()

    override fun sessions(): Flow<List<String>> = sessionsFlow.asStateFlow()
    override fun messages(sessionId: String): Flow<List<AssistantMessage>> = messagesFlow.asStateFlow()
    override fun memories(): Flow<List<AssistantMemoryView>> = memoriesFlow.asStateFlow()
    override fun cloudConsent(): Boolean = consentValue
    override fun setCloudConsent(accepted: Boolean) { consentValue = accepted }

    override fun send(sessionId: String, clientMessageId: String, content: String): AssistantTurn {
        sentCalls.add(Triple(sessionId, clientMessageId, content))
        return AssistantTurn(
            id = 100L,
            sessionId = sessionId,
            clientMessageId = clientMessageId,
            taskId = 200L,
            receivedAtMs = 123456L,
            timeZoneId = "UTC",
            responseMessageId = null,
            requiresResume = false,
        )
    }

    override fun sendWithContext(
        sessionId: String,
        clientMessageId: String,
        content: String,
        explicitContext: com.dailysatori.service.diary.DiaryThoughtChatContext?,
        includeThoughts: Boolean,
    ): AssistantTurn {
        sentWithContextCalls.add(Triple(content, explicitContext, includeThoughts))
        return send(sessionId, clientMessageId, content)
    }

    override fun cancel(turnId: Long) { cancelledTurns.add(turnId) }
    override fun retry(turnId: Long) { retriedTurns.add(turnId) }
    override fun excludeMessage(messageId: Long) { excludedMessages.add(messageId) }
    override fun deleteMessage(messageId: Long) { deletedMessages.add(messageId) }
    override fun deleteSession(sessionId: String) { deletedSessions.add(sessionId) }
    override fun forgetMemory(memoryId: Long) { forgottenMemories.add(memoryId) }
    override fun correctMemory(memoryId: Long, corrected: String) { correctedMemories.add(memoryId to corrected) }
}

class AssistantConversationViewModelTest {

    @OptIn(ExperimentalCoroutinesApi::class)
    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun provisionalReplyIsStreamingNotPersistableAndIsReplacedByOneFinalReply() {
        val fake = FakeAssistantConversation()
        fake.consentValue = true
        val vm = AiChatViewModel(fake)
        fake.messagesFlow.value = listOf(AssistantMessage(-100L, "assistant", "正在增量生成", 1L,
            turnId = 100L, taskId = 200L, status = "streaming"))
        val partial = vm.state.value.messages.single()
        assertTrue(partial.isStreaming)
        assertTrue(vm.state.value.isProcessing)
        assertEquals(null, partial.messageId)
        assertEquals(null, vm.state.value.finalizedAssistantMessageForPersistence(partial.id))
        vm.deleteMessage(partial)
        assertTrue(fake.deletedMessages.isEmpty(), "a provisional UI identity is not a saved chat row")
        fake.messagesFlow.value = listOf(AssistantMessage(101L, "assistant", "最终回答", 2L,
            turnId = 100L, taskId = 200L, status = "complete"))
        assertEquals(1, vm.state.value.messages.size)
        assertFalse(vm.state.value.messages.single().isStreaming)
        assertFalse(vm.state.value.isProcessing)
        assertEquals(101L, vm.state.value.messages.single().messageId)
    }

    @Test
    fun initialStateLoadsSessionsAndCloudConsent() {
        val fake = FakeAssistantConversation()
        fake.consentValue = true
        val vm = AiChatViewModel(fake)

        val state = vm.state.value
        assertEquals("s1", state.sessionId)
        assertTrue(state.cloudConsentAccepted)
        assertFalse(state.showConsentPrompt)
    }

    @Test
    fun unconsentedShowsPrivacyPrompt() {
        val fake = FakeAssistantConversation()
        fake.consentValue = false
        val vm = AiChatViewModel(fake)

        val state = vm.state.value
        assertFalse(state.cloudConsentAccepted)
        assertTrue(state.showConsentPrompt)

        vm.acceptCloudConsent()
        assertTrue(fake.consentValue)
        assertTrue(vm.state.value.cloudConsentAccepted)
        assertFalse(vm.state.value.showConsentPrompt)
    }

    @Test
    fun clientUuidIsStableAcrossRetriesAndInputIsPreservedUntilSaved() {
        val fake = FakeAssistantConversation()
        fake.consentValue = true
        val vm = AiChatViewModel(fake)

        val draftId = vm.prepareInput("整理今天的日记")
        assertNotNull(draftId)

        // Sending message with draftId
        var cleared = false
        vm.sendMessage("整理今天的日记", draftId, onSuccess = { cleared = true })

        assertTrue(cleared)
        assertEquals(1, fake.sentCalls.size)
        val (session1, clientUuid1, content1) = fake.sentCalls.first()
        assertEquals("s1", session1)
        assertEquals("整理今天的日记", content1)
        assertEquals(draftId, clientUuid1)

        // Simulating retry with the same clientUuid
        vm.retryTurn(100L)
        assertEquals(listOf(100L), fake.retriedTurns)
    }

    @Test
    fun taskStatesExposedCorrectlyInMessages() {
        val fake = FakeAssistantConversation()
        val vm = AiChatViewModel(fake)

        val msgWaiting = AssistantMessage(
            id = 1L, role = "user", content = "hello", createdAt = 1000L,
            turnId = 10L, taskId = 20L, status = "pending",
        )
        val msgProcessing = AssistantMessage(
            id = 2L, role = "user", content = "work", createdAt = 2000L,
            turnId = 11L, taskId = 21L, status = "running",
        )
        val msgFailed = AssistantMessage(
            id = 3L, role = "user", content = "bad", createdAt = 3000L,
            turnId = 12L, taskId = 22L, status = "failed", error = "Model error",
        )
        val msgCancelled = AssistantMessage(
            id = 4L, role = "user", content = "cancel", createdAt = 4000L,
            turnId = 13L, taskId = 23L, status = "cancelled",
        )
        val msgRequiresResume = AssistantMessage(
            id = 5L, role = "user", content = "resume", createdAt = 5000L,
            turnId = 14L, taskId = 24L, status = "requires_resume",
        )

        fake.messagesFlow.value = listOf(msgWaiting, msgProcessing, msgFailed, msgCancelled, msgRequiresResume)

        val messages = vm.state.value.messages
        assertEquals(5, messages.size)
        assertEquals(TaskDisplayState.Waiting, messages[0].taskDisplayState)
        assertEquals(TaskDisplayState.Processing, messages[1].taskDisplayState)
        assertEquals(TaskDisplayState.Failed, messages[2].taskDisplayState)
        assertEquals(TaskDisplayState.Cancelled, messages[3].taskDisplayState)
        assertEquals(TaskDisplayState.RequiresResume, messages[4].taskDisplayState)
    }

    @Test
    fun memoryOperationsDelegateDirectlyToConversation() {
        val fake = FakeAssistantConversation()
        val vm = AiChatViewModel(fake)

        vm.forgetMemory(42L)
        assertEquals(listOf(42L), fake.forgottenMemories)

        vm.correctMemory(42L, "更新后的事实")
        assertEquals(listOf(42L to "更新后的事实"), fake.correctedMemories)

        vm.excludeMessage(99L)
        assertEquals(listOf(99L), fake.excludedMessages)

        vm.deleteMessage(99L)
        assertEquals(listOf(99L), fake.deletedMessages)

        vm.deleteSession("s1")
        assertEquals(listOf("s1"), fake.deletedSessions)
    }

    @Test
    fun unconsentedSendingPersistsMessageLocallyAndPromptsConsent() {
        val fake = FakeAssistantConversation()
        fake.consentValue = false
        val vm = AiChatViewModel(fake)

        // Sending when unconsented still sends to conversation (local persistence first)
        var cleared = false
        vm.sendMessage("未授权测试输入", onSuccess = { cleared = true })
        assertTrue(cleared)
        assertEquals(1, fake.sentCalls.size)
        assertEquals("未授权测试输入", fake.sentCalls.first().third)

        // Simulate backend failing with cloud_consent_required and requires_resume
        val msgRequiresResume = AssistantMessage(
            id = 1L, role = "user", content = "未授权测试输入", createdAt = 1000L,
            turnId = 101L, taskId = 201L, status = "requires_resume", error = "cloud_consent_required",
        )
        fake.messagesFlow.value = listOf(msgRequiresResume)

        // State displays RequiresResume
        val messages = vm.state.value.messages
        assertEquals(1, messages.size)
        assertEquals(TaskDisplayState.RequiresResume, messages[0].taskDisplayState)

        // When user agrees to consent, acceptCloudConsent retries the turn
        vm.acceptCloudConsent()
        assertTrue(fake.consentValue)
        assertEquals(listOf(101L), fake.retriedTurns)
    }

    @Test
    fun sendFailurePreservesDraftAndDoesNotTriggerSuccess() {
        val fake = object : FakeAssistantConversation() {
            override fun send(sessionId: String, clientMessageId: String, content: String): AssistantTurn {
                throw IllegalStateException("Database write failed")
            }
        }
        val vm = AiChatViewModel(fake)

        val draftId = vm.prepareInput("待保存的日记内容")
        var cleared = false
        vm.sendMessage("待保存的日记内容", draftId, onSuccess = { cleared = true })

        assertFalse(cleared)
        assertEquals(draftId, vm.prepareInput("待保存的日记内容"))
        assertNotNull(vm.uiErrors.value)
        assertTrue(vm.uiErrors.value!!.message.contains("发送失败"))
    }

    @Test
    fun sendWithExplicitContextPassesContextToConversationWithoutMutatingContent() {
        val fake = FakeAssistantConversation()
        val vm = AiChatViewModel(fake)

        val context = com.dailysatori.service.diary.DiaryThoughtChatContext(
            prompt = "测试日记上下文提示词",
            references = emptyList(),
        )

        var successCalled = false
        vm.sendMessage(
            content = "帮我分析日记",
            explicitContext = context,
            includeThoughts = true,
            onSuccess = { successCalled = true },
        )

        assertTrue(successCalled)
        assertEquals(1, fake.sentCalls.size)
        assertEquals("帮我分析日记", fake.sentCalls.first().third)

        assertEquals(1, fake.sentWithContextCalls.size)
        val (sentContent, sentContext, sentIncludeThoughts) = fake.sentWithContextCalls.first()
        assertEquals("帮我分析日记", sentContent)
        assertEquals(context, sentContext)
        assertTrue(sentIncludeThoughts)
    }

    @Test
    fun newUnpersistedSessionIsNotOverwrittenByOlderSessionsFlowEmit() {
        val fake = FakeAssistantConversation()
        fake.sessionsFlow.value = listOf("s1")
        val vm = AiChatViewModel(fake)

        assertEquals("s1", vm.state.value.sessionId)

        val newSessionId = vm.startNewSession()
        assertTrue(newSessionId.startsWith("chat_"))
        assertEquals(newSessionId, vm.state.value.sessionId)

        // Simulating memory/store.changed triggering an emit of older persisted sessions: listOf("s1", "s2")
        fake.sessionsFlow.value = listOf("s1", "s2")

        // The active unpersisted session must NOT be overwritten back to s1
        assertEquals(newSessionId, vm.state.value.sessionId)

        // Sending a message targets the new session id
        vm.sendMessage(content = "hello from new session")
        assertEquals(1, fake.sentCalls.size)
        assertEquals(newSessionId, fake.sentCalls.first().first)
    }

    @Test
    fun deletedPersistedSessionFallsBackProperly() {
        val fake = FakeAssistantConversation()
        fake.sessionsFlow.value = listOf("s1", "s2")
        val vm = AiChatViewModel(fake)

        assertEquals("s1", vm.state.value.sessionId)

        // Simulating that s1 was deleted and sessions flow emits updated list
        fake.sessionsFlow.value = listOf("s2")

        assertEquals("s2", vm.state.value.sessionId)
    }

    @Test
    fun startNewSessionAvoidsSameIdCollisionWithinSameMillisecond() {
        val fake = FakeAssistantConversation()
        val vm = AiChatViewModel(fake)

        val id1 = vm.startNewSession()
        val id2 = vm.startNewSession()

        // Even if called rapidly in the same millisecond, UUID guarantees globally distinct IDs
        kotlin.test.assertNotEquals(id1, id2)
    }
}
