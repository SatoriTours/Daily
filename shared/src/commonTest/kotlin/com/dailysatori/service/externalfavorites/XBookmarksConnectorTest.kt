package com.dailysatori.service.externalfavorites

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull

class XBookmarksConnectorTest {
    @Test
    fun bookmarkEndpointUsesAuthenticatedUserIdPath() {
        assertEquals("/2/users/2244994945/bookmarks", xBookmarksEndpointPath("2244994945"))
    }

    @Test
    fun canonicalizesXAndTwitterStatusUrls() {
        assertEquals("https://x.com/jack/status/20", canonicalizeXStatusUrl("https://twitter.com/jack/status/20?s=20"))
        assertEquals("https://x.com/jack/status/20", canonicalizeXStatusUrl("https://mobile.twitter.com/jack/status/20"))
        assertEquals("https://x.com/i/status/20", canonicalizeXStatusUrl("https://x.com/i/status/20"))
        assertEquals("https://x.com/jack/status/20", canonicalizeXStatusUrl("https://x.com/jack/status/20?utm_source=test"))
    }

    @Test
    fun extractsPostIdFromXStatusUrlsForLookupApi() {
        assertEquals("2068340624907202872", xPostIdFromStatusUrl("https://x.com/i/status/2068340624907202872"))
        assertEquals("2068340624907202872", xPostIdFromStatusUrl("https://x.com/user/status/2068340624907202872?s=20"))
        assertEquals("2068340624907202872", xPostIdFromStatusUrl("https://twitter.com/user/status/2068340624907202872"))
        assertNull(xPostIdFromStatusUrl("https://x.com/search?q=2068340624907202872"))
    }

    @Test
    fun parsesBookmarkResponseIntoDrafts() {
        val json = """{"data":[{"id":"123","text":"Saved post","author_id":"42","created_at":"2026-06-01T00:00:00.000Z"}],"includes":{"users":[{"id":"42","username":"daily","name":"Daily"}]},"meta":{"result_count":1}}"""

        val page = XBookmarksResponseParser.parse(json)

        assertEquals("123", page.items.single().externalId)
        assertEquals(ExternalFavoriteProvider.X.id, page.items.single().provider)
        assertEquals("https://x.com/daily/status/123", page.items.single().canonicalUrl)
        assertEquals("Saved post", page.items.single().title)
        assertEquals("Saved post", page.items.single().text)
        assertEquals("Daily", page.items.single().authorName)
        assertEquals(1_780_272_000_000L, page.items.single().sourceCreatedAt)
        assertNull(page.items.single().favoritedAt)
        assertEquals("", page.items.single().debugJson)
        assertEquals(null, page.nextCursor)
    }

    @Test
    fun parsesLongPostAndLinkCardFieldsForArticleImport() {
        val json = """
            {
              "data": [
                {
                  "id": "123",
                  "text": "Short text https://t.co/a",
                  "author_id": "42",
                  "created_at": "2026-06-01T00:00:00.000Z",
                  "note_tweet": {
                    "text": "This is the full long-form post body with much more useful context.",
                    "entities": {
                      "urls": [
                        {
                          "url": "https://t.co/a",
                          "expanded_url": "https://example.com/article",
                          "unwound_url": "https://example.com/article-final",
                          "display_url": "example.com/article",
                          "title": "External Article Title",
                          "description": "External article description",
                          "images": [{"url": "https://example.com/cover.jpg"}]
                        }
                      ]
                    }
                  },
                  "entities": {
                    "urls": [
                      {
                        "url": "https://t.co/a",
                        "expanded_url": "https://example.com/article",
                        "title": "Short Card Title"
                      }
                    ]
                  }
                }
              ],
              "includes": {
                "users": [{"id": "42", "username": "daily", "name": "Daily", "profile_image_url": "https://x.com/avatar.jpg"}]
              },
              "meta": {"result_count": 1}
            }
        """.trimIndent()

        val item = XBookmarksResponseParser.parse(json).items.single()
        val normalized = Json.parseToJsonElement(item.normalizedJson).jsonObject

        assertEquals("This is the full long-form post body with much more useful context.", item.text)
        assertEquals("External Article Title", item.title)
        assertEquals("https://example.com/article-final", item.canonicalUrl)
        assertEquals("This is the full long-form post body with much more useful context.", normalized["note_text"]?.jsonPrimitive?.contentOrNull)
        assertEquals("https://example.com/article-final", normalized["primary_url"]?.jsonPrimitive?.contentOrNull)
        assertEquals("External Article Title", normalized["url_title"]?.jsonPrimitive?.contentOrNull)
        assertEquals("External article description", normalized["url_description"]?.jsonPrimitive?.contentOrNull)
        assertTrue(item.normalizedJson.contains("https://example.com/cover.jpg"))
        assertTrue(item.normalizedJson.contains("profile_image_url"))
    }

