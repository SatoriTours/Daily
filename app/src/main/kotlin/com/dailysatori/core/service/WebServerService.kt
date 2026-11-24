package com.dailysatori.core.service

import android.content.Context
import co.touchlab.kermit.Logger
import com.dailysatori.config.WebServiceConfig
import com.dailysatori.core.worker.AsyncTaskScheduler
import com.dailysatori.data.repository.SessionRepository
import com.dailysatori.data.repository.SettingRepository
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json as registerJson
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.path
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import java.io.File
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.koin.java.KoinJavaComponent.get

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
    private var server: EmbeddedServer<*, *>? = null
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
                    install(com.dailysatori.core.diagnostics.DiagnosticServerPlugin)
                    install(ContentNegotiation) { registerJson(svc.json) }
                    install(createApplicationPlugin(name = "ApiAuth") {
                        val log = Logger.withTag("ApiAuth")
                        onCall { call ->
                            val path = call.request.path()
                            if (!path.startsWith("/api/v2/")) return@onCall
                            if (path == "/api/v2/auth/login" || path == "/api/v2/auth/status") return@onCall

                            if (!call.requireWebCredentials()) log.w { "ApiAuth rejected request: path=$path" }
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
                            setupNewsRoutes { AsyncTaskScheduler(ctx).enqueue(it) }
                            setupAiRoutes()
                            setupTaskRoutes { AsyncTaskScheduler(ctx).cancel(it) }
                            setupStatsRoutes()
                            setupAuthRoutes()
                        }
                    }
                }.start(wait = false)
                currentPort = port
                log.i { "Web server started on port $port" }
                return port
            } catch (e: Exception) {
                log.d(e) { "Port $port unavailable, trying next" }
            }
        }
        throw IllegalStateException(
            "No available port in range ${WebServiceConfig.portRangeStart}-${WebServiceConfig.portRangeEnd}"
        )
    }

    fun stop() {
        server?.stop(1000, 2000)
        server = null
        currentPort = 0
        log.i { "Web server stopped" }
    }

    fun getPort(): Int = currentPort

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

    private suspend fun serveFile(call: ApplicationCall) {
        // All aliases enter here; an auth failure returns before path resolution or file I/O.
        call.response.headers.append("Cache-Control", "no-store")
        if (!call.requireWebCredentials()) return
        val path = call.parameters.getAll("path")?.joinToString("/") ?: ""
        if (path.isEmpty() || path.split("/").any { it.isEmpty() || it == "." || it == ".." } ||
            path.any { it == '\\' || it.isISOControl() }) {
            call.respond(HttpStatusCode.NotFound)
            return
        }

        val root = File(ctx.filesDir, "DailySatori")
        val legacyRoot = File(ctx.filesDir.parentFile, "app_flutter")
        val base = path.substringBeforeLast('.')
        val extensions = listOf("jpg", "jpeg", "png", "webp", "gif")
        val roots = listOf(root, legacyRoot)
        val candidates = roots.flatMap { dir ->
            (listOf(path) + extensions.map { "$base.$it" }).map { dir to it }
        }.toMutableList()
        if (!path.contains("/")) {
            val imgs = listOf("images", "diary_images")
            roots.forEach { dir ->
                candidates.addAll(imgs.flatMap { d -> extensions.map { dir to "$d/$base.$it" } })
                candidates.addAll(imgs.map { dir to "$it/$path" })
            }
        }

        val file = candidates.firstNotNullOfOrNull { (dir, relative) -> privateAttachmentFile(dir, relative) }
        if (file != null) {
            val contentType = detectFileContentType(file, path)
            call.respondBytes(file.readBytes(), contentType)
        } else {
            call.respond(HttpStatusCode.NotFound)
        }
    }

    private fun privateAttachmentFile(root: File, path: String): File? = try {
        val expectedRoot = File(root.parentFile!!.canonicalFile, root.name)
        val file = File(expectedRoot, path).canonicalFile
        file.takeIf {
            // Do not follow a symlink replacing the allowed root, or return a target outside it.
            expectedRoot == root.canonicalFile && it.toPath().startsWith(expectedRoot.toPath()) && it.isFile
        }
    } catch (_: java.io.IOException) {
        null
    }

    private suspend fun ApplicationCall.requireWebCredentials(): Boolean {
        val sessionId = parseCookie(request.headers["Cookie"], "session_id")
        val token = request.headers["Authorization"]?.removePrefix("Bearer ")
        if (sessionId != null) {
            try {
                val sessions = get<SessionRepository>(SessionRepository::class.java)
                if (sessions.getBySessionId(sessionId) != null) return true
            } catch (e: Exception) {
                // Never log the session id or cookie value.
                log.w(e) { "Web auth lookup failed" }
            }
        }
        if (token != null) {
            try {
                val settings = get<SettingRepository>(SettingRepository::class.java)
                val stored = settings.get("web_server_token")
                if (stored != null && token == stored) return true
            } catch (e: Exception) {
                // Never log the bearer token value.
                log.w(e) { "Web auth lookup failed" }
            }
        }
        respondText(respondFail("Authentication required"), ContentType.Application.Json, HttpStatusCode.Unauthorized)
        return false
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
