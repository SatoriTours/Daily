package com.dailysatori.core.service

import co.touchlab.kermit.Logger
import com.dailysatori.config.WebServiceConfig
import com.dailysatori.data.repository.ArticleRepository
import com.dailysatori.data.repository.BookRepository
import com.dailysatori.data.repository.BookViewpointRepository
import com.dailysatori.data.repository.DiaryRepository
import com.dailysatori.data.repository.SessionRepository
import com.dailysatori.data.repository.SettingRepository
import com.dailysatori.data.repository.TagRepository
import com.dailysatori.data.repository.WeeklySummaryRepository
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json as registerJson
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.koin.java.KoinJavaComponent.get
import java.time.Instant

@Serializable
data class ApiResponse(
    val code: Int = 0,
    val msg: String = "success",
    val data: JsonObject? = null,
)

class WebServerService {
    private val log = Logger.withTag("WebServer")
    private var server: Any? = null

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun start() {
        if (server != null) return
        log.i { "Starting web server on port ${WebServiceConfig.httpPort}" }

        server = embeddedServer(Netty, port = WebServiceConfig.httpPort) {
            install(ContentNegotiation) { registerJson(this@WebServerService.json) }
            install(CORS) { anyHost() }
            routing {
                get("/ping") {
                    call.respondText("pong", ContentType.Text.Plain)
                }

                route("/api/v2") {
                    setupArticleRoutes()
                    setupDiaryRoutes()
                    setupBookRoutes()
                    setupStatsRoutes()
                    setupAuthRoutes()
                }
            }
        }.start(wait = false)
    }

    fun stop() {
        (server as? ApplicationEngine)?.stop(1000, 2000)
        server = null
        log.i { "Web server stopped" }
    }

    fun isRunning(): Boolean = server != null

    // ---- Article Routes ----

    private fun Route.setupArticleRoutes() {
        route("/articles") {
            get {
                val repo = get<ArticleRepository>(ArticleRepository::class.java)
                val page = call.request.queryParameters["page"]?.toLongOrNull() ?: 1L
                val limit = 20L
                val offset = (page - 1) * limit
                val articles = try {
                    repo.getPaginated(limit, offset)
                    "ok"
                } catch (e: Exception) {
                    ""
                }
                call.respond(ApiResponse(0, "success", buildJsonObject {
                    put("page", JsonPrimitive(page))
                    put("count", JsonPrimitive(articles.length.toLong()))
                }))
            }

            get("/search") {
                val repo = get<ArticleRepository>(ArticleRepository::class.java)
                val query = call.request.queryParameters["q"] ?: ""
                val results = try { repo.searchSync(query) } catch (_: Exception) { emptyList() }
                call.respond(ApiResponse(0, "success", buildJsonObject {
                    put("count", JsonPrimitive(results.size))
                }))
            }

            get("/{id}") {
                val repo = get<ArticleRepository>(ArticleRepository::class.java)
                val id = call.parameters["id"]?.toLongOrNull() ?: 0L
                val article = repo.getById(id)
                if (article != null) {
                    call.respond(ApiResponse(0, "success", buildJsonObject {
                        put("title", JsonPrimitive(article.title ?: ""))
                        put("content", JsonPrimitive(article.content ?: ""))
                        put("aiContent", JsonPrimitive(article.ai_content ?: ""))
                    }))
                } else {
                    call.respond(HttpStatusCode.NotFound, ApiResponse(-1, "Not found"))
                }
            }

            post {
                call.respond(HttpStatusCode.OK, ApiResponse(0, "Article creation endpoint"))
            }

            put("/{id}") {
                call.respond(HttpStatusCode.OK, ApiResponse(0, "Article update endpoint"))
            }

            delete("/{id}") {
                val repo = get<ArticleRepository>(ArticleRepository::class.java)
                val id = call.parameters["id"]?.toLongOrNull() ?: 0L
                repo.delete(id)
                call.respond(ApiResponse(0, "Deleted"))
            }
        }
    }

    // ---- Diary Routes ----

