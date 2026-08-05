package com.dailysatori.core.service

import android.content.Context
import co.touchlab.kermit.Logger
import com.dailysatori.config.WebServiceConfig
import com.dailysatori.data.repository.ArticleRepository
import com.dailysatori.data.repository.AsyncTaskRepository
import com.dailysatori.data.repository.BookRepository
import com.dailysatori.data.repository.BookViewpointRepository
import com.dailysatori.data.repository.ChatConversationRepository
import com.dailysatori.data.repository.DiaryRepository
import com.dailysatori.data.repository.DiaryAttachmentRepository
import com.dailysatori.data.repository.SessionRepository
import com.dailysatori.data.repository.SettingRepository
import com.dailysatori.data.repository.TagRepository
import com.dailysatori.data.repository.UnifiedNewsSummaryRepository
import com.dailysatori.data.repository.WeeklySummaryRepository
import com.dailysatori.core.worker.ArticleProcessingScheduler
import com.dailysatori.service.parser.WebpageParserService
import com.dailysatori.service.mcp.McpAgentService
import com.dailysatori.service.mcp.McpSearchResult
import com.dailysatori.service.mcp.decodeMcpSearchResults
import com.dailysatori.service.mcp.encodeMcpSearchResults
import com.dailysatori.service.asynctask.AsyncTaskFilter
import com.dailysatori.service.unifiednews.UnifiedNewsSummaryService
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
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
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
import java.io.File
import java.util.UUID

@Serializable
data class ApiResponse(
    val code: Int = 0,
    val msg: String = "success",
    val data: JsonObject? = null,
)

private fun parseCookie(header: String?, name: String): String? {
    if (header == null) return null
    return header.split(";")
        .map { it.trim().split("=", limit = 2) }
        .firstOrNull { it.size == 2 && it[0] == name }
        ?.get(1)
}

private fun detectFileContentType(file: File, requestedPath: String): ContentType {
    val name = (file.name + " " + requestedPath).lowercase()
    if (name.contains(".png")) return ContentType.Image.PNG
    if (name.contains(".jpg") || name.contains(".jpeg")) return ContentType.Image.JPEG
    if (name.contains(".gif")) return ContentType.Image.GIF
    if (name.contains(".svg")) return ContentType.Image.SVG
    if (name.contains(".webp")) return ContentType("image", "webp")

    val header = file.inputStream().use { input -> ByteArray(12).also { input.read(it) } }
    return when {
        header.size >= 8 && header[0] == 0x89.toByte() && header.copyOfRange(1, 4).contentEquals("PNG".encodeToByteArray()) -> ContentType.Image.PNG
        header.size >= 3 && header[0] == 0xFF.toByte() && header[1] == 0xD8.toByte() && header[2] == 0xFF.toByte() -> ContentType.Image.JPEG
        header.copyOfRange(0, 3).contentEquals("GIF".encodeToByteArray()) -> ContentType.Image.GIF
        header.size >= 12 && header.copyOfRange(0, 4).contentEquals("RIFF".encodeToByteArray()) && header.copyOfRange(8, 12).contentEquals("WEBP".encodeToByteArray()) -> ContentType("image", "webp")
        else -> ContentType.Application.OctetStream
    }
}

