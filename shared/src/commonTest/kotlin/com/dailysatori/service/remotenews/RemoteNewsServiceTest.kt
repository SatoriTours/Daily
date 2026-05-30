package com.dailysatori.service.remotenews

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
    fun parsesPublishedAtFromTopArticlesToday() {
        val response = parseTopArticlesTodayResponse(
            """
            {
              "articles": [
                {
                  "id": 100,
                  "title": "Published article",
                  "url": "https://example.com/published",
                  "published_at": "2026-05-28T06:30:00Z",
                  "created_at": "2026-05-29T11:04:12Z",
                  "processed_at": "2026-05-29T12:04:12Z"
                }
              ]
            }
            """.trimIndent(),
        )

        assertEquals("2026-05-28T06:30:00Z", response.articles.first().publishedAt)
    }

    @Test
    fun parsesAliasFieldsWhenStandardArticlesContainerDecodesSuccessfully() {
        val response = parseTopArticlesTodayResponse(
            """
            {
              "articles": [
                {
                  "id": 101,
                  "title": "Alias article",
                  "url": "https://example.com/alias",
                  "feedName": "Alias Feed",
                  "importanceScore": 8.5,
                  "coverUrl": "https://example.com/cover.jpg",
                  "publishedAt": "2026-05-28T06:30:00Z"
                }
              ]
            }
            """.trimIndent(),
        )

        val article = response.articles.first()
        assertEquals("Alias Feed", article.feedName)
        assertEquals(8.5, article.importanceScore)
        assertEquals("https://example.com/cover.jpg", article.coverUrl)
        assertEquals("2026-05-28T06:30:00Z", article.publishedAt)
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

    @Test
    fun parsesStandardRemoteNewsErrorEnvelope() {
        val message = parseRemoteNewsErrorMessage(
            """
            {
              "error": {
                "code": "unauthorized",
                "message": "Invalid token"
              }
            }
            """.trimIndent(),
        )

        assertEquals("Invalid token", message)
    }

    @Test
    fun parsesSimpleRemoteNewsErrorMessage() {
        val message = parseRemoteNewsErrorMessage(
            """
            {
              "error": "bad_request",
              "message": "page must be greater than 0"
            }
            """.trimIndent(),
        )

        assertEquals("page must be greater than 0", message)
    }

    @Test
    fun remoteNewsApiDocCoversErrorAndCompatibilityRules() {
        val doc = java.io.File("../docs/08-remote-news-api.md").readText()

        assertTrue(doc.contains("## 错误响应格式"))
        assertTrue(doc.contains("## HTTP 状态码约定"))
        assertTrue(doc.contains("## 字段兼容别名"))
        assertTrue(doc.contains("## 字段缺失时的 APP 行为"))
        assertTrue(doc.contains("## 内容、时间与图片规范"))
        assertTrue(doc.contains("`published_at` 表示原文实际发表时间"))
        assertTrue(doc.contains("不要用 `404` 表示今天没有新闻"))
    }
}
