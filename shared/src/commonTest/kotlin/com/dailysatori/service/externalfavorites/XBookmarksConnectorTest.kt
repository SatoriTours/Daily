package com.dailysatori.service.externalfavorites

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class XBookmarksConnectorTest {
    @Test
    fun canonicalizesXAndTwitterStatusUrls() {
        assertEquals("https://x.com/jack/status/20", canonicalizeXStatusUrl("https://twitter.com/jack/status/20?s=20"))
        assertEquals("https://x.com/jack/status/20", canonicalizeXStatusUrl("https://mobile.twitter.com/jack/status/20"))
        assertEquals("https://x.com/i/status/20", canonicalizeXStatusUrl("https://x.com/i/status/20"))
        assertEquals("https://x.com/jack/status/20", canonicalizeXStatusUrl("https://x.com/jack/status/20?utm_source=test"))
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
        assertEquals(null, page.nextCursor)
    }

    @Test
    fun parsesNextTokenAndCapabilities() {
        val json = """{"data":[],"meta":{"next_token":"cursor-2","result_count":0}}"""

        val page = XBookmarksResponseParser.parse(json)
        val connector = XBookmarksConnector()

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
}