    @Test
    fun parsesXArticleTitleFromBookmarkResponseWhenTextOnlyContainsLink() {
        val json = """
            {
              "data": [
                {
                  "id": "2068340624907202872",
                  "lang": "zxx",
                  "text": "https://t.co/iAedHNUNSa",
                  "author_id": "1056949890",
                  "article": {"title": "内容创作不是表达欲，而是普通人最低成本的杠杆"},
                  "entities": {
                    "urls": [
                      {
                        "url": "https://t.co/iAedHNUNSa",
                        "expanded_url": "http://x.com/i/article/2068336874515734528",
                        "display_url": "x.com/i/article/2068…",
                        "status": 500,
                        "unwound_url": "https://x.com/i/article/2068336874515734528"
                      }
                    ]
                  }
                }
              ],
              "includes": {
                "users": [{"id": "1056949890", "username": "nolan", "name": "Nolan"}]
              },
              "meta": {"result_count": 1}
            }
        """.trimIndent()

        val item = XBookmarksResponseParser.parse(json).items.single()
        val normalized = Json.parseToJsonElement(item.normalizedJson).jsonObject

        assertEquals("https://x.com/i/article/2068336874515734528", item.canonicalUrl)
        assertEquals("内容创作不是表达欲，而是普通人最低成本的杠杆", item.title)
        assertEquals("https://t.co/iAedHNUNSa", item.text)
        assertEquals("内容创作不是表达欲，而是普通人最低成本的杠杆", normalized["url_title"]?.jsonPrimitive?.contentOrNull)
    }

    @Test
    fun parsesReferencedRetweetedArticleCardAsCanonicalArticleUrl() {
        val json = """
            {
              "data": [
                {
                  "id": "2068340624907202872",
                  "text": "收藏列表里只能看到转发壳",
                  "author_id": "42",
                  "referenced_tweets": [{"type": "retweeted", "id": "100"}]
                }
              ],
              "includes": {
                "users": [
                  {"id": "42", "username": "daily", "name": "Daily"},
                  {"id": "99", "username": "writer", "name": "Writer"}
                ],
                "tweets": [
                  {
                    "id": "100",
                    "text": "原推里的文章卡片 https://t.co/article",
                    "author_id": "99",
                    "entities": {
                      "urls": [
                        {
                          "url": "https://t.co/article",
                          "expanded_url": "https://example.com/long-article",
                          "unwound_url": "https://example.com/long-article",
                          "title": "Long Article",
                          "description": "Article summary from the card"
                        }
                      ]
                    }
                  }
                ]
              },
              "meta": {"result_count": 1}
            }
        """.trimIndent()

        val item = XBookmarksResponseParser.parse(json).items.single()
        val normalized = Json.parseToJsonElement(item.normalizedJson).jsonObject

        assertEquals("https://example.com/long-article", item.canonicalUrl)
        assertEquals("Long Article", item.title)
        assertEquals("https://example.com/long-article", normalized["primary_url"]?.jsonPrimitive?.contentOrNull)
        assertTrue(item.normalizedJson.contains("referenced_tweets"))
    }

