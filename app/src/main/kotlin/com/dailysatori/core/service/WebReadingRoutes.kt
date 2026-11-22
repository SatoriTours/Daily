package com.dailysatori.core.service

import com.dailysatori.data.repository.BookRepository
import com.dailysatori.data.repository.BookViewpointRepository
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.koin.java.KoinJavaComponent.get

internal fun Route.setupBookRoutes() {
    route("/books") {
        get {
            val repo = get<BookRepository>(BookRepository::class.java)
            val books = try { repo.getAllSync() } catch (_: Exception) { emptyList() }
            call.respondText(respondOk(buildJsonObject {
                put("items", JsonArray(
                    books.map { b -> buildJsonObject {
                        put("id", JsonPrimitive(b.id))
                        put("title", JsonPrimitive(b.title))
                        put("author", JsonPrimitive(b.author))
                        put("category", JsonPrimitive(b.category))
                        put("coverImage", JsonPrimitive(b.cover_image))
                        put("introduction", JsonPrimitive(b.introduction))
                        put("createdAt", JsonPrimitive(b.created_at))
                    put("version", JsonPrimitive(webVersion(b)))
                    }}
                ))
            }), ContentType.Application.Json)
        }

        get("/{id}") {
            val repo = get<BookRepository>(BookRepository::class.java)
            val id = call.parameters["id"]?.toLongOrNull() ?: 0L
            val b = repo.getById(id)
            if (b != null) {
                call.respondText(respondOk(buildJsonObject {
                    put("id", JsonPrimitive(b.id))
                    put("title", JsonPrimitive(b.title))
                    put("author", JsonPrimitive(b.author))
                    put("category", JsonPrimitive(b.category))
                    put("coverImage", JsonPrimitive(b.cover_image))
                    put("introduction", JsonPrimitive(b.introduction))
                    put("createdAt", JsonPrimitive(b.created_at))
                    put("version", JsonPrimitive(webVersion(b)))
                }), ContentType.Application.Json)
            } else {
                call.respondText(respondFail("Not found"), ContentType.Application.Json, HttpStatusCode.NotFound)
            }
        }

        get("/search") {
            val query = call.request.queryParameters["q"].orEmpty()
            val books = get<BookRepository>(BookRepository::class.java).searchSync(query)
            call.respondText(respondOk(buildJsonObject {
                put("items", JsonArray(books.map { book -> bookToJson(book) }))
            }), ContentType.Application.Json)
        }

        get("/{id}/viewpoints") {
            val vpRepo = get<BookViewpointRepository>(BookViewpointRepository::class.java)
            val bookId = call.parameters["id"]?.toLongOrNull() ?: 0L
            val viewpoints = try { vpRepo.getByBookSync(bookId) } catch (_: Exception) { emptyList() }
            call.respondText(respondOk(buildJsonObject {
                put("items", JsonArray(
                    viewpoints.map { v -> buildJsonObject {
                        put("id", JsonPrimitive(v.id))
                        put("title", JsonPrimitive(v.title))
                        put("content", JsonPrimitive(v.content))
                        put("example", JsonPrimitive(v.example))
                        put("createdAt", JsonPrimitive(v.created_at))
                        put("version", JsonPrimitive(webVersion(v)))
                    }}
                ))
            }), ContentType.Application.Json)
        }

        post {
            try {
                val body = call.receive<JsonObject>()
                val title = body.stringValue("title").trim()
                if (title.isBlank()) {
                    call.respondText(respondFail("Title is required"), ContentType.Application.Json, HttpStatusCode.BadRequest)
                    return@post
                }
                val repo = get<BookRepository>(BookRepository::class.java)
                val id = repo.insertAndReturnId(
                    title = title,
                    author = body.stringValue("author").trim(),
                    category = body.stringValue("category").trim(),
                    coverImage = body.stringValue("coverImage").trim(),
                    introduction = body.stringValue("introduction").trim(),
                )
                call.respondText(respondOk(buildJsonObject { put("id", JsonPrimitive(id)) }), ContentType.Application.Json)
            } catch (e: Exception) {
                call.respondText(respondFail(e.message ?: "Invalid request"), ContentType.Application.Json, HttpStatusCode.BadRequest)
            }
        }

        put("/{id}") {
            try {
                val id = call.parameters["id"]?.toLongOrNull() ?: 0L
                val repo = get<BookRepository>(BookRepository::class.java)
                val existing = repo.getById(id)
                if (existing == null) {
                    call.respondText(respondFail("Not found"), ContentType.Application.Json, HttpStatusCode.NotFound)
                    return@put
                }
                val body = call.receive<JsonObject>()
                val title = body.optionalStringValue("title", existing.title).orEmpty()
                if (title.isBlank()) {
                    call.respondText(respondFail("Title is required"), ContentType.Application.Json, HttpStatusCode.BadRequest)
                    return@put
                }
                if (!call.checkEditVersion(body, webVersion(existing))) return@put
                val saved = repo.updateIfUnchanged(
                    existing, title,
                    body.optionalStringValue("author", existing.author).orEmpty(),
                    body.optionalStringValue("category", existing.category).orEmpty(),
                    body.optionalStringValue("coverImage", existing.cover_image).orEmpty(),
                    body.optionalStringValue("introduction", existing.introduction).orEmpty(),
                )
                if (!saved) { call.editConflict(); return@put }
                call.respondText(respondOk(bookToJson(repo.getById(id)!!)), ContentType.Application.Json)
            } catch (e: Exception) {
                call.respondText(respondFail(e.message ?: "Invalid request"), ContentType.Application.Json, HttpStatusCode.BadRequest)
            }
        }

        post("/{id}/viewpoints") {
            try {
                val bookId = call.parameters["id"]?.toLongOrNull() ?: 0L
                if (get<BookRepository>(BookRepository::class.java).getById(bookId) == null) {
                    call.respondText(respondFail("Book not found"), ContentType.Application.Json, HttpStatusCode.NotFound)
                    return@post
                }
                val body = call.receive<JsonObject>()
                val title = body.stringValue("title").trim()
                if (title.isBlank()) {
                    call.respondText(respondFail("Title is required"), ContentType.Application.Json, HttpStatusCode.BadRequest)
                    return@post
                }
                get<BookViewpointRepository>(BookViewpointRepository::class.java).insert(
                    bookId = bookId,
                    title = title,
                    content = body.stringValue("content").trim(),
                    example = body.stringValue("example").trim(),
                )
                call.respondText(respondOk(), ContentType.Application.Json)
            } catch (e: Exception) {
                call.respondText(respondFail(e.message ?: "Invalid request"), ContentType.Application.Json, HttpStatusCode.BadRequest)
            }
        }

        get("/viewpoints/{viewpointId}") {
            val id = call.parameters["viewpointId"]?.toLongOrNull() ?: 0L
            val existing = get<BookViewpointRepository>(BookViewpointRepository::class.java).getById(id)
            if (existing == null) {
                call.respondText(respondFail("Not found"), ContentType.Application.Json, HttpStatusCode.NotFound)
            } else call.respondText(respondOk(buildJsonObject {
                put("id", JsonPrimitive(existing.id))
                put("title", JsonPrimitive(existing.title))
                put("content", JsonPrimitive(existing.content))
                put("example", JsonPrimitive(existing.example))
                put("version", JsonPrimitive(webVersion(existing)))
            }), ContentType.Application.Json)
        }

        put("/viewpoints/{viewpointId}") {
            val repo = get<BookViewpointRepository>(BookViewpointRepository::class.java)
            val id = call.parameters["viewpointId"]?.toLongOrNull() ?: 0L
            val existing = repo.getById(id)
            if (existing == null) {
                call.respondText(respondFail("Not found"), ContentType.Application.Json, HttpStatusCode.NotFound)
                return@put
            }
            val body = call.receive<JsonObject>()
            val title = body.optionalStringValue("title", existing.title).orEmpty()
            if (title.isBlank()) {
                call.respondText(respondFail("标题不能为空"), ContentType.Application.Json, HttpStatusCode.BadRequest)
                return@put
            }
            if (!call.checkEditVersion(body, webVersion(existing))) return@put
            val saved = repo.updateIfUnchanged(
                existing, title, body.optionalStringValue("content", existing.content).orEmpty(),
                body.optionalStringValue("example", existing.example).orEmpty(),
            )
            if (!saved) { call.editConflict(); return@put }
            call.respondText(respondOk(), ContentType.Application.Json)
        }

        delete("/viewpoints/{viewpointId}") {
            val repo = get<BookViewpointRepository>(BookViewpointRepository::class.java)
            val id = call.parameters["viewpointId"]?.toLongOrNull() ?: 0L
            if (repo.getById(id) == null) {
                call.respondText(respondFail("Not found"), ContentType.Application.Json, HttpStatusCode.NotFound)
                return@delete
            }
            repo.delete(id)
            call.respondText(respondOk(), ContentType.Application.Json)
        }

        delete("/{id}") {
            val repo = get<BookRepository>(BookRepository::class.java)
            val id = call.parameters["id"]?.toLongOrNull() ?: 0L
            repo.delete(id)
            call.respondText(respondOk(), ContentType.Application.Json)
        }
    }
}

private fun bookToJson(book: com.dailysatori.shared.db.Book): JsonObject = buildJsonObject {
    put("id", JsonPrimitive(book.id))
    put("title", JsonPrimitive(book.title))
    put("author", JsonPrimitive(book.author))
    put("category", JsonPrimitive(book.category))
    put("coverImage", JsonPrimitive(book.cover_image))
    put("introduction", JsonPrimitive(book.introduction))
    put("createdAt", JsonPrimitive(book.created_at))
    put("version", JsonPrimitive(webVersion(book)))
}
