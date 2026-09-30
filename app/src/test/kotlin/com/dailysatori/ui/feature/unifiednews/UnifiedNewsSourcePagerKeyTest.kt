package com.dailysatori.ui.feature.unifiednews

import androidx.compose.runtime.mutableStateOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class UnifiedNewsSourcePagerKeyTest {
    @Test
    fun retainedPagerCallbackUsesNewSourcesWhenPageCountGrows() {
        val pages = mutableStateOf(listOf(UnifiedNewsSourceSelection.Summary, UnifiedNewsSourceSelection.LocalArticles))
        val key = unifiedNewsSourcePagerKey(pages)
        assertEquals("news-local", key(1))

        pages.value = listOf(UnifiedNewsSourceSelection.Summary,
            UnifiedNewsSourceSelection.RemoteSource(7, "Source"), UnifiedNewsSourceSelection.LocalArticles)

        assertEquals("news-local", key(2))
        assertEquals("news-remote-7", key(1))
    }

    @Test
    fun retainedPagerCallbackHasUniqueFallbackKeysWhileRemovedPagesAreBeingDisposed() {
        val pages = mutableStateOf(listOf(UnifiedNewsSourceSelection.Summary,
            UnifiedNewsSourceSelection.RemoteSource(7, "Source"), UnifiedNewsSourceSelection.LocalArticles))
        val key = unifiedNewsSourcePagerKey(pages)
        pages.value = listOf(UnifiedNewsSourceSelection.Summary, UnifiedNewsSourceSelection.LocalArticles)

        assertNotEquals(key(0), key(2))
        assertEquals("news-local", key(1))
        assertNotEquals(key(1), key(2))
        assertNotEquals(key(2), key(3))
    }

    @Test
    fun retainedPagerCallbackPreservesSourceIdentityAfterReorderingAndRenaming() {
        val first = UnifiedNewsSourceSelection.RemoteSource(7, "First")
        val second = UnifiedNewsSourceSelection.ExternalFavoriteSource(8, "Second")
        val pages = mutableStateOf(listOf(UnifiedNewsSourceSelection.Summary, first, second, UnifiedNewsSourceSelection.LocalArticles))
        val key = unifiedNewsSourcePagerKey(pages)
        val firstKey = key(1)
        val secondKey = key(2)

        pages.value = listOf(UnifiedNewsSourceSelection.Summary, second, first.copy(name = "Renamed"), UnifiedNewsSourceSelection.LocalArticles)

        assertEquals(firstKey, key(2))
        assertEquals(secondKey, key(1))
    }
}