    @Test
    fun parsesSinglePostLookupResponseWithReferencedArticleCard() {
        val json = """
            {
              "data": {
                "id": "2068340624907202872",
                "text": "转发壳",
                "author_id": "42",
                "referenced_tweets": [{"type": "retweeted", "id": "100"}]
              },
              "includes": {
                "users": [
                  {"id": "42", "username": "daily", "name": "Daily"},
                  {"id": "99", "username": "writer", "name": "Writer"}
                ],
                "tweets": [
                  {
                    "id": "100",
                    "text": "原推里的文章卡片 https://t.co/article",
                    "author_id": "99",
                    "entities": {
                      "urls": [
                        {
                          "url": "https://t.co/article",
                          "expanded_url": "https://example.com/long-article",
                          "unwound_url": "https://example.com/long-article",
                          "title": "Long Article",
                          "description": "Article summary from the card"
                        }
                      ]
                    }
                  }
                ]
              }
            }
        """.trimIndent()

        val item = XBookmarksResponseParser.parsePostLookup(json)

        assertNotNull(item)
        assertEquals("2068340624907202872", item.externalId)
        assertEquals("https://example.com/long-article", item.canonicalUrl)
        assertEquals("Long Article", item.title)
        assertTrue(item.normalizedJson.contains("referenced_tweets"))
    }

    @Test
    fun parsesArticleLookupResponseContentFieldAsDraftText() {
        val json = """
            {
              "data": {
                "id": "2010742786430021632",
                "content": "This is the full article body returned by the posts endpoint.",
                "author_id": "256523056",
                "article": {"title": "How to fix your entire life in 1 day"}
              },
              "includes": {
                "users": [{"id": "256523056", "username": "thedankoe", "name": "DAN KOE"}]
              }
            }
        """.trimIndent()

        val item = XBookmarksResponseParser.parsePostLookup(json)

        assertNotNull(item)
        assertEquals("2010742786430021632", item.externalId)
        assertEquals("This is the full article body returned by the posts endpoint.", item.text)
        assertEquals("How to fix your entire life in 1 day", item.title)
    }

    @Test
    fun referencedBookmarkCanUseFetchedChildPostContentWhileKeepingBookmarkIdentity() {
        val bookmarkJson = """
            {
              "data": [
                {
                  "id": "2068340624907202872",
                  "text": "收藏列表里只能看到转发壳",
                  "author_id": "42",
                  "referenced_tweets": [{"type": "retweeted", "id": "100"}]
                }
              ],
              "includes": {
                "users": [{"id": "42", "username": "daily", "name": "Daily"}]
              },
              "meta": {"result_count": 1}
            }
        """.trimIndent()
        val lookupJson = """
            {
              "data": {
                "id": "100",
                "text": "原始长文章的短文本 https://t.co/article",
                "author_id": "99",
                "note_tweet": {
                  "text": "这是引用子帖 API 才返回的完整长文章内容，应该用于后续文章导入。",
                  "entities": {
                    "urls": [
                      {
                        "url": "https://t.co/article",
                        "expanded_url": "https://example.com/long-article",
                        "unwound_url": "https://example.com/long-article",
                        "title": "子帖长文章",
                        "description": "来自子帖 API 的文章摘要"
                      }
                    ]
                  }
                }
              },
              "includes": {
                "users": [{"id": "99", "username": "writer", "name": "Writer"}]
              }
            }
        """.trimIndent()

        val bookmark = XBookmarksResponseParser.parse(bookmarkJson).items.single()
        val fetchedChild = XBookmarksResponseParser.parsePostLookup(lookupJson)
        val enriched = xBookmarkItemWithFetchedReferencedPost(bookmark, fetchedChild)

        assertNotNull(fetchedChild)
        assertNotNull(enriched)
        assertEquals("2068340624907202872", enriched.externalId)
        assertEquals("https://example.com/long-article", enriched.canonicalUrl)
        assertEquals("子帖长文章", enriched.title)
        assertEquals("这是引用子帖 API 才返回的完整长文章内容，应该用于后续文章导入。", enriched.text)
        assertEquals("Writer", enriched.authorName)
        assertTrue(enriched.contentHash != bookmark.contentHash)
        assertTrue(enriched.aiInputHash != bookmark.aiInputHash)
    }

