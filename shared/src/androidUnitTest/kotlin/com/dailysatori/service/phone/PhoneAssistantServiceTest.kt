package com.dailysatori.service.phone

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.shared.db.DailySatoriDatabase
import com.dailysatori.data.repository.*
import com.dailysatori.bookkeeping.*
import com.dailysatori.service.sms.*
import com.dailysatori.service.security.SecretValueCipher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.Channel
import kotlinx.datetime.*
import kotlin.test.*

class PhoneAssistantServiceTest {
    private val now = Instant.parse("2026-10-05T15:00:00Z")

    @Test fun bothChannelsProduceBothKindsOfResults() = runBlocking {
        withService { service, records, ledger, sms, settings ->
            PhoneChannel.entries.forEach { channel ->
                service.configure(channel, PhoneOptions(enabled = true))
                if (channel == PhoneChannel.NOTIFICATION) service.selectSource("bank.app", true)
                val amount = if (channel == PhoneChannel.SMS) 100 else 200
                val event = event(channel, channel.name, "已付款${amount}元，请取件").copy(origin = if (channel == PhoneChannel.SMS) "bank" else "bank.app")
                assertNotNull(service.accept(event))
                val row = assertNotNull(records.get(event.id))
                service.process(row.id)
                val result = assertNotNull(records.get(row.id))
                assertEquals(PhoneResultState.DONE, result.todos.single().state)
                assertEquals(PhoneResultState.DONE, result.ledgerState)
                assertEquals(LedgerStatus.POSTED, ledger.snapshot().entries.first { it.id == result.ledgerId }.status)
                assertNotNull(sms.get(result.todos.single().id)?.reminderId)
            }
        }
    }

    @Test fun unrelatedTextsAreKeptForLaterAlgorithmsWhileVerificationCodesAreNot() = runBlocking {
        withService { service, records, _, _, _ ->
            service.configure(PhoneChannel.SMS, PhoneOptions(enabled = true))
            // Texts without a result are still stored so a future algorithm can process them again.
            listOf("今天真开心", "消费满100元可领取优惠券").forEachIndexed { i, text ->
                assertNull(service.accept(event(PhoneChannel.SMS, "kept$i", text)))
            }
            assertEquals(2, records.all().size)
            assertTrue(records.all().all { !it.textErased && it.todos.isEmpty() &&
                it.ledgerState == PhoneResultState.SKIPPED })
            // Verification codes stay out of storage entirely.
            assertNull(service.accept(event(PhoneChannel.SMS, "otp", "验证码123456，支付56元")))
            assertEquals(2, records.all().size)
        }
    }

    @Test fun purposeSwitchesAreIndependentAndLegacySettingsArePreserved() = runBlocking {
        withService { service, records, ledger, sms, settings ->
            settings.upsert(SmsReminderService.ENABLED_KEY, "true")
            settings.upsert("bookkeeping.enabled", "true")
            assertFalse(service.preferences().sms.ledger)
            assertFalse(service.preferences().notification.todos)
            service.configure(PhoneChannel.SMS, PhoneOptions(enabled = true, todos = false))
            val event = event(PhoneChannel.SMS, "one", "已付款100元，请取件")
            service.accept(event); service.process(event.id)
            assertTrue(records.get(event.id)!!.todos.isEmpty())
            assertEquals(1, ledger.snapshot().entries.size)
            assertTrue(sms.all().isEmpty())
        }
    }

    @Test fun multipleTodosAreIndependentFromTheLedger() = runBlocking {
        withService { service, records, ledger, sms, _ ->
            service.configure(PhoneChannel.SMS, PhoneOptions(enabled = true))
            val event = event(PhoneChannel.SMS, "multi", "已付款100元，请取件；请续费服务")
            service.accept(event)
            val todo = records.get(event.id)!!.todos.first()
            service.ignoreTodo(event.id, todo.id)
            service.process(event.id)
            val result = records.get(event.id)!!
            assertEquals(listOf(PhoneResultState.IGNORED, PhoneResultState.DONE), result.todos.map { it.state })
            assertEquals(PhoneResultState.DONE, result.ledgerState)
            assertEquals(1, ledger.snapshot().entries.size)
            assertNull(sms.get(todo.id)?.reminderId)
        }
    }

