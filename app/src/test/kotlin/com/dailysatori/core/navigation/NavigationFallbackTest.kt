package com.dailysatori.core.navigation

import kotlin.test.Test
import kotlin.test.assertEquals

class NavigationFallbackTest {
    @Test
    fun contentSearchResultNavigatesHomeWhenPopFails() {
        assertEquals(true, shouldNavigateHomeAfterPop(popBackStackSucceeded = false))
        assertEquals(false, shouldNavigateHomeAfterPop(popBackStackSucceeded = true))
    }
}
