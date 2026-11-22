package com.dailysatori.core.service

import com.dailysatori.data.repository.ArticleRepository
import com.dailysatori.data.repository.BookRepository
import com.dailysatori.data.repository.ChatConversationRepository
import com.dailysatori.data.repository.DiaryRepository
import com.dailysatori.data.repository.TagRepository
import com.dailysatori.data.repository.WeeklySummaryRepository
import com.dailysatori.service.mcp.McpAgentService
import com.dailysatori.service.mcp.McpSearchResult
import com.dailysatori.service.mcp.decodeMcpSearchResults
import com.dailysatori.service.mcp.encodeMcpSearchResults
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
import kotlinx.coroutines.flow.first
import java.util.UUID
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.koin.java.KoinJavaComponent.get

internal fun Route.setupStatsRoutes() {
    route("/stats") {
        get("/overview") {
            val articleRepo = get<ArticleRepository>(ArticleRepository::class.java)
            val diaryRepo = get<DiaryRepository>(DiaryRepository::class.java)
            val bookRepo = get<BookRepository>(BookRepository::class.java)
            val tagRepo = get<TagRepository>(TagRepository::class.java)
            call.respondText(respondOk(buildJsonObject {
                put("totals", buildJsonObject {
                    put("articles", JsonPrimitive(articleRepo.count()))
                    put("diaries", JsonPrimitive(diaryRepo.count()))
                    put("books", JsonPrimitive(bookRepo.count()))
                    put("tags", JsonPrimitive(tagRepo.count()))
                    put("favoriteArticles", JsonPrimitive(articleRepo.getFavoritesSync().size))
                })
            }), ContentType.Application.Json)
        }

        get("/weekly-report") {
            val repo = get<WeeklySummaryRepository>(WeeklySummaryRepository::class.java)
            val reports = try { repo.getAll().first() } catch (_: Exception) { emptyList() }
            call.respondText(respondOk(buildJsonObject {
                put("reports", JsonArray(
                    reports.map { r -> buildJsonObject {
                        put("id", JsonPrimitive(r.id))
                        put("content", JsonPrimitive(r.content ?: ""))
                        put("weekStart", JsonPrimitive(r.week_start_date))
                        put("weekEnd", JsonPrimitive(r.week_end_date))
                    }}
                ))
            }), ContentType.Application.Json)
        }

        get("/recent") {
            val articleRepo = get<ArticleRepository>(ArticleRepository::class.java)
            val diaryRepo = get<DiaryRepository>(DiaryRepository::class.java)
            val bookRepo = get<BookRepository>(BookRepository::class.java)
            val articles = try { articleRepo.getPaginated(10, 0).first() } catch (_: Exception) { emptyList() }
            val diaries = try { diaryRepo.getPaginated(10, 0).first() } catch (_: Exception) { emptyList() }
            val books = try { bookRepo.getAllSync().take(10) } catch (_: Exception) { emptyList() }
            call.respondText(respondOk(buildJsonObject {
                put("articles", JsonArray(
                    articles.map { a -> buildJsonObject {
                        put("id", JsonPrimitive(a.id))
                        put("type", JsonPrimitive("article"))
                        put("title", JsonPrimitive(a.title ?: ""))
                        put("createdAt", JsonPrimitive(a.created_at))
                    }}
                ))
                put("diaries", JsonArray(
                    diaries.map { d -> buildJsonObject {
                        put("id", JsonPrimitive(d.id))
                        put("type", JsonPrimitive("diary"))
                        put("content", JsonPrimitive(d.content?.take(50) ?: ""))
                        put("createdAt", JsonPrimitive(d.created_at))
                    }}
                ))
                put("books", JsonArray(
                    books.map { b -> buildJsonObject {
                        put("id", JsonPrimitive(b.id))
                        put("type", JsonPrimitive("book"))
                        put("title", JsonPrimitive(b.title))
                        put("createdAt", JsonPrimitive(b.created_at))
                    put("version", JsonPrimitive(webVersion(b)))
                    }}
                ))
            }), ContentType.Application.Json)
        }
    }
}