    private fun Route.setupDiaryRoutes() {
        route("/diary") {
            get {
                val repo = get<DiaryRepository>(DiaryRepository::class.java)
                val page = call.request.queryParameters["page"]?.toLongOrNull() ?: 1L
                call.respond(ApiResponse(0, "success", buildJsonObject {
                    put("page", JsonPrimitive(page))
                }))
            }

            get("/search") {
                call.respond(ApiResponse(0, "success"))
            }

            get("/{id}") {
                val repo = get<DiaryRepository>(DiaryRepository::class.java)
                val id = call.parameters["id"]?.toLongOrNull() ?: 0L
                val diary = repo.getById(id)
                if (diary != null) {
                    call.respond(ApiResponse(0, "success", buildJsonObject {
                        put("content", JsonPrimitive(diary.content ?: ""))
                        put("mood", JsonPrimitive(diary.mood ?: ""))
                    }))
                } else {
                    call.respond(HttpStatusCode.NotFound, ApiResponse(-1, "Not found"))
                }
            }

            post {
                val repo = get<DiaryRepository>(DiaryRepository::class.java)
                try {
                    val body = call.receive<JsonObject>()
                    val content = (body["content"] as? JsonPrimitive)?.content ?: ""
                    val mood = (body["mood"] as? JsonPrimitive)?.content
                    val tags = (body["tags"] as? JsonPrimitive)?.content
                    repo.insert(content, tags, mood, null)
                    call.respond(ApiResponse(0, "created"))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, ApiResponse(-1, e.message ?: "Invalid request"))
                }
            }

            put("/{id}") {
                call.respond(ApiResponse(0, "updated"))
            }

            delete("/{id}") {
                val repo = get<DiaryRepository>(DiaryRepository::class.java)
                val id = call.parameters["id"]?.toLongOrNull() ?: 0L
                repo.delete(id)
                call.respond(ApiResponse(0, "deleted"))
            }
        }
    }

    // ---- Book Routes ----

    private fun Route.setupBookRoutes() {
        route("/books") {
            get {
                val repo = get<BookRepository>(BookRepository::class.java)
                call.respond(ApiResponse(0, "success"))
            }

            get("/{id}") {
                val repo = get<BookRepository>(BookRepository::class.java)
                val id = call.parameters["id"]?.toLongOrNull() ?: 0L
                val book = repo.getById(id)
                if (book != null) {
                    call.respond(ApiResponse(0, "success", buildJsonObject {
                        put("title", JsonPrimitive(book.title))
                        put("author", JsonPrimitive(book.author))
                        put("category", JsonPrimitive(book.category))
                    }))
                } else {
                    call.respond(HttpStatusCode.NotFound, ApiResponse(-1, "Not found"))
                }
            }

            get("/{id}/viewpoints") {
                val vpRepo = get<BookViewpointRepository>(BookViewpointRepository::class.java)
                val bookId = call.parameters["id"]?.toLongOrNull() ?: 0L
                call.respond(ApiResponse(0, "success"))
            }

            post("/{id}/viewpoints") {
                call.respond(ApiResponse(0, "created"))
            }

            delete("/{id}") {
                val repo = get<BookRepository>(BookRepository::class.java)
                val id = call.parameters["id"]?.toLongOrNull() ?: 0L
                repo.delete(id)
                call.respond(ApiResponse(0, "deleted"))
            }
        }
    }

    // ---- Stats Routes ----

    private fun Route.setupStatsRoutes() {
        route("/stats") {
            get("/overview") {
                val articleRepo = get<ArticleRepository>(ArticleRepository::class.java)
                val diaryRepo = get<DiaryRepository>(DiaryRepository::class.java)
                val bookRepo = get<BookRepository>(BookRepository::class.java)
                val tagRepo = get<TagRepository>(TagRepository::class.java)
                call.respond(ApiResponse(0, "success", buildJsonObject {
                    put("totalArticles", JsonPrimitive(articleRepo.count()))
                    put("totalDiaries", JsonPrimitive(diaryRepo.count()))
                    put("totalBooks", JsonPrimitive(bookRepo.count()))
                    put("totalTags", JsonPrimitive(tagRepo.count()))
                }))
            }

            get("/recent") {
                call.respond(ApiResponse(0, "success"))
            }
        }
    }

    // ---- Auth Routes ----

    private fun Route.setupAuthRoutes() {
        route("/auth") {
            post("/login") {
                try {
                    val body = call.receive<JsonObject>()
                    val password = (body["password"] as? JsonPrimitive)?.content ?: ""
                    val settingRepo = get<SettingRepository>(SettingRepository::class.java)
                    val storedPassword = settingRepo.get("web_server_password") ?: "daily_satori"
                    if (password == storedPassword) {
                        val sessionId = Instant.now().toEpochMilli().toString()
                        val sessionRepo = get<SessionRepository>(SessionRepository::class.java)
                        sessionRepo.insert(sessionId = sessionId, username = "admin")
                        call.response.cookies.append("session_id", sessionId, path = "/")
                        call.respond(ApiResponse(0, "Login successful"))
                    } else {
                        call.respond(HttpStatusCode.Unauthorized, ApiResponse(-1, "Invalid password"))
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, ApiResponse(-1, e.message ?: "Login failed"))
                }
            }

            post("/logout") {
                call.response.cookies.append("session_id", "", maxAge = 0, path = "/")
                call.respond(ApiResponse(0, "Logout successful"))
            }

            get("/status") {
                val sessionId = call.request.cookies["session_id"]
                val authenticated = if (sessionId != null) {
                    try {
                        val sessionRepo = get<SessionRepository>(SessionRepository::class.java)
                        val session = sessionRepo.getBySessionId(sessionId)
                        session != null
                    } catch (_: Exception) {
                        false
                    }
                } else false
                call.respond(ApiResponse(0, "success", buildJsonObject {
                    put("authenticated", JsonPrimitive(authenticated))
                }))
            }
        }
    }
}