class WebServerService(private val ctx: Context) {
    private val log = Logger.withTag("WebServer")
    private var server: Any? = null
    private var currentPort: Int = 0

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun start(): Int {
        if (server != null) return currentPort

        for (port in WebServiceConfig.portRangeStart..WebServiceConfig.portRangeEnd) {
            try {
                java.net.ServerSocket(port).use { }
                val svc = this
                server = embeddedServer(CIO, port = port, host = "0.0.0.0") {
            install(ContentNegotiation) { registerJson(svc.json) }
            install(createApplicationPlugin(name = "ApiAuth") {
                val log = Logger.withTag("ApiAuth")
                onCall { call ->
                    val path = call.request.path()
                    if (!path.startsWith("/api/v2/")) return@onCall
                    if (path == "/api/v2/auth/login" || path == "/api/v2/auth/status") return@onCall

                    val sessionId = parseCookie(call.request.headers["Cookie"], "session_id")
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

                    call.respondText(respondFail("Authentication required"), ContentType.Application.Json, HttpStatusCode.Unauthorized)
                    log.w { "ApiAuth rejected request: path=$path" }
                }
            })
            routing {
                get("/ping") {
                    call.respondText("pong", ContentType.Text.Plain)
                }

                get("/") { svc.serveAsset(call, "website/admin.html", ContentType.Text.Html) }
                get("/website/{path...}") { svc.serveWebsiteAsset(call) }
                get("/files/{path...}") { svc.serveFile(call) }
                get("/images/{path...}") { svc.serveFile(call) }
                get("/diary_images/{path...}") { svc.serveFile(call) }

                route("/api/v2") {
                    setupArticleRoutes()
                    setupDiaryRoutes()
                    setupBookRoutes()
                    setupNewsRoutes()
                    setupAiRoutes()
                    setupTaskRoutes()
                    setupStatsRoutes()
                    setupAuthRoutes()
                }
            }
                }.start(wait = false)
                currentPort = port
                log.i { "Web server started on port $port" }
                return port
            } catch (_: Exception) {}
        }
        throw IllegalStateException(
            "No available port in range ${WebServiceConfig.portRangeStart}-${WebServiceConfig.portRangeEnd}"
        )
    }

    fun stop() {
        (server as? ApplicationEngine)?.stop(1000, 2000)
        server = null
        currentPort = 0
        log.i { "Web server stopped" }
    }

    fun getPort(): Int = currentPort

    fun isRunning(): Boolean = server != null

    private fun respondOk(data: JsonObject? = null): String =
        """{"code":0,"msg":"success","data":${data?.toString() ?: "null"}}"""

    private fun respondFail(msg: String): String =
        """{"code":-1,"msg":"$msg"}"""

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

    private suspend fun serveFile(call: ApplicationCall) {
        val path = call.parameters.getAll("path")?.joinToString("/") ?: ""
        if (path.isEmpty() || path.split("/").any { it == ".." }) {
            call.respond(HttpStatusCode.NotFound)
            return
        }

        val root = File(ctx.filesDir, "DailySatori")
        val legacyRoot = File(ctx.filesDir.parentFile, "app_flutter")
        val base = path.substringBeforeLast('.')
        val candidates = mutableListOf(File(root, path))
        candidates.addAll(listOf("jpg", "jpeg", "png", "webp", "gif").map { File(root, "$base.$it") })
        candidates.add(File(legacyRoot, path))
        candidates.addAll(listOf("jpg", "jpeg", "png", "webp", "gif").map { File(legacyRoot, "$base.$it") })
        if (!path.contains("/")) {
            val imgs = listOf("images", "diary_images")
            candidates.addAll(imgs.flatMap { d -> listOf("jpg", "jpeg", "png", "webp", "gif").map { File(root, "$d/$base.$it") } })
            candidates.addAll(imgs.map { File(root, "$it/$path") })
            candidates.addAll(imgs.flatMap { d -> listOf("jpg", "jpeg", "png", "webp", "gif").map { File(legacyRoot, "$d/$base.$it") } })
            candidates.addAll(imgs.map { File(legacyRoot, "$it/$path") })
        }

        val file = candidates.firstOrNull { it.exists() && it.isFile }
        if (file != null) {
            val contentType = detectFileContentType(file, path)
            call.respondBytes(file.readBytes(), contentType)
        } else {
            call.respond(HttpStatusCode.NotFound)
        }
    }

    private fun fileUrl(path: String?): String {
        if (path.isNullOrEmpty() || path == "null" || path.endsWith("/null")) return ""
        if (path.startsWith("http://") || path.startsWith("https://")) return path
        val normalized = path
            .substringAfter("DailySatori/", path)
            .removePrefix("/")
            .removePrefix("files/")
        return "/files/$normalized"
    }

    private fun articleCoverUrl(localPath: String?, remoteUrl: String?): String =
        fileUrl(localPath?.takeIf { it.isNotBlank() } ?: remoteUrl)

