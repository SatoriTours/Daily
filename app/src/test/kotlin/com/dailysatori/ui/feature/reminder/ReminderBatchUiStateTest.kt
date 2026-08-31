package com.dailysatori.ui.feature.reminder

import com.dailysatori.service.reminder.ReminderBatchInterpretation
import com.dailysatori.service.reminder.ReminderBatchItem
import com.dailysatori.service.reminder.ReminderDraft
import com.dailysatori.service.reminder.ReminderInterpretation
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith
import kotlinx.coroutines.CancellationException

class ReminderBatchUiStateTest {
    @Test
    fun validItemsStartSelectedAndFailedItemsStartUnselected() {
        val valid = item("a")
        val failed = item("b", failure = "unreadable")

        val state = ReminderBatchUiState.from(batch(valid, failed))

        assertEquals(setOf("a"), state.selectedIds)
        assertEquals(1, state.selectedCount)
        assertFalse(state.items.getValue("b").selected)
    }

    @Test
    fun editingAndRemovingItemsPreservesTheOtherSelection() {
        val state = ReminderBatchUiState.from(batch(item("a"), item("b")))
            .updateItem("a") { it.copy(draft = it.draft.editContent("已修改")) }
            .removeItem("b")

        assertEquals("已修改", state.items.getValue("a").draft.content)
        assertEquals(setOf("a"), state.selectedIds)
    }

    @Test
    fun partialFailureKeepsOnlyFailedItemRetryable() = runTest {
        val result = saveBatch(ReminderBatchUiState.from(batch(item("a"), item("b")))) { item ->
            if (item.id == "b") error("disk") else "created-a"
        }

        assertEquals(BatchSaveStatus.SAVED, result.items.getValue("a").saveStatus)
        assertEquals("created-a", result.items.getValue("a").createdReminderId)
        assertEquals(BatchSaveStatus.FAILED, result.items.getValue("b").saveStatus)
        assertEquals(setOf("b"), result.selectedIds)
    }

    @Test
    fun savedItemIsSkippedOnSecondSave() = runTest {
        var calls = 0
        val once = saveBatch(ReminderBatchUiState.from(batch(item("a")))) { calls++; "created" }

        saveBatch(once) { calls++; "duplicate" }

        assertEquals(1, calls)
    }

    @Test
    fun concurrentSaveClaimsLeaveEachItemForOnlyOneCreate() {
        val state = ReminderBatchUiState.from(batch(item("a"), item("b")))
        val first = state.claimSelectedItems()
        val second = first.state.claimSelectedItems()
        var creates = 0

        runTest { saveClaim(first) { creates++; "created-${it.id}" } }
        runTest { saveClaim(second) { creates++; "duplicate-${it.id}" } }

        assertEquals(setOf("a", "b"), first.items.keys)
        assertTrue(second.items.isEmpty())
        assertTrue(first.state.items.values.all { it.saveStatus == BatchSaveStatus.SAVING })
        assertEquals(2, creates)
    }

    @Test
    fun cancellationStopsTheBatchWithoutMarkingLaterItemsFailed() = runTest {
        var laterCalls = 0

        assertFailsWith<CancellationException> {
            saveBatch(ReminderBatchUiState.from(batch(item("a"), item("b")))) { item ->
                if (item.id == "a") throw CancellationException("stopped")
                laterCalls++
                "created-b"
            }
        }

        assertEquals(0, laterCalls)
    }

    private fun batch(vararg items: ReminderBatchItem) = ReminderBatchInterpretation(
        batchId = "batch",
        normalizedInput = "input",
        items = items.toList(),
    )

    private fun item(id: String, failure: String? = null) = ReminderBatchItem(
        id = id,
        sourceIndex = if (id == "a") 0 else 1,
        sourceText = id,
        interpretation = ReminderInterpretation(
            draft = ReminderDraft(id, "提醒 $id", LocalDate(2026, 9, 2), LocalDate(2026, 9, 2), LocalTime(20, 0)),
            requiresConfirmation = true,
            failure = failure,
        ),
    )
}
