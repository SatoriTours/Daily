package com.dailysatori.ui.feature.crayfishnews

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class CrayfishNewsScreenLayoutTest {
    @Test
    fun crayfishNewsListUsesStableKeysSharedPaddingAndDerivedLoadMoreState() {
        val source = File("src/main/kotlin/com/dailysatori/ui/feature/crayfishnews/CrayfishNewsScreen.kt").readText()

        assertTrue(source.contains("derivedStateOf"))
        assertTrue(source.contains("itemsIndexed(articles, key = { _, item -> item.filename })"))
        assertTrue(source.contains("item(key = \"crayfish-news-loading-more\")"))
        assertTrue(source.contains("contentPadding = newsListContentPadding()"))
        assertTrue(source.contains("NewsStateMessage("))
        assertTrue(source.contains("remember(article.content) { article.content.withoutIntroBlock() }"))
    }
}
