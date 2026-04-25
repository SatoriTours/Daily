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
    val author: String,
    val category: String = "",
    val introduction: String = "",
    val isbn: String = "",
    val coverUrl: String = "",
)

interface BookSearchEngine {
    suspend fun search(query: String, limit: Int = 10): List<BookSearchResult>
}

class GoogleBooksSearchEngine(private val client: HttpClient) : BookSearchEngine {
    override suspend fun search(query: String, limit: Int): List<BookSearchResult> {
        return try {
            val encodedQuery = query.encodeURLParameter()
            val response = client.get("https://www.googleapis.com/books/v1/volumes?q=$encodedQuery&maxResults=$limit")
            val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            json["items"]?.jsonArray?.mapNotNull { item ->
                val info = item.jsonObject["volumeInfo"]?.jsonObject ?: return@mapNotNull null
                BookSearchResult(
                    title = info["title"]?.jsonPrimitive?.content ?: "",
                    author = info["authors"]?.jsonArray?.joinToString(", ") { it.jsonPrimitive.content } ?: "",
                    introduction = info["description"]?.jsonPrimitive?.content ?: "",
                    coverUrl = info["imageLinks"]?.jsonObject?.get("thumbnail")?.jsonPrimitive?.content ?: "",
                )
            } ?: emptyList()
        } catch (_: Exception) { emptyList() }
    }
}

class OpenLibrarySearchEngine(private val client: HttpClient) : BookSearchEngine {
    override suspend fun search(query: String, limit: Int): List<BookSearchResult> {
        return try {
            val encodedQuery = query.encodeURLParameter()
            val response = client.get("https://openlibrary.org/search.json?q=$encodedQuery&limit=$limit")
            val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            json["docs"]?.jsonArray?.map { doc ->
                BookSearchResult(
                    title = doc.jsonObject["title"]?.jsonPrimitive?.content ?: "",
                    author = doc.jsonObject["author_name"]?.jsonArray?.joinToString(", ") { it.jsonPrimitive.content } ?: "",
                    introduction = "",
                    coverUrl = doc.jsonObject["cover_i"]?.jsonPrimitive?.longOrNull?.let { "https://covers.openlibrary.org/b/id/$it-M.jpg" } ?: "",
                )
            } ?: emptyList()
        } catch (_: Exception) { emptyList() }
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
