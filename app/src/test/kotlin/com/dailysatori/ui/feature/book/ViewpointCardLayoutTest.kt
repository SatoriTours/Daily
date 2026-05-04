package com.dailysatori.ui.feature.book

import kotlin.test.Test
import kotlin.test.assertEquals

class ViewpointCardLayoutTest {
    @Test
    fun shortViewpointCardsStayTopAligned() {
        assertEquals(true, viewpointCardFillsAvailableHeight(fillAvailableHeight = true))
        assertEquals(false, viewpointCardFillsAvailableHeight(fillAvailableHeight = false))
        assertEquals(true, viewpointCardContentStartsAtTop())
    }
}