    @Test
    fun parsesNextTokenAndCapabilities() {
        val json = """{"data":[],"meta":{"next_token":"cursor-2","result_count":0}}"""

        val page = XBookmarksResponseParser.parse(json)
        val connector = XBookmarksConnector(developmentMode = false)

        assertEquals("cursor-2", page.nextCursor)
        assertEquals(100, connector.capabilities.maxPageSize)
        assertEquals(15, connector.capabilities.defaultBackoffMinutes)
        assertEquals(3, connector.capabilities.maxPagesPerRun)
        assertEquals(300, connector.capabilities.maxItemsPerRun)
        assertEquals(false, connector.capabilities.supportsFolders)
        assertEquals(false, connector.capabilities.supportsFavoritedAt)
        assertEquals(false, connector.capabilities.supportsWriteBack)
        assertEquals(true, connector.capabilities.supportsRefreshToken)
    }

    @Test
    fun parsesNextTokenWhenBookmarkPageHasNinetyFiveItems() {
        val tweets = (1..95).joinToString(",") { index ->
            """{"id":"$index","text":"Saved post $index","author_id":"42"}"""
        }
        val json = """
            {
              "data": [$tweets],
              "includes": {"users": [{"id": "42", "username": "daily", "name": "Daily"}]},
              "meta": {"result_count": 95, "next_token": "cursor-2"}
            }
        """.trimIndent()

        val page = XBookmarksResponseParser.parse(json)

        assertEquals(95, page.items.size)
        assertEquals("cursor-2", page.nextCursor)
    }

    @Test
    fun developmentModeUsesProductionBookmarkPageSizeForRealApiSafety() {
        assertEquals(100, XBookmarksConnector(developmentMode = true).capabilities.maxPageSize)
        assertEquals(100, XBookmarksConnector(developmentMode = false).capabilities.maxPageSize)
    }

    @Test
    fun fallsBackToIStatusUrlWhenUsernameIsMissing() {
        val json = """{"data":[{"id":"456","text":"No user","author_id":"missing"}],"meta":{"result_count":1}}"""

        val item = XBookmarksResponseParser.parse(json).items.single()

        assertEquals("https://x.com/i/status/456", item.canonicalUrl)
        assertEquals("", item.authorName)
    }

    @Test
    fun registryLooksUpXConnectorByProvider() {
        val registry = FavoriteConnectorRegistry.default()

        val connector = registry.get(ExternalFavoriteProvider.X.id)

        assertNotNull(connector)
        assertEquals(ExternalFavoriteProvider.X.id, connector.provider)
        assertNull(registry.get("unknown"))
    }

    @Test
    fun responseHandlerThrowsAuthRateLimitAndProviderErrorsForNonSuccess() {
        val emptyPageJson = """{"data":[],"meta":{"result_count":0}}"""

        val authError = assertFailsWith<XFavoriteAuthException> {
            parseXBookmarksHttpResponse(statusCode = 401, body = emptyPageJson)
        }
        assertEquals(401, authError.statusCode)

        val rateLimitError = assertFailsWith<XFavoriteRateLimitException> {
            parseXBookmarksHttpResponse(
                statusCode = 429,
                body = emptyPageJson,
                headers = mapOf("x-rate-limit-reset" to "1780272000"),
            )
        }
        assertEquals(1_780_272_000_000L, rateLimitError.rateLimitResetAt)

        val providerError = assertFailsWith<XFavoriteProviderException> {
            parseXBookmarksHttpResponse(statusCode = 500, body = emptyPageJson)
        }
        assertEquals(500, providerError.statusCode)
    }

