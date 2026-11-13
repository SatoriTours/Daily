package com.dailysatori.service.remotenews

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class RemoteFeedParsingTest {
    @Test
    fun readsCdataHtmlNamespacedContentAndPublishedTime() {
        val articles = parseRemoteFeedArticles(RSS_FEED, FEED_URL, limit = 50)

        assertEquals(1, articles.size)
        val article = articles.single()
        assertEquals("CDATA 标题", article.title)
        assertEquals("https://example.com/a?utm=x#frag", article.url)
        assertTrue(article.content.orEmpty().contains("正文段落"))
        assertTrue(article.content.orEmpty().contains("要点"))
        assertTrue(article.summary.orEmpty().contains("摘要"))
        assertEquals("2026-11-12T08:00:00Z", article.publishedAt)
        assertTrue(article.id != 0L)
    }

    @Test
    fun readsAtomEntriesWithRelativeAlternateLink() {
        val articles = parseRemoteFeedArticles(ATOM_FEED, "https://example.com/feed.xml", limit = 50)

        assertEquals(1, articles.size)
        val article = articles.single()
        assertEquals("Atom 标题", article.title)
        assertEquals("https://example.com/posts/1", article.url)
        assertTrue(article.content.orEmpty().contains("原子正文"))
        assertEquals("2026-11-11T02:03:04Z", article.publishedAt)
    }

    @Test
    fun keepsStableIdentityForNonNumericAndMissingGuids() {
        val first = parseRemoteFeedArticles(RSS_FEED, FEED_URL, limit = 50).single()
        val second = parseRemoteFeedArticles(RSS_FEED, FEED_URL, limit = 50).single()
        assertEquals(first.id, second.id)

        val withoutGuid = parseRemoteFeedArticles(RSS_FEED.replace(GUID_LINE, ""), FEED_URL, limit = 50).single()
        val repeated = parseRemoteFeedArticles(RSS_FEED.replace(GUID_LINE, ""), FEED_URL, limit = 50).single()
        assertEquals(withoutGuid.id, repeated.id)
        assertNotEquals(first.id, withoutGuid.id)

        val otherLink = parseRemoteFeedArticles(
            RSS_FEED.replace(GUID_LINE, "").replace("https://example.com/a?utm=x#frag", "https://example.com/b"),
            FEED_URL,
            limit = 50,
        ).single()
        assertNotEquals(withoutGuid.id, otherLink.id)
    }

    @Test
    fun collapsesDuplicateEntriesAndHonorsLimit() {
        val duplicated = """
            <rss version="2.0"><channel>
              <item><title>重复</title><link>https://example.com/dup</link></item>
              <item><title>重复</title><link>https://example.com/dup</link></item>
            </channel></rss>
        """.trimIndent()
        assertEquals(1, parseRemoteFeedArticles(duplicated, FEED_URL, limit = 50).size)
        assertEquals(0, parseRemoteFeedArticles(RSS_FEED, FEED_URL, limit = 0).size)
        assertEquals(1, parseRemoteFeedArticles(RSS_FEED, FEED_URL, limit = 1).size)
    }

    @Test
    fun emptyChannelIsAnEmptyFeedInsteadOfAFailure() {
        assertTrue(parseRemoteFeedArticles("<rss version=\"2.0\"><channel><title>空</title></channel></rss>", FEED_URL, 50).isEmpty())
    }

    @Test
    fun rejectsNonFeedAndBrokenXml() {
        assertFailsWith<RemoteFeedFormatException> {
            parseRemoteFeedArticles("<html><body>登录页面</body></html>", FEED_URL, 50)
        }
        assertFailsWith<RemoteFeedFormatException> {
            parseRemoteFeedArticles("这不是 XML", FEED_URL, 50)
        }
        assertFailsWith<RemoteFeedFormatException> {
            parseRemoteFeedArticles("", FEED_URL, 50)
        }
    }

    @Test
    fun neverResolvesExternalEntitiesFromDoctype() {
        val malicious = """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE rss [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
            <rss version="2.0"><channel><item><title>&xxe;</title><link>https://example.com/xxe</link>
            <description>&xxe;</description></item></channel></rss>
        """.trimIndent()

        // The XML parser keeps the reference literal: the external file is never read or inlined.
        val articles = parseRemoteFeedArticles(malicious, FEED_URL, 50)

        assertEquals(listOf("&xxe;"), articles.map { it.title })
        assertFalse(articles.any { it.content.orEmpty().contains("root:") })
        assertFalse(articles.any { it.url.orEmpty().contains("/etc/passwd") })
    }

    @Test
    fun skipsEntriesWithoutUsableHttpLink() {
        val feed = """
            <rss version="2.0"><channel>
              <item><title>脚本链接</title><link>javascript:alert(1)</link><guid>1</guid></item>
              <item><title>正常链接</title><link>https://example.com/ok</link></item>
            </channel></rss>
        """.trimIndent()

        val articles = parseRemoteFeedArticles(feed, FEED_URL, 50)

        assertEquals(listOf("正常链接"), articles.map { it.title })
    }

    private companion object {
        const val FEED_URL = "https://example.com/feed?category=tech"
        const val GUID_LINE = "<guid isPermaLink=\"false\">tag:example.com,2026:a</guid>"
        val RSS_FEED = """
            <rss version="2.0" xmlns:content="http://purl.org/rss/1.0/modules/content/">
              <channel>
                <title>Tech</title>
                <item>
                  <title>CDATA 标题</title>
                  <link>https://example.com/a?utm=x#frag</link>
                  $GUID_LINE
                  <description><![CDATA[<p>摘要<strong>重点</strong></p>]]></description>
                  <content:encoded><![CDATA[<p>正文段落</p><ul><li>要点</li></ul>]]></content:encoded>
                  <pubDate>Tue, 12 Nov 2026 08:00:00 GMT</pubDate>
                </item>
              </channel>
            </rss>
        """.trimIndent()
        val ATOM_FEED = """
            <feed xmlns="http://www.w3.org/2005/Atom">
              <title>Atom 源</title>
              <entry>
                <title>Atom 标题</title>
                <link rel="alternate" href="/posts/1"/>
                <id>urn:uuid:0001</id>
                <summary>原子摘要</summary>
                <content type="html">&lt;p&gt;原子正文&lt;/p&gt;</content>
                <updated>2026-11-11T02:03:04Z</updated>
              </entry>
            </feed>
        """.trimIndent()
    }
}
