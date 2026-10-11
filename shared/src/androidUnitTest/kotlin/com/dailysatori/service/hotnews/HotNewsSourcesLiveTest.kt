package com.dailysatori.service.hotnews

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import kotlin.test.*

class HotNewsSourcesLiveTest {
    @Test fun publicRankingsReturnStableIdentitiesAndPlatformOrder() = runBlocking {
        assumeTrue("Opt in to real source validation", System.getenv("DAILY_HOT_NEWS_LIVE_TEST") == "1")
        val failures = mutableListOf<String>()
        HttpClient(OkHttp).use { http ->
            val client = HotNewsClient(http)
            HotNewsPlatform.entries.forEach { platform ->
                try {
                    val items = client.fetch(platform)
                    assertTrue(items.isNotEmpty())
                    assertEquals(items.map { it.rank }.sorted(), items.map { it.rank })
                    assertEquals(items.size, items.map { it.identity }.distinct().size)
                    assertTrue(items.all { safeHotNewsUrl(it.url) != null })
                    println("${platform.key}: ${items.size} items, ranks and links verified")
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { failures += "${platform.key}: ${e::class.simpleName}" }
            }
        }
        assertTrue(failures.isEmpty(), "真实热榜来源未通过：${failures.joinToString()}，不计为通过")
    }
}
