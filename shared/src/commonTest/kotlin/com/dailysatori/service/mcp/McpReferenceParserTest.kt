package com.dailysatori.service.mcp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class McpReferenceParserTest {
    @Test
    fun parsesReferenceIdsByTypeAndIgnoresMalformedEntries() {
        val refs = parseMcpReferenceIds("article_1, diary_2, book_3, book_viewpoint_4, bad, article_x")

        assertEquals(setOf(1L), refs.getValue("article"))
        assertEquals(setOf(2L), refs.getValue("diary"))
        assertEquals(setOf(3L), refs.getValue("book"))
        assertEquals(setOf(4L), refs.getValue("book_viewpoint"))
    }

    @Test
    fun emptyReferenceMapHasNoReferencedIds() {
        assertFalse(parseMcpReferenceIds("bad, article_x").hasMcpReferenceIds())
        assertTrue(parseMcpReferenceIds("article_1").hasMcpReferenceIds())
    }

    @Test
    fun matchesSearchResultsByReferenceTypeAndId() {
        val refs = parseMcpReferenceIds("article_1, diary_2")

        assertTrue(McpSearchResult(1, "article", "文章", null, null).matchesMcpReferenceIds(refs))
        assertTrue(McpSearchResult(2, "diary", "日记", null, null).matchesMcpReferenceIds(refs))
        assertFalse(McpSearchResult(3, "article", "文章", null, null).matchesMcpReferenceIds(refs))
        assertFalse(McpSearchResult(1, "unknown", "未知", null, null).matchesMcpReferenceIds(refs))
    }

    @Test
    fun noneReferenceContentFiltersAllResults() {
        assertTrue(mcpReferenceContentRequestsNoResults("none"))
        assertTrue(mcpReferenceContentRequestsNoResults(" NONE "))
        assertFalse(mcpReferenceContentRequestsNoResults("article_1"))
    }
}
