package com.dailysatori.ui.feature.aiconfig

import kotlin.test.Test
import kotlin.test.assertEquals

class AiConfigLayoutTest {
    @Test
    fun deleteActionUsesCompactIconSizing() {
        assertEquals(32, aiConfigDeleteActionSizeDp)
        assertEquals(18, aiConfigDeleteIconSizeDp)
    }

    @Test
    fun selectedAndDestructiveColorsStaySubtle() {
        assertEquals(0.28f, aiConfigDefaultCardBorderAlpha)
        assertEquals(0.82f, aiConfigDefaultIconAlpha)
        assertEquals(0.62f, aiConfigDeleteIconAlpha)
    }
}
