package com.dailysatori.ui.feature.article

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