    @Test
    fun authErrorIncludesSanitizedProviderDetails() {
        val body = """
            {
              "title": "Unauthorized",
              "detail": "Access token does not have required scope bookmark.read",
              "type": "https://api.x.com/2/problems/not-authorized-for-resource"
            }
        """.trimIndent()

        val error = assertFailsWith<XFavoriteAuthException> {
            parseXBookmarksHttpResponse(statusCode = 401, body = body)
        }

        assertEquals(401, error.statusCode)
        assertEquals(
            "X bookmarks authorization failed with HTTP 401: Unauthorized - Access token does not have required scope bookmark.read",
            error.message,
        )
    }

    @Test
    fun aiInputHashIgnoresNonPromptMetadataChanges() {
        val firstJson = """
            {
              "data": [
                {
                  "id": "789",
                  "text": "Stable prompt text",
                  "author_id": "42",
                  "created_at": "2026-06-01T00:00:00.000Z",
                  "attachments": {"media_keys": ["media-a"]}
                }
              ],
              "includes": {
                "users": [{"id": "42", "username": "daily", "name": "Daily"}],
                "media": [{"media_key": "media-a", "type": "photo", "url": "https://cdn.example.com/image.jpg"}]
              },
              "meta": {"result_count": 1}
            }
        """.trimIndent()
        val secondJson = """
            {
              "data": [
                {
                  "id": "789",
                  "text": "Stable prompt text",
                  "author_id": "42",
                  "created_at": "2026-06-01T00:00:00.000Z",
                  "attachments": {"media_keys": ["media-b"]}
                }
              ],
              "includes": {
                "users": [{"id": "42", "username": "daily", "name": "Daily"}],
                "media": [{"media_key": "media-b", "type": "animated_gif", "url": "https://cdn.example.com/image.jpg"}]
              },
              "meta": {"result_count": 1, "next_token": "different-page-metadata"}
            }
        """.trimIndent()

        val first = XBookmarksResponseParser.parse(firstJson).items.single()
        val second = XBookmarksResponseParser.parse(secondJson).items.single()

        assertEquals(first.aiInputHash, second.aiInputHash)
        assertTrue(first.normalizedJson != second.normalizedJson)
        assertEquals("", first.debugJson)
        assertEquals("", second.debugJson)
    }

    @Test
    fun canonicalizerRejectsInvalidHandlesAndNonStatusUrls() {
        assertNull(canonicalizeXStatusUrl("https://x.com/jack"))
        assertNull(canonicalizeXStatusUrl("https://x.com/home/status/20"))
        assertNull(canonicalizeXStatusUrl("https://x.com/bad-handle/status/20"))
        assertEquals("https://x.com/i/status/20", canonicalizeXStatusUrl("https://x.com/i/status/20"))
    }

    @Test
    fun expiredAuthJsonCanBeRefreshedWithoutLosingRefreshToken() {
        val authJson = """{"client_id":"client","access_token":"old","refresh_token":"refresh","expires_at":1000}"""

        assertTrue(xAuthShouldRefresh(authJson, nowMillis = 2_000))

        val refreshed = xRefreshedAuthJson(
            existingAuthJson = authJson,
            accessToken = "new",
            refreshToken = "",
            expiresInSeconds = 7200,
            nowMillis = 2_000,
            scope = "bookmark.read tweet.read users.read offline.access",
            tokenType = "bearer",
        )

        assertTrue(refreshed.contains(""""access_token":"new""""))
        assertTrue(refreshed.contains(""""refresh_token":"refresh""""))
        assertTrue(refreshed.contains(""""expires_at":7202000"""))
    }

}
