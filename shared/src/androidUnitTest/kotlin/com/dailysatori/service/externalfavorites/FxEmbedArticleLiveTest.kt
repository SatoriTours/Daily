package com.dailysatori.service.externalfavorites

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assume.assumeTrue
import kotlin.test.*

class FxEmbedArticleLiveTest {
    @Test fun retrievesTheReportedArticleWithoutOfficialCredentials() = runBlocking<Unit> {
        assumeTrue(System.getenv("DAILY_X_LIVE_TEST") == "1")
        HttpClient(OkHttp) {
            install(HttpTimeout) { requestTimeoutMillis = 20_000; connectTimeoutMillis = 10_000 }
        }.use { client ->
            var officialCalls = 0
            val draft = assertNotNull(XBookmarksConnector(client).fetchPostById(null, "2080877134319624492") {
                officialCalls++
                error("FxEmbed should return the article without paid official fallback")
            })
            assertEquals(0, officialCalls)
            assertEquals("https://x.com/i/article/2080872025028235264", draft.canonicalUrl)
            assertTrue(draft.title.contains("Saily"))
            assertTrue(draft.text.length > 12_000)
            assertTrue(draft.text.contains("免责声明"))
            assertTrue(draft.text.contains("办卡要趁早"))
            assertTrue(draft.text.contains("现在也办理不了了"))
            assertTrue(draft.text.contains("!["))
            val metadata = Json.parseToJsonElement(draft.normalizedJson).jsonObject
            assertEquals("true", metadata.textValue("article_content_complete"))
        }
    }
}
