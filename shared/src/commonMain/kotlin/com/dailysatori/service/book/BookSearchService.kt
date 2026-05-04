package com.dailysatori.service.book

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

@Serializable
data class BookSearchResult(
    val title: String,
    val author: String = "",
    val category: String = "",
    val introduction: String = "",
    val isbn: String = "",
    val coverUrl: String = "",
    val sourceSummary: String = "",
    val sourceUrl: String = "",
)

interface BookSearchEngine {
    suspend fun search(query: String, limit: Int = 10): List<BookSearchResult>
}

class WebSearchEngine(private val client: HttpClient) : BookSearchEngine {

    override suspend fun search(query: String, limit: Int): List<BookSearchResult> {
        val encoded = externalBookSearchQuery(query).encodeURLParameter()
        val json = try {
            val url = "https://en.wikipedia.org/w/api.php?action=query" +
                "&generator=search&gsrsearch=$encoded&gsrlimit=$limit" +
                "&prop=extracts|pageimages&exintro&explaintext" +
                "&piprop=thumbnail&pithumbsize=120&format=json"
            client.get(url).bodyAsText()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            return emptyList()
        }

        return parseResults(json)
    }

    private fun parseResults(jsonText: String): List<BookSearchResult> {
        val root = runCatching { Json.parseToJsonElement(jsonText).jsonObject }.getOrNull()
            ?: return emptyList()
        val pages = root["query"]?.jsonObject?.get("pages")?.jsonObject ?: return emptyList()
        return pages.entries.mapNotNull { (_, pageObj) ->
            val page = pageObj.jsonObject
            BookSearchResult(
                title = page["title"]?.jsonPrimitive?.content ?: return@mapNotNull null,
                introduction = page["extract"]?.jsonPrimitive?.content ?: "",
                coverUrl = page["thumbnail"]?.jsonObject?.get("source")?.jsonPrimitive?.content ?: "",
            )
        }
    }
}

class DoubanSuggestSearchEngine(private val client: HttpClient) : BookSearchEngine {
    override suspend fun search(query: String, limit: Int): List<BookSearchResult> {
        if (!query.any { it.code > 127 }) return emptyList()
        val encoded = externalBookSearchQuery(query).encodeURLParameter()
        val json = try {
            client.get("https://book.douban.com/j/subject_suggest?q=$encoded") { doubanHeaders() }.bodyAsText()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            return emptyList()
        }
        return parseDoubanSuggestResults(json).take(limit).map { result -> result.withDoubanDetails() }
    }

    private suspend fun BookSearchResult.withDoubanDetails(): BookSearchResult {
        if (sourceUrl.isBlank()) return this
        val html = try {
            withTimeoutOrNull(doubanSubjectDetailTimeoutMs()) {
                client.get(sourceUrl) { doubanHeaders() }.bodyAsText()
            } ?: return this
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            return this
        }
        val details = parseDoubanSubjectDetails(html)
        return copy(
            introduction = details.introduction.ifBlank { introduction },
            coverUrl = details.coverUrl.ifBlank { coverUrl },
            isbn = details.isbn.ifBlank { isbn },
        )
    }
}

fun doubanSubjectDetailTimeoutMs(): Long = 2_500L

private fun HttpRequestBuilder.doubanHeaders() {
    header(HttpHeaders.UserAgent, "Mozilla/5.0 DailySatori Android")
    header(HttpHeaders.Referrer, "https://book.douban.com/")
}

data class DoubanSubjectDetails(
    val introduction: String = "",
    val coverUrl: String = "",
    val isbn: String = "",
)

fun parseDoubanSubjectDetails(html: String): DoubanSubjectDetails = DoubanSubjectDetails(
    introduction = html.metaContent("og:description"),
    coverUrl = html.metaContent("og:image"),
    isbn = html.metaContent("book:isbn"),
)

private fun String.metaContent(property: String): String {
    val escapedProperty = Regex.escape(property)
    val regex = Regex("""<meta\s+property=["']$escapedProperty["']\s+content=["']([^"']*)["']""", RegexOption.IGNORE_CASE)
    return regex.find(this)?.groupValues?.getOrNull(1).orEmpty().htmlDecode()
}

private fun String.htmlDecode(): String = this
    .replace("&quot;", "\"")
    .replace("&#34;", "\"")
    .replace("&amp;", "&")
    .replace("&lt;", "<")
    .replace("&gt;", ">")

fun parseDoubanSuggestResults(jsonText: String): List<BookSearchResult> {
    val array = runCatching { Json.parseToJsonElement(jsonText).jsonArray }.getOrNull() ?: return emptyList()
    return array.mapNotNull { item ->
        val obj = runCatching { item.jsonObject }.getOrNull() ?: return@mapNotNull null
        val title = obj["title"]?.jsonPrimitive?.content ?: return@mapNotNull null
        if (title.isBlank()) return@mapNotNull null
        BookSearchResult(
            title = title,
            author = obj["author_name"]?.jsonPrimitive?.content ?: "",
            introduction = obj["year"]?.jsonPrimitive?.content?.let { if (it.isBlank()) "" else "出版年份：$it" } ?: "",
            coverUrl = obj["pic"]?.jsonPrimitive?.content ?: "",
            sourceSummary = "豆瓣图书",
            sourceUrl = obj["url"]?.jsonPrimitive?.content ?: "",
        )
    }
}

class BookSearchService(private val engines: List<BookSearchEngine>) {
    suspend fun search(query: String): List<BookSearchResult> {
        val results = mutableListOf<BookSearchResult>()
        for (engine in engines) {
            results.addAll(engine.search(query))
        }
        return results.distinctBy { normalizeBookKey(it.title) to it.author }
    }
}

private fun normalizeBookKey(title: String): String = title.lowercase().replace(Regex("[：:—\\-\\s]"), "")

fun externalBookSearchQuery(query: String): String =
    query.replace("中文书籍", "").replace("中文资料", "").trim().replace(Regex("\\s+"), " ")
