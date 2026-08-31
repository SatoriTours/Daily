package com.dailysatori.ui.feature.home

import com.dailysatori.ui.component.appbar.reminderBadgeLabel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.io.File

class HomeCompactHeaderTest {
    @Test
    fun compactHeaderKeepsContentBelowSystemStatusBar() {
        val source = File("src/main/kotlin/com/dailysatori/ui/component/appbar/HomeCompactHeader.kt").readText()

        assertTrue(source.contains("windowInsetsPadding(WindowInsets.statusBars)"))
    }

    @Test
    fun reminderBadgeIsHiddenWhenCountIsZero() {
        assertEquals(null, reminderBadgeLabel(0))
        assertEquals("9+", reminderBadgeLabel(12))
    }
}
