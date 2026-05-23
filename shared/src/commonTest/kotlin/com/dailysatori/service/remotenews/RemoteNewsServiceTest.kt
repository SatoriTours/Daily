package com.dailysatori.service.remotenews

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
}