    @Test fun disablingAndReenablingInvalidatesQueuedWorkUntilExplicitRetry() = runBlocking {
        withService { service, records, ledger, _, _ ->
            service.configure(PhoneChannel.SMS, PhoneOptions(enabled = true))
            val event = event(PhoneChannel.SMS, "one", "已付款100元，请取件")
            service.accept(event)
            service.configure(PhoneChannel.SMS, PhoneOptions(enabled = false))
            service.configure(PhoneChannel.SMS, PhoneOptions(enabled = true))
            service.process(event.id)
            assertTrue(ledger.snapshot().entries.isEmpty())
            assertNotNull(service.retry(event.id))
            service.process(event.id)
            assertEquals(PhoneResultState.DONE, records.get(event.id)!!.ledgerState)
        }
    }

    @Test fun notificationUpdateEnrichesPendingResultAndPreservesConfirmedResult() = runBlocking {
        withService { service, records, ledger, _, _ ->
            service.configure(PhoneChannel.NOTIFICATION, PhoneOptions(enabled = true))
            service.selectSource("bank", true)
            val event = event(PhoneChannel.NOTIFICATION, "update", "支付成功")
            service.accept(event); service.process(event.id)
            assertEquals(PhoneResultState.PENDING, records.get(event.id)!!.ledgerState)
            service.accept(event.copy(body = "支付成功56元")); service.process(event.id)
            val id = records.get(event.id)!!.ledgerId
            service.editLedger(id, "58", "USD", LedgerKind.INCOME, "修正")
            service.accept(event.copy(body = "支付成功99元")); service.process(event.id)
            assertEquals(5800L, ledger.snapshot().entries.single().amountMinor)
            assertEquals("USD", ledger.snapshot().entries.single().currency)
        }
    }

    @Test fun clearingSourceDoesNotRemoveResultsOrAllowRecreation() = runBlocking {
        withService { service, records, ledger, sms, _ ->
            service.configure(PhoneChannel.SMS, PhoneOptions(enabled = true))
            val event = event(PhoneChannel.SMS, "one", "已付款100元，请取件")
            service.accept(event); service.process(event.id)
            service.clearText(event.id)
            val result = records.get(event.id)!!
            assertEquals("", result.event.body)
            assertEquals("", ledger.snapshot().entries.single().text)
            assertEquals("", sms.get(result.todos.single().id)!!.source.body)
            assertNotNull(sms.get(result.todos.single().id)!!.reminderId)
            assertNull(service.accept(event))
        }
    }

    @Test fun ambiguousDatesStayPendingWithoutInventedReminder() = runBlocking {
        withService { service, records, _, sms, _ ->
            service.configure(PhoneChannel.SMS, PhoneOptions(enabled = true))
            val event = event(PhoneChannel.SMS, "dates", "请明天或后天取件")
            service.accept(event); service.process(event.id)
            val todo = records.get(event.id)!!.todos.single()
            assertEquals(PhoneResultState.PENDING, todo.state)
            assertNull(sms.get(todo.id)?.reminderId)
        }
    }

    @Test fun cloudResultsAreDiscardedAfterDisableAndReenable() = runBlocking {
        val started = Channel<Unit>()
        val release = Channel<Unit>()
        val remote = SmsReminderRemote { input ->
            started.send(Unit); release.receive()
            SmsAiResult(true, "pickup", "领取包裹", "", input.text, null)
        }
        withService(remote) { service, records, ledger, sms, _ ->
            service.configure(PhoneChannel.SMS, PhoneOptions(enabled = true, cloud = true))
            val event = event(PhoneChannel.SMS, "cloud", "已付款100元，请取件")
            service.accept(event)
            coroutineScope {
                val job = launch { service.process(event.id) }
                started.receive()
                service.configure(PhoneChannel.SMS, PhoneOptions(enabled = false))
                service.configure(PhoneChannel.SMS, PhoneOptions(enabled = true, cloud = true))
                release.send(Unit); job.join()
            }
            assertEquals(1, ledger.snapshot().entries.size)
            assertNull(sms.get(records.get(event.id)!!.todos.single().id)?.reminderId)
        }
    }

    @Test fun manualConfirmationWinsAgainstInFlightAi() = runBlocking {
        val started = Channel<Unit>(); val release = Channel<Unit>()
        val remote = SmsReminderRemote { input ->
            started.send(Unit); release.receive()
            SmsAiResult(true, "pickup", "领取包裹", "", input.text, null)
        }
        withService(remote) { service, records, _, sms, _ ->
            service.configure(PhoneChannel.SMS, PhoneOptions(enabled = true, cloud = true))
            val event = event(PhoneChannel.SMS, "manual", "请取件")
            service.accept(event)
            val todo = records.get(event.id)!!.todos.single()
            coroutineScope {
                val job = launch { service.process(event.id) }
                started.receive()
                service.confirmTodo(event.id, todo.id, SmsReminderDraft("用户确认的事项"))
                release.send(Unit); job.join()
            }
            assertEquals("用户确认的事项", sms.get(todo.id)!!.draft!!.title)
        }
    }

