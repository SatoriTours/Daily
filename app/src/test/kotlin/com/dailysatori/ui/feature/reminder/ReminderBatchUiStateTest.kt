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
import kotlin.test.assertNull
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class ReminderBatchUiStateTest {
    @Test
    fun confirmationRequiredItemCannotSaveUntilExplicitlyConfirmed() {
        val initial = ReminderBatchUiState.from(batch(item("a", requiresConfirmation = true)))

        val confirmed = initial.confirmItem("a")

        assertEquals(0, initial.selectedCount)
        assertFalse(initial.items.getValue("a").canSave)
        assertEquals(1, confirmed.selectedCount)
        assertFalse(confirmed.items.getValue("a").requiresConfirmation)
    }

    @Test
    fun validManualEditClearsParseFailureAndMakesItemSelectable() {
        val initial = ReminderBatchUiState.from(batch(item("a", failure = "unreadable")))

        val edited = initial.updateItem("a") { item ->
            item.copy(draft = item.draft.editContent("manual reminder"))
        }

        assertNull(edited.items.getValue("a").parseError)
        assertEquals(1, edited.selectedCount)
    }

    @Test
    fun savingItemRejectsSelectionEditAndRemoval() {
        val saving = ReminderBatchUiState.from(batch(item("a"))).confirmItem("a").claimSelectedItems().state

        assertEquals(saving, saving.toggleItem("a"))
        assertEquals(saving, saving.removeItem("a"))
        assertEquals(saving, saving.updateItem("a") { it.copy(sourceText = "changed") })
    }

    @Test
    fun batchIsCompleteOnlyAfterEveryRemainingItemSaves() {
        val initial = ReminderBatchUiState.from(batch(item("a"), item("b"))).confirmItem("a").confirmItem("b")
        val partiallySaved = initial.markSaved("a", "created-a")

        assertFalse(partiallySaved.isComplete)
        assertTrue(partiallySaved.markSaved("b", "created-b").isComplete)
    }

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
    fun concurrentStateFlowClaimsGiveEachItemToOnlyOneSaver() = runTest {
        val flow = MutableStateFlow(ReminderUiState(aiParse = ReminderAiParseState(prompt = "batch")))
        val transitions = ReminderBatchStateTransitions(flow)
        val request = transitions.beginInterpretation()
        transitions.completeInterpretation(request.token, ReminderBatchUiState.from(batch(item("a"), item("b"))))

        val claims = List(2) {
            async(Dispatchers.Default) { transitions.claimSelectedBatch() }
        }.awaitAll().filterNotNull()

        assertEquals(1, claims.size)
        assertEquals(setOf("a", "b"), claims.single().claim.items.keys)
        assertTrue(flow.value.aiParse.batch!!.items.values.all { it.saveStatus == BatchSaveStatus.SAVING })
    }

    @Test
    fun parseInFlightAndFailedReplacementCannotClaimTheVisibleOldBatch() {
        val flow = MutableStateFlow(ReminderUiState(aiParse = ReminderAiParseState(prompt = "old")))
        val transitions = ReminderBatchStateTransitions(flow)
        val first = transitions.beginInterpretation()
        transitions.completeInterpretation(first.token, ReminderBatchUiState.from(batch(item("a"))))

        val replacement = transitions.beginInterpretation()

        assertEquals(null, transitions.claimSelectedBatch())
        assertEquals(BatchSaveStatus.PENDING, flow.value.aiParse.batch!!.items.getValue("a").saveStatus)

        transitions.failInterpretation(replacement.token, "parse failed")

        assertEquals(null, transitions.claimSelectedBatch())
        assertEquals(BatchSaveStatus.PENDING, flow.value.aiParse.batch!!.items.getValue("a").saveStatus)
    }

    @Test
    fun activeSaveRejectsRetryClaimUntilTheSlowItemFinishes() = runTest {
        val flow = MutableStateFlow(ReminderUiState(aiParse = ReminderAiParseState(prompt = "batch")))
        val transitions = ReminderBatchStateTransitions(flow)
        val request = transitions.beginInterpretation()
        transitions.completeInterpretation(request.token, ReminderBatchUiState.from(batch(item("a"), item("b"))))
        val controller = ReminderBatchSaveLaunchController()
        val slowItemStarted = CountDownLatch(1)
        val releaseSlowItem = CountDownLatch(1)
        var aAttempts = 0

        fun launchSave() = controller.launchIfIdle(
            scope = backgroundScope,
            context = Dispatchers.Default,
            claim = transitions::claimSelectedBatch,
        ) { operation ->
            operation.claim.items.forEach { (id, _) ->
                if (id == "a") {
                    aAttempts++
                    if (aAttempts == 1) {
                        transitions.markFailed(operation.key, id, "disk")
                    } else {
                        transitions.markSaved(operation.key, id, "created-a", null)
                    }
                } else {
                    slowItemStarted.countDown()
                    assertTrue(releaseSlowItem.await(5, TimeUnit.SECONDS))
                    ensureActive()
                    transitions.markSaved(operation.key, id, "created-b", null)
                }
            }
        }

        val firstSave = launchSave()
        assertTrue(firstSave != null)
        assertTrue(slowItemStarted.await(5, TimeUnit.SECONDS))

        val rejectedRetry = launchSave()

        assertEquals(null, rejectedRetry)
        assertEquals(BatchSaveStatus.FAILED, flow.value.aiParse.batch!!.items.getValue("a").saveStatus)
        assertEquals(BatchSaveStatus.SAVING, flow.value.aiParse.batch!!.items.getValue("b").saveStatus)

        releaseSlowItem.countDown()
        firstSave!!.join()

        val retry = launchSave()
        assertTrue(retry != null)
        retry!!.join()
        assertEquals(BatchSaveStatus.SAVED, flow.value.aiParse.batch!!.items.getValue("a").saveStatus)
        assertEquals(BatchSaveStatus.SAVED, flow.value.aiParse.batch!!.items.getValue("b").saveStatus)
    }

    @Test
    fun resetThatPublishesBeforeStaleCompletionPreventsOldKeyCreation() {
        val flow = MutableStateFlow(ReminderUiState(aiParse = ReminderAiParseState(prompt = "old")))
        val gate = ReminderBatchSaveGate()
        val transitions = ReminderBatchStateTransitions(flow, gate)
        val request = transitions.beginInterpretation()
        val oldBatch = ReminderBatchUiState.from(batch(item("a")))
        val oldKey = ReminderBatchOperationKey(oldBatch.batchId, request.generation)
        val resetOwnsGate = CountDownLatch(1)
        val releaseReset = CountDownLatch(1)
        val completionAttempted = CountDownLatch(1)
        var creates = 0

        val reset = thread {
            gate.serialized {
                resetOwnsGate.countDown()
                assertTrue(releaseReset.await(5, TimeUnit.SECONDS))
                transitions.reset()
            }
        }
        assertTrue(resetOwnsGate.await(5, TimeUnit.SECONDS))
        val staleCompletion = thread {
            completionAttempted.countDown()
            transitions.completeInterpretation(request.token, oldBatch)
        }
        assertTrue(completionAttempted.await(5, TimeUnit.SECONDS))
        releaseReset.countDown()
        reset.join()
        staleCompletion.join()

        gate.createIfCurrent(oldKey) { creates++ }

        assertEquals(0, creates)
        assertEquals(null, flow.value.aiParse.batch)
        assertFalse(flow.value.aiParse.acceptsInterpretation(request.token))
    }

    @Test
    fun invalidationThatWinsBeforeCreateSkipsTheOldBatch() {
        val gate = ReminderBatchSaveGate()
        val key = ReminderBatchOperationKey("old", 1)
        val releaseCreate = CountDownLatch(1)
        val invalidated = CountDownLatch(1)
        var creates = 0
        gate.serialized { activate(key) }

        val saver = thread {
            releaseCreate.await(5, TimeUnit.SECONDS)
            gate.createIfCurrent(key) { creates++ }
        }
        val reset = thread {
            gate.serialized { invalidate() }
            invalidated.countDown()
        }

        assertTrue(invalidated.await(5, TimeUnit.SECONDS))
        releaseCreate.countDown()
        saver.join()
        reset.join()

        assertEquals(0, creates)
    }

    @Test
    fun createThatWinsBeforeInvalidationRunsOnceAndOldKeyCannotRunAgain() {
        val gate = ReminderBatchSaveGate()
        val key = ReminderBatchOperationKey("old", 1)
        val createEntered = CountDownLatch(1)
        val releaseCreate = CountDownLatch(1)
        var creates = 0
        gate.serialized { activate(key) }

        val saver = thread {
            gate.createIfCurrent(key) {
                createEntered.countDown()
                assertTrue(releaseCreate.await(5, TimeUnit.SECONDS))
                creates++
            }
        }
        assertTrue(createEntered.await(5, TimeUnit.SECONDS))
        val reset = thread { gate.serialized { invalidate() } }
        releaseCreate.countDown()
        saver.join()
        reset.join()

        gate.createIfCurrent(key) { creates++ }

        assertEquals(1, creates)
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

    private fun item(id: String, failure: String? = null, requiresConfirmation: Boolean = false) = ReminderBatchItem(
        id = id,
        sourceIndex = if (id == "a") 0 else 1,
        sourceText = id,
        interpretation = ReminderInterpretation(
            draft = ReminderDraft(id, "提醒 $id", LocalDate(2026, 9, 2), LocalDate(2026, 9, 2), LocalTime(20, 0)),
            requiresConfirmation = requiresConfirmation,
            failure = failure,
        ),
    )
}
