package com.dailysatori.ui.feature.unifiednews

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class UnifiedNewsLocalArticleBackTest {
    @Test
    fun localArticlePagesPassBackActionToArticleList() {
        val source = File("src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsScreen.kt").readText()
        val pageSwitch = source.substringAfter("when (state.page)")
            .substringBefore("}\n}")

        assertTrue(pageSwitch.contains("UnifiedNewsPage.LOCAL_ARTICLES -> ArticleListScreen("))
        assertTrue(pageSwitch.contains("UnifiedNewsPage.LOCAL_FAVORITES -> ArticleListScreen("))
        assertTrue(pageSwitch.contains("onBack = { viewModel.switchPage(UnifiedNewsPage.SUMMARY) }"))
    }

    @Test
    fun articleListCanRenderBackButtonWhenEmbedded() {
        val source = File("src/main/kotlin/com/dailysatori/ui/feature/article/ArticleListScreen.kt").readText()

        assertTrue(source.contains("onBack: (() -> Unit)? = null"))
        assertTrue(source.contains("showBack = onBack != null"))
        assertTrue(source.contains("onBack = onBack"))
    }
}
