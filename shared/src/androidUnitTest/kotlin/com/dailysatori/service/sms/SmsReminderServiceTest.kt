package com.dailysatori.service.sms

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.shared.db.DailySatoriDatabase
import com.dailysatori.data.repository.*
import com.dailysatori.service.reminder.*
import com.dailysatori.service.security.SecretValueCipher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.Channel
import kotlinx.datetime.*
import kotlin.test.*

class SmsReminderServiceTest {
    private val now = Instant.parse("2026-10-05T15:00:00Z")
    private val text = "Top-Up within the next 24 hours or your credit will expire. Account 123456789 balance $5.50 /Skinny"

    @Test fun retriesKeepOneReminderAndNeverSendSensitiveNumbers() = runBlocking {
        withService { service, sources, reminders, settings, inputs, db ->
            settings.upsert(SmsReminderService.CLOUD_KEY, "true")
            val task = assertNotNull(service.accept("one", SmsSource("Skinny", text), now, TimeZone.UTC))
            val repeated = service.accept("one", SmsSource("Skinny", text), now, TimeZone.UTC)
            assertEquals(task, repeated)
            service.process("one")
            service.process("one")
            assertEquals(1, db.dailySatoriQueries.selectAllReminders().executeAsList().size)
            assertEquals(Instant.parse("2026-10-06T15:00:00Z"), reminders.get("sms:one")!!.deadlineAt)
            assertEquals(1, inputs.size)
            assertFalse(inputs.single().text.any(Char::isDigit))
            assertTrue(db.dailySatoriQueries.selectSmsSource("one").executeAsOne().encrypted_source.contains("123456789"))
            assertEquals(text, sources.get("one")!!.source.body)
        }
    }

    @Test fun otpAndBlockedSendersAreNotStoredOrSent() = runBlocking {
        withService { service, sources, _, settings, inputs, _ ->
            settings.upsert(SmsReminderService.CLOUD_KEY, "true")
            assertNull(service.accept("otp", SmsSource("bank", "OTP 123456 expires within 24 hours"), now, TimeZone.UTC))
            service.blockSender("bank")
            assertNull(service.accept("blocked", SmsSource("bank", text), now, TimeZone.UTC))
            assertTrue(sources.all().isEmpty())
            assertTrue(inputs.isEmpty())
        }
    }

    @Test fun disablingCloudBeforeExecutionPreventsRequest() = runBlocking {
        withService { service, sources, _, settings, inputs, db ->
            settings.upsert(SmsReminderService.CLOUD_KEY, "true")
            service.accept("one", SmsSource("Skinny", text), now, TimeZone.UTC)
            settings.upsert(SmsReminderService.CLOUD_KEY, "false")
            service.process("one")
            assertTrue(inputs.isEmpty())
            assertTrue(db.dailySatoriQueries.selectAllReminders().executeAsList().isEmpty())
            assertEquals(SmsSourceStatus.LOCAL_ONLY, sources.get("one")!!.status)
        }
    }

    @Test fun ordinaryConversationsAndCompletedTransactionsAreNotUploaded() = runBlocking {
        withService { service, sources, _, settings, inputs, _ ->
            settings.upsert(SmsReminderService.CLOUD_KEY, "true")
            assertNull(service.accept("chat", SmsSource("friend", "Hi John, meet me at my house tomorrow"), now, TimeZone.UTC))
            assertNull(service.accept("done", SmsSource("bank", "Payment successful. Your account balance is $55"), now, TimeZone.UTC))
            assertTrue(sources.all().isEmpty())
            assertTrue(inputs.isEmpty())
        }
    }

    @Test fun userCanEditDeadlineAndBusinessDuplicatesAreSkipped() = runBlocking {
        withService { service, sources, reminders, settings, _, db ->
            settings.upsert(SmsReminderService.CLOUD_KEY, "true")
            service.accept("one", SmsSource("Skinny", text), now, TimeZone.UTC)
            service.process("one")
            val first = assertNotNull(reminders.get("sms:one"))
            val editedDeadline = Instant.parse("2026-10-07T12:00:00Z")
            assertTrue(reminders.update(first.id, ReminderEdit(first.version, deadlineAt = editedDeadline), at = now))
            assertEquals(editedDeadline, reminders.get(first.id)!!.deadlineAt)
            assertFalse(reminders.update(first.id, ReminderEdit(first.version, deadlineAt = Instant.parse("2026-10-08T12:00:00Z")), at = now))
            service.accept("two", SmsSource("Skinny", text), now, TimeZone.UTC)
            service.process("two")
            assertEquals(SmsSourceStatus.IGNORED, sources.get("two")!!.status)
            assertEquals(1, db.dailySatoriQueries.selectAllReminders().executeAsList().size)
            assertEquals("1", settings.get("sms_reminder.created_count"))
        }
    }

