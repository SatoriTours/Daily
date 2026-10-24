package com.dailysatori.ui.feature.home

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HomeBottomBarVisibilityTest {

    @Test
    fun bottomBarIsVisibleWhenNoDetailHidesIt() {
        assertTrue(shouldShowHomeBottomBar(tabIndex = TODAY_TAB_INDEX, isDetailHidingBottomBar = false))
        assertTrue(shouldShowHomeBottomBar(tabIndex = DIARY_TAB_INDEX, isDetailHidingBottomBar = false))
        assertTrue(shouldShowHomeBottomBar(tabIndex = ASSISTANT_TAB_INDEX, isDetailHidingBottomBar = false))
        assertTrue(shouldShowHomeBottomBar(tabIndex = READING_TAB_INDEX, isDetailHidingBottomBar = false))
        assertTrue(shouldShowHomeBottomBar(tabIndex = MY_TAB_INDEX, isDetailHidingBottomBar = false))
    }

    @Test
    fun bottomBarHidingAppliesOnlyToTodayTabWhenHotNewsDetailIsOpen() {
        // While hot news detail is open on Today tab, Today tab hides the bottom bar
        assertFalse(shouldShowHomeBottomBar(tabIndex = TODAY_TAB_INDEX, isDetailHidingBottomBar = true))

        // But when switching to Diary, Assistant, Reading, or MySpace tabs, the bottom bar must still show!
        assertTrue(shouldShowHomeBottomBar(tabIndex = DIARY_TAB_INDEX, isDetailHidingBottomBar = true))
        assertTrue(shouldShowHomeBottomBar(tabIndex = ASSISTANT_TAB_INDEX, isDetailHidingBottomBar = true))
        assertTrue(shouldShowHomeBottomBar(tabIndex = READING_TAB_INDEX, isDetailHidingBottomBar = true))
        assertTrue(shouldShowHomeBottomBar(tabIndex = MY_TAB_INDEX, isDetailHidingBottomBar = true))
    }

    @Test
    fun bottomBarRestoresWhenDetailClosesOrExits() {
        // When detail is closed, isDetailHidingBottomBar becomes false and Today tab shows bottom bar again
        assertTrue(shouldShowHomeBottomBar(tabIndex = TODAY_TAB_INDEX, isDetailHidingBottomBar = false))
    }

    @Test
    fun invalidOrOutOfRangeTabIndexNeverShowsBottomBar() {
        assertFalse(shouldShowHomeBottomBar(tabIndex = -1, isDetailHidingBottomBar = false))
        assertFalse(shouldShowHomeBottomBar(tabIndex = 99, isDetailHidingBottomBar = false))
    }
}