    @Test fun notificationHasSeparateCloudConsentAndSmsMirrorsAreSkipped() = runBlocking {
        withService { service, records, _, _, settings ->
            settings.upsert(SmsReminderService.CLOUD_KEY, "true")
            service.configure(PhoneChannel.SMS, PhoneOptions(enabled = true, cloud = true))
            service.configure(PhoneChannel.NOTIFICATION, PhoneOptions(enabled = true))
            service.selectSource("bank", true)
            val event = event(PhoneChannel.NOTIFICATION, "notification", "请取件")
            service.accept(event); service.process(event.id)
            assertEquals(PhoneResultState.DONE, records.get(event.id)!!.todos.single().state)
            assertNull(service.accept(event.copy(key = "mirror", mirrorsSms = true)))
        }
    }

    @Test fun sourceUpdatesKeepGeneratedTodosWhenOnlyTheLedgerIsEnabled() = runBlocking {
        withService { service, records, _, _, _ ->
            service.configure(PhoneChannel.SMS, PhoneOptions(enabled = true))
            val event = event(PhoneChannel.SMS, "retain", "请取件")
            service.accept(event); service.process(event.id)
            val todo = records.get(event.id)!!.todos.single()
            service.configure(PhoneChannel.SMS, PhoneOptions(enabled = true, todos = false))
            service.accept(event.copy(body = "消费56元")); service.process(event.id)
            assertEquals(todo, records.get(event.id)!!.todos.single())
        }
    }

    @Test fun differentActionsWithTheSameCategoryAndDeadlineAreBothCreated() = runBlocking {
        withService { service, records, _, sms, _ ->
            service.configure(PhoneChannel.SMS, PhoneOptions(enabled = true))
            val event = event(PhoneChannel.SMS, "two_bills", "请于2026-10-06 17:30前缴费水费；请于2026-10-06 17:30前缴费电费")
            service.accept(event); service.process(event.id)
            val tasks = records.get(event.id)!!.todos
            assertEquals(2, tasks.size)
            assertTrue(tasks.all { it.state == PhoneResultState.DONE })
            assertEquals(2, tasks.mapNotNull { sms.get(it.id)?.reminderId }.distinct().size)
        }
    }

    @Test fun ambiguousPaymentIsNotAutomaticallyAnOutstandingBill() = runBlocking {
        withService { service, records, _, sms, _ ->
            service.configure(PhoneChannel.SMS, PhoneOptions(enabled = true))
            val event = event(PhoneChannel.SMS, "unclear", "付款56元")
            service.accept(event); service.process(event.id)
            val todo = records.get(event.id)!!.todos.single()
            assertEquals(PhoneResultState.PENDING, todo.state)
            assertNull(sms.get(todo.id)?.reminderId)
        }
    }

    @Test fun changedWordingCannotRecreateAnIgnoredTodo() = runBlocking {
        withService { service, records, _, sms, _ ->
            service.configure(PhoneChannel.SMS, PhoneOptions(enabled = true))
            val event = event(PhoneChannel.SMS, "ignored", "请取件")
            service.accept(event)
            val todo = records.get(event.id)!!.todos.single()
            service.ignoreTodo(event.id, todo.id)
            service.accept(event.copy(body = "请取货")); service.process(event.id)
            assertEquals(listOf(PhoneResultState.IGNORED), records.get(event.id)!!.todos.map { it.state })
            assertNull(sms.get(todo.id)?.reminderId)
        }
    }

    @Test fun notificationUpdatesDoNotReuseResultsFromDifferentActions() = runBlocking {
        withService { service, records, _, sms, _ ->
            service.configure(PhoneChannel.NOTIFICATION, PhoneOptions(enabled = true))
            service.selectSource("bank", true)
            val event = event(PhoneChannel.NOTIFICATION, "changed_action", "请取件；请缴费")
            service.accept(event)
            val original = records.get(event.id)!!.todos
            service.ignoreTodo(event.id, original.first().id)
            service.process(event.id)
            service.accept(event.copy(body = "请续费；请缴费")); service.process(event.id)
            val result = records.get(event.id)!!.todos
            assertEquals(3, result.size)
            assertEquals(PhoneResultState.DONE, result.single { it.text == "请续费" }.state)
            assertEquals(original[1].id, result.single { it.text == "请缴费" }.id)
            assertEquals(PhoneResultState.IGNORED, result.single { it.id == original[0].id }.state)
            assertNotNull(sms.get(result.single { it.text == "请续费" }.id)?.reminderId)
        }
    }

