package com.dailysatori.ui.feature.reminder

import com.dailysatori.service.reminder.ReminderProfileSnapshot
import com.dailysatori.service.reminder.ReminderDraft
import com.dailysatori.service.reminder.Reminder
import com.dailysatori.service.reminder.ReminderStatus
import kotlinx.datetime.TimeZone
import com.dailysatori.service.reminder.ReminderRecurrence
import com.dailysatori.service.reminder.ReminderActiveDayRule
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import java.io.File

class ReminderRouteStateTest {
    @Test
    fun detailTimelineExplainsInitialBackoffEveningAndCutoff() {
        val reminder = Reminder(
            id = "bill",
            content = "还信用卡",
            startDate = LocalDate(2026, 9, 2),
            endDate = LocalDate(2026, 9, 2),
            firstReminderTime = LocalTime(10, 0),
            activeDayRule = ReminderActiveDayRule.Daily,
            profile = ReminderProfileSnapshot.strong(),
            status = ReminderStatus.ACTIVE,
            timeZone = TimeZone.of("Asia/Hong_Kong"),
            version = 1,
        )

        val timeline = buildReminderTimeline(reminder, LocalDate(2026, 9, 1))

        assertEquals(LocalDate(2026, 9, 2), timeline.occurrenceDate)
        assertEquals(listOf("首次提醒", "划掉后", "晚间加强", "当天结束"), timeline.steps.map { it.title })
        assertTrue(timeline.steps[1].detail.contains("2 小时、4 小时"))
        assertTrue(timeline.steps[2].detail.contains("22:00"))
        assertTrue(timeline.steps[3].detail.contains("24:00"))
    }
    @Test
    fun aiDraftOnlyChangesEditorAfterUserAppliesIt() {
        val original = ReminderEditorState(
            content = "旧内容",
            startDate = LocalDate(2026, 8, 31),
            endDate = LocalDate(2026, 8, 31),
            firstReminderTime = LocalTime(9, 0),
        )
        val parsed = ReminderDraft(
            id = "parsed",
            content = "给海外手机号充值",
            startDate = LocalDate(2026, 9, 2),
            endDate = LocalDate(2026, 9, 4),
            firstReminderTime = LocalTime(20, 0),
            activeDayRule = ReminderActiveDayRule.ConsecutiveDateRange,
            recurrence = ReminderRecurrence.Once,
        )

        assertEquals("旧内容", original.content)
        val applied = original.applyParsedDraft(parsed)
        assertEquals("给海外手机号充值", applied.content)
        assertEquals(LocalDate(2026, 9, 2), applied.startDate)
        assertEquals(LocalDate(2026, 9, 4), applied.endDate)
        assertEquals(LocalTime(20, 0), applied.firstReminderTime)
        assertTrue(applied.activeDayRule is ReminderActiveDayRule.ConsecutiveDateRange)
    }

    @Test
    fun incompleteAiDraftKeepsExistingRequiredValues() {
        val original = ReminderEditorState(
            content = "旧内容",
            startDate = LocalDate(2026, 8, 31),
            endDate = LocalDate(2026, 8, 31),
            firstReminderTime = LocalTime(9, 0),
        )
        val parsed = ReminderDraft(id = "parsed", content = "新内容", startDate = null, endDate = null, firstReminderTime = null)

        val applied = original.applyParsedDraft(parsed)

        assertEquals(LocalDate(2026, 8, 31), applied.startDate)
        assertEquals(LocalTime(9, 0), applied.firstReminderTime)
        assertEquals("新内容", applied.content)
    }
    @Test
    fun yearlyEditorSummaryExplainsActualBehavior() {
        val editor = ReminderEditorState(
            content = "生日",
            startDate = LocalDate(2026, 9, 2),
            endDate = LocalDate(2026, 9, 4),
            firstReminderTime = LocalTime(20, 0),
            recurrence = ReminderRecurrence.Yearly(9, 2, com.dailysatori.service.reminder.LeapDayPolicy.FEBRUARY_28),
            profile = ReminderProfileSnapshot.standard(),
        )

        assertEquals("每年9月2日至4日，20:00开始提醒；工作时间仅显示通知，不播放声音。", editor.actualBehaviorSummary())
    }

    @Test
    fun leapDayYearlyReminderRequiresAnExplicitFallback() {
        val editor = ReminderEditorState(
            content = "生日",
            startDate = LocalDate(2028, 2, 29),
            endDate = LocalDate(2028, 2, 29),
            firstReminderTime = LocalTime(9, 0),
            recurrence = ReminderRecurrence.Yearly(2, 29, com.dailysatori.service.reminder.LeapDayPolicy.FEBRUARY_28),
            profile = ReminderProfileSnapshot.standard(),
            leapDayFallbackChosen = false,
        )

        assertTrue(editor.validationMessage != null)
    }

    @Test
    fun consecutiveModeAtomicallyResetsRecurrenceAndUsesTheRangeRule() {
        val state = ReminderEditorState.createDefault().copy(
            recurrence = ReminderRecurrence.Yearly(9, 2, com.dailysatori.service.reminder.LeapDayPolicy.FEBRUARY_28),
        ).selectMode(ReminderEditorMode.CONSECUTIVE)

        assertEquals(ReminderRecurrence.Once, state.recurrence)
        assertEquals(ReminderActiveDayRule.ConsecutiveDateRange, state.activeDayRule)
        assertTrue(state.actualBehaviorSummary().startsWith("连续"))
    }

    @Test
    fun reminderSettingsEntryDoesNotCarryRemovedListCallbacks() {
        val navHost = source("core/navigation/NavHost.kt")
        val settingsRoute = navHost
            .substringAfter("composable<SettingsRoute>")
            .substringBefore("composable<ReminderListRoute>")
        val settingsHost = source("ui/feature/settings/SettingsScreen.kt")
        val settings = source("ui/feature/settings/reminder/ReminderSettingsScreen.kt")

        assertTrue(navHost.contains("SettingsScreen(\n                viewModel = settingsViewModel,"))
        assertTrue(settingsHost.contains("SettingsPage.REMINDERS -> ReminderSettingsScreen("))
        assertTrue(settingsHost.contains("onBack = { currentPage = SettingsPage.MAIN }"))
        assertTrue(!settings.contains("ReminderListScreen("))
        listOf(settingsRoute, settingsHost, settings).forEach { source ->
            assertFalse(source.contains("onAddReminder"))
            assertFalse(source.contains("onOpenReminder"))
            assertFalse(source.contains("onOpenSettings"))
        }
    }

    @Test
    fun standaloneReminderListShowsItsSettingsButton() {
        val list = source("ui/feature/reminder/ReminderListScreen.kt")

        assertTrue(list.contains("showSettings: Boolean = true"))
    }

    private fun source(relative: String) = File("src/main/kotlin/com/dailysatori/$relative").readText()
}
