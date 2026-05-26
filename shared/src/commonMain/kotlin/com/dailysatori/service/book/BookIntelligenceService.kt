package com.dailysatori.service.book

import kotlinx.serialization.Serializable

@Serializable
data class BookViewpointDraft(
    val title: String,
    val content: String,
    val example: String,
    val status: String = "ready",
    val errorMessage: String = "",
    val outlineJson: String = "",
    val sourceNotes: String = "",
)

enum class BookViewpointSource { WeRead, AiFallback }

data class BookViewpointGenerationResult(
    val drafts: List<BookViewpointDraft>,
    val source: BookViewpointSource,
)

interface BookIntelligenceSource {
    suspend fun searchBooks(query: String): List<BookSearchResult>
    suspend fun generateViewpoints(book: BookSearchResult): BookViewpointGenerationResult
}

class BookIntelligenceService(
    private val weReadSkillService: BookIntelligenceSource,
) {
    suspend fun searchBooks(query: String): List<BookSearchResult> =
        weReadSkillService.searchBooks(query)

    suspend fun generateViewpoints(book: BookSearchResult): BookViewpointGenerationResult =
        weReadSkillService.generateViewpoints(book)
}
