package com.dailysatori.ui.feature.article

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp

internal const val articleCoverMaxHeightDp = 260

internal fun articleCollapsedCoverHeightDp(scrollOffsetDp: Int): Int =
    (articleCoverMaxHeightDp - scrollOffsetDp).coerceIn(0, articleCoverMaxHeightDp)

internal fun articleSyncedCoverHeightsDp(sharedScrollOffsetDp: Int, pageCount: Int): List<Int> =
    List(pageCount) { articleCollapsedCoverHeightDp(sharedScrollOffsetDp) }

internal fun articleCoverHeightAfterScroll(
    currentHeightDp: Int,
    scrollDeltaDp: Int,
    contentAtTop: Boolean,
): Int {
    if (scrollDeltaDp < 0) {
        return (currentHeightDp + scrollDeltaDp).coerceIn(0, articleCoverMaxHeightDp)
    }
    if (scrollDeltaDp > 0 && contentAtTop) {
        return (currentHeightDp + scrollDeltaDp).coerceIn(0, articleCoverMaxHeightDp)
    }
    return currentHeightDp
}

@Composable
internal fun rememberArticleCoverNestedScrollConnection(
    hasCover: Boolean,
    coverHeightDp: Int,
    onCoverHeightChange: (Int) -> Unit,
    listState: LazyListState,
    density: Density,
): NestedScrollConnection = remember(listState, hasCover, density, coverHeightDp) {
    object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (!hasCover) return Offset.Zero
            val deltaDp = with(density) { available.y.toDp().value.toInt() }
            val contentAtTop = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
            val nextHeight = articleCoverHeightAfterScroll(coverHeightDp, deltaDp, contentAtTop)
            if (nextHeight == coverHeightDp) return Offset.Zero
            onCoverHeightChange(nextHeight)
            return Offset(x = 0f, y = with(density) { (nextHeight - coverHeightDp).dp.toPx() })
        }
    }
}
