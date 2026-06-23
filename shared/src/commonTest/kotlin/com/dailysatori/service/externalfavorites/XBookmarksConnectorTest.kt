package com.dailysatori.service.externalfavorites

import com.dailysatori.shared.db.External_favorite_source
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
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
    fun fetchPageWritesRequestAndResponseToTaskHttpLogger() = runBlocking {
        val responseJson = """
            {
              "data": [{"id":"123","text":"Saved post","author_id":"42"}],
              "includes": {"users": [{"id":"42","username":"daily","name":"Daily"}]},
              "meta": {"result_count": 95, "next_token": "cursor-2"}
            }
        """.trimIndent()
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    assertEquals("/2/users/account-1/bookmarks", request.url.encodedPath)
                    respondJson(responseJson)
                }
            }
        }
        val logger = RecordingHttpLogger()
        val connector = XBookmarksConnector(client = client)

        connector.fetchPage(
            source = xTestSource(),
            cursor = null,
            pageSize = 100,
            httpLogger = logger,
            taskId = 77,
        )

        assertTrue(logger.entries.any { it.contains("request:77:bookmarks:GET") && it.contains("max_results=100") })
        assertTrue(logger.entries.any { it.contains("response:77:bookmarks:200") && it.contains("next_token") })
        assertTrue(logger.entries.none { it.contains("Authorization") || it.contains("secret") })
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
        assertEquals("https://x.com/daily/status/123", item.canonicalUrl)
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
    fun detectsOnlyXArticleLinkFromRawTweetTextEvenWhenArticleMetadataHasDescription() {
        val json = """
            {
              "data": [
                {
                  "id": "2068340624907202872",
                  "lang": "zxx",
                  "text": "https://t.co/iAedHNUNSa",
                  "author_id": "1056949890",
                  "article": {
                    "title": "内容创作不是表达欲，而是普通人最低成本的杠杆",
                    "description": "卡片摘要不是完整正文。"
                  },
                  "entities": {
                    "urls": [
                      {
                        "url": "https://t.co/iAedHNUNSa",
                        "expanded_url": "http://x.com/i/article/2068336874515734528",
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

        assertEquals("https://x.com/i/article/2068336874515734528", item.canonicalUrl)
        assertEquals("卡片摘要不是完整正文。", item.text)
    }

    @Test
    fun parsesReferencedRetweetedArticleCardAsReferencedTweetUrlNotExternalUrl() {
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

        assertEquals("https://x.com/daily/status/2068340624907202872", item.canonicalUrl)
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
        assertEquals("https://x.com/daily/status/2068340624907202872", item.canonicalUrl)
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
        assertEquals("https://x.com/writer/status/100", enriched.canonicalUrl)
        assertEquals("子帖长文章", enriched.title)
        assertEquals("这是引用子帖 API 才返回的完整长文章内容，应该用于后续文章导入。", enriched.text)
        assertEquals("Writer", enriched.authorName)
        assertTrue(enriched.contentHash != bookmark.contentHash)
        assertTrue(enriched.aiInputHash != bookmark.aiInputHash)
    }

    @Test
    fun xArticleBookmarkKeepsArticleUrlWhenMergingFetchedArticlePostContent() {
        val bookmark = ExternalFavoriteItemDraft(
            provider = ExternalFavoriteProvider.X.id,
            externalId = "2068340624907202872",
            canonicalUrl = "https://x.com/i/article/2010742786430021632",
            title = "X article link",
            text = "https://t.co/article",
            authorName = "Bookmark Author",
            sourceCreatedAt = null,
            favoritedAt = null,
            normalizedJson = """{"id":"2068340624907202872"}""",
            debugJson = "",
            contentHash = "bookmark-content",
            aiInputHash = "bookmark-ai",
        )
        val fetchedArticle = ExternalFavoriteItemDraft(
            provider = ExternalFavoriteProvider.X.id,
            externalId = "2010742786430021632",
            canonicalUrl = "https://x.com/writer/status/2010742786430021632",
            title = "Fetched article title",
            text = "Fetched article body from X API.",
            authorName = "Writer",
            sourceCreatedAt = null,
            favoritedAt = null,
            normalizedJson = """{"id":"2010742786430021632"}""",
            debugJson = "",
            contentHash = "article-content",
            aiInputHash = "article-ai",
        )

        val enriched = xBookmarkItemWithFetchedReferencedPost(bookmark, fetchedArticle)

        assertNotNull(enriched)
        assertEquals("2068340624907202872", enriched.externalId)
        assertEquals("https://x.com/i/article/2010742786430021632", enriched.canonicalUrl)
        assertEquals("Fetched article title", enriched.title)
        assertEquals("Fetched article body from X API.", enriched.text)
    }

    @Test
    fun fetchPageFetchesXArticleApiWhenBookmarkTextOnlyContainsXArticleLink() = runBlocking {
        val requestedPaths = mutableListOf<String>()
        val client = HttpClient(MockEngine { request ->
            requestedPaths += request.url.encodedPath
            when (request.url.encodedPath) {
                "/2/users/account-1/bookmarks" -> respondJson(
                    """
                        {
                          "data": [
                            {
                              "id": "bookmark-1",
                              "text": "https://t.co/article",
                              "author_id": "42",
                              "entities": {
                                "urls": [
                                  {
                                    "url": "https://t.co/article",
                                    "expanded_url": "https://x.com/i/article/2010742786430021632",
                                    "unwound_url": "https://x.com/i/article/2010742786430021632"
                                  }
                                ]
                              }
                            }
                          ],
                          "includes": {
                            "users": [{"id": "42", "username": "daily", "name": "Daily"}]
                          },
                          "meta": {"result_count": 1}
                        }
                    """.trimIndent(),
                )
                "/2/posts/2010742786430021632" -> respondJson(
                    """
                        {
                          "data": {
                            "id": "2010742786430021632",
                            "content": "Full X article body from the posts API.",
                            "author_id": "99",
                            "article": {"title": "Full X Article"}
                          },
                          "includes": {
                            "users": [{"id": "99", "username": "writer", "name": "Writer"}]
                          }
                        }
                    """.trimIndent(),
                )
                else -> respond("unexpected path ${request.url.encodedPath}", HttpStatusCode.NotFound)
            }
        })
        val connector = XBookmarksConnector(client = client)

        val page = connector.fetchPage(xTestSource(), cursor = null, pageSize = 100)

        assertEquals(listOf("/2/users/account-1/bookmarks", "/2/posts/2010742786430021632"), requestedPaths)
        val item = page.items.single()
        assertEquals("bookmark-1", item.externalId)
        assertEquals("https://x.com/i/article/2010742786430021632", item.canonicalUrl)
        assertEquals("Full X Article", item.title)
        assertEquals("Full X article body from the posts API.", item.text)
        assertEquals("Writer", item.authorName)
    }

    @Test
    fun fetchPageFetchesXArticleApiWhenBookmarkHasArticleTitleAndExpandedArticleUrl() = runBlocking {
        val requestedPaths = mutableListOf<String>()
        val client = HttpClient(MockEngine { request ->
            requestedPaths += request.url.encodedPath
            when (request.url.encodedPath) {
                "/2/users/account-1/bookmarks" -> respondJson(
                    """
                        {
                          "data": [
                            {
                              "id": "bookmark-article-card",
                              "text": "这篇文章值得读 https://t.co/article",
                              "author_id": "42",
                              "article": {"title": "收藏返回里的文章标题"},
                              "entities": {
                                "urls": [
                                  {
                                    "url": "https://t.co/article",
                                    "expanded_url": "https://x.com/i/article/2010742786430021632"
                                  }
                                ]
                              }
                            }
                          ],
                          "includes": {
                            "users": [{"id": "42", "username": "daily", "name": "Daily"}]
                          },
                          "meta": {"result_count": 1}
                        }
                    """.trimIndent(),
                )
                "/2/posts/2010742786430021632" -> respondJson(
                    """
                        {
                          "data": {
                            "id": "2010742786430021632",
                            "content": "API 返回的完整文章正文。",
                            "author_id": "99"
                          },
                          "includes": {
                            "users": [{"id": "99", "username": "writer", "name": "Writer"}]
                          }
                        }
                    """.trimIndent(),
                )
                else -> respond("unexpected path ${request.url.encodedPath}", HttpStatusCode.NotFound)
            }
        })
        val connector = XBookmarksConnector(client = client)

        val page = connector.fetchPage(xTestSource(), cursor = null, pageSize = 100)

        assertEquals(listOf("/2/users/account-1/bookmarks", "/2/posts/2010742786430021632"), requestedPaths)
        val item = page.items.single()
        assertEquals("bookmark-article-card", item.externalId)
        assertEquals("https://x.com/i/article/2010742786430021632", item.canonicalUrl)
        assertEquals("收藏返回里的文章标题", item.title)
        assertEquals("API 返回的完整文章正文。", item.text)
        assertEquals("Writer", item.authorName)
    }

    @Test
    fun fetchPageDoesNotFetchXArticleApiWhenXArticleLinkHasOtherText() = runBlocking {
        val requestedPaths = mutableListOf<String>()
        val client = HttpClient(MockEngine { request ->
            requestedPaths += request.url.encodedPath
            when (request.url.encodedPath) {
                "/2/users/account-1/bookmarks" -> respondJson(
                    """
                        {
                          "data": [
                            {
                              "id": "bookmark-2",
                              "text": "Read this https://t.co/article",
                              "author_id": "42",
                              "entities": {
                                "urls": [
                                  {
                                    "url": "https://t.co/article",
                                    "expanded_url": "https://x.com/i/article/2010742786430021632",
                                    "unwound_url": "https://x.com/i/article/2010742786430021632"
                                  }
                                ]
                              }
                            }
                          ],
                          "includes": {
                            "users": [{"id": "42", "username": "daily", "name": "Daily"}]
                          },
                          "meta": {"result_count": 1}
                        }
                    """.trimIndent(),
                )
                else -> respond("unexpected path ${request.url.encodedPath}", HttpStatusCode.NotFound)
            }
        })
        val connector = XBookmarksConnector(client = client)

        val page = connector.fetchPage(xTestSource(), cursor = null, pageSize = 100)

        assertEquals(listOf("/2/users/account-1/bookmarks"), requestedPaths)
        val item = page.items.single()
        assertEquals("bookmark-2", item.externalId)
        assertEquals("https://x.com/daily/status/bookmark-2", item.canonicalUrl)
        assertEquals("Read this https://t.co/article", item.text)
    }

    @Test
    fun fetchPageDoesNotFetchXArticleApiForRegularExternalUrl() = runBlocking {
        val requestedPaths = mutableListOf<String>()
        val client = HttpClient(MockEngine { request ->
            requestedPaths += request.url.encodedPath
            when (request.url.encodedPath) {
                "/2/users/account-1/bookmarks" -> respondJson(
                    """
                        {
                          "data": [
                            {
                              "id": "bookmark-3",
                              "text": "https://t.co/external",
                              "author_id": "42",
                              "entities": {
                                "urls": [
                                  {
                                    "url": "https://t.co/external",
                                    "expanded_url": "https://example.com/article",
                                    "unwound_url": "https://example.com/article",
                                    "title": "External Article",
                                    "description": "External article summary"
                                  }
                                ]
                              }
                            }
                          ],
                          "includes": {
                            "users": [{"id": "42", "username": "daily", "name": "Daily"}]
                          },
                          "meta": {"result_count": 1}
                        }
                    """.trimIndent(),
                )
                else -> respond("unexpected path ${request.url.encodedPath}", HttpStatusCode.NotFound)
            }
        })
        val connector = XBookmarksConnector(client = client)

        val page = connector.fetchPage(xTestSource(), cursor = null, pageSize = 100)

        assertEquals(listOf("/2/users/account-1/bookmarks"), requestedPaths)
        val item = page.items.single()
        val normalized = Json.parseToJsonElement(item.normalizedJson).jsonObject
        assertEquals("bookmark-3", item.externalId)
        assertEquals("https://x.com/daily/status/bookmark-3", item.canonicalUrl)
        assertEquals("https://example.com/article", normalized["primary_url"]?.jsonPrimitive?.contentOrNull)
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

    private fun MockRequestHandleScope.respondJson(content: String) =
        respond(
            content = content,
            status = HttpStatusCode.OK,
            headers = headersOf(HttpHeaders.ContentType, "application/json"),
        )

    private fun xTestSource(): External_favorite_source =
        External_favorite_source(
            id = 1,
            provider = ExternalFavoriteProvider.X.id,
            display_name = "X",
            account_id = "account-1",
            account_name = "Daily",
            enabled = 1,
            sync_interval_minutes = 720,
            last_sync_started_at = null,
            last_sync_completed_at = null,
            last_success_at = null,
            last_sync_window_started_at = null,
            last_items_seen_count = 0,
            last_pages_seen_count = 0,
            last_error = "",
            last_error_code = "",
            last_error_message = "",
            status = "healthy",
            last_sync_mode = "",
            rate_limit_reset_at = null,
            auth_json = """{"access_token":"token"}""",
            config_json = "{}",
            capabilities_json = "{}",
            created_at = 0,
            updated_at = 0,
        )

    private class RecordingHttpLogger : FavoriteSyncHttpLogger {
        val entries = mutableListOf<String>()

        override fun logRequest(
            taskId: Long?,
            label: String,
            method: String,
            url: String,
            parameters: Map<String, String>,
        ) {
            entries += "request:$taskId:$label:$method:$url:${parameters.entries.joinToString("&") { "${it.key}=${it.value}" }}"
        }

        override fun logResponse(
            taskId: Long?,
            label: String,
            statusCode: Int,
            headers: Map<String, String>,
            body: String,
        ) {
            entries += "response:$taskId:$label:$statusCode:$body"
        }
    }
}
