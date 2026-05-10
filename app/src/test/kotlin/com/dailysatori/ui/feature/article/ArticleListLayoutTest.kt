package com.dailysatori.ui.feature.article

import com.dailysatori.ui.component.card.articleCoverSlotVisible
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ArticleListLayoutTest {
    @Test
    fun usesComfortableArticleListDensity() {
        assertEquals(128, articleCardHeightDp)
        assertEquals(16, articleListItemSpacingDp)
        assertEquals(14, articleCardContentVerticalPaddingDp)
        assertEquals(2, articleCardSummaryMaxLines)
    }

    @Test
    fun articleCardsKeepImageSlotWhenCoverIsMissing() {
        assertTrue(articleCoverSlotVisible(null))
        assertTrue(articleCoverSlotVisible(""))
        assertTrue(articleCoverSlotVisible("https://example.com/cover.jpg"))
    }
}
