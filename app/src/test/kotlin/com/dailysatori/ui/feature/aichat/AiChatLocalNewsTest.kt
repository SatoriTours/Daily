package com.dailysatori.ui.feature.aichat

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.dailysatori.service.localnews.LocalNewsBriefing
import com.dailysatori.service.localnews.LocalNewsBriefingReader
import com.dailysatori.service.localnews.LocalNewsBriefingState
import com.dailysatori.service.localnews.LocalNewsIntent
import com.dailysatori.service.localnews.LocalNewsSourceView
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AiChatLocalNewsTest {
    private val todayBriefing = LocalNewsBriefing(
        intent = LocalNewsIntent.Today,
        state = LocalNewsBriefingState.Ready,
        summaryDate = "2026-11-28",
        window = "1330",
        title = "今日要闻标题",
        content = "今日要点正文",
        sources = listOf(LocalNewsSourceView("榜单标题", "hot_news", "知乎")),
        isToday = true,
        latestDate = "2026-11-28",
    )

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun aTodayQuestionAnswersFromTheDeviceWithoutSendingOrAskingForConsent() {
        val conversation = FakeAssistantConversation()
        val reader = RecordingReader { _, _, _ -> todayBriefing }
        val vm = AiChatViewModel(conversation, localNews = reader)

        vm.sendMessage("今天有什么重点的新闻吗？")

        assertEquals(listOf(LocalNewsIntent.Today), reader.intents)
        assertTrue(conversation.sentWithContextCalls.isEmpty(), "本机问法不得进入云端发送")
        assertTrue(conversation.sentCalls.isEmpty())
        assertFalse(conversation.consentValue, "本机读取不得消耗或改变云端授权")
        val turn = requireNotNull(vm.state.value.localNews[vm.state.value.sessionId])
        assertEquals("今天有什么重点的新闻吗？", turn.question)
        assertFalse(turn.isLoading)
        assertEquals("今日要点正文", turn.briefing?.content)
        assertEquals("知乎", turn.briefing?.sources?.single()?.platformName)
        assertFalse(vm.state.value.isProcessing, "本机读取不进入云端等待态")
    }

    @Test
    fun anOrdinaryQuestionKeepsTheCloudPathAndNeverTouchesTheLocalReader() {
        val conversation = FakeAssistantConversation()
        val reader = RecordingReader { _, _, _ -> todayBriefing }
        val vm = AiChatViewModel(conversation, localNews = reader)

        vm.sendMessage("帮我把这周的日记整理成一篇周报")

        assertEquals(1, conversation.sentWithContextCalls.size)
        assertTrue(reader.intents.isEmpty())
        assertTrue(vm.state.value.localNews.isEmpty())
    }

    @Test
    fun theLatestQuestionReadsTheLastBriefingAndMarksItsRealDate() {
        val conversation = FakeAssistantConversation()
        val reader = RecordingReader { intent, _, _ ->
            todayBriefing.copy(intent = intent, summaryDate = "2026-11-25", isToday = false, latestDate = "2026-11-25")
        }
        val vm = AiChatViewModel(conversation, localNews = reader)

        vm.sendMessage("最近一次简报")

        assertEquals(listOf(LocalNewsIntent.Latest), reader.intents)
        val briefing = requireNotNull(vm.state.value.localNews.values.single().briefing)
        assertEquals("2026-11-25", briefing.summaryDate)
        assertFalse(briefing.isToday)
        assertTrue(conversation.sentWithContextCalls.isEmpty())
    }

    @Test
    fun aGrantedCloudConsentDoesNotTurnTheLocalAnswerIntoACloudRequest() {
        val conversation = FakeAssistantConversation().apply { consentValue = true }
        val reader = RecordingReader { _, _, _ -> todayBriefing }
        val vm = AiChatViewModel(conversation, localNews = reader)

        vm.sendMessage("今日要闻")

        assertTrue(conversation.sentWithContextCalls.isEmpty())
        assertEquals(listOf(LocalNewsIntent.Today), reader.intents)
        assertTrue(vm.state.value.cloudConsentAccepted)
    }

    @Test
    fun statesWithoutABriefingStayLocalAndHonest() {
        listOf(
            LocalNewsBriefingState.NotPrepared,
            LocalNewsBriefingState.Generating,
            LocalNewsBriefingState.Failed,
            LocalNewsBriefingState.Empty,
        ).forEach { state ->
            val conversation = FakeAssistantConversation()
            val reader = RecordingReader { _, _, _ ->
                LocalNewsBriefing(LocalNewsIntent.Today, state, summaryDate = "2026-11-28", isToday = true)
            }
            val vm = AiChatViewModel(conversation, localNews = reader)

            vm.sendMessage("今天有什么新闻")

            val briefing = requireNotNull(vm.state.value.localNews.values.single().briefing)
            assertEquals(state, briefing.state)
            assertEquals("", briefing.content)
            assertTrue(conversation.sentWithContextCalls.isEmpty(), "状态 $state 也不得转云端")
            assertFalse(conversation.consentValue)
        }
    }

    @Test
    fun aFailedLocalReadShowsAFailureWithoutReachingTheCloud() {
        val conversation = FakeAssistantConversation()
        val vm = AiChatViewModel(conversation, localNews = { _, _, _ -> error("数据库不可读") })

        vm.sendMessage("今天的新闻有哪些")

        val turn = requireNotNull(vm.state.value.localNews.values.single())
        assertFalse(turn.isLoading)
        assertNull(turn.briefing)
        assertTrue(conversation.sentWithContextCalls.isEmpty())
        assertFalse(conversation.consentValue)
    }

    @Test
    fun switchingSessionsNeverShowsTheOtherSessionsNewsBody() {
        val conversation = FakeAssistantConversation().apply { sessionsFlow.value = listOf("s1", "s2") }
        val reader = RecordingReader { _, _, _ -> todayBriefing }
        val vm = AiChatViewModel(conversation, localNews = reader)

        vm.switchSession("s1")
        vm.sendMessage("今日要闻")
        assertTrue(vm.state.value.localNews.containsKey("s1"))

        vm.switchSession("s2")

        assertNull(vm.state.value.localNews["s2"])
        assertEquals("s1", vm.state.value.localNews.keys.single(), "正文只留在提问的那个会话")
        assertTrue(conversation.sentWithContextCalls.isEmpty())
    }

    @Test
    fun aLateLocalAnswerNeverLandsAfterSwitchingSessionsOrANewerQuestion() {
        val conversation = FakeAssistantConversation().apply { sessionsFlow.value = listOf("s1", "s2") }
        val pending = CompletableDeferred<LocalNewsBriefing>()
        val reader = LocalNewsBriefingReader { _, _, _ -> pending.await() }
        val vm = AiChatViewModel(conversation, localNews = reader)

        vm.switchSession("s1")
        vm.sendMessage("今天有什么新闻")
        assertTrue(requireNotNull(vm.state.value.localNews["s1"]).isLoading)
        vm.switchSession("s2")
        pending.complete(todayBriefing)

        assertTrue(vm.state.value.localNews.isEmpty(), "切换会话后晚回的正文必须丢弃")

        vm.switchSession("s1")
        val late = CompletableDeferred<LocalNewsBriefing>()
        val lateReader = LocalNewsBriefingReader { _, _, _ -> late.await() }
        val second = AiChatViewModel(conversation, localNews = lateReader)
        second.sendMessage("今天有什么新闻")
        second.sendMessage("帮我写今天的日记总结")
        late.complete(todayBriefing)

        assertTrue(second.state.value.localNews.isEmpty(), "下一问（即使走云端）不得再出现旧新闻正文")
        assertEquals(1, conversation.sentWithContextCalls.size)
    }

    @Test
    fun onlyTheAcceptedDraftIsClearedAndOtherSessionsKeepTheirDraft() {
        val conversation = FakeAssistantConversation().apply { sessionsFlow.value = listOf("s1", "s2") }
        val vm = AiChatViewModel(conversation, localNews = { _, _, _ -> todayBriefing })

        vm.switchSession("s1")
        vm.updateDraft(TextFieldValue("今天有什么新闻", TextRange(2, 2)))
        vm.switchSession("s2")
        vm.updateDraft(TextFieldValue("未发送的第二份草稿"))
        vm.switchSession("s1")

        vm.sendMessage("今天有什么新闻")

        assertEquals("", vm.state.value.drafts["s1"]?.value?.text.orEmpty(), "已发送的草稿被清空")
        assertEquals("未发送的第二份草稿", vm.state.value.drafts["s2"]?.value?.text, "其他会话草稿不受影响")
    }

    @Test
    fun aRestoredProcessDoesNotPretendToRestoreTheLocalRound() {
        val conversation = FakeAssistantConversation()
        val vm = AiChatViewModel(conversation, localNews = { _, _, _ -> todayBriefing })

        vm.sendMessage("最近一次简报")

        assertTrue(vm.state.value.localNews.isNotEmpty())
        val restored = AiChatViewModel(conversation, localNews = { _, _, _ -> todayBriefing })
        assertTrue(restored.state.value.localNews.isEmpty(), "本机回合不持久化，进程重建后不再出现")
    }

    @Test
    fun theCardKeepsKnownWindowTimesHidesInternalKeysAndSkipsARepeatedTitle() {
        val known = todayBriefing.copy(window = "1330")
        assertEquals("2026-11-28 13:30", localNewsMetaLabel(known))
        assertEquals("2026-11-26", localNewsMetaLabel(todayBriefing.copy(summaryDate = "2026-11-26", window = "demo_daily")))
        assertFalse(localNewsShowsTitle("今日要闻 · 2026-11-26", "# 今日要闻\n\n正文"), "标题与首个标题重复时不重复展示")
        assertTrue(localNewsShowsTitle("端侧模型与本地工具", "## 今日要点\n\n- 要点"), "不同的真实标题保留")
        assertFalse(localNewsShowsTitle("  ", "正文"))
    }

    @Test
    fun anEarlierRequestThatReturnsFirstNeverOverwritesTheNewerIdenticalQuestion() {
        val conversation = FakeAssistantConversation()
        val pending = PendingReader()
        val vm = AiChatViewModel(conversation, localNews = pending)
        val older = todayBriefing.copy(summaryDate = "2026-11-25", isToday = false, content = "更旧回答")
        val newer = todayBriefing.copy(summaryDate = "2026-11-27", isToday = false, content = "较新回答")

        vm.sendMessage("latest briefing")
        vm.sendMessage("latest briefing")

        pending.pending[0].complete(older)
        val whileSecondPending = requireNotNull(vm.state.value.localNews.values.single())
        assertTrue(whileSecondPending.isLoading, "第一次请求的旧答案不得提前占位")
        assertNull(whileSecondPending.briefing)

        pending.pending[1].complete(newer)
        val answered = requireNotNull(vm.state.value.localNews.values.single())
        assertFalse(answered.isLoading)
        assertEquals("较新回答", answered.briefing?.content)
        assertEquals("2026-11-27", answered.briefing?.summaryDate, "同文重复提问时以最后一次的真实日期为准")
    }

    @Test
    fun aLateResultOfThePreviousRequestDoesNotClearTheNewPendingOneAfterSwitchingBack() {
        val conversation = FakeAssistantConversation().apply { sessionsFlow.value = listOf("s1", "s2") }
        val pending = PendingReader()
        val vm = AiChatViewModel(conversation, localNews = pending)
        val older = todayBriefing.copy(summaryDate = "2026-11-25", isToday = false, content = "更旧回答")
        val newer = todayBriefing.copy(summaryDate = "2026-11-27", isToday = false, content = "较新回答")

        vm.switchSession("s1")
        vm.sendMessage("latest briefing")
        vm.switchSession("s2")
        vm.switchSession("s1")
        vm.sendMessage("latest briefing")

        pending.pending[0].complete(older)
        val stillPending = requireNotNull(vm.state.value.localNews["s1"])
        assertTrue(stillPending.isLoading, "旧请求晚回不得清理新的 pending")

        pending.pending[1].complete(newer)
        assertEquals("较新回答", vm.state.value.localNews["s1"]?.briefing?.content)
    }

    @Test
    fun theQuestionInstantAndZoneAreCapturedWhenTheQuestionIsSent() {
        val standard = StandardTestDispatcher()
        Dispatchers.setMain(standard)
        val reader = RecordingReader { _, _, _ -> todayBriefing }
        val vm = AiChatViewModel(FakeAssistantConversation(), localNews = reader)

        val sentAt = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        vm.sendMessage("latest briefing")
        Thread.sleep(40)
        val afterDelay = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        standard.scheduler.advanceUntilIdle()

        val captured = requireNotNull(reader.receivedAtMs.singleOrNull())
        assertTrue(captured >= sentAt, "时刻不得早于发送")
        assertTrue(captured < sentAt + 25, "时刻应在发送瞬间捕获，而不是协程真正启动时（实际捕获 $captured，发送 $sentAt）")
        assertTrue(captured < afterDelay)
        assertEquals(kotlinx.datetime.TimeZone.currentSystemDefault().id, reader.zoneIds.single())
    }

    private class PendingReader : LocalNewsBriefingReader {
        val pending = mutableListOf<CompletableDeferred<LocalNewsBriefing>>()

        override suspend fun read(intent: LocalNewsIntent, receivedAtMs: Long, timeZoneId: String): LocalNewsBriefing =
            CompletableDeferred<LocalNewsBriefing>().also { pending += it }.await()
    }

    private class RecordingReader(
        private val block: suspend (LocalNewsIntent, Long, String) -> LocalNewsBriefing,
    ) : LocalNewsBriefingReader {
        val intents = mutableListOf<LocalNewsIntent>()
        val receivedAtMs = mutableListOf<Long>()
        val zoneIds = mutableListOf<String>()

        override suspend fun read(intent: LocalNewsIntent, receivedAtMs: Long, timeZoneId: String): LocalNewsBriefing {
            intents += intent
            this.receivedAtMs += receivedAtMs
            zoneIds += timeZoneId
            return block(intent, receivedAtMs, timeZoneId)
        }
    }
}
