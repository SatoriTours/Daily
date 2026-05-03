package com.dailysatori.ui.feature.article

import kotlin.test.Test
import kotlin.test.assertEquals

class ArticleListLayoutTest {
    @Test
    fun usesComfortableArticleListDensity() {
        assertEquals(128, articleCardHeightDp)
        assertEquals(16, articleListItemSpacingDp)
        assertEquals(14, articleCardContentVerticalPaddingDp)
        assertEquals(2, articleCardSummaryMaxLines)
    }
}
