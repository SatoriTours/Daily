package com.dailysatori.service.book

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
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
)

interface BookSearchEngine {
    suspend fun search(query: String, limit: Int = 10): List<BookSearchResult>
}

class WebSearchEngine(private val client: HttpClient) : BookSearchEngine {

    override suspend fun search(query: String, limit: Int): List<BookSearchResult> {
        val encoded = query.encodeURLParameter()
        val json = try {
            val url = "https://en.wikipedia.org/w/api.php?action=query" +
                "&generator=search&gsrsearch=$encoded&gsrlimit=$limit" +
                "&prop=extracts|pageimages&exintro&explaintext" +
                "&piprop=thumbnail&pithumbsize=120&format=json"
            client.get(url).bodyAsText()
        } catch (_: Exception) { return emptyList() }

        return parseResults(json)
    }

    private fun parseResults(jsonText: String): List<BookSearchResult> {
        val root = Json.parseToJsonElement(jsonText).jsonObject
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

class BookSearchService(private val engines: List<BookSearchEngine>) {
    suspend fun search(query: String): List<BookSearchResult> {
        val results = mutableListOf<BookSearchResult>()
        for (engine in engines) {
            results.addAll(engine.search(query))
        }
        return results.distinctBy { it.title to it.author }
    }
}
