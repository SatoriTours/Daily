package com.dailysatori.service.mcp

import kotlin.test.Test
import kotlin.test.assertEquals

class McpSearchResultPersistenceTest {
    @Test
    fun encodesAndDecodesSearchResultsForChatPersistence() {
        val results = listOf(
            McpSearchResult(
                id = 42L,
                type = "diary",
                title = "最近一篇日记",
                summary = "今天记录了重要想法",
                createdAt = "2026-05-03",
                tags = listOf("生活", "想法"),
                isFavorite = null,
            ),
            McpSearchResult(
                id = 7L,
                type = "article",
                title = "一篇文章",
                summary = null,
                createdAt = null,
                tags = null,
                isFavorite = true,
            ),
        )

        val encoded = encodeMcpSearchResults(results)
        val decoded = decodeMcpSearchResults(encoded)

        assertEquals(results, decoded)
    }

    @Test
    fun decodesBlankSearchResultsAsEmptyList() {
        assertEquals(emptyList(), decodeMcpSearchResults(null))
        assertEquals(emptyList(), decodeMcpSearchResults(""))
    }
}
