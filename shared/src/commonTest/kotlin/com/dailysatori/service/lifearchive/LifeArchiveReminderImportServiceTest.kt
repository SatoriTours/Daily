package com.dailysatori.service.lifearchive

import com.dailysatori.service.reminder.*
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.*
import kotlinx.serialization.json.*
import kotlin.test.*

class LifeArchiveReminderImportServiceTest {
    @Test
    fun analyzesAllRemindersAndAcceptsOnlyIdentifiedSubscriptionRecords() = runBlocking {
        var request: JsonObject? = null
        val service = LifeArchiveReminderImportService(LifeArchiveReminderRemote { input ->
            request = input
            response("subscription")
        })
        val drafts = service.analyze(listOf(reminder("subscription"), reminder("chores")), emptyList(), defaultLifeArchiveCategories())
        assertEquals(listOf("subscription"), drafts.map { it.sourceReminderId })
        assertEquals(0L, drafts.single().sourceReminderVersion)
        assertTrue(request.toString().contains("chores"))
        assertTrue(request.toString().contains("reminderSchedule"))
    }

    @Test
    fun unchangedSourceIsSkippedAndChangedSourceUpdatesExistingId() = runBlocking {
        var calls = 0
        val service = LifeArchiveReminderImportService(LifeArchiveReminderRemote { calls++; response("subscription") })
        val existing = LifeArchiveRecord("saved", "订阅", "subscription", fields = listOf(LifeArchiveField("备注", "手动备注")),
            createdAt = 10, updatedAt = 20, sourceReminderId = "subscription", sourceReminderVersion = 0)
        assertTrue(service.analyze(listOf(reminder("subscription")), listOf(existing), defaultLifeArchiveCategories()).isEmpty())
        assertEquals(0, calls)
        val result = service.analyze(listOf(reminder("subscription").copy(version = 1)), listOf(existing), defaultLifeArchiveCategories()).single()
        assertEquals("saved", result.id)
        assertEquals(20L, result.updatedAt)
        assertEquals("手动备注", result.fields.single { it.name == "备注" }.value)
        assertEquals(1L, result.sourceReminderVersion)
    }

    @Test
    fun rejectsForgedOrRepeatedSourceIds() = runBlocking {
        listOf(response("forged"), response("subscription", twice = true)).forEach { raw ->
            val service = LifeArchiveReminderImportService(LifeArchiveReminderRemote { raw })
            assertFailsWith<LifeArchiveInvalidResponse> {
                service.analyze(listOf(reminder("subscription")), emptyList(), defaultLifeArchiveCategories())
            }
        }
    }

    @Test
    fun partitionsRequestsAndReportsCompletedDraftsBeforeLaterFailure() = runBlocking {
        var calls = 0
        val delivered = mutableListOf<LifeArchiveRecord>()
        val service = LifeArchiveReminderImportService(LifeArchiveReminderRemote { input ->
            calls++
            val entries = input["reminders"]!!.jsonArray
            assertTrue(entries.size <= 20 && input.toString().length <= 20_000)
            if (calls == 2) error("offline")
            response(entries.first().jsonObject["id"]!!.jsonPrimitive.content)
        })
        assertFailsWith<IllegalStateException> {
            service.analyze(List(21) { reminder("r$it") }, emptyList(), defaultLifeArchiveCategories()) { drafts, _, _ -> delivered += drafts }
        }
        assertEquals(1, delivered.size)
        assertEquals(2, calls)
    }

    @Test
    fun emptyAiClassificationProducesNoDrafts() = runBlocking {
        val service = LifeArchiveReminderImportService(LifeArchiveReminderRemote { "{\"records\":[]}" })
        assertTrue(service.analyze(listOf(reminder("chores")), emptyList(), defaultLifeArchiveCategories()).isEmpty())
    }

    private fun response(source: String, twice: Boolean = false): String {
        val record = """{"title":"订阅","categoryId":"subscription","body":"","fields":[{"name":"服务","value":"ChatGPT"}],"sourceReminderId":"$source"}"""
        return "{\"records\":[$record${if (twice) ",$record" else ""}]}"
    }

    private fun reminder(id: String) = Reminder(id, if (id == "chores") "买菜" else "订阅续费", LocalDate(2026, 10, 4),
        LocalDate(2026, 10, 4), LocalTime(9, 0), ReminderActiveDayRule.Daily, ReminderProfileSnapshot.standard(),
        ReminderStatus.ACTIVE, TimeZone.UTC, 0, recurrence = ReminderRecurrence.Once)
}
