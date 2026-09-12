package com.dailysatori.ui.feature.unifiednews

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UnifiedNewsSourceSwipeTest {
    private val state = UnifiedNewsState(
        remoteSources = listOf(UnifiedNewsRemoteSourceOption(1, "来源")),
        externalFavoriteSources = listOf(UnifiedNewsExternalFavoriteSourceOption(1, "来源")),
    )

    @Test
    fun swipeFollowsHeaderOrderEvenWithDuplicateNamesAndIds() {
        assertEquals(1, unifiedNewsSwipeTarget(state, -100f, 50f))
        val remote = state.copy(sourceSelection = UnifiedNewsSourceSelection.RemoteSource(1, "旧名称"))
        assertEquals(2, unifiedNewsSwipeTarget(remote, -100f, 50f))
        assertEquals(0, unifiedNewsSwipeTarget(remote, 100f, 50f))
        val favorite = state.copy(sourceSelection = UnifiedNewsSourceSelection.ExternalFavoriteSource(1, "来源"))
        assertEquals(3, unifiedNewsSwipeTarget(favorite, -100f, 50f))
        assertEquals(1, unifiedNewsSwipeTarget(favorite, 100f, 50f))
    }

    @Test
    fun shortSwipesAndOutwardSwipesDoNotSwitch() {
        assertNull(unifiedNewsSwipeTarget(state, -49f, 50f))
        assertNull(unifiedNewsSwipeTarget(state, 0f, 50f))
        assertNull(unifiedNewsSwipeTarget(state, 100f, 50f))
        val local = state.copy(sourceSelection = UnifiedNewsSourceSelection.LocalArticles)
        assertNull(unifiedNewsSwipeTarget(local, -100f, 50f))
        assertEquals(2, unifiedNewsSwipeTarget(local, 50f, 50f))
    }

    @Test
    fun emptySourcesStillAllowSwitchingBetweenSummaryAndLocal() {
        assertEquals(1, unifiedNewsSwipeTarget(UnifiedNewsState(), -50f, 50f))
        assertEquals(0, unifiedNewsSwipeTarget(
            UnifiedNewsState(sourceSelection = UnifiedNewsSourceSelection.LocalArticles), 50f, 50f,
        ))
    }

    @Test
    fun removedSourceDoesNotSwitchToAnUnrelatedTab() {
        val removed = state.copy(sourceSelection = UnifiedNewsSourceSelection.RemoteSource(99, "已移除"))
        assertNull(unifiedNewsSwipeTarget(removed, -100f, 50f))
    }
}
