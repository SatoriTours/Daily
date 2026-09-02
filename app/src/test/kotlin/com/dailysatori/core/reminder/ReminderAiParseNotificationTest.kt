package com.dailysatori.core.reminder

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ReminderAiParseNotificationTest {
    @Test
    fun androidNotifierPostsProductionReadyAndFailurePayloadsWithBatchOnlyNavigation() {
        val poster = RecordingPoster()
        val notifier = AndroidReminderAiParseNotification(poster)

        notifier.notifyReady("ready-42")
        notifier.notifyFailed("failed-42")

        assertEquals(listOf("ready-42", "failed-42"), poster.posts.map { it.batchId })
        assertEquals(ReminderAiParseNotifier.ACTION_VIEW_BATCH, poster.posts.first().action)
        assertEquals(mapOf(ReminderAiParseNotifier.EXTRA_BATCH_ID to "ready-42"), poster.posts.first().extras)
        assertEquals(ReminderAiParseNotificationPendingIntentFlags, poster.posts.first().pendingIntentFlags)
        assertEquals("提醒已解析，等待确认", poster.posts.first().copy.title)
        assertEquals("提醒解析失败，点击处理", poster.posts.last().copy.title)
    }

    @Test
    fun composeConsumptionNavigatesOnceThenClearsThePersistedOpenRequest() {
        val openRequest = ReminderAiBatchOpenRequestState()
        val routes = mutableListOf<String>()
        openRequest.open("batch-42")

        consumeReminderAiBatchOpenRequest(openRequest) { batchId -> routes += batchId }
        consumeReminderAiBatchOpenRequest(openRequest) { batchId -> routes += batchId }

        assertEquals(listOf("batch-42"), routes)
        assertEquals(null, openRequest.pending.value)
    }
    @Test
    fun batchIdentityIsUniqueAndCarriesOnlyTheBatchId() {
        val first = ReminderAiParseNotificationIdentity("batch-one")
        val second = ReminderAiParseNotificationIdentity("batch-two")

        assertNotEquals(first.uri, second.uri)
        assertEquals("batch-one", first.batchId)
        assertTrue(first.uri.contains("batch-one"))
    }

    @Test
    fun readyAndFailedNotificationsUseDistinctUserFacingCopy() {
        assertEquals("提醒已解析，等待确认", reminderAiParseNotificationCopy(ready = true).title)
        assertEquals("点击确认提醒草稿", reminderAiParseNotificationCopy(ready = true).text)
        assertEquals("提醒解析失败，点击处理", reminderAiParseNotificationCopy(ready = false).title)
        assertEquals("查看原文并重新解析", reminderAiParseNotificationCopy(ready = false).text)
    }

    private class RecordingPoster : ReminderAiParseNotificationPoster {
        val posts = mutableListOf<ReminderAiParseNotificationPost>()
        override fun post(post: ReminderAiParseNotificationPost) { posts += post }
    }
}
