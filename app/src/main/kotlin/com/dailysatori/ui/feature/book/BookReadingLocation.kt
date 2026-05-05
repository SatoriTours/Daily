package com.dailysatori.ui.feature.book

internal data class BookReadingLocation(val bookId: Long, val page: Int)

internal fun rememberReadingLocation(currentBookId: Long?, currentPage: Int): BookReadingLocation? =
    currentBookId?.let { BookReadingLocation(it, currentPage) }
