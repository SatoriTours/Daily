package com.dailysatori.service.hotnews

import com.dailysatori.service.parser.articleHtmlToMarkdown
import io.ktor.client.HttpClient
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.*

class HotNewsClient(private val client: HttpClient) {
    suspend fun fetch(platform: HotNewsPlatform): List<HotNewsItem> {
        val items = when (platform) {
            HotNewsPlatform.GITHUB -> parseGithubHotNews(get(platform.boardUrl))
            HotNewsPlatform.HACKER_NEWS -> hackerNews()
            HotNewsPlatform.ZHIHU -> zhihu(get("https://www.zhihu.com/api/v3/feed/topstory/hot-list-web?limit=30&desktop=true"))
            HotNewsPlatform.TOUTIAO -> toutiao(get("https://www.toutiao.com/hot-event/hot-board/?origin=toutiao_pc"))
            HotNewsPlatform.V2EX -> v2ex(get("https://www.v2ex.com/api/topics/hot.json"))
        }.distinctBy { it.id }.take(30)
        require(items.isNotEmpty()) { "热榜暂不可用" }
        return items
    }

    private suspend fun get(url: String): String = client.get(url) {
        expectSuccess = true
        timeout { requestTimeoutMillis = 20_000; connectTimeoutMillis = 10_000 }
        header("User-Agent", "Mozilla/5.0 (compatible; DailySatori/1.0)")
        header("Accept", "application/json,text/html;q=0.9")
    }.bodyAsText()

    private suspend fun hackerNews(): List<HotNewsItem> = coroutineScope {
        val ids = Json.parseToJsonElement(get("https://hacker-news.firebaseio.com/v0/topstories.json")).jsonArray.take(30)
        val semaphore = Semaphore(4)
        ids.mapIndexed { index, id -> async {
            semaphore.withPermit {
                try {
                    val item = Json.parseToJsonElement(get("https://hacker-news.firebaseio.com/v0/item/${id.jsonPrimitive.content}.json"))
                    (item as? JsonObject)?.let { hackerNewsItem(it, index + 1) }
                } catch (e: CancellationException) { throw e }
                catch (_: Exception) { null }
            }
        } }.awaitAll().filterNotNull()
    }

    private fun hackerNewsItem(obj: JsonObject, rank: Int): HotNewsItem? {
        if (obj.text("type") != "story" || obj.text("deleted") == "true" || obj.text("dead") == "true") return null
        val id = obj.text("id") ?: return null
        val discussion = "https://news.ycombinator.com/item?id=$id"
        val title = obj.text("title")?.takeIf(String::isNotBlank) ?: return null
        val body = obj.text("text")?.let { articleHtmlToMarkdown(it, discussion) }.orEmpty()
        return HotNewsItem(id, HotNewsPlatform.HACKER_NEWS, rank, title,
            safeHotNewsUrl(obj.text("url").orEmpty()) ?: discussion, body, discussion,
            obj.text("score"), obj.text("descendants")?.toIntOrNull(), originalContent = body)
    }

    private fun zhihu(body: String): List<HotNewsItem> = Json.parseToJsonElement(body).jsonObject["data"]!!.jsonArray
        .mapIndexedNotNull { index, element ->
            val target = element.jsonObject["target"]?.jsonObject ?: return@mapIndexedNotNull null
            val title = target["title_area"]?.jsonObject?.text("text") ?: return@mapIndexedNotNull null
            val url = target["link"]?.jsonObject?.text("url")?.let(::safeHotNewsUrl) ?: return@mapIndexedNotNull null
            HotNewsItem(url.substringAfterLast('/'), HotNewsPlatform.ZHIHU, index + 1, title, url,
                target["excerpt_area"]?.jsonObject?.text("text").orEmpty(),
                heat = target["metrics_area"]?.jsonObject?.text("text"))
        }

    private fun toutiao(body: String): List<HotNewsItem> = Json.parseToJsonElement(body).jsonObject["data"]!!.jsonArray
        .mapIndexedNotNull { index, element ->
            val obj = element.jsonObject
            val id = obj.text("ClusterIdStr") ?: return@mapIndexedNotNull null
            val title = obj.text("Title") ?: return@mapIndexedNotNull null
            HotNewsItem(id, HotNewsPlatform.TOUTIAO, index + 1, title, "https://www.toutiao.com/trending/$id/", heat = obj.text("HotValue"))
        }

    private fun v2ex(body: String): List<HotNewsItem> = Json.parseToJsonElement(body).jsonArray
        .mapIndexedNotNull { index, element ->
            val obj = element.jsonObject
            val id = obj.text("id") ?: return@mapIndexedNotNull null
            val title = obj.text("title") ?: return@mapIndexedNotNull null
            val url = safeHotNewsUrl(obj.text("url").orEmpty()) ?: return@mapIndexedNotNull null
            val content = obj.text("content_rendered")?.let { articleHtmlToMarkdown(it, url) } ?: obj.text("content").orEmpty()
            HotNewsItem(id, HotNewsPlatform.V2EX, index + 1, title, url, content.take(1000),
                comments = obj.text("replies")?.toIntOrNull(), originalContent = content.take(12_000))
        }
}

internal fun JsonObject.text(key: String): String? = (get(key) as? JsonPrimitive)?.contentOrNull
internal expect fun parseGithubHotNews(html: String): List<HotNewsItem>