    private fun diaryImagesToJson(images: String?): JsonArray {
        if (images.isNullOrBlank()) return JsonArray(emptyList())
        val list = images.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() && it != "null" && !it.endsWith("/null") }
            .map { fileUrl(it) }
        return JsonArray(list.map { JsonPrimitive(it) })
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
                call.respondText(respondOk(buildJsonObject {
                    put("items", JsonArray(
                        articles.map { a -> buildJsonObject {
                            put("id", JsonPrimitive(a.id))
                            put("title", JsonPrimitive(a.title ?: ""))
                            put("url", JsonPrimitive(a.url ?: ""))
                            put("coverImage", JsonPrimitive(articleCoverUrl(a.cover_image, a.cover_image_url)))
                            put("aiTitle", JsonPrimitive(a.ai_title ?: ""))
                            put("aiContent", JsonPrimitive(a.ai_content ?: ""))
                            put("isFavorite", JsonPrimitive((a.is_favorite ?: 0) > 0))
                            put("status", JsonPrimitive(a.status ?: ""))
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
                }), ContentType.Application.Json)
            }

            get("/search") {
                val repo = get<ArticleRepository>(ArticleRepository::class.java)
                val query = call.request.queryParameters["q"] ?: ""
                val results = try { repo.searchSync(query) } catch (_: Exception) { emptyList() }
                call.respondText(respondOk(buildJsonObject {
                    put("items", JsonArray(
                        results.map { a -> buildJsonObject {
                            put("id", JsonPrimitive(a.id))
                            put("title", JsonPrimitive(a.title ?: ""))
                            put("url", JsonPrimitive(a.url ?: ""))
                            put("coverImage", JsonPrimitive(articleCoverUrl(a.cover_image, a.cover_image_url)))
                            put("aiTitle", JsonPrimitive(a.ai_title ?: ""))
                            put("aiContent", JsonPrimitive(a.ai_content ?: ""))
                            put("isFavorite", JsonPrimitive((a.is_favorite ?: 0) > 0))
                            put("status", JsonPrimitive(a.status ?: ""))
                            put("createdAt", JsonPrimitive(a.created_at))
                            put("updatedAt", JsonPrimitive(a.updated_at))
                        }}
                    ))
                    put("count", JsonPrimitive(results.size))
                }), ContentType.Application.Json)
            }

            get("/{id}") {
                val repo = get<ArticleRepository>(ArticleRepository::class.java)
                val id = call.parameters["id"]?.toLongOrNull() ?: 0L
                val a = repo.getById(id)
                if (a != null) {
                    call.respondText(respondOk(buildJsonObject {
                        put("id", JsonPrimitive(a.id))
                        put("title", JsonPrimitive(a.title ?: ""))
                        put("url", JsonPrimitive(a.url ?: ""))
                        put("aiContent", JsonPrimitive(a.ai_content ?: ""))
                        put("aiMarkdownContent", JsonPrimitive(a.ai_markdown_content ?: ""))
                        put("originalMarkdownContent", JsonPrimitive(a.original_markdown_content ?: ""))
                        put("sourceType", JsonPrimitive(a.source_type))
                        put("aiTitle", JsonPrimitive(a.ai_title ?: ""))
                        put("coverImage", JsonPrimitive(articleCoverUrl(a.cover_image, a.cover_image_url)))
                        put("isFavorite", JsonPrimitive((a.is_favorite ?: 0) > 0))
                        put("comment", JsonPrimitive(a.comment ?: ""))
                        put("status", JsonPrimitive(a.status ?: ""))
                        put("createdAt", JsonPrimitive(a.created_at))
                        put("updatedAt", JsonPrimitive(a.updated_at))
                    }), ContentType.Application.Json)
                } else {
                    call.respondText(respondFail("Not found"), ContentType.Application.Json, HttpStatusCode.NotFound)
                }
            }

            post {
                try {
                    val body = call.receive<JsonObject>()
                    val url = body.stringValue("url").trim()
                    if (url.isBlank()) {
                        call.respondText(respondFail("URL is required"), ContentType.Application.Json, HttpStatusCode.BadRequest)
                        return@post
                    }
                    val repo = get<ArticleRepository>(ArticleRepository::class.java)
                    val existing = repo.getByUrl(url)
                    if (existing != null) {
                        call.respondText(respondOk(buildJsonObject {
                            put("id", JsonPrimitive(existing.id))
                            put("queued", JsonPrimitive(false))
                            put("existing", JsonPrimitive(true))
                        }), ContentType.Application.Json)
                        return@post
                    }
                    get<ArticleProcessingScheduler>(ArticleProcessingScheduler::class.java).enqueueSave(url)
                    call.respondText(respondOk(buildJsonObject {
                        put("queued", JsonPrimitive(true))
                        put("existing", JsonPrimitive(false))
                    }), ContentType.Application.Json, HttpStatusCode.Accepted)
                } catch (e: Exception) {
                    call.respondText(respondFail(e.message ?: "Invalid request"), ContentType.Application.Json, HttpStatusCode.BadRequest)
                }
            }

            put("/{id}") {
                try {
                    val id = call.parameters["id"]?.toLongOrNull() ?: 0L
                    val repo = get<ArticleRepository>(ArticleRepository::class.java)
                    val article = repo.getById(id)
                    if (article == null) {
                        call.respondText(respondFail("Not found"), ContentType.Application.Json, HttpStatusCode.NotFound)
                        return@put
                    }
                    val body = call.receive<JsonObject>()
                    repo.update(
                        id = id,
                        title = body.optionalStringValue("title", article.title),
                        aiTitle = article.ai_title,
                        aiContent = article.ai_content,
                        aiMarkdownContent = article.ai_markdown_content,
                        url = body.optionalStringValue("url", article.url),
                        isFavorite = body.booleanValue("isFavorite")?.let { if (it) 1L else 0L } ?: article.is_favorite ?: 0L,
                        comment = body.optionalStringValue("comment", article.comment),
                        status = article.status ?: "pending",
                        coverImage = article.cover_image,
                        coverImageUrl = article.cover_image_url,
                        pubDate = article.pub_date,
                    )
                    call.respondText(respondOk(articleSummary(repo.getById(id)!!)), ContentType.Application.Json)
                } catch (e: Exception) {
                    call.respondText(respondFail(e.message ?: "Invalid request"), ContentType.Application.Json, HttpStatusCode.BadRequest)
                }
            }

            post("/{id}/favorite") {
                val repo = get<ArticleRepository>(ArticleRepository::class.java)
                val id = call.parameters["id"]?.toLongOrNull() ?: 0L
                if (repo.getById(id) == null) {
                    call.respondText(respondFail("Not found"), ContentType.Application.Json, HttpStatusCode.NotFound)
                    return@post
                }
                repo.toggleFavorite(id)
                call.respondText(respondOk(articleSummary(repo.getById(id)!!)), ContentType.Application.Json)
            }

            post("/{id}/reprocess") {
                val repo = get<ArticleRepository>(ArticleRepository::class.java)
                val id = call.parameters["id"]?.toLongOrNull() ?: 0L
                if (repo.getById(id) == null) {
                    call.respondText(respondFail("Not found"), ContentType.Application.Json, HttpStatusCode.NotFound)
                    return@post
                }
                get<WebpageParserService>(WebpageParserService::class.java).reprocessArticle(id)
                call.respondText(respondOk(buildJsonObject { put("queued", JsonPrimitive(true)) }), ContentType.Application.Json)
            }

            delete("/{id}") {
                val repo = get<ArticleRepository>(ArticleRepository::class.java)
                val id = call.parameters["id"]?.toLongOrNull() ?: 0L
                repo.delete(id)
                call.respondText(respondOk(), ContentType.Application.Json)
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
                call.respondText(respondOk(buildJsonObject {
                    put("items", JsonArray(
                        diaries.map { d -> buildJsonObject {
                            put("id", JsonPrimitive(d.id))
                            put("content", JsonPrimitive(d.content ?: ""))
                            put("mood", JsonPrimitive(d.mood ?: ""))
                            put("tags", JsonPrimitive(d.tags ?: ""))
                            put("images", diaryImagesToJson(d.images))
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
                }), ContentType.Application.Json)
            }

            get("/search") {
                val repo = get<DiaryRepository>(DiaryRepository::class.java)
                val query = call.request.queryParameters["q"] ?: ""
                val results = try { repo.searchSync(query) } catch (_: Exception) { emptyList() }
                call.respondText(respondOk(buildJsonObject {
                    put("items", JsonArray(
                        results.map { d -> buildJsonObject {
                            put("id", JsonPrimitive(d.id))
                            put("content", JsonPrimitive(d.content ?: ""))
                            put("createdAt", JsonPrimitive(d.created_at))
                        }}
                    ))
                }), ContentType.Application.Json)
            }

            get("/{id}") {
                val repo = get<DiaryRepository>(DiaryRepository::class.java)
                val id = call.parameters["id"]?.toLongOrNull() ?: 0L
                val d = repo.getById(id)
                if (d != null) {
                    val attachments = get<DiaryAttachmentRepository>(DiaryAttachmentRepository::class.java).getForDiary(id)
                    call.respondText(respondOk(buildJsonObject {
                        put("id", JsonPrimitive(d.id))
                        put("content", JsonPrimitive(d.content ?: ""))
                        put("mood", JsonPrimitive(d.mood ?: ""))
                        put("tags", JsonPrimitive(d.tags ?: ""))
                        put("images", diaryImagesToJson(d.images))
                        put("attachments", JsonArray(attachments.map { attachment -> buildJsonObject {
                            put("id", JsonPrimitive(attachment.id))
                            put("kind", JsonPrimitive(attachment.kind))
                            put("name", JsonPrimitive(attachment.display_name.ifBlank { attachment.local_path.substringAfterLast('/') }))
                            put("url", JsonPrimitive(fileUrl(attachment.local_path)))
                            put("mimeType", JsonPrimitive(attachment.mime_type))
                            put("sizeBytes", JsonPrimitive(attachment.size_bytes))
                            put("durationMs", JsonPrimitive(attachment.duration_ms))
                            put("transcript", JsonPrimitive(attachment.transcript))
                            put("transcriptStatus", JsonPrimitive(attachment.transcript_status))
                        } }))
                        put("createdAt", JsonPrimitive(d.created_at))
                        put("updatedAt", JsonPrimitive(d.updated_at))
                    }), ContentType.Application.Json)
                } else {
                    call.respondText(respondFail("Not found"), ContentType.Application.Json, HttpStatusCode.NotFound)
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
                    call.respondText(respondOk(), ContentType.Application.Json)
                } catch (e: Exception) {
                    call.respondText(respondFail(e.message ?: "Invalid request"), ContentType.Application.Json, HttpStatusCode.BadRequest)
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
                    val existing = repo.getById(id)
                    if (existing == null) {
                        call.respondText(respondFail("Not found"), ContentType.Application.Json, HttpStatusCode.NotFound)
                        return@put
                    }
                    repo.update(id, content, tags, mood, existing.images)
                    call.respondText(respondOk(), ContentType.Application.Json)
                } catch (e: Exception) {
                    call.respondText(respondFail(e.message ?: "Invalid request"), ContentType.Application.Json, HttpStatusCode.BadRequest)
                }
            }

            delete("/{id}") {
                val repo = get<DiaryRepository>(DiaryRepository::class.java)
                val id = call.parameters["id"]?.toLongOrNull() ?: 0L
                repo.delete(id)
                call.respondText(respondOk(), ContentType.Application.Json)
            }
        }
    }

    private fun Route.setupBookRoutes() {
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
                    repo.update(
                        id = id,
                        title = title,
                        author = body.optionalStringValue("author", existing.author).orEmpty(),
                        category = body.optionalStringValue("category", existing.category).orEmpty(),
                        coverImage = body.optionalStringValue("coverImage", existing.cover_image).orEmpty(),
                        introduction = body.optionalStringValue("introduction", existing.introduction).orEmpty(),
                        hasUpdate = existing.has_update ?: 0L,
                    )
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

            put("/viewpoints/{viewpointId}") {
                val repo = get<BookViewpointRepository>(BookViewpointRepository::class.java)
                val id = call.parameters["viewpointId"]?.toLongOrNull() ?: 0L
                val existing = repo.getById(id)
                if (existing == null) {
                    call.respondText(respondFail("Not found"), ContentType.Application.Json, HttpStatusCode.NotFound)
                    return@put
                }
                val body = call.receive<JsonObject>()
                repo.update(
                    id = id,
                    title = body.optionalStringValue("title", existing.title).orEmpty(),
                    content = body.optionalStringValue("content", existing.content).orEmpty(),
                    example = body.optionalStringValue("example", existing.example).orEmpty(),
                )
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

    private fun Route.setupStatsRoutes() {
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
                        }}
                    ))
                }), ContentType.Application.Json)
            }
        }
    }

    private fun Route.setupNewsRoutes() {
        route("/news") {
            get("/summary") {
                val repo = get<UnifiedNewsSummaryRepository>(UnifiedNewsSummaryRepository::class.java)
                val summary = repo.getLatestSuccessful()
                if (summary == null) {
                    call.respondText(respondOk(buildJsonObject { put("summary", JsonPrimitive("")) }), ContentType.Application.Json)
                    return@get
                }
                val sources = repo.getSources(summary.id)
                call.respondText(respondOk(buildJsonObject {
                    put("id", JsonPrimitive(summary.id))
                    put("date", JsonPrimitive(summary.summary_date))
                    put("title", JsonPrimitive(summary.title))
                    put("summary", JsonPrimitive(summary.content ?: ""))
                    put("generatedAt", JsonPrimitive(summary.generated_at ?: summary.updated_at))
                    put("sources", JsonArray(sources.map { source -> buildJsonObject {
                        put("refKey", JsonPrimitive(source.ref_key))
                        put("title", JsonPrimitive(source.title))
                        put("sourceType", JsonPrimitive(source.source_type))
                        put("sourceId", JsonPrimitive(source.source_id ?: 0L))
                        put("url", JsonPrimitive(source.source_url ?: ""))
                    } }))
                }), ContentType.Application.Json)
            }

            post("/summary/refresh") {
                val result = get<UnifiedNewsSummaryService>(UnifiedNewsSummaryService::class.java).generateDaily(force = true)
                if (result.success) {
                    call.respondText(respondOk(buildJsonObject { put("status", JsonPrimitive(result.status.value)) }), ContentType.Application.Json)
                } else {
                    call.respondText(respondFail(result.message ?: "Summary generation failed"), ContentType.Application.Json, HttpStatusCode.BadRequest)
                }
            }
        }
    }

    private fun Route.setupTaskRoutes() {
        route("/tasks") {
            get {
                val showTerminal = call.request.queryParameters["showTerminal"]?.toBooleanStrictOrNull() ?: true
                val page = get<AsyncTaskRepository>(AsyncTaskRepository::class.java)
                    .observeTaskCenter(AsyncTaskFilter(showTerminal = showTerminal), limit = 60)
                    .first()
                call.respondText(respondOk(buildJsonObject {
                    put("items", JsonArray(page.tasks.map { task -> buildJsonObject {
                        put("id", JsonPrimitive(task.id))
                        put("type", JsonPrimitive(task.type))
                        put("status", JsonPrimitive(task.status))
                        put("progressCurrent", JsonPrimitive(task.progressCurrent))
                        put("progressTotal", JsonPrimitive(task.progressTotal))
                        put("progressMessage", JsonPrimitive(task.progressMessage))
                        put("createdAt", JsonPrimitive(task.createdAt))
                        put("updatedAt", JsonPrimitive(task.updatedAt))
                        put("hasError", JsonPrimitive(task.lastErrorMessage.isNotBlank()))
                    } }))
                    put("hasMore", JsonPrimitive(page.hasMore))
                }), ContentType.Application.Json)
            }

            post("/{id}/cancel") {
                val id = call.parameters["id"]?.toLongOrNull() ?: 0L
                val repo = get<AsyncTaskRepository>(AsyncTaskRepository::class.java)
                if (repo.getById(id) == null) {
                    call.respondText(respondFail("Not found"), ContentType.Application.Json, HttpStatusCode.NotFound)
                    return@post
                }
                repo.cancel(id)
                call.respondText(respondOk(), ContentType.Application.Json)
            }
        }
    }

    private fun Route.setupAiRoutes() {
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

    private fun Route.setupAuthRoutes() {
        route("/auth") {
            post("/login") {
                try {
                    val body = call.receive<JsonObject>()
                    val token = (body["token"] as? JsonPrimitive)?.content ?: ""
                    val settingRepo = get<SettingRepository>(SettingRepository::class.java)
                    val storedToken = settingRepo.get("web_server_token") ?: ""
                    if (token.isNotEmpty() && token == storedToken) {
                        val sessionId = UUID.randomUUID().toString()
                        val sessionRepo = get<SessionRepository>(SessionRepository::class.java)
                        sessionRepo.insert(sessionId = sessionId, username = "admin")
                        call.response.headers.append("Set-Cookie", "session_id=$sessionId; Path=/; HttpOnly; SameSite=Strict")
                        call.respondText("""{"code":0,"msg":"ok"}""", ContentType.Application.Json)
                    } else {
                        call.respondText("""{"code":1,"msg":"Invalid token"}""", ContentType.Application.Json, HttpStatusCode.Unauthorized)
                    }
                } catch (e: Exception) {
                    call.respondText("""{"code":1,"msg":"${e.message}"}""", ContentType.Application.Json, HttpStatusCode.BadRequest)
                }
            }

            post("/logout") {
                parseCookie(call.request.headers["Cookie"], "session_id")?.let { sessionId ->
                    get<SessionRepository>(SessionRepository::class.java).delete(sessionId)
                }
                call.response.headers.append("Set-Cookie", "session_id=; Path=/; Max-Age=0; HttpOnly; SameSite=Strict")
                call.respondText(respondOk(), ContentType.Application.Json)
            }

            get("/status") {
                val sessionId = parseCookie(call.request.headers["Cookie"], "session_id")
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
                call.respondText(respondOk(buildJsonObject {
                    put("authenticated", JsonPrimitive(authenticated))
                }), ContentType.Application.Json)
            }

            get("/token") {
                val settingRepo = get<SettingRepository>(SettingRepository::class.java)
                val token = settingRepo.get("web_server_token") ?: ""
                call.respondText(respondOk(buildJsonObject {
                    put("token", JsonPrimitive(token))
                }), ContentType.Application.Json)
            }
        }
    }
}

