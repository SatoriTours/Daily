package com.dailysatori.service.remotenews

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RemoteFeedRfc5005ParsingTest {
    @Test
    fun atomPagingLinksAreReadAndResolvedAgainstTheDocumentAddress() {
        val document = parseRemoteFeedDocument(
            """
            <feed xmlns="http://www.w3.org/2005/Atom">
              <title>示例</title>
              <link rel="self" href="index.atom"/>
              <link rel="next" href="page2.atom"/>
              <link rel="previous" href="page0.atom"/>
              <entry><title>一</title><link href="https://example.com/1"/><id>1</id></entry>
            </feed>
            """.trimIndent(),
            "https://example.com/feeds/index.atom",
        )

        assertEquals("https://example.com/feeds/page2.atom", document.links.next)
        assertEquals("https://example.com/feeds/page0.atom", document.links.previous)
        assertEquals(1, document.entries.size)
        assertFalse(document.isArchive)
    }

    @Test
    fun rssAtomLinkRequiresTheAtomNamespace() {
        val atom = parseRemoteFeedDocument(
            """
            <rss version="2.0" xmlns:atom="http://www.w3.org/2005/Atom">
              <channel>
                <title>示例</title>
                <atom:link rel="prev-archive" href="https://example.com/2026-10/index.rss"/>
                <item><title>一</title><link>https://example.com/1</link></item>
              </channel>
            </rss>
            """.trimIndent(),
            "https://example.com/index.rss",
        )
        assertEquals("https://example.com/2026-10/index.rss", atom.links.prevArchive)

        val foreign = parseRemoteFeedDocument(
            """
            <rss version="2.0" xmlns:vendor="https://vendor.example/ns">
              <channel>
                <title>示例</title>
                <vendor:link rel="prev-archive" href="https://vendor.example/archive"/>
                <item><title>一</title><link>https://example.com/1</link></item>
              </channel>
            </rss>
            """.trimIndent(),
            "https://example.com/index.rss",
        )
        assertNull(foreign.links.prevArchive, "a link outside the Atom namespace is not an RFC 5005 relation")
        assertEquals(RemoteFeedPagingMode.PLAIN, remoteFeedPagingMode(foreign.toPage()))
    }

    @Test
    fun rssArchiveAndCompleteMarkersAreRecognised() {
        val archive = parseRemoteFeedDocument(
            """
            <rss version="2.0" xmlns:atom="http://www.w3.org/2005/Atom" xmlns:fh="http://purl.org/syndication/history/1.0">
              <channel>
                <title>示例</title>
                <fh:archive/>
                <atom:link rel="current" href="https://example.com/index.rss"/>
                <atom:link rel="prev-archive" href="https://example.com/2026-09/index.rss"/>
                <item><title>一</title><link>https://example.com/1</link></item>
              </channel>
            </rss>
            """.trimIndent(),
            "https://example.com/2026-10/index.rss",
        )
        assertTrue(archive.isArchive)
        assertEquals("https://example.com/index.rss", archive.links.current)
        assertEquals(RemoteFeedPagingMode.ARCHIVE, remoteFeedPagingMode(archive.toPage()))

        val complete = parseRemoteFeedDocument(
            """
            <feed xmlns="http://www.w3.org/2005/Atom" xmlns:fh="http://purl.org/syndication/history/1.0">
              <title>示例</title>
              <fh:complete/>
              <entry><title>一</title><link href="https://example.com/1"/><id>1</id></entry>
            </feed>
            """.trimIndent(),
            "https://example.com/index.atom",
        )
        assertTrue(complete.isComplete)
        assertEquals(RemoteFeedPagingMode.PLAIN, remoteFeedPagingMode(complete.toPage()))
    }

    @Test
    fun xmlBaseAndRelativeReferencesAreResolved() {
        val document = parseRemoteFeedDocument(
            """
            <feed xmlns="http://www.w3.org/2005/Atom" xml:base="https://cdn.example.com/archive/">
              <title>示例</title>
              <link rel="next" href="page2.atom"/>
              <entry><title>一</title><link href="post/1"/><id>1</id></entry>
            </feed>
            """.trimIndent(),
            "https://example.com/feeds/index.atom",
        )

        assertEquals("https://cdn.example.com/archive/page2.atom", document.links.next)
    }

    @Test
    fun rss10ChannelLinksAreRead() {
        val document = parseRemoteFeedDocument(
            """
            <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"
                     xmlns="http://purl.org/rss/1.0/"
                     xmlns:atom="http://www.w3.org/2005/Atom">
              <channel rdf:about="https://example.com/">
                <title>示例</title>
                <atom:link rel="prev-archive" href="https://example.com/2026-09/index.rdf"/>
              </channel>
              <item rdf:about="https://example.com/1"><title>一</title><link>https://example.com/1</link></item>
            </rdf:RDF>
            """.trimIndent(),
            "https://example.com/index.rdf",
        )

        assertEquals("https://example.com/2026-09/index.rdf", document.links.prevArchive)
        assertEquals(1, document.entries.size)
    }

    @Test
    fun previousIsNeverUsedAsTheHistoryDirection() {
        val page = RemoteFeedPage(
            url = "https://example.com/index.atom",
            articles = emptyList(),
            links = RemoteFeedLinks(previous = "https://example.com/page0.atom", first = "https://example.com/first.atom"),
        )

        assertNull(remoteFeedOlderDocumentUrl(page, RemoteFeedPagingMode.PAGED))
        assertEquals(
            "https://example.com/page2.atom",
            remoteFeedOlderDocumentUrl(page.copy(links = RemoteFeedLinks(next = "https://example.com/page2.atom")), RemoteFeedPagingMode.PAGED),
        )
        assertEquals(
            "https://example.com/2026-09/index.atom",
            remoteFeedOlderDocumentUrl(
                page.copy(links = RemoteFeedLinks(prevArchive = "https://example.com/2026-09/index.atom")),
                RemoteFeedPagingMode.ARCHIVE,
            ),
        )
    }

    private fun RemoteFeedDocument.toPage(): RemoteFeedPage = RemoteFeedPage(
        url = "https://example.com/index",
        articles = emptyList(),
        links = links,
        isArchive = isArchive,
        isComplete = isComplete,
    )
}
