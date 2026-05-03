package com.dailysatori.ui.feature.article

import kotlin.test.Test
import kotlin.test.assertEquals

class ArticleDetailLayoutTest {
    @Test
    fun coverHeightShrinksWithScrollAndNeverGoesBelowZero() {
        assertEquals(260, articleCollapsedCoverHeightDp(scrollOffsetDp = 0))
        assertEquals(140, articleCollapsedCoverHeightDp(scrollOffsetDp = 120))
        assertEquals(0, articleCollapsedCoverHeightDp(scrollOffsetDp = 300))
    }

    @Test
    fun syncedCoverHeightsUseOneSharedScrollOffsetForAllPages() {
        assertEquals(listOf(180, 180), articleSyncedCoverHeightsDp(sharedScrollOffsetDp = 80, pageCount = 2))
    }

    @Test
    fun sharedCoverHeightChangesBeforePageContentScrolls() {
        assertEquals(220, articleCoverHeightAfterScroll(currentHeightDp = 260, scrollDeltaDp = -40, contentAtTop = true))
        assertEquals(0, articleCoverHeightAfterScroll(currentHeightDp = 20, scrollDeltaDp = -40, contentAtTop = true))
        assertEquals(80, articleCoverHeightAfterScroll(currentHeightDp = 40, scrollDeltaDp = 40, contentAtTop = true))
        assertEquals(40, articleCoverHeightAfterScroll(currentHeightDp = 40, scrollDeltaDp = 40, contentAtTop = false))
    }
}