    @Test fun ambiguousAndPastDeadlinesCreateTodosWithoutInventingReminderTimes() = runBlocking {
        withService { service, sources, reminders, settings, _, db ->
            settings.upsert(SmsReminderService.CLOUD_KEY, "true")
            service.accept("ambiguous", SmsSource("Skinny", "Top-Up tomorrow or your credit will expire"), now, TimeZone.UTC)
            service.process("ambiguous")
            assertEquals(SmsSourceStatus.CREATED, sources.get("ambiguous")!!.status)
            assertEquals(ReminderStatus.ACTIVE, reminders.get("sms:ambiguous")!!.status)
            assertNull(reminders.get("sms:ambiguous")!!.deadlineAt)
            service.accept("late", SmsSource("Skinny", text), Instant.parse("2026-10-01T15:00:00Z"), TimeZone.UTC)
            service.process("late")
            assertEquals(SmsSourceStatus.CREATED, sources.get("late")!!.status)
            assertEquals(ReminderStatus.ACTIVE, reminders.get("sms:late")!!.status)
            assertNull(reminders.get("sms:late")!!.deadlineAt)
            assertEquals(2, db.dailySatoriQueries.selectAllReminders().executeAsList().size)
            assertEquals("2", settings.get("sms_reminder.created_count"))
        }
    }

    @Test fun unsafeForAiMessagesCreateLocalTodosWithoutLeakingPersonalData() = runBlocking {
        withService { service, sources, reminders, settings, inputs, _ ->
            settings.upsert(SmsReminderService.CLOUD_KEY, "true")
            val body = "Dear Alice, payment due within 24 hours. Account 123456789 balance $55.50"
            service.accept("private", SmsSource("Bank", body), now, TimeZone.UTC)
            service.process("private")
            assertEquals(SmsSourceStatus.CREATED, sources.get("private")!!.status)
            val reminder = assertNotNull(reminders.get("sms:private"))
            assertEquals(Instant.parse("2026-10-06T15:00:00Z"), reminder.deadlineAt)
            assertFalse(reminder.content.contains("Alice"))
            assertFalse(reminder.content.any(Char::isDigit))
            assertTrue(inputs.isEmpty())
            service.accept("private", SmsSource("Bank", body), now, TimeZone.UTC)
            assertEquals("1", settings.get("sms_reminder.created_count"))
        }
    }

    @Test fun unscheduledTodoIsWrittenInActiveAndAcceptsADeadlineLater() = runBlocking {
        withService { service, _, reminders, _, _, _ ->
            service.accept("local", SmsSource("Skinny", "Top-Up tomorrow or your credit will expire"), now, TimeZone.UTC)
            val reminder = assertNotNull(reminders.get("sms:local"))
            // No confirmation step: todos are active right away and can be deleted or rescheduled.
            assertEquals(ReminderStatus.ACTIVE, reminder.status)
            assertNull(reminder.deadlineAt)
            assertTrue(reminders.update(reminder.id, ReminderEdit(reminder.version, deadlineAt = Instant.parse("2026-10-07T15:00:00Z")), now))
            assertEquals(ReminderStatus.ACTIVE, reminders.get(reminder.id)!!.status)
            assertEquals(Instant.parse("2026-10-07T15:00:00Z"), reminders.get(reminder.id)!!.deadlineAt)
        }
    }

    @Test fun creationNoticeCountsAreConsumedOnceAndSeparateBatchesStillEmit() = runBlocking {
        withService { service, sources, _, _, _, _ ->
            val updates = Channel<Int>(Channel.UNLIMITED)
            val collector = launch { sources.observeCreatedCount().collect { updates.send(it) } }
            try {
                assertEquals(0, updates.receive())
                service.accept("batch-one", SmsSource("Skinny", text), now, TimeZone.UTC)
                assertEquals(1, updates.receive())
                assertEquals(1, sources.takeCreatedCount())
                assertEquals(0, updates.receive())
                assertEquals(0, sources.takeCreatedCount())
                service.accept("batch-two", SmsSource("Other", text), now, TimeZone.UTC)
                assertEquals(1, updates.receive())
                assertEquals(1, sources.takeCreatedCount())
                assertEquals(0, updates.receive())
            } finally { collector.cancel() }
        }
    }

    private suspend fun withService(block: suspend (SmsReminderService, SmsSourceRepository, ReminderRepository, SettingRepository, MutableList<SmsAiInput>, DailySatoriDatabase) -> Unit) {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            DailySatoriDatabase.Schema.create(driver)
            val db = DailySatoriDatabase(driver)
            val sources = SmsSourceRepository(db)
            val reminders = ReminderRepository(db)
            val settings = SettingRepository(db)
            val inputs = mutableListOf<SmsAiInput>()
            val remote = SmsReminderRemote { input ->
                inputs += input
                SmsAiResult(true, "top_up", "给 Skinny 账户充值", "credit will expire", "Top-Up", if (input.deadlines.isEmpty()) null else 0)
            }
            val service = SmsReminderService(sources, reminders, settings, remote, object : Clock { override fun now() = this@SmsReminderServiceTest.now })
            block(service, sources, reminders, settings, inputs, db)
        } finally { driver.close() }
    }

    private object TestCipher : SecretValueCipher {
        override fun encrypt(value: String) = "enc:" + java.util.Base64.getEncoder().encodeToString(value.toByteArray())
        override fun decrypt(value: String) = String(java.util.Base64.getDecoder().decode(value.removePrefix("enc:")))
        override fun isEncrypted(value: String) = value.startsWith("enc:")
    }
}
