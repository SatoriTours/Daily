package com.dailysatori.ui.component.news

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class NewsListLayoutsTest {
    @Test
    fun sharedNewsLayoutsExposeConsistentPaddingAndStateMessage() {
        val layouts = File("src/main/kotlin/com/dailysatori/ui/component/news/NewsListLayouts.kt").readText()
        val banner = File("src/main/kotlin/com/dailysatori/ui/component/news/NewsStatusBanner.kt").readText()

        assertTrue(layouts.contains("fun newsListContentPadding(): PaddingValues"))
        assertTrue(layouts.contains("fun newsCompactListContentPadding(): PaddingValues"))
        assertTrue(layouts.contains("PaddingValues(Spacing.m)"))
        assertTrue(layouts.contains("PaddingValues(start = Spacing.m, end = Spacing.m, top = Spacing.xs, bottom = Spacing.m)"))
        assertTrue(layouts.contains("fun NewsStateMessage("))
        assertTrue(layouts.contains("actionLabel: String? = null"))
        assertTrue(layouts.contains("IconSize.xxl"))
        assertTrue(banner.contains("fun NewsStatusBanner("))
        assertTrue(banner.contains("color = MaterialTheme.colorScheme.surfaceContainerHighest"))
    }
}