private fun JsonObject.stringValue(name: String): String =
    (this[name] as? JsonPrimitive)?.content.orEmpty()

private fun JsonObject.optionalStringValue(name: String, fallback: String?): String? =
    if (containsKey(name)) (this[name] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() } else fallback

private fun JsonObject.booleanValue(name: String): Boolean? =
    (this[name] as? JsonPrimitive)?.content?.toBooleanStrictOrNull()

private fun articleSummary(article: com.dailysatori.shared.db.Article): JsonObject = buildJsonObject {
    put("id", JsonPrimitive(article.id))
    put("title", JsonPrimitive(article.title.orEmpty()))
    put("isFavorite", JsonPrimitive((article.is_favorite ?: 0L) > 0L))
    put("comment", JsonPrimitive(article.comment.orEmpty()))
    put("status", JsonPrimitive(article.status.orEmpty()))
}

private fun bookToJson(book: com.dailysatori.shared.db.Book): JsonObject = buildJsonObject {
    put("id", JsonPrimitive(book.id))
    put("title", JsonPrimitive(book.title))
    put("author", JsonPrimitive(book.author))
    put("category", JsonPrimitive(book.category))
    put("coverImage", JsonPrimitive(book.cover_image))
    put("introduction", JsonPrimitive(book.introduction))
    put("createdAt", JsonPrimitive(book.created_at))
}

private fun mcpReferenceToJson(reference: McpSearchResult): JsonObject = buildJsonObject {
    put("id", JsonPrimitive(reference.id))
    put("type", JsonPrimitive(reference.type))
    put("title", JsonPrimitive(reference.title))
    put("summary", JsonPrimitive(reference.summary.orEmpty()))
    put("createdAt", JsonPrimitive(reference.createdAt.orEmpty()))
    put("matchReason", JsonPrimitive(reference.matchReason.orEmpty()))
}