internal fun Route.setupAiRoutes() {
    route("/ai") {
        get("/sessions") {
            val repo = get<ChatConversationRepository>(ChatConversationRepository::class.java)
            val sessions = repo.getSessions().mapNotNull { sessionId ->
                val messages = repo.getLatestBySession(sessionId, 30)
                val latest = messages.lastOrNull() ?: return@mapNotNull null
                val title = messages.firstOrNull { it.role == "user" }?.content.orEmpty().take(42)
                buildJsonObject {
                    put("id", JsonPrimitive(sessionId))
                    put("title", JsonPrimitive(title.ifBlank { "新对话" }))
                    put("updatedAt", JsonPrimitive(latest.created_at))
                }
            }
            call.respondText(respondOk(buildJsonObject { put("items", JsonArray(sessions)) }), ContentType.Application.Json)
        }

        get("/sessions/{sessionId}") {
            val sessionId = call.parameters["sessionId"].orEmpty()
            val repo = get<ChatConversationRepository>(ChatConversationRepository::class.java)
            val messages = repo.getBySession(sessionId).map { message -> buildJsonObject {
                put("id", JsonPrimitive(message.id))
                put("role", JsonPrimitive(message.role))
                put("content", JsonPrimitive(message.content))
                put("createdAt", JsonPrimitive(message.created_at))
                put("references", JsonArray(decodeMcpSearchResults(message.search_results).map(::mcpReferenceToJson)))
                put("steps", JsonArray(message.steps?.lines()?.filter { it.isNotBlank() }.orEmpty().map(::JsonPrimitive)))
            } }
            call.respondText(respondOk(buildJsonObject { put("items", JsonArray(messages)) }), ContentType.Application.Json)
        }

        delete("/sessions/{sessionId}") {
            val sessionId = call.parameters["sessionId"].orEmpty()
            get<ChatConversationRepository>(ChatConversationRepository::class.java).deleteBySession(sessionId)
            call.respondText(respondOk(), ContentType.Application.Json)
        }

        post("/chat") {
            try {
                val body = call.receive<JsonObject>()
                val query = body.stringValue("query").trim()
                if (query.isBlank()) {
                    call.respondText(respondFail("Message is required"), ContentType.Application.Json, HttpStatusCode.BadRequest)
                    return@post
                }
                val sessionId = body.stringValue("sessionId").trim().ifBlank { "web_${UUID.randomUUID()}" }
                val repo = get<ChatConversationRepository>(ChatConversationRepository::class.java)
                val userCreatedAt = System.currentTimeMillis()
                repo.insert(sessionId, "user", query, createdAt = userCreatedAt)
                val steps = mutableListOf<String>()
                val result = get<McpAgentService>(McpAgentService::class.java).processQueryStreaming(
                    query = query,
                    onStep = { step, status -> if (status == "completed" && step !in steps) steps += step },
                    onChunk = { },
                )
                val assistantCreatedAt = System.currentTimeMillis()
                repo.insert(
                    sessionId = sessionId,
                    role = "assistant",
                    content = result.answer,
                    searchResults = encodeMcpSearchResults(result.searchResults),
                    steps = steps.takeIf { it.isNotEmpty() }?.joinToString("\n"),
                    createdAt = assistantCreatedAt,
                )
                call.respondText(respondOk(buildJsonObject {
                    put("sessionId", JsonPrimitive(sessionId))
                    put("message", buildJsonObject {
                        put("role", JsonPrimitive("assistant"))
                        put("content", JsonPrimitive(result.answer))
                        put("createdAt", JsonPrimitive(assistantCreatedAt))
                        put("references", JsonArray(result.searchResults.map(::mcpReferenceToJson)))
                        put("steps", JsonArray(steps.map(::JsonPrimitive)))
                    })
                }), ContentType.Application.Json)
            } catch (e: Exception) {
                call.respondText(respondFail(e.message ?: "AI request failed"), ContentType.Application.Json, HttpStatusCode.BadRequest)
            }
        }
    }
}

private fun mcpReferenceToJson(reference: McpSearchResult): JsonObject = buildJsonObject {
    put("id", JsonPrimitive(reference.id))
    put("type", JsonPrimitive(reference.type))
    put("title", JsonPrimitive(reference.title))
    put("summary", JsonPrimitive(reference.summary.orEmpty()))
    put("createdAt", JsonPrimitive(reference.createdAt.orEmpty()))
    put("matchReason", JsonPrimitive(reference.matchReason.orEmpty()))
}
