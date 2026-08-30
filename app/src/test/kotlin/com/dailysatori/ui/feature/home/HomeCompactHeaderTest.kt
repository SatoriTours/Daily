package com.dailysatori.ui.feature.home

import com.dailysatori.ui.component.appbar.reminderBadgeLabel
import kotlin.test.Test
import kotlin.test.assertEquals

class HomeCompactHeaderTest {
    @Test
    fun reminderBadgeIsHiddenWhenCountIsZero() {
        assertEquals(null, reminderBadgeLabel(0))
        assertEquals("9+", reminderBadgeLabel(12))
    }
}
