package com.dailysatori.service.book

import kotlinx.serialization.Serializable

@Serializable
data class BookViewpointDraft(
    val title: String,
    val content: String,
    val example: String,
)

interface BookIntelligenceSource {
    suspend fun searchBooks(query: String): List<BookSearchResult>
    suspend fun generateViewpoints(book: BookSearchResult): List<BookViewpointDraft>
}

class BookIntelligenceService(
    private val weReadSkillService: BookIntelligenceSource,
) {
    suspend fun searchBooks(query: String): List<BookSearchResult> =
        weReadSkillService.searchBooks(query)

    suspend fun generateViewpoints(book: BookSearchResult): List<BookViewpointDraft> =
        weReadSkillService.generateViewpoints(book)
}