    @Test fun updatedPickupWithDifferentReferenceIsANewTask() = runBlocking {
        withService { service, records, _, _, _ ->
            service.configure(PhoneChannel.SMS, PhoneOptions(enabled = true))
            val event = event(PhoneChannel.SMS, "new_parcel", "请取件，取件码123")
            service.accept(event); service.process(event.id)
            val previous = records.get(event.id)!!.todos.first().id
            service.accept(event.copy(body = "请取件，取件码456")); service.process(event.id)
            // The clause containing a different pickup reference must remain independent.
            assertTrue(records.get(event.id)!!.todos.any { it.id != previous && it.text.contains("456") })
        }
    }

    @Test fun notificationUpdateKeepsCompletedTaskButAddsDifferentAction() = runBlocking {
        withService { service, records, _, sms, _ ->
            service.configure(PhoneChannel.NOTIFICATION, PhoneOptions(enabled = true))
            service.selectSource("bank", true)
            val event = event(PhoneChannel.NOTIFICATION, "completed_change", "请取件")
            service.accept(event); service.process(event.id)
            val original = records.get(event.id)!!.todos.single()
            service.accept(event.copy(body = "请续费")); service.process(event.id)
            val todos = records.get(event.id)!!.todos
            assertEquals(2, todos.size)
            assertEquals(original, todos.single { it.id == original.id })
            assertNotEquals(original.reminderId, sms.get(todos.single { it.text == "请续费" }.id)?.reminderId)
        }
    }

    @Test fun smsMirrorDeduplicatesOnlyPurposesHandledBySms() = runBlocking {
        withService { service, records, _, _, _ ->
            service.selectSource("bank", true)
            service.configure(PhoneChannel.NOTIFICATION, PhoneOptions(enabled = true))
            service.configure(PhoneChannel.SMS, PhoneOptions(enabled = true, todos = false, ledger = true))
            val todoOnly = event(PhoneChannel.NOTIFICATION, "mirror_todo", "已付款100元，请取件").copy(mirrorsSms = true)
            assertNotNull(service.accept(todoOnly)); service.process(todoOnly.id)
            assertEquals(PhoneResultState.DONE, records.get(todoOnly.id)!!.todos.single().state)
            assertEquals(PhoneResultState.SKIPPED, records.get(todoOnly.id)!!.ledgerState)
            service.configure(PhoneChannel.SMS, PhoneOptions(enabled = true, todos = true, ledger = false))
            val ledgerOnly = todoOnly.copy(key = "mirror_ledger", body = "已付款200元，请取件")
            assertNotNull(service.accept(ledgerOnly)); service.process(ledgerOnly.id)
            assertTrue(records.get(ledgerOnly.id)!!.todos.isEmpty())
            assertEquals(PhoneResultState.DONE, records.get(ledgerOnly.id)!!.ledgerState)
            service.configure(PhoneChannel.SMS, PhoneOptions(enabled = true))
            assertNull(service.accept(todoOnly.copy(key = "fully_covered")))
        }
    }

    private fun event(channel: PhoneChannel, key: String, body: String) =
        PhoneEvent(channel, "bank", key, "", body, now.toEpochMilliseconds(), "UTC")

    private suspend fun withService(remote: SmsReminderRemote = SmsReminderRemote { error("Cloud was not authorized") },
        block: suspend (PhoneAssistantService, PhoneMessageRepository, BookkeepingRepository, SmsSourceRepository, SettingRepository) -> Unit) {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            DailySatoriDatabase.Schema.create(driver)
            val db = DailySatoriDatabase(driver)
            val settings = SettingRepository(db)
            val sms = SmsSourceRepository(db, Cipher)
            val reminder = SmsReminderService(sms, ReminderRepository(db), settings, remote, object : Clock { override fun now() = now })
            val records = PhoneMessageRepository(db, Cipher)
            val ledger = BookkeepingRepository(db, Cipher)
            val service = PhoneAssistantService(records, sms, reminder, ledger, settings, object : Clock { override fun now() = now })
            block(service, records, ledger, sms, settings)
        } finally { driver.close() }
    }

    private object Cipher : SecretValueCipher {
        override fun encrypt(value: String) = "enc:" + value.reversed()
        override fun decrypt(value: String) = value.removePrefix("enc:").reversed()
        override fun isEncrypted(value: String) = value.startsWith("enc:")
    }
}
