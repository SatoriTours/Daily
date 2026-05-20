package com.dailysatori.ui.feature.records

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RecordsScreenTest {
    @Test
    fun recordsDestinationsUseRequiredLabels() {
        assertEquals(listOf("日记", "文章", "本地收藏"), recordsDestinations().map { it.title })
        assertEquals(listOf(RecordsDestination.Diary, RecordsDestination.Articles, RecordsDestination.Favorites), recordsDestinations().map { it.destination })
    }

    @Test
    fun recordsScreenUsesExistingSurfacesInline() {
        val source = File("src/main/kotlin/com/dailysatori/ui/feature/records/RecordsScreen.kt").readText()

        assertTrue(source.contains("AppScaffold("))
        assertTrue(source.contains("title = \"记录\""))
        assertTrue(source.contains("DiaryScreen(onMyClick = onMyClick)"))
        assertTrue(source.contains("ArticleListScreen(onArticleClick = onArticleClick)"))
        assertTrue(source.contains("showFavoritesOnly = true"))
        assertTrue(source.contains("lockFavoritesFilter = true"))
    }
}
