package com.dailysatori.core.service

import android.content.Context
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
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.header
import io.ktor.server.request.path
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
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

class WebServerService(private val ctx: Context) {
    private val log = Logger.withTag("WebServer")
    private var server: Any? = null

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun start() {
        if (server != null) return
        log.i { "Starting web server on port ${WebServiceConfig.httpPort}" }

        val svc = this
        server = embeddedServer(CIO, port = WebServiceConfig.httpPort, host = "0.0.0.0") {
            install(ContentNegotiation) { registerJson(svc.json) }
            install(CORS) { anyHost() }
            install(createApplicationPlugin(name = "ApiAuth") {
                val log = Logger.withTag("ApiAuth")
                onCall { call ->
                    val path = call.request.path()
                    if (!path.startsWith("/api/v2/")) return@onCall
                    if (path == "/api/v2/auth/login" || path == "/api/v2/auth/status") return@onCall

                    val sessionId = call.request.cookies["session_id"]
                    val token = call.request.headers["Authorization"]?.removePrefix("Bearer ")

                    if (sessionId != null) {
                        try {
                            val sessionRepo = get<SessionRepository>(SessionRepository::class.java)
                            if (sessionRepo.getBySessionId(sessionId) != null) return@onCall
                        } catch (_: Exception) {}
                    }

                    if (token != null) {
                        try {
                            val settingRepo = get<SettingRepository>(SettingRepository::class.java)
                            val storedToken = settingRepo.get("web_server_token")
                            if (storedToken != null && token == storedToken) return@onCall
                        } catch (_: Exception) {}
                    }

                    call.respond(HttpStatusCode.Unauthorized, ApiResponse(-1, "Authentication required"))
                }
            })
            routing {
                get("/ping") {
                    call.respondText("pong", ContentType.Text.Plain)
                }

                get("/") { svc.serveAsset(call, "website/admin.html", ContentType.Text.Html) }
                get("/website/{path...}") { svc.serveWebsiteAsset(call) }

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

    private suspend fun serveAsset(call: ApplicationCall, path: String, contentType: ContentType) {
        try {
            val bytes = ctx.assets.open(path).use { it.readBytes() }
            call.respondBytes(bytes, contentType)
        } catch (e: Exception) {
            log.e(e) { "Asset not found: $path" }
            call.respond(HttpStatusCode.NotFound)
        }
    }

    private suspend fun serveWebsiteAsset(call: ApplicationCall) {
        val path = call.parameters.getAll("path")?.joinToString("/") ?: ""
        if (path.isEmpty() || path.endsWith("/")) {
            call.respond(HttpStatusCode.NotFound)
            return
        }
        val contentType = when {
            path.endsWith(".css") -> ContentType.Text.CSS
            path.endsWith(".js") -> ContentType.Text.JavaScript
            path.endsWith(".html") -> ContentType.Text.Html
            path.endsWith(".svg") -> ContentType.Image.SVG
            path.endsWith(".png") -> ContentType.Image.PNG
            path.endsWith(".jpg") || path.endsWith(".jpeg") -> ContentType.Image.JPEG
            path.endsWith(".woff2") -> ContentType.Font.Woff2
            else -> ContentType.Application.OctetStream
        }
        serveAsset(call, "website/$path", contentType)
    }

    private fun Route.setupArticleRoutes() {
        route("/articles") {
            get {
                val repo = get<ArticleRepository>(ArticleRepository::class.java)
                val page = call.request.queryParameters["page"]?.toLongOrNull() ?: 1L
                val limit = 20L
                val offset = (page - 1) * limit
                val articles = try { repo.getPaginated(limit, offset).first() } catch (_: Exception) { emptyList() }
                val total = try { repo.count() } catch (_: Exception) { 0L }
                call.respond(ApiResponse(0, "success", buildJsonObject {
                    put("items", JsonArray(
                        articles.map { a -> buildJsonObject {
                            put("id", JsonPrimitive(a.id))
                            put("title", JsonPrimitive(a.title ?: ""))
                            put("url", JsonPrimitive(a.url ?: ""))
                            put("coverImage", JsonPrimitive(a.cover_image ?: ""))
                            put("aiTitle", JsonPrimitive(a.ai_title ?: ""))
                            put("aiContent", JsonPrimitive(a.ai_content ?: ""))
                            put("isFavorite", JsonPrimitive((a.is_favorite ?: 0) > 0))
                            put("createdAt", JsonPrimitive(a.created_at))
                            put("updatedAt", JsonPrimitive(a.updated_at))
                        }}
                    ))
                    put("pagination", buildJsonObject {
                        put("page", JsonPrimitive(page))
                        put("pageSize", JsonPrimitive(limit))
                        put("totalItems", JsonPrimitive(total))
                        put("totalPages", JsonPrimitive((total + limit - 1) / limit))
                    })
                }))
            }

            get("/search") {
                val repo = get<ArticleRepository>(ArticleRepository::class.java)
                val query = call.request.queryParameters["q"] ?: ""
                val results = try { repo.searchSync(query) } catch (_: Exception) { emptyList() }
                call.respond(ApiResponse(0, "success", buildJsonObject {
                    put("items", JsonArray(
                        results.map { a -> buildJsonObject {
                            put("id", JsonPrimitive(a.id))
                            put("title", JsonPrimitive(a.title ?: ""))
                            put("createdAt", JsonPrimitive(a.created_at))
                        }}
                    ))
                    put("count", JsonPrimitive(results.size))
                }))
            }

            get("/{id}") {
                val repo = get<ArticleRepository>(ArticleRepository::class.java)
                val id = call.parameters["id"]?.toLongOrNull() ?: 0L
                val a = repo.getById(id)
                if (a != null) {
                    call.respond(ApiResponse(0, "success", buildJsonObject {
                        put("id", JsonPrimitive(a.id))
                        put("title", JsonPrimitive(a.title ?: ""))
                        put("url", JsonPrimitive(a.url ?: ""))
                        put("content", JsonPrimitive(a.content ?: ""))
                        put("aiContent", JsonPrimitive(a.ai_content ?: ""))
                        put("aiMarkdownContent", JsonPrimitive(a.ai_markdown_content ?: ""))
                        put("aiTitle", JsonPrimitive(a.ai_title ?: ""))
                        put("coverImage", JsonPrimitive(a.cover_image ?: ""))
                        put("isFavorite", JsonPrimitive((a.is_favorite ?: 0) > 0))
                        put("createdAt", JsonPrimitive(a.created_at))
                        put("updatedAt", JsonPrimitive(a.updated_at))
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

    private fun Route.setupDiaryRoutes() {
        route("/diary") {
            get {
                val repo = get<DiaryRepository>(DiaryRepository::class.java)
                val page = call.request.queryParameters["page"]?.toLongOrNull() ?: 1L
                val limit = 20L
                val offset = (page - 1) * limit
                val diaries = try { repo.getPaginated(limit, offset).first() } catch (_: Exception) { emptyList() }
                val total = try { repo.count() } catch (_: Exception) { 0L }
                call.respond(ApiResponse(0, "success", buildJsonObject {
                    put("items", JsonArray(
                        diaries.map { d -> buildJsonObject {
                            put("id", JsonPrimitive(d.id))
                            put("content", JsonPrimitive(d.content ?: ""))
                            put("mood", JsonPrimitive(d.mood ?: ""))
                            put("tags", JsonPrimitive(d.tags ?: ""))
                            put("images", JsonArray(emptyList()))
                            put("createdAt", JsonPrimitive(d.created_at))
                            put("updatedAt", JsonPrimitive(d.updated_at))
                        }}
                    ))
                    put("pagination", buildJsonObject {
                        put("page", JsonPrimitive(page))
                        put("pageSize", JsonPrimitive(limit))
                        put("totalItems", JsonPrimitive(total))
                        put("totalPages", JsonPrimitive((total + limit - 1) / limit))
                    })
                }))
            }

            get("/search") {
                val repo = get<DiaryRepository>(DiaryRepository::class.java)
                val query = call.request.queryParameters["q"] ?: ""
                val results = try { repo.searchSync(query) } catch (_: Exception) { emptyList() }
                call.respond(ApiResponse(0, "success", buildJsonObject {
                    put("items", JsonArray(
                        results.map { d -> buildJsonObject {
                            put("id", JsonPrimitive(d.id))
                            put("content", JsonPrimitive(d.content ?: ""))
                            put("createdAt", JsonPrimitive(d.created_at))
                        }}
                    ))
                }))
            }

            get("/{id}") {
                val repo = get<DiaryRepository>(DiaryRepository::class.java)
                val id = call.parameters["id"]?.toLongOrNull() ?: 0L
                val d = repo.getById(id)
                if (d != null) {
                    call.respond(ApiResponse(0, "success", buildJsonObject {
                        put("id", JsonPrimitive(d.id))
                        put("content", JsonPrimitive(d.content ?: ""))
                        put("mood", JsonPrimitive(d.mood ?: ""))
                        put("tags", JsonPrimitive(d.tags ?: ""))
                        put("images", JsonArray(emptyList()))
                        put("createdAt", JsonPrimitive(d.created_at))
                        put("updatedAt", JsonPrimitive(d.updated_at))
                    }))
                } else {
                    call.respond(HttpStatusCode.NotFound, ApiResponse(-1, "Not found"))
                }
            }

            post {
                try {
                    val body = call.receive<JsonObject>()
                    val content = (body["content"] as? JsonPrimitive)?.content ?: ""
                    val mood = (body["mood"] as? JsonPrimitive)?.content
                    val tags = (body["tags"] as? JsonPrimitive)?.content
                    val repo = get<DiaryRepository>(DiaryRepository::class.java)
                    repo.insert(content, tags, mood, null)
                    call.respond(ApiResponse(0, "created"))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, ApiResponse(-1, e.message ?: "Invalid request"))
                }
            }

            put("/{id}") {
                try {
                    val id = call.parameters["id"]?.toLongOrNull() ?: 0L
                    val body = call.receive<JsonObject>()
                    val content = (body["content"] as? JsonPrimitive)?.content ?: ""
                    val mood = (body["mood"] as? JsonPrimitive)?.content
                    val tags = (body["tags"] as? JsonPrimitive)?.content
                    val repo = get<DiaryRepository>(DiaryRepository::class.java)
                    repo.update(id, content, tags, mood, null)
                    call.respond(ApiResponse(0, "updated"))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, ApiResponse(-1, e.message ?: "Invalid request"))
                }
            }

            delete("/{id}") {
                val repo = get<DiaryRepository>(DiaryRepository::class.java)
                val id = call.parameters["id"]?.toLongOrNull() ?: 0L
                repo.delete(id)
                call.respond(ApiResponse(0, "deleted"))
            }
        }
    }

    private fun Route.setupBookRoutes() {
        route("/books") {
            get {
                val repo = get<BookRepository>(BookRepository::class.java)
                val books = try { repo.getAllSync() } catch (_: Exception) { emptyList() }
                call.respond(ApiResponse(0, "success", buildJsonObject {
                    put("items", JsonArray(
                        books.map { b -> buildJsonObject {
                            put("id", JsonPrimitive(b.id))
                            put("title", JsonPrimitive(b.title))
                            put("author", JsonPrimitive(b.author))
                            put("category", JsonPrimitive(b.category))
                            put("coverImage", JsonPrimitive(b.cover_image))
                            put("introduction", JsonPrimitive(b.introduction))
                            put("createdAt", JsonPrimitive(b.created_at))
                        }}
                    ))
                }))
            }

            get("/{id}") {
                val repo = get<BookRepository>(BookRepository::class.java)
                val id = call.parameters["id"]?.toLongOrNull() ?: 0L
                val b = repo.getById(id)
                if (b != null) {
                    call.respond(ApiResponse(0, "success", buildJsonObject {
                        put("id", JsonPrimitive(b.id))
                        put("title", JsonPrimitive(b.title))
                        put("author", JsonPrimitive(b.author))
                        put("category", JsonPrimitive(b.category))
                        put("coverImage", JsonPrimitive(b.cover_image))
                        put("introduction", JsonPrimitive(b.introduction))
                        put("createdAt", JsonPrimitive(b.created_at))
                    }))
                } else {
                    call.respond(HttpStatusCode.NotFound, ApiResponse(-1, "Not found"))
                }
            }

            get("/{id}/viewpoints") {
                val vpRepo = get<BookViewpointRepository>(BookViewpointRepository::class.java)
                val bookId = call.parameters["id"]?.toLongOrNull() ?: 0L
                val viewpoints = try { vpRepo.getByBookSync(bookId) } catch (_: Exception) { emptyList() }
                call.respond(ApiResponse(0, "success", buildJsonObject {
                    put("items", JsonArray(
                        viewpoints.map { v -> buildJsonObject {
                            put("id", JsonPrimitive(v.id))
                            put("title", JsonPrimitive(v.title))
                            put("content", JsonPrimitive(v.content))
                            put("example", JsonPrimitive(v.example))
                            put("createdAt", JsonPrimitive(v.created_at))
                        }}
                    ))
                }))
            }

            post {
                try {
                    val body = call.receive<JsonObject>()
                    val title = (body["title"] as? JsonPrimitive)?.content ?: ""
                    val repo = get<BookRepository>(BookRepository::class.java)
                    repo.insert(title = title, author = "", category = "", coverImage = "", introduction = "")
                    call.respond(ApiResponse(0, "created"))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, ApiResponse(-1, e.message ?: "Invalid request"))
                }
            }

            delete("/{id}") {
                val repo = get<BookRepository>(BookRepository::class.java)
                val id = call.parameters["id"]?.toLongOrNull() ?: 0L
                repo.delete(id)
                call.respond(ApiResponse(0, "deleted"))
            }
        }
    }

    private fun Route.setupStatsRoutes() {
        route("/stats") {
            get("/overview") {
                val articleRepo = get<ArticleRepository>(ArticleRepository::class.java)
                val diaryRepo = get<DiaryRepository>(DiaryRepository::class.java)
                val bookRepo = get<BookRepository>(BookRepository::class.java)
                val tagRepo = get<TagRepository>(TagRepository::class.java)
                call.respond(ApiResponse(0, "success", buildJsonObject {
                    put("totals", buildJsonObject {
                        put("articles", JsonPrimitive(articleRepo.count()))
                        put("diaries", JsonPrimitive(diaryRepo.count()))
                        put("books", JsonPrimitive(bookRepo.count()))
                        put("tags", JsonPrimitive(tagRepo.count()))
                        put("favoriteArticles", JsonPrimitive(0))
                    })
                }))
            }

            get("/weekly-report") {
                val repo = get<WeeklySummaryRepository>(WeeklySummaryRepository::class.java)
                val reports = try { repo.getAll().first() } catch (_: Exception) { emptyList() }
                call.respond(ApiResponse(0, "success", buildJsonObject {
                    put("reports", JsonArray(
                        reports.map { r -> buildJsonObject {
                            put("id", JsonPrimitive(r.id))
                            put("content", JsonPrimitive(r.content ?: ""))
                            put("weekStart", JsonPrimitive(r.week_start_date))
                            put("weekEnd", JsonPrimitive(r.week_end_date))
                        }}
                    ))
                }))
            }

            get("/recent") {
                val articleRepo = get<ArticleRepository>(ArticleRepository::class.java)
                val diaryRepo = get<DiaryRepository>(DiaryRepository::class.java)
                val bookRepo = get<BookRepository>(BookRepository::class.java)
                val articles = try { articleRepo.getPaginated(10, 0).first() } catch (_: Exception) { emptyList() }
                val diaries = try { diaryRepo.getPaginated(10, 0).first() } catch (_: Exception) { emptyList() }
                val books = try { bookRepo.getAllSync().take(10) } catch (_: Exception) { emptyList() }
                call.respond(ApiResponse(0, "success", buildJsonObject {
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
                        }}
                    ))
                }))
            }
        }
    }

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
                        call.response.cookies.append("session_id", sessionId)
                        call.respond(ApiResponse(0, "Login successful"))
                    } else {
                        call.respond(HttpStatusCode.Unauthorized, ApiResponse(-1, "Invalid password"))
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, ApiResponse(-1, e.message ?: "Login failed"))
                }
            }

            post("/logout") {
                call.response.cookies.append("session_id", "", maxAge = 0)
                call.respond(ApiResponse(0, "Logout successful"))
            }

            get("/status") {
                val sessionId = call.request.cookies["session_id"]
                val token = call.request.headers["Authorization"]?.removePrefix("Bearer ")
                val settingRepo = get<SettingRepository>(SettingRepository::class.java)
                val storedToken = settingRepo.get("web_server_token")

                val authenticated = when {
                    sessionId != null -> {
                        try {
                            val sessionRepo = get<SessionRepository>(SessionRepository::class.java)
                            sessionRepo.getBySessionId(sessionId) != null
                        } catch (_: Exception) { false }
                    }
                    token != null && storedToken != null -> token == storedToken
                    else -> false
                }
                call.respond(ApiResponse(0, "success", buildJsonObject {
                    put("authenticated", JsonPrimitive(authenticated))
                }))
            }

            get("/token") {
                val settingRepo = get<SettingRepository>(SettingRepository::class.java)
                val token = settingRepo.get("web_server_token") ?: ""
                call.respond(ApiResponse(0, "success", buildJsonObject {
                    put("token", JsonPrimitive(token))
                }))
            }
        }
    }
}
