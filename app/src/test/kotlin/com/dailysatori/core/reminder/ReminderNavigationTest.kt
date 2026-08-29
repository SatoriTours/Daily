package com.dailysatori.core.reminder

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReminderNavigationTest {
    @Test
    fun viewIntentRoutesReminderIdIntoConsumableAppState() {
        val source = ReminderOpenRequestState()
        reminderIdForViewIntent(ReminderCoordinator.ACTION_VIEW_REMINDER, "bill-42")?.let(source::open)

        assertEquals("bill-42", source.pending.value)
        source.consume("bill-42")
        assertNull(source.pending.value)
    }

    @Test
    fun unrelatedOrMissingIdentityIntentDoesNotChangeReminderRoute() {
        val source = ReminderOpenRequestState()

        reminderIdForViewIntent("other", "bill")?.let(source::open)
        reminderIdForViewIntent(ReminderCoordinator.ACTION_VIEW_REMINDER, null)?.let(source::open)

        assertNull(source.pending.value)
    }
}
