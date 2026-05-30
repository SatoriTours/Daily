package com.dailysatori.service.remotenews

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class RemoteNewsServiceTest {
    @Test
    fun parsesTopArticlesTodayWhenApiReturnsDataArray() {
        val response = parseTopArticlesTodayResponse(
            """
            {
              "data": [
                {"id": 1, "title": "First", "url": "https://example.com/first"}
              ]
            }
            """.trimIndent(),
        )

        assertEquals(1, response.articles.size)
        assertEquals("First", response.articles.first().title)
    }

    @Test
    fun parsesTopArticlesTodayWhenApiWrapsArticleItems() {
        val response = parseTopArticlesTodayResponse(
            """
            {
              "top_articles": [
                {"score": 99, "article": {"id": 2, "title": "Wrapped", "url": "https://example.com/wrapped"}}
              ]
            }
            """.trimIndent(),
        )

        assertEquals(1, response.articles.size)
        assertEquals("Wrapped", response.articles.first().title)
    }

    @Test
    fun parsesTopArticlesTodayWhenArticleIdIsNumericString() {
        val response = parseTopArticlesTodayResponse(
            """
            {
              "data": {
                "articles": [
                  {"article_id": "46", "title": "String id", "url": "https://example.com/string-id"}
                ]
              }
            }
            """.trimIndent(),
        )

        assertEquals(1, response.articles.size)
        assertEquals(46, response.articles.first().id)
        assertEquals("String id", response.articles.first().title)
    }

    @Test
    fun parsesTopArticlesTodayWhenViewpointsIsNull() {
        val response = parseTopArticlesTodayResponse(
            """
            {
              "articles": [
                {
                  "id": 9935,
                  "title": "claude-code",
                  "url": "https://github.com/anthropics/claude-code",
                  "summary": "Claude Code is an agentic coding tool.",
                  "viewpoints": null,
                  "content": "",
                  "domain": "github.com",
                  "importance_score": 9.88026,
                  "cover_url": null,
                  "created_at": "2026-05-29T11:04:12Z",
                  "processed_at": "2026-05-29T11:04:12.317756353Z",
                  "SourceName": ""
                }
              ],
              "pagination": {"next": 2, "page": 1, "per_page": 50, "total": 1043, "total_pages": 21}
            }
            """.trimIndent(),
        )

        assertEquals(1, response.articles.size)
        assertEquals(9935, response.articles.first().id)
        assertEquals(emptyList(), response.articles.first().viewpoints)
        assertEquals(2, response.pagination.next)
    }

    @Test
    fun decodesRemoteArticlesResponseWhenViewpointsIsNull() {
        val response = Json { ignoreUnknownKeys = true; isLenient = true }.decodeFromString<RemoteArticlesResponse>(
            """
            {
              "articles": [
                {"id": 9935, "title": "claude-code", "viewpoints": null}
              ],
              "pagination": {"page": 1}
            }
            """.trimIndent(),
        )

        assertEquals(1, response.articles.size)
        assertEquals(emptyList(), response.articles.first().viewpoints)
    }
}
