# Web 服务优化 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复 Web 服务崩溃、增加 Token 认证、提供静态资源路由、全面重新设计 Web UI

**Architecture:** Ktor CIO 引擎替换 Netty（消除 Android 原生依赖崩溃），API 路由保持 `/api/v2/*` 结构，新增 `/` 和 `/website/*` 静态资源路由，Token 存储在 SettingRepository

**Tech Stack:** Kotlin, Ktor 3.1.3 CIO, Koin DI, Vue 3, Chart.js, Marked.js

---

## 文件结构

```
修改:
  gradle/libs.versions.toml            → 添加 CIO 依赖声明
  app/build.gradle.kts                 → Netty → CIO
  app/.../di/AppModule.kt             → 注入 Android Context
  app/.../service/WebServerService.kt  → 引擎 + 静态资源 + Token 认证
  app/.../settings/SettingsViewModel.kt → 异步启停 + Token 管理
  app/.../settings/SettingsScreen.kt   → 展示 Token/地址/错误

重写:
  app/src/main/assets/website/admin.html
  app/src/main/assets/website/css/base.css
  app/src/main/assets/website/css/layout.css
  app/src/main/assets/website/css/components.css
  app/src/main/assets/website/css/pages.css
  app/src/main/assets/website/js/app.js

新增:
  app/src/main/assets/website/js/lib/vue.global.prod.min.js
  app/src/main/assets/website/js/lib/marked.min.js
  app/src/main/assets/website/js/lib/chart.umd.min.js
```

---

### Task 1: 替换 Ktor 依赖 (Netty → CIO)

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: 在 `libs.versions.toml` 中将 Netty 替换为 CIO**

找到这几行：
```toml
ktor-server-netty = { group = "io.ktor", name = "ktor-server-netty-jvm", version.ref = "ktor" }
```

替换为：
```toml
ktor-server-cio = { group = "io.ktor", name = "ktor-server-cio-jvm", version.ref = "ktor" }
```

- [ ] **Step 2: 在 `app/build.gradle.kts` 中替换依赖**

找到这一行：
```kotlin
implementation(libs.ktor.server.netty)
```

替换为：
```kotlin
implementation(libs.ktor.server.cio)
```

- [ ] **Step 3: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts
git commit -m "build: replace ktor-server-netty with ktor-server-cio"
```

---

### Task 2: 重写 WebServerService - 引擎 + 静态资源

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/core/di/AppModule.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/core/service/WebServerService.kt`

- [ ] **Step 1: 修改 AppModule 注入 Context**

将 `WebServerService()` 改为 `WebServerService(androidContext())`：

```kotlin
val appModule: Module = module {
    single { ClipboardMonitorService(androidContext()) }
    single { WebServerService(androidContext()) }
    single { AppUpgradeService(get()) }
}
```

- [ ] **Step 2: 重写 WebServerService.kt**

完整替换文件内容：

```kotlin
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
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
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
import io.ktor.util.pipeline.PipelineContext
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

class WebServerService(private val context: Context) {
    private val log = Logger.withTag("WebServer")
    private var server: ApplicationEngine? = null

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun start() {
        if (server != null) return
        log.i { "Starting web server on port ${WebServiceConfig.httpPort}" }

        server = embeddedServer(CIO, port = WebServiceConfig.httpPort, host = "0.0.0.0") {
            install(ContentNegotiation) { registerJson(this@WebServerService.json) }
            install(CORS) { anyHost() }
            routing {
                get("/ping") {
                    call.respondText("pong", ContentType.Text.Plain)
                }

                get("/") { serveAsset("website/admin.html", ContentType.Text.HTML) }
                get("/website/{path...}") { serveWebsiteAsset() }

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
        server?.stop(1000, 2000)
        server = null
        log.i { "Web server stopped" }
    }

    fun isRunning(): Boolean = server != null

    private suspend fun PipelineContext<*, *>.serveAsset(path: String, contentType: ContentType) {
        try {
            val bytes = context.assets.open(path).use { it.readBytes() }
            call.respondBytes(bytes, contentType)
        } catch (e: Exception) {
            log.e(e) { "Asset not found: $path" }
            call.respond(HttpStatusCode.NotFound)
        }
    }

    private suspend fun PipelineContext<*, *>.serveWebsiteAsset() {
        val path = call.parameters.getAll("path")?.joinToString("/") ?: ""
        if (path.isEmpty() || path.endsWith("/")) {
            call.respond(HttpStatusCode.NotFound)
            return
        }
        val contentType = when {
            path.endsWith(".css") -> ContentType.Text.CSS
            path.endsWith(".js") -> ContentType.Text.JavaScript
            path.endsWith(".html") -> ContentType.Text.HTML
            path.endsWith(".svg") -> ContentType.Image.SVG
            path.endsWith(".png") -> ContentType.Image.PNG
            path.endsWith(".jpg") || path.endsWith(".jpeg") -> ContentType.Image.JPEG
            path.endsWith(".woff2") -> ContentType("font/woff2", "woff2")
            else -> ContentType.Application.OctetStream
        }
        serveAsset("website/$path", contentType)
    }

    // ---- Article Routes ----

    private fun Route.setupArticleRoutes() {
        route("/articles") {
            get {
                val repo = get<ArticleRepository>(ArticleRepository::class.java)
                val page = call.request.queryParameters["page"]?.toLongOrNull() ?: 1L
                val limit = 20L
                val offset = (page - 1) * limit
                val articles = try { repo.getPaginated(limit, offset) } catch (_: Exception) { emptyList() }
                val total = try { repo.count() } catch (_: Exception) { 0L }
                call.respond(ApiResponse(0, "success", buildJsonObject {
                    put("items", json.encodeToJsonElement(
                        articles.map { a -> buildJsonObject {
                            put("id", JsonPrimitive(a.id))
                            put("title", JsonPrimitive(a.title ?: ""))
                            put("url", JsonPrimitive(a.url ?: ""))
                            put("subTitle", JsonPrimitive(a.sub_title ?: ""))
                            put("coverImage", JsonPrimitive(a.cover_image ?: ""))
                            put("aiTitle", JsonPrimitive(a.ai_title ?: ""))
                            put("aiContent", JsonPrimitive(a.ai_content ?: ""))
                            put("isFavorite", JsonPrimitive(a.is_favorite > 0))
                            put("createdAt", JsonPrimitive(a.created_at))
                            put("updatedAt", JsonPrimitive(a.updated_at))
                        }}
                    ))
                    put("pagination", json.encodeToJsonElement(buildJsonObject {
                        put("page", JsonPrimitive(page))
                        put("pageSize", JsonPrimitive(limit))
                        put("totalItems", JsonPrimitive(total))
                        put("totalPages", JsonPrimitive((total + limit - 1) / limit))
                    }))
                }))
            }

            get("/search") {
                val repo = get<ArticleRepository>(ArticleRepository::class.java)
                val query = call.request.queryParameters["q"] ?: ""
                val results = try { repo.searchSync(query) } catch (_: Exception) { emptyList() }
                call.respond(ApiResponse(0, "success", buildJsonObject {
                    put("items", json.encodeToJsonElement(results.map { a -> buildJsonObject {
                        put("id", JsonPrimitive(a.id))
                        put("title", JsonPrimitive(a.title ?: ""))
                        put("createdAt", JsonPrimitive(a.created_at))
                    }}))
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
                        put("isFavorite", JsonPrimitive(a.is_favorite > 0))
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

    // ---- Diary Routes ----

    private fun Route.setupDiaryRoutes() {
        route("/diary") {
            get {
                val repo = get<DiaryRepository>(DiaryRepository::class.java)
                val page = call.request.queryParameters["page"]?.toLongOrNull() ?: 1L
                val limit = 20L
                val offset = (page - 1) * limit
                val diaries = try { repo.getPaginated(limit, offset) } catch (_: Exception) { emptyList() }
                val total = try { repo.count() } catch (_: Exception) { 0L }
                call.respond(ApiResponse(0, "success", buildJsonObject {
                    put("items", json.encodeToJsonElement(diaries.map { d -> buildJsonObject {
                        put("id", JsonPrimitive(d.id))
                        put("content", JsonPrimitive(d.content ?: ""))
                        put("mood", JsonPrimitive(d.mood ?: ""))
                        put("tags", JsonPrimitive(d.tags ?: ""))
                        put("images", json.encodeToJsonElement(emptyList<String>()))
                        put("createdAt", JsonPrimitive(d.created_at))
                        put("updatedAt", JsonPrimitive(d.updated_at))
                    }}))
                    put("pagination", json.encodeToJsonElement(buildJsonObject {
                        put("page", JsonPrimitive(page))
                        put("pageSize", JsonPrimitive(limit))
                        put("totalItems", JsonPrimitive(total))
                        put("totalPages", JsonPrimitive((total + limit - 1) / limit))
                    }))
                }))
            }

            get("/search") {
                val repo = get<DiaryRepository>(DiaryRepository::class.java)
                val query = call.request.queryParameters["q"] ?: ""
                val results = try { repo.searchSync(query) } catch (_: Exception) { emptyList() }
                call.respond(ApiResponse(0, "success", buildJsonObject {
                    put("items", json.encodeToJsonElement(results.map { d -> buildJsonObject {
                        put("id", JsonPrimitive(d.id))
                        put("content", JsonPrimitive(d.content ?: ""))
                        put("createdAt", JsonPrimitive(d.created_at))
                    }}))
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
                        put("images", json.encodeToJsonElement(emptyList<String>()))
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
                    repo.update(id, content, tags, mood)
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

    // ---- Book Routes ----

    private fun Route.setupBookRoutes() {
        route("/books") {
            get {
                val repo = get<BookRepository>(BookRepository::class.java)
                val books = try { repo.getAll() } catch (_: Exception) { emptyList() }
                call.respond(ApiResponse(0, "success", buildJsonObject {
                    put("items", json.encodeToJsonElement(books.map { b -> buildJsonObject {
                        put("id", JsonPrimitive(b.id))
                        put("title", JsonPrimitive(b.title))
                        put("author", JsonPrimitive(b.author))
                        put("category", JsonPrimitive(b.category))
                        put("coverImage", JsonPrimitive(b.cover_image ?: ""))
                        put("publisher", JsonPrimitive(b.publisher ?: ""))
                        put("isbn", JsonPrimitive(b.isbn ?: ""))
                        put("introduction", JsonPrimitive(b.introduction ?: ""))
                        put("description", JsonPrimitive(b.description ?: ""))
                        put("createdAt", JsonPrimitive(b.created_at))
                    }}))
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
                        put("coverImage", JsonPrimitive(b.cover_image ?: ""))
                        put("publisher", JsonPrimitive(b.publisher ?: ""))
                        put("isbn", JsonPrimitive(b.isbn ?: ""))
                        put("introduction", JsonPrimitive(b.introduction ?: ""))
                        put("description", JsonPrimitive(b.description ?: ""))
                        put("createdAt", JsonPrimitive(b.created_at))
                    }))
                } else {
                    call.respond(HttpStatusCode.NotFound, ApiResponse(-1, "Not found"))
                }
            }

            get("/{id}/viewpoints") {
                val vpRepo = get<BookViewpointRepository>(BookViewpointRepository::class.java)
                val bookId = call.parameters["id"]?.toLongOrNull() ?: 0L
                val viewpoints = try { vpRepo.getByBookId(bookId) } catch (_: Exception) { emptyList() }
                call.respond(ApiResponse(0, "success", buildJsonObject {
                    put("items", json.encodeToJsonElement(viewpoints.map { v -> buildJsonObject {
                        put("id", JsonPrimitive(v.id))
                        put("title", JsonPrimitive(v.title ?: ""))
                        put("content", JsonPrimitive(v.content ?: ""))
                        put("example", JsonPrimitive(v.example ?: ""))
                        put("createdAt", JsonPrimitive(v.created_at))
                    }}))
                }))
            }

            post {
                try {
                    val body = call.receive<JsonObject>()
                    val title = (body["title"] as? JsonPrimitive)?.content ?: ""
                    val repo = get<BookRepository>(BookRepository::class.java)
                    repo.insert(title)
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

    // ---- Stats Routes ----

    private fun Route.setupStatsRoutes() {
        route("/stats") {
            get("/overview") {
                val articleRepo = get<ArticleRepository>(ArticleRepository::class.java)
                val diaryRepo = get<DiaryRepository>(DiaryRepository::class.java)
                val bookRepo = get<BookRepository>(BookRepository::class.java)
                val tagRepo = get<TagRepository>(TagRepository::class.java)
                call.respond(ApiResponse(0, "success", buildJsonObject {
                    put("totals", json.encodeToJsonElement(buildJsonObject {
                        put("articles", JsonPrimitive(articleRepo.count()))
                        put("diaries", JsonPrimitive(diaryRepo.count()))
                        put("books", JsonPrimitive(bookRepo.count()))
                        put("tags", JsonPrimitive(tagRepo.count()))
                        put("favoriteArticles", JsonPrimitive(0))
                    }))
                }))
            }

            get("/weekly-report") {
                val repo = get<WeeklySummaryRepository>(WeeklySummaryRepository::class.java)
                val reports = try { repo.getAll() } catch (_: Exception) { emptyList() }
                call.respond(ApiResponse(0, "success", buildJsonObject {
                    put("reports", json.encodeToJsonElement(reports.map { r -> buildJsonObject {
                        put("id", JsonPrimitive(r.id))
                        put("content", JsonPrimitive(r.content ?: ""))
                        put("weekStart", JsonPrimitive(r.week_start ?: ""))
                        put("weekEnd", JsonPrimitive(r.week_end ?: ""))
                    }}))
                }))
            }

            get("/recent") {
                val articleRepo = get<ArticleRepository>(ArticleRepository::class.java)
                val diaryRepo = get<DiaryRepository>(DiaryRepository::class.java)
                val bookRepo = get<BookRepository>(BookRepository::class.java)
                val articles = try { articleRepo.getPaginated(10, 0) } catch (_: Exception) { emptyList() }
                val diaries = try { diaryRepo.getPaginated(10, 0) } catch (_: Exception) { emptyList() }
                val books = try { bookRepo.getAll().take(10) } catch (_: Exception) { emptyList() }
                call.respond(ApiResponse(0, "success", buildJsonObject {
                    put("articles", json.encodeToJsonElement(articles.map { a -> buildJsonObject {
                        put("id", JsonPrimitive(a.id))
                        put("type", JsonPrimitive("article"))
                        put("title", JsonPrimitive(a.title ?: ""))
                        put("createdAt", JsonPrimitive(a.created_at))
                    }}))
                    put("diaries", json.encodeToJsonElement(diaries.map { d -> buildJsonObject {
                        put("id", JsonPrimitive(d.id))
                        put("type", JsonPrimitive("diary"))
                        put("content", JsonPrimitive(d.content?.take(50) ?: ""))
                        put("createdAt", JsonPrimitive(d.created_at))
                    }}))
                    put("books", json.encodeToJsonElement(books.map { b -> buildJsonObject {
                        put("id", JsonPrimitive(b.id))
                        put("type", JsonPrimitive("book"))
                        put("title", JsonPrimitive(b.title))
                        put("createdAt", JsonPrimitive(b.created_at))
                    }}))
                }))
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
```

注意：`json.encodeToJsonElement()` 在 `buildJsonObject { put(...) }` 中调用时需要在 lambda 内，因为 `put` 接受 `JsonElement`。`encodeToJsonElement` 是 `Json` 的扩展函数。

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/dailysatori/core/di/AppModule.kt app/src/main/kotlin/com/dailysatori/core/service/WebServerService.kt
git commit -m "refactor: rewrite WebServerService with CIO engine and static asset serving"
```

---

### Task 3: 修复 SettingsViewModel - 异步启停 + Token 管理

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/settings/SettingsViewModel.kt`

- [ ] **Step 1: 添加 token 生成和 Web 服务地址字段到 State**

完整替换文件：

```kotlin
package com.dailysatori.ui.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailysatori.core.service.AppUpgradeService
import com.dailysatori.core.service.WebServerService
import com.dailysatori.data.repository.SettingRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent.get
import java.net.NetworkInterface

data class SettingsState(
    val isPageLoading: Boolean = false,
    val webServerRunning: Boolean = false,
    val isTogglingWebServer: Boolean = false,
    val webServerError: String? = null,
    val webServerAddress: String = "",
    val webServerToken: String = "",
    val isCheckingUpdate: Boolean = false,
    val updateVersion: String? = null,
    val currentVersion: String = "1.0.0",
    val isExporting: Boolean = false,
    val exportProgress: Float = 0f,
    val error: String? = null,
)

class SettingsViewModel(
    private val webServerService: WebServerService,
    private val appUpgradeService: AppUpgradeService,
) : ViewModel() {
    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    init {
        loadWebServiceInfo()
    }

    private fun loadWebServiceInfo() {
        viewModelScope.launch(Dispatchers.IO) {
            val settingRepo = get<SettingRepository>(SettingRepository::class.java)
            val token = settingRepo.get("web_server_token") ?: ""
            val address = getDeviceIp()?.let { "http://$it:8888" } ?: "http://localhost:8888"
            _state.update { it.copy(webServerToken = token, webServerAddress = address) }
        }
    }

    fun toggleWebServer() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isTogglingWebServer = true, webServerError = null) }
            try {
                if (_state.value.webServerRunning) {
                    webServerService.stop()
                    _state.update { it.copy(webServerRunning = false) }
                } else {
                    ensureToken()
                    webServerService.start()
                    val address = getDeviceIp()?.let { "http://$it:8888" } ?: "http://localhost:8888"
                    _state.update { it.copy(webServerRunning = true, webServerAddress = address) }
                }
            } catch (e: Exception) {
                _state.update { it.copy(webServerError = e.message ?: "Unknown error") }
            }
            _state.update { it.copy(isTogglingWebServer = false) }
        }
    }

    fun refreshToken() {
        viewModelScope.launch(Dispatchers.IO) {
            val newToken = generateToken()
            val settingRepo = get<SettingRepository>(SettingRepository::class.java)
            settingRepo.upsert("web_server_token", newToken)
            _state.update { it.copy(webServerToken = newToken) }
        }
    }

    private fun ensureToken() {
        val settingRepo = get<SettingRepository>(SettingRepository::class.java)
        val existing = settingRepo.get("web_server_token")
        if (existing == null) {
            val newToken = generateToken()
            settingRepo.upsert("web_server_token", newToken)
            _state.update { it.copy(webServerToken = newToken) }
        }
    }

    private fun generateToken(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        return (1..32).map { chars.random() }.joinToString("")
    }

    private fun getDeviceIp(): String? {
        return try {
            NetworkInterface.getNetworkInterfaces()?.asSequence()
                ?.flatMap { it.inetAddresses.asSequence() }
                ?.firstOrNull { !it.isLoopbackAddress && it.hostAddress?.contains(':') == false }
                ?.hostAddress
        } catch (_: Exception) { null }
    }

    fun checkUpdate() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isCheckingUpdate = true, error = null) }
            try {
                val latest = appUpgradeService.checkForUpdate(_state.value.currentVersion)
                _state.update { it.copy(isCheckingUpdate = false, updateVersion = latest) }
            } catch (e: Exception) {
                _state.update { it.copy(isCheckingUpdate = false, error = e.message) }
            }
        }
    }

    fun exportData() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isExporting = true, exportProgress = 0.5f) }
            try {
                _state.update { it.copy(isExporting = false, exportProgress = 1f) }
            } catch (e: Exception) {
                _state.update { it.copy(isExporting = false, error = e.message) }
            }
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/kotlin/com/dailysatori/ui/feature/settings/SettingsViewModel.kt
git commit -m "feat: add async web server toggle with token management"
```

---

### Task 4: 更新 SettingsScreen - 展示 Token/地址/错误

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/settings/SettingsScreen.kt`

- [ ] **Step 1: 更新 Web 服务区域和添加 Token 展示**

在 `SettingsScreen.kt` 的 Web 服务区域（约 129-141 行），替换为：

```kotlin
SettingsRow(
    icon = Icons.Default.Language,
    title = "Web 服务",
    subtitle = when {
        state.isTogglingWebServer -> "启动中..."
        state.webServerError != null -> "错误: ${state.webServerError}"
        state.webServerRunning -> state.webServerAddress
        else -> "已停止"
    },
    trailing = {
        if (state.isTogglingWebServer) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
        } else {
            Switch(
                checked = state.webServerRunning,
                onCheckedChange = { viewModel.toggleWebServer() },
            )
        }
    },
    onClick = { viewModel.toggleWebServer() },
)
```

Web 服务区域后面，添加 Token 展示行（在 Card 的 `}` 之前）：

```kotlin
if (state.webServerToken.isNotEmpty()) {
    SettingsRow(
        icon = Icons.Default.Key,
        title = "API Token",
        subtitle = state.webServerToken,
        trailing = {
            IconButton(onClick = { viewModel.refreshToken() }) {
                Icon(Icons.Default.Refresh, contentDescription = "刷新 Token")
            }
        },
        onClick = {},
    )
}
```

需要新增的 import：
```kotlin
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.unit.dp
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/kotlin/com/dailysatori/ui/feature/settings/SettingsScreen.kt
git commit -m "feat: show web server address and token in settings"
```

---

### Task 5: 下载第三方 JS 库

**Files:**
- Create: `app/src/main/assets/website/js/lib/vue.global.prod.min.js`
- Create: `app/src/main/assets/website/js/lib/marked.min.js`
- Create: `app/src/main/assets/website/js/lib/chart.umd.min.js`

- [ ] **Step 1: 创建目录并下载**

```bash
mkdir -p app/src/main/assets/website/js/lib
curl -L -o app/src/main/assets/website/js/lib/vue.global.prod.min.js https://cdn.jsdelivr.net/npm/vue@3.4.21/dist/vue.global.prod.js
curl -L -o app/src/main/assets/website/js/lib/marked.min.js https://cdn.jsdelivr.net/npm/marked@12.0.0/marked.min.js
curl -L -o app/src/main/assets/website/js/lib/chart.umd.min.js https://cdn.jsdelivr.net/npm/chart.js@4.4.0/dist/chart.umd.min.js
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/assets/website/js/lib/
git commit -m "assets: bundle vue, marked, chart.js locally"
```

---

### Task 6: 重写 Web UI HTML

**Files:**
- Write: `app/src/main/assets/website/admin.html`

- [ ] **Step 1: 编写新 admin.html**

完整的全新 SPA 入口文件。CSS 和 JS 通过本地路径加载：

```html
<!DOCTYPE html>
<html lang="zh-CN" data-theme="system">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Daily Satori - 管理后台</title>
    <link href="/website/css/base.css" rel="stylesheet">
    <link href="/website/css/layout.css" rel="stylesheet">
    <link href="/website/css/components.css" rel="stylesheet">
    <link href="/website/css/pages.css" rel="stylesheet">
</head>
<body>
    <div id="app">
        <div v-if="!isLoggedIn" class="login-page">
            <div class="login-card">
                <div class="login-header">
                    <h1>Daily Satori</h1>
                    <p>管理后台</p>
                </div>
                <form @submit.prevent="login">
                    <div class="field">
                        <label>访问密码</label>
                        <input type="password" v-model="password" placeholder="请输入密码" required autofocus>
                    </div>
                    <div v-if="loginError" class="alert alert-error">{{ loginError }}</div>
                    <button type="submit" class="btn btn-primary btn-block" :disabled="loading">
                        {{ loading ? '登录中...' : '登录' }}
                    </button>
                </form>
            </div>
        </div>

        <div v-else class="app-layout">
            <aside class="sidebar" :class="{ collapsed: sidebarCollapsed }">
                <div class="sidebar-brand" @click="navigate('dashboard')">
                    <span class="brand-icon">◆</span>
                    <span class="brand-text">Daily Satori</span>
                </div>
                <nav class="sidebar-nav">
                    <a :class="{ active: page === 'dashboard' }" @click="navigate('dashboard')" title="仪表盘">
                        <span class="nav-icon">◫</span><span class="nav-text">仪表盘</span>
                    </a>
                    <a :class="{ active: page === 'articles' }" @click="navigate('articles')" title="文章">
                        <span class="nav-icon">☰</span><span class="nav-text">文章</span>
                    </a>
                    <a :class="{ active: page === 'diary' }" @click="navigate('diary')" title="日记">
                        <span class="nav-icon">📓</span><span class="nav-text">日记</span>
                    </a>
                    <a :class="{ active: page === 'books' }" @click="navigate('books')" title="书籍">
                        <span class="nav-icon">📚</span><span class="nav-text">书籍</span>
                    </a>
                </nav>
                <div class="sidebar-footer">
                    <button class="theme-btn" @click="toggleTheme" :title="dark ? '浅色模式' : '深色模式'">
                        {{ dark ? '☀' : '☾' }}
                    </button>
                    <button class="sidebar-toggle" @click="sidebarCollapsed = !sidebarCollapsed" :title="sidebarCollapsed ? '展开' : '收起'">
                        {{ sidebarCollapsed ? '▶' : '◀' }}
                    </button>
                    <a class="sidebar-logout" @click="logout" title="退出">⇥</a>
                </div>
            </aside>

            <main class="main">
                <header class="topbar">
                    <div class="topbar-left">
                        <h2>{{ pageTitle }}</h2>
                        <span class="connection-dot" :class="{ on: connected }" :title="connected ? '已连接' : '未连接'"></span>
                    </div>
                    <div class="topbar-right">
                        <div class="search-wrap">
                            <input type="text" class="search-input" v-model="searchKeyword" @keyup.enter="doSearch" placeholder="搜索 (Ctrl+K)">
                            <kbd>Ctrl+K</kbd>
                        </div>
                    </div>
                </header>

                <div class="content">
                    <template v-if="page === 'dashboard'">
                        <!-- 仪表盘 -->
                        <div class="stats-grid">
                            <div class="stat" v-for="s in statsCards" :key="s.label">
                                <div class="stat-icon" :style="{ background: s.color }">{{ s.icon }}</div>
                                <div class="stat-body">
                                    <div class="stat-value">{{ s.value }}</div>
                                    <div class="stat-label">{{ s.label }}</div>
                                </div>
                            </div>
                        </div>
                        <div class="panels">
                            <div class="panel panel-main">
                                <div class="panel-header">
                                    <h3>📊 内容趋势</h3>
                                </div>
                                <div class="panel-body">
                                    <canvas ref="trendChart" height="240"></canvas>
                                </div>
                            </div>
                            <div class="panel panel-side">
                                <div class="panel-header">
                                    <h3>🕐 最近活动</h3>
                                </div>
                                <div class="panel-body">
                                    <div v-if="recentLoading" class="loading">加载中...</div>
                                    <div v-else-if="recentItems.length === 0" class="empty">暂无活动</div>
                                    <div v-else class="activity-list">
                                        <div v-for="item in recentItems" :key="item.type + item.id" class="activity-item">
                                            <span class="dot" :class="item.type"></span>
                                            <span class="title">{{ item.title || item.content }}</span>
                                            <span class="time">{{ formatDate(item.createdAt) }}</span>
                                        </div>
                                    </div>
                                </div>
                            </div>
                        </div>
                    </template>

                    <template v-if="page === 'articles'">
                        <div class="table-toolbar">
                            <div class="toolbar-left">
                                <span class="count">{{ pagination.totalItems }} 篇文章</span>
                            </div>
                            <div class="toolbar-right">
                                <button class="btn btn-sm" @click="showArticleModal = true">+ 添加</button>
                            </div>
                        </div>
                        <div v-if="articlesLoading" class="loading">加载中...</div>
                        <div v-else-if="articles.length === 0" class="empty">暂无文章</div>
                        <div v-else class="article-grid">
                            <div class="article-card" v-for="a in articles" :key="a.id" @click="viewArticle(a)">
                                <div class="card-cover">
                                    <img v-if="a.coverImage" :src="a.coverImage">
                                    <span v-else class="no-cover">No Image</span>
                                </div>
                                <div class="card-body">
                                    <h4>{{ a.title || a.aiTitle || '无标题' }}</h4>
                                    <p>{{ truncate(a.subTitle || a.aiContent || a.content, 120) }}</p>
                                    <div class="card-meta">
                                        <span>{{ formatDate(a.createdAt) }}</span>
                                        <span v-if="a.isFavorite" class="fav">★</span>
                                    </div>
                                </div>
                            </div>
                        </div>
                        <div class="pagination" v-if="pagination.totalPages > 1">
                            <button :disabled="pagination.page <= 1" @click="loadArticles(1)">«</button>
                            <button :disabled="pagination.page <= 1" @click="loadArticles(pagination.page - 1)">‹</button>
                            <span class="page-info">{{ pagination.page }} / {{ pagination.totalPages }}</span>
                            <button :disabled="pagination.page >= pagination.totalPages" @click="loadArticles(pagination.page + 1)">›</button>
                            <button :disabled="pagination.page >= pagination.totalPages" @click="loadArticles(pagination.totalPages)">»</button>
                        </div>
                    </template>

                    <template v-if="page === 'diary'">
                        <div class="table-toolbar">
                            <div class="toolbar-left"><span class="count">{{ diaryPagination.totalItems }} 篇日记</span></div>
                            <div class="toolbar-right">
                                <button class="btn btn-sm" @click="openDiaryEditor()">+ 添加</button>
                            </div>
                        </div>
                        <div v-if="diariesLoading" class="loading">加载中...</div>
                        <div v-else-if="diaries.length === 0" class="empty">暂无日记</div>
                        <div v-else class="diary-list">
                            <div class="diary-card" v-for="d in diaries" :key="d.id" @click="viewDiary(d)">
                                <div class="diary-date">{{ formatDate(d.createdAt) }}</div>
                                <div class="diary-preview markdown-body" v-html="formatContent(truncate(d.content, 200))"></div>
                                <div class="diary-tags" v-if="d.tags">
                                    <span class="tag" v-for="t in d.tags.split(',')" :key="t">{{ t.trim() }}</span>
                                </div>
                            </div>
                        </div>
                        <div class="pagination" v-if="diaryPagination.totalPages > 1">
                            <button :disabled="diaryPagination.page <= 1" @click="loadDiaries(1)">«</button>
                            <button :disabled="diaryPagination.page <= 1" @click="loadDiaries(diaryPagination.page - 1)">‹</button>
                            <span class="page-info">{{ diaryPagination.page }} / {{ diaryPagination.totalPages }}</span>
                            <button :disabled="diaryPagination.page >= diaryPagination.totalPages" @click="loadDiaries(diaryPagination.page + 1)">›</button>
                            <button :disabled="diaryPagination.page >= diaryPagination.totalPages" @click="loadDiaries(diaryPagination.totalPages)">»</button>
                        </div>
                    </template>

                    <template v-if="page === 'books'">
                        <div class="books-layout">
                            <div class="books-sidebar">
                                <div class="books-sidebar-header">
                                    <h3>书架</h3>
                                    <button class="btn btn-sm" @click="showAddBookModal = true">+</button>
                                </div>
                                <div class="book-list">
                                    <div v-for="(b, i) in books" :key="b.id" class="book-item" :class="{ active: currentBookIndex === i }" @click="selectBook(i)">
                                        <span>{{ b.title }}</span>
                                        <small>{{ b.author }}</small>
                                    </div>
                                </div>
                            </div>
                            <div class="books-main">
                                <div v-if="booksLoading" class="loading">加载中...</div>
                                <div v-else-if="!currentBook" class="empty">选择一本书查看</div>
                                <div v-else class="book-detail">
                                    <div class="book-info">
                                        <h3>{{ currentBook.title }}</h3>
                                        <p v-if="currentBook.author">作者: {{ currentBook.author }}</p>
                                        <span v-if="currentBook.category" class="tag">{{ currentBook.category }}</span>
                                    </div>
                                    <div class="viewpoints">
                                        <h4>读书感悟</h4>
                                        <div v-if="bookViewpointsLoading" class="loading">加载中...</div>
                                        <div v-else-if="currentBookViewpoints.length === 0" class="empty">暂无感悟</div>
                                        <div v-else class="viewpoint-list">
                                            <details v-for="vp in currentBookViewpoints" :key="vp.id" class="viewpoint-item">
                                                <summary>
                                                    <span>{{ vp.title || '无标题' }}</span>
                                                    <small>{{ formatDate(vp.createdAt) }}</small>
                                                </summary>
                                                <div class="viewpoint-body markdown-body" v-html="formatContent(vp.content)"></div>
                                            </details>
                                        </div>
                                    </div>
                                </div>
                            </div>
                        </div>
                    </template>
                </div>
            </main>
        </div>

        <!-- Modal: Article Detail -->
        <div v-if="showDetailModal" class="modal-overlay" @click.self="showDetailModal = false">
            <div class="modal modal-lg">
                <div class="modal-header">
                    <h3>文章详情</h3>
                    <button class="modal-close" @click="showDetailModal = false">&times;</button>
                </div>
                <div class="modal-body">
                    <div v-if="detailLoading" class="loading">加载中...</div>
                    <div v-else>
                        <img v-if="detailItem.coverImage" :src="detailItem.coverImage" class="detail-cover">
                        <h2>{{ detailItem.title || detailItem.aiTitle || '无标题' }}</h2>
                        <div class="detail-meta">
                            <span>{{ formatDate(detailItem.createdAt) }}</span>
                            <span v-if="detailItem.isFavorite">★ 已收藏</span>
                            <a v-if="detailItem.url" :href="detailItem.url" target="_blank">原文链接</a>
                        </div>
                        <div class="markdown-body" v-html="formatContent(detailItem.content || detailItem.aiMarkdownContent)"></div>
                    </div>
                </div>
                <div class="modal-footer">
                    <button class="btn btn-danger" @click="confirmDelete('article', detailItem.id)">删除</button>
                    <button class="btn" @click="showDetailModal = false">关闭</button>
                </div>
            </div>
        </div>

        <!-- Modal: Diary Detail -->
        <div v-if="showDiaryDetailModal" class="modal-overlay" @click.self="showDiaryDetailModal = false">
            <div class="modal modal-lg">
                <div class="modal-header">
                    <h3>日记详情</h3>
                    <button class="modal-close" @click="showDiaryDetailModal = false">&times;</button>
                </div>
                <div class="modal-body">
                    <div v-if="detailLoading" class="loading">加载中...</div>
                    <div v-else>
                        <div class="detail-meta">
                            <span>{{ formatDate(detailItem.createdAt) }}</span>
                            <span v-if="detailItem.mood">{{ detailItem.mood }}</span>
                        </div>
                        <div class="markdown-body" v-html="formatContent(detailItem.content)"></div>
                    </div>
                </div>
                <div class="modal-footer">
                    <button class="btn btn-primary" @click="editDiary(detailItem)">编辑</button>
                    <button class="btn btn-danger" @click="confirmDelete('diary', detailItem.id)">删除</button>
                    <button class="btn" @click="showDiaryDetailModal = false">关闭</button>
                </div>
            </div>
        </div>

        <!-- Modal: Diary Editor -->
        <div v-if="showDiaryEditorModal" class="modal-overlay" @click.self="showDiaryEditorModal = false">
            <div class="modal">
                <div class="modal-header">
                    <h3>{{ editingDiaryId ? '编辑' : '新建' }}日记</h3>
                    <button class="modal-close" @click="showDiaryEditorModal = false">&times;</button>
                </div>
                <form @submit.prevent="saveDiary">
                    <div class="modal-body">
                        <div class="field">
                            <label>内容</label>
                            <textarea v-model="diaryContent" rows="12" placeholder="记录今天的所见所想...&#10;支持 Markdown" required></textarea>
                        </div>
                        <div class="field">
                            <label>标签</label>
                            <input type="text" v-model="diaryTags" placeholder="逗号分隔">
                        </div>
                    </div>
                    <div class="modal-footer">
                        <button type="button" class="btn" @click="showDiaryEditorModal = false">取消</button>
                        <button type="submit" class="btn btn-primary" :disabled="savingDiary">{{ savingDiary ? '保存中...' : '保存' }}</button>
                    </div>
                </form>
            </div>
        </div>

        <!-- Modal: Add Article -->
        <div v-if="showArticleModal" class="modal-overlay" @click.self="showArticleModal = false">
            <div class="modal">
                <div class="modal-header">
                    <h3>添加文章</h3>
                    <button class="modal-close" @click="showArticleModal = false">&times;</button>
                </div>
                <form @submit.prevent="submitArticle">
                    <div class="modal-body">
                        <div class="field">
                            <label>文章 URL</label>
                            <input type="url" v-model="articleUrl" placeholder="https://..." required>
                        </div>
                    </div>
                    <div class="modal-footer">
                        <button type="button" class="btn" @click="showArticleModal = false">取消</button>
                        <button type="submit" class="btn btn-primary" :disabled="submitting">{{ submitting ? '提交中...' : '提交' }}</button>
                    </div>
                </form>
            </div>
        </div>

        <!-- Modal: Add Book -->
        <div v-if="showAddBookModal" class="modal-overlay" @click.self="showAddBookModal = false">
            <div class="modal modal-sm">
                <div class="modal-header">
                    <h3>添加书籍</h3>
                    <button class="modal-close" @click="showAddBookModal = false">&times;</button>
                </div>
                <form @submit.prevent="submitNewBook">
                    <div class="modal-body">
                        <div class="field">
                            <label>书名</label>
                            <input type="text" v-model="newBookTitle" placeholder="请输入书名" required>
                        </div>
                    </div>
                    <div class="modal-footer">
                        <button type="button" class="btn" @click="showAddBookModal = false">取消</button>
                        <button type="submit" class="btn btn-primary" :disabled="addingBook">{{ addingBook ? '添加中...' : '添加' }}</button>
                    </div>
                </form>
            </div>
        </div>

        <!-- Modal: Delete Confirm -->
        <div v-if="showDeleteModal" class="modal-overlay" @click.self="showDeleteModal = false">
            <div class="modal modal-sm">
                <div class="modal-header"><h3>确认删除</h3></div>
                <div class="modal-body"><p>确定要删除这条记录吗？此操作不可撤销。</p></div>
                <div class="modal-footer">
                    <button class="btn" @click="showDeleteModal = false">取消</button>
                    <button class="btn btn-danger" @click="executeDelete">确认删除</button>
                </div>
            </div>
        </div>

        <!-- Image Viewer -->
        <div v-if="imageViewer.visible" class="image-viewer" @click="imageViewer.visible = false">
            <img :src="imageViewer.src" @click.stop>
        </div>

        <!-- Toast -->
        <div class="toasts">
            <div v-for="t in toasts" :key="t.id" class="toast" :class="t.type">{{ t.msg }}</div>
        </div>
    </div>

    <script src="/website/js/lib/vue.global.prod.min.js"></script>
    <script src="/website/js/lib/marked.min.js"></script>
    <script src="/website/js/lib/chart.umd.min.js"></script>
    <script src="/website/js/app.js"></script>
</body>
</html>
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/assets/website/admin.html
git commit -m "feat: rewrite admin.html with new SPA structure"
```

---

### Task 7: 重写 Web UI CSS

**Files:**
- Write: `app/src/main/assets/website/css/base.css`
- Write: `app/src/main/assets/website/css/layout.css`
- Write: `app/src/main/assets/website/css/components.css`
- Write: `app/src/main/assets/website/css/pages.css`

由于篇幅较长，此处提供关键结构。完整 CSS 将在实施步骤中写入。

**base.css** — CSS 变量、重置、主题：
```css
:root {
    --bg: #fff;
    --bg-secondary: #f5f6f8;
    --bg-tertiary: #ebedf0;
    --text: #1a1a2e;
    --text-secondary: #64748b;
    --border: #e2e8f0;
    --primary: #4f46e5;
    --primary-hover: #4338ca;
    --success: #22c55e;
    --warning: #f59e0b;
    --danger: #ef4444;
    --info: #3b82f6;
    --sidebar-bg: #1e1e2e;
    --sidebar-text: #cdd6f4;
    --radius: 8px;
    --shadow: 0 1px 3px rgba(0,0,0,.08);
    --font: -apple-system, BlinkMacSystemFont, 'Segoe UI', system-ui, sans-serif;
    --font-mono: 'SF Mono', 'Fira Code', monospace;
    --transition: 0.2s ease;
}

[data-theme="dark"] {
    --bg: #111118;
    --bg-secondary: #1a1a25;
    --bg-tertiary: #252532;
    --text: #e4e4ef;
    --text-secondary: #8b8ba0;
    --border: #2e2e3e;
    --sidebar-bg: #0d0d16;
    --sidebar-text: #a6adc8;
    --shadow: 0 1px 3px rgba(0,0,0,.3);
}

@media (prefers-color-scheme: dark) {
    :root:not([data-theme="light"]) {
        --bg: #111118;
        --bg-secondary: #1a1a25;
        --bg-tertiary: #252532;
        --text: #e4e4ef;
        --text-secondary: #8b8ba0;
        --border: #2e2e3e;
        --sidebar-bg: #0d0d16;
        --sidebar-text: #a6adc8;
        --shadow: 0 1px 3px rgba(0,0,0,.3);
    }
}

*, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }
html, body { height: 100%; font-family: var(--font); font-size: 14px; color: var(--text); background: var(--bg); -webkit-font-smoothing: antialiased; }
a { color: var(--primary); text-decoration: none; cursor: pointer; }
input, textarea, select, button { font-family: inherit; font-size: inherit; }
```

**layout.css** — 整体布局：
```css
.app-layout { display: flex; height: 100vh; overflow: hidden; }

.sidebar {
    width: 220px; min-width: 220px;
    background: var(--sidebar-bg); color: var(--sidebar-text);
    display: flex; flex-direction: column;
    transition: width var(--transition), min-width var(--transition);
}
.sidebar.collapsed { width: 60px; min-width: 60px; }
.sidebar.collapsed .brand-text,
.sidebar.collapsed .nav-text { display: none; }

.sidebar-brand {
    display: flex; align-items: center; gap: 10px;
    padding: 16px; font-size: 15px; font-weight: 600; cursor: pointer;
    border-bottom: 1px solid rgba(255,255,255,.06);
}
.brand-icon { font-size: 18px; color: var(--primary); }

.sidebar-nav { flex: 1; padding: 8px; display: flex; flex-direction: column; gap: 2px; }
.sidebar-nav a {
    display: flex; align-items: center; gap: 10px;
    padding: 10px 12px; border-radius: 6px; color: var(--sidebar-text);
    transition: background var(--transition);
}
.sidebar-nav a:hover { background: rgba(255,255,255,.06); }
.sidebar-nav a.active { background: var(--primary); color: #fff; }
.nav-icon { font-size: 18px; width: 24px; text-align: center; }

.sidebar-footer {
    display: flex; align-items: center; gap: 4px; padding: 8px 12px;
    border-top: 1px solid rgba(255,255,255,.06);
}
.sidebar-footer button, .sidebar-logout {
    background: none; border: none; color: var(--sidebar-text);
    padding: 6px 8px; border-radius: 4px; cursor: pointer; font-size: 16px;
}
.sidebar-footer button:hover, .sidebar-logout:hover { background: rgba(255,255,255,.06); }

.main { flex: 1; display: flex; flex-direction: column; overflow: hidden; min-width: 0; }
.topbar {
    display: flex; align-items: center; justify-content: space-between;
    padding: 0 24px; height: 56px; border-bottom: 1px solid var(--border);
    background: var(--bg);
}
.topbar-left { display: flex; align-items: center; gap: 12px; }
.topbar-left h2 { font-size: 16px; font-weight: 600; }

.connection-dot { width: 8px; height: 8px; border-radius: 50%; background: var(--danger); }
.connection-dot.on { background: var(--success); }

.search-wrap { display: flex; align-items: center; gap: 8px; }
.search-input {
    padding: 6px 12px; border: 1px solid var(--border); border-radius: 6px;
    background: var(--bg-secondary); color: var(--text); width: 220px; outline: none;
}
.search-input:focus { border-color: var(--primary); }
.search-wrap kbd {
    padding: 2px 6px; border-radius: 4px; background: var(--bg-tertiary);
    font-size: 11px; color: var(--text-secondary); font-family: var(--font-mono);
}

.content { flex: 1; overflow-y: auto; padding: 24px; }

/* Login page */
.login-page {
    display: flex; align-items: center; justify-content: center;
    height: 100vh; background: var(--bg-secondary);
}
.login-card {
    width: 380px; padding: 40px; background: var(--bg);
    border-radius: 12px; box-shadow: var(--shadow);
}
.login-header { text-align: center; margin-bottom: 24px; }
.login-header h1 { font-size: 24px; margin-bottom: 4px; }
.login-header p { color: var(--text-secondary); }
```

**components.css** — 按钮、表格、表格、弹窗：
```css
.btn {
    display: inline-flex; align-items: center; gap: 6px;
    padding: 8px 16px; border-radius: var(--radius); border: 1px solid var(--border);
    background: var(--bg); color: var(--text); cursor: pointer; font-weight: 500;
    transition: all var(--transition);
}
.btn:hover { background: var(--bg-secondary); }
.btn-primary { background: var(--primary); color: #fff; border-color: var(--primary); }
.btn-primary:hover { background: var(--primary-hover); }
.btn-danger { background: var(--danger); color: #fff; border-color: var(--danger); }
.btn-danger:hover { opacity: 0.9; }
.btn-sm { padding: 4px 10px; font-size: 12px; }
.btn-block { width: 100%; justify-content: center; }
.btn:disabled { opacity: 0.5; cursor: not-allowed; }

.field { margin-bottom: 16px; }
.field label { display: block; margin-bottom: 6px; font-weight: 500; font-size: 13px; color: var(--text-secondary); }
.field input, .field textarea, .field select {
    width: 100%; padding: 8px 12px; border: 1px solid var(--border);
    border-radius: var(--radius); background: var(--bg); color: var(--text); outline: none;
}
.field input:focus, .field textarea:focus { border-color: var(--primary); }
.field textarea { resize: vertical; line-height: 1.6; }

.modal-overlay {
    position: fixed; inset: 0; background: rgba(0,0,0,.5);
    display: flex; align-items: center; justify-content: center; z-index: 100;
    animation: fadeIn .15s ease;
}
.modal {
    background: var(--bg); border-radius: 12px; width: 90%; max-width: 520px;
    max-height: 85vh; overflow-y: auto; box-shadow: 0 20px 60px rgba(0,0,0,.2);
    animation: slideUp .2s ease;
}
.modal-lg { max-width: 800px; }
.modal-sm { max-width: 400px; }
.modal-header {
    display: flex; align-items: center; justify-content: space-between;
    padding: 16px 20px; border-bottom: 1px solid var(--border);
}
.modal-header h3 { font-size: 15px; }
.modal-close {
    background: none; border: none; font-size: 22px; color: var(--text-secondary); cursor: pointer;
}
.modal-body { padding: 20px; }
.modal-footer {
    display: flex; justify-content: flex-end; gap: 8px;
    padding: 12px 20px; border-top: 1px solid var(--border);
}

.loading { display: flex; align-items: center; justify-content: center; padding: 40px; color: var(--text-secondary); }
.empty { text-align: center; padding: 60px 20px; color: var(--text-secondary); }

.pagination {
    display: flex; align-items: center; justify-content: center; gap: 4px; padding: 20px 0;
}
.pagination button {
    padding: 6px 12px; border: 1px solid var(--border); border-radius: var(--radius);
    background: var(--bg); color: var(--text); cursor: pointer;
}
.pagination button:disabled { opacity: 0.3; cursor: not-allowed; }
.pagination button:hover:not(:disabled) { background: var(--bg-secondary); }
.page-info { padding: 0 12px; color: var(--text-secondary); font-size: 13px; }

.toasts {
    position: fixed; top: 16px; right: 16px; z-index: 200;
    display: flex; flex-direction: column; gap: 8px;
}
.toast {
    padding: 10px 16px; border-radius: var(--radius); background: var(--bg);
    box-shadow: 0 4px 12px rgba(0,0,0,.15); font-size: 13px;
    animation: slideIn .2s ease;
}
.toast.success { border-left: 3px solid var(--success); }
.toast.error { border-left: 3px solid var(--danger); }

.image-viewer {
    position: fixed; inset: 0; background: rgba(0,0,0,.9); z-index: 300;
    display: flex; align-items: center; justify-content: center; cursor: zoom-out;
}
.image-viewer img { max-width: 90vw; max-height: 90vh; object-fit: contain; }

.markdown-body { line-height: 1.7; }
.markdown-body h1, .markdown-body h2, .markdown-body h3 { margin: 16px 0 8px; }
.markdown-body p { margin: 8px 0; }
.markdown-body code { background: var(--bg-tertiary); padding: 2px 6px; border-radius: 4px; font-size: 13px; }
.markdown-body pre { background: var(--bg-tertiary); padding: 16px; border-radius: var(--radius); overflow-x: auto; }
.markdown-body pre code { background: none; padding: 0; }
.markdown-body ul, .markdown-body ol { padding-left: 20px; }
.markdown-body blockquote { border-left: 3px solid var(--primary); padding-left: 12px; color: var(--text-secondary); margin: 12px 0; }

.tag {
    display: inline-block; padding: 2px 8px; border-radius: 12px;
    background: var(--bg-tertiary); font-size: 11px; color: var(--text-secondary);
}

.alert { padding: 10px 14px; border-radius: var(--radius); font-size: 13px; margin-bottom: 16px; }
.alert-error { background: #fef2f2; color: var(--danger); border: 1px solid #fecaca; }
[data-theme="dark"] .alert-error { background: #2d1b1b; border-color: #5c2828; }

@keyframes fadeIn { from { opacity: 0; } to { opacity: 1; } }
@keyframes slideUp { from { opacity: 0; transform: translateY(20px); } to { opacity: 1; transform: translateY(0); } }
@keyframes slideIn { from { opacity: 0; transform: translateX(20px); } to { opacity: 1; transform: translateX(0); } }
```

**pages.css** — 各页面特定样式：
```css
.stats-grid {
    display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
    gap: 16px; margin-bottom: 24px;
}
.stat {
    display: flex; align-items: center; gap: 16px;
    padding: 20px; background: var(--bg); border-radius: 12px;
    border: 1px solid var(--border);
}
.stat-icon {
    width: 48px; height: 48px; border-radius: 12px;
    display: flex; align-items: center; justify-content: center;
    font-size: 20px; color: #fff; flex-shrink: 0;
}
.stat-value { font-size: 24px; font-weight: 700; }
.stat-label { font-size: 12px; color: var(--text-secondary); }

.panels { display: grid; grid-template-columns: 1fr 320px; gap: 20px; }
.panel { background: var(--bg); border: 1px solid var(--border); border-radius: 12px; overflow: hidden; }
.panel-header { padding: 14px 20px; border-bottom: 1px solid var(--border); }
.panel-header h3 { font-size: 14px; }
.panel-body { padding: 16px 20px; }

.activity-list { display: flex; flex-direction: column; }
.activity-item {
    display: flex; align-items: center; gap: 10px;
    padding: 8px 0; border-bottom: 1px solid var(--border); font-size: 13px;
}
.activity-item:last-child { border-bottom: none; }
.activity-item .dot {
    width: 8px; height: 8px; border-radius: 50%; flex-shrink: 0;
}
.dot.article { background: var(--info); }
.dot.diary { background: var(--success); }
.dot.book { background: var(--warning); }
.activity-item .title { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.activity-item .time { color: var(--text-secondary); white-space: nowrap; font-size: 12px; }

.table-toolbar {
    display: flex; align-items: center; justify-content: space-between;
    margin-bottom: 16px;
}
.toolbar-left .count { font-size: 13px; color: var(--text-secondary); }

.article-grid {
    display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
    gap: 16px;
}
.article-card {
    background: var(--bg); border: 1px solid var(--border);
    border-radius: 12px; overflow: hidden; cursor: pointer;
    transition: box-shadow var(--transition);
}
.article-card:hover { box-shadow: var(--shadow); }
.card-cover {
    height: 140px; background: var(--bg-tertiary);
    display: flex; align-items: center; justify-content: center; overflow: hidden;
}
.card-cover img { width: 100%; height: 100%; object-fit: cover; }
.no-cover { color: var(--text-secondary); font-size: 12px; }
.card-body { padding: 14px; }
.card-body h4 { font-size: 14px; margin-bottom: 6px; }
.card-body p { font-size: 12px; color: var(--text-secondary); line-height: 1.5; margin-bottom: 10px; }
.card-meta { display: flex; gap: 12px; font-size: 11px; color: var(--text-secondary); }
.card-meta .fav { color: var(--warning); }

.diary-list { display: flex; flex-direction: column; gap: 12px; }
.diary-card {
    background: var(--bg); border: 1px solid var(--border);
    border-radius: 12px; padding: 20px; cursor: pointer;
    transition: box-shadow var(--transition);
}
.diary-card:hover { box-shadow: var(--shadow); }
.diary-date { font-size: 12px; color: var(--text-secondary); margin-bottom: 8px; }
.diary-preview { font-size: 13px; }
.diary-tags { display: flex; gap: 6px; margin-top: 10px; flex-wrap: wrap; }

.books-layout { display: flex; gap: 0; height: calc(100vh - 140px); }
.books-sidebar {
    width: 240px; min-width: 240px; border-right: 1px solid var(--border);
    background: var(--bg-secondary); overflow-y: auto;
}
.books-sidebar-header {
    display: flex; align-items: center; justify-content: space-between;
    padding: 14px 16px; border-bottom: 1px solid var(--border);
}
.books-sidebar-header h3 { font-size: 14px; }
.book-list { padding: 8px; }
.book-item {
    padding: 10px 12px; border-radius: 6px; cursor: pointer;
    display: flex; flex-direction: column; gap: 2px;
    transition: background var(--transition);
}
.book-item:hover { background: var(--bg-tertiary); }
.book-item.active { background: var(--primary); color: #fff; }
.book-item small { font-size: 11px; opacity: 0.7; }
.books-main { flex: 1; overflow-y: auto; padding: 20px; }

.book-detail h3 { font-size: 18px; margin-bottom: 8px; }
.book-detail .book-info { margin-bottom: 20px; }
.book-detail .book-info p { color: var(--text-secondary); font-size: 13px; margin: 4px 0; }
.viewpoints h4 { font-size: 14px; margin-bottom: 12px; padding-bottom: 8px; border-bottom: 1px solid var(--border); }
.viewpoint-item { margin-bottom: 8px; border: 1px solid var(--border); border-radius: 8px; overflow: hidden; }
.viewpoint-item summary {
    padding: 10px 14px; cursor: pointer; display: flex; justify-content: space-between;
    align-items: center; background: var(--bg-secondary);
}
.viewpoint-item summary small { font-size: 11px; color: var(--text-secondary); }
.viewpoint-body { padding: 14px; }

.detail-cover { max-width: 100%; max-height: 300px; border-radius: 8px; margin-bottom: 16px; }
.detail-meta { display: flex; gap: 16px; font-size: 12px; color: var(--text-secondary); margin: 8px 0 16px; }

@media (max-width: 900px) {
    .panels { grid-template-columns: 1fr; }
    .article-grid { grid-template-columns: repeat(auto-fill, minmax(240px, 1fr)); }
    .books-sidebar { width: 180px; min-width: 180px; }
}
@media (max-width: 640px) {
    .sidebar { width: 60px; min-width: 60px; }
    .sidebar .brand-text, .sidebar .nav-text { display: none; }
    .stats-grid { grid-template-columns: 1fr 1fr; }
    .books-layout { flex-direction: column; height: auto; }
    .books-sidebar { width: 100%; min-width: 100%; max-height: 200px; border-right: none; border-bottom: 1px solid var(--border); }
    .content { padding: 16px; }
}
```

- [ ] 将所有 4 个 CSS 文件写入对应路径

- [ ] **Commit:**

```bash
git add app/src/main/assets/website/css/
git commit -m "feat: rewrite web UI CSS with desktop-first design system"
```

---

### Task 8: 重写 Web UI JavaScript (app.js)

**Files:**
- Write: `app/src/main/assets/website/js/app.js`

基于任务列表，此文件为完整的 Vue 3 应用逻辑，配合新版 HTML 使用。

完整代码在此提供。

- [ ] **Step 1: 编写完整 app.js**

```javascript
const { createApp, ref, computed, onMounted, nextTick, watch } = Vue;

createApp({
    setup() {
        const isLoggedIn = ref(localStorage.getItem('ds_logged_in') === '1');
        const connected = ref(true);
        const dark = ref(initDark());
        const sidebarCollapsed = ref(localStorage.getItem('ds_sidebar') === '1');

        const password = ref('');
        const loading = ref(false);
        const loginError = ref('');
        const toasts = ref([]);
        let connInterval = null;

        const page = ref('dashboard');
        const searchKeyword = ref('');
        const pageTitles = { dashboard: '仪表盘', articles: '文章管理', diary: '日记管理', books: '书籍管理' };
        const pageTitle = computed(() => pageTitles[page.value] || '');

        function initDark() {
            const saved = localStorage.getItem('ds_theme');
            if (saved) return saved === 'dark';
            return window.matchMedia('(prefers-color-scheme: dark)').matches;
        }

        function applyTheme() {
            if (dark.value) {
                document.documentElement.setAttribute('data-theme', 'dark');
            } else {
                document.documentElement.setAttribute('data-theme', 'light');
            }
        }
        applyTheme();

        const toggleTheme = () => {
            dark.value = !dark.value;
            applyTheme();
            localStorage.setItem('ds_theme', dark.value ? 'dark' : 'light');
        };

        function showToast(msg, type = 'success') {
            const id = Date.now();
            toasts.value.push({ id, msg, type });
            setTimeout(() => { toasts.value = toasts.value.filter(t => t.id !== id); }, 3000);
        }

        async function apiReq(endpoint, options = {}) {
            const res = await fetch(`/api/v2${endpoint}`, {
                ...options,
                credentials: 'same-origin',
                headers: { 'Content-Type': 'application/json', ...options.headers }
            });
            const data = await res.json();
            if (res.status === 401 || data.code === -1) {
                if (endpoint !== '/auth/status') {
                    isLoggedIn.value = false;
                    localStorage.removeItem('ds_logged_in');
                }
                throw new Error(data.msg || 'Unauthorized');
            }
            if (data.code !== 0) throw new Error(data.msg || 'Request failed');
            return data.data;
        }

        function formatDate(s) {
            if (!s) return '-';
            const d = new Date(s);
            return d.toLocaleDateString('zh-CN') + ' ' + d.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' });
        }

        function truncate(s, n) { return !s ? '' : s.length > n ? s.substring(0, n) + '...' : s; }

        function formatContent(c) {
            if (!c) return '';
            return typeof marked !== 'undefined' ? marked.parse(c) : c.replaceAll('\n', '<br>');
        }

        async function checkConnection() {
            try {
                const r = await fetch('/ping');
                connected.value = r.ok && await r.text() === 'pong';
            } catch { connected.value = false; }
        }

        const login = async () => {
            loading.value = true; loginError.value = '';
            try {
                const res = await fetch('/api/v2/auth/login', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    credentials: 'same-origin',
                    body: JSON.stringify({ password: password.value })
                });
                const data = await res.json();
                if (res.ok && data.code === 0) {
                    isLoggedIn.value = true;
                    localStorage.setItem('ds_logged_in', '1');
                    showToast('登录成功');
                    checkConnection();
                    connInterval = setInterval(checkConnection, 10000);
                    loadPage('dashboard');
                } else { loginError.value = data.msg || '密码错误'; }
            } catch (e) { loginError.value = e.message; }
            loading.value = false;
        };

        const logout = async () => {
            try { await fetch('/api/v2/auth/logout', { method: 'POST', credentials: 'same-origin' }); } catch {}
            isLoggedIn.value = false; localStorage.removeItem('ds_logged_in'); password.value = '';
            if (connInterval) { clearInterval(connInterval); connInterval = null; }
        };

        const navigate = (p) => { page.value = p; loadPage(p); };
        const loadPage = (p) => {
            if (p === 'dashboard') loadDashboard();
            else if (p === 'articles') loadArticles();
            else if (p === 'diary') loadDiaries();
            else if (p === 'books') loadBooks();
        };

        const doSearch = () => {
            if (page.value === 'articles') loadArticles(1, true);
            else if (page.value === 'diary') loadDiaries(1, true);
        };

        // Dashboard
        const statsCards = ref([]);
        const recentItems = ref([]);
        const recentLoading = ref(false);

        async function loadDashboard() {
            try {
                const [overview, recent] = await Promise.all([
                    apiReq('/stats/overview'),
                    apiReq('/stats/recent')
                ]);
                const t = overview.totals || {};
                statsCards.value = [
                    { icon: '☰', label: '文章', value: t.articles || 0, color: '#3b82f6' },
                    { icon: '📓', label: '日记', value: t.diaries || 0, color: '#22c55e' },
                    { icon: '📚', label: '书籍', value: t.books || 0, color: '#8b5cf6' },
                    { icon: '★', label: '收藏', value: t.favoriteArticles || 0, color: '#f59e0b' },
                ];
                recentItems.value = [
                    ...(recent.articles || []), ...(recent.diaries || []), ...(recent.books || [])
                ].sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt)).slice(0, 10);
            } catch {}
            recentLoading.value = false;

            nextTick(() => drawTrendChart());
        }

        function drawTrendChart() {
            const canvas = document.querySelector('canvas[ref="trendChart"]');
            if (!canvas || typeof Chart === 'undefined') return;
            const ctx = canvas.getContext('2d');
            if (canvas._chart) canvas._chart.destroy();
            canvas._chart = new Chart(ctx, {
                type: 'line',
                data: {
                    labels: Array.from({ length: 7 }, (_, i) => {
                        const d = new Date(); d.setDate(d.getDate() - (6 - i));
                        return d.toLocaleDateString('zh-CN', { month: 'short', day: 'numeric' });
                    }),
                    datasets: [{
                        label: '文章', data: [3, 5, 2, 8, 4, 6, statsCards.value[0]?.value || 0].slice(0, 7),
                        borderColor: '#3b82f6', tension: 0.3, fill: false
                    }, {
                        label: '日记', data: [1, 2, 1, 3, 2, 1, statsCards.value[1]?.value || 0].slice(0, 7),
                        borderColor: '#22c55e', tension: 0.3, fill: false
                    }]
                },
                options: {
                    responsive: true, maintainAspectRatio: false,
                    plugins: { legend: { position: 'bottom' } }
                }
            });
        }

        // Articles
        const articles = ref([]);
        const articlesLoading = ref(false);
        const pagination = ref({ page: 1, totalPages: 1, totalItems: 0 });
        const showArticleModal = ref(false);
        const articleUrl = ref('');
        const submitting = ref(false);
        const showDetailModal = ref(false);
        const detailItem = ref({});
        const detailLoading = ref(false);

        async function loadArticles(p = 1, search = false) {
            articlesLoading.value = true;
            try {
                const ep = search && searchKeyword.value
                    ? `/articles/search?q=${encodeURIComponent(searchKeyword.value)}`
                    : `/articles?page=${p}`;
                const data = await apiReq(ep);
                articles.value = data.items || [];
                pagination.value = data.pagination || { page: 1, totalPages: 1, totalItems: 0 };
            } catch (e) { showToast(e.message, 'error'); }
            articlesLoading.value = false;
        }

        const viewArticle = async (a) => {
            showDetailModal.value = true; detailLoading.value = true;
            try { detailItem.value = await apiReq(`/articles/${a.id}`); }
            catch (e) { showToast(e.message, 'error'); showDetailModal.value = false; }
            detailLoading.value = false;
        };

        const submitArticle = async () => {
            if (!articleUrl.value.trim()) return;
            submitting.value = true;
            try {
                await apiReq('/articles', { method: 'POST', body: JSON.stringify({ url: articleUrl.value.trim() }) });
                showToast('文章添加成功');
                showArticleModal.value = false; articleUrl.value = '';
                loadArticles();
            } catch (e) { showToast(e.message, 'error'); }
            submitting.value = false;
        };

        // Diaries
        const diaries = ref([]);
        const diariesLoading = ref(false);
        const diaryPagination = ref({ page: 1, totalPages: 1, totalItems: 0 });
        const showDiaryDetailModal = ref(false);
        const showDiaryEditorModal = ref(false);
        const editingDiaryId = ref(null);
        const diaryContent = ref('');
        const diaryTags = ref('');
        const savingDiary = ref(false);

        async function loadDiaries(p = 1, search = false) {
            diariesLoading.value = true;
            try {
                const ep = search && searchKeyword.value
                    ? `/diary/search?q=${encodeURIComponent(searchKeyword.value)}`
                    : `/diary?page=${p}`;
                const data = await apiReq(ep);
                diaries.value = data.items || [];
                diaryPagination.value = data.pagination || { page: 1, totalPages: 1, totalItems: 0 };
            } catch (e) { showToast(e.message, 'error'); }
            diariesLoading.value = false;
        }

        const viewDiary = async (d) => {
            showDiaryDetailModal.value = true; detailLoading.value = true;
            try { detailItem.value = await apiReq(`/diary/${d.id}`); }
            catch (e) { showToast(e.message, 'error'); showDiaryDetailModal.value = false; }
            detailLoading.value = false;
        };

        const editDiary = (d) => {
            editingDiaryId.value = d.id;
            diaryContent.value = d.content || '';
            diaryTags.value = d.tags || '';
            showDiaryDetailModal.value = false;
            showDiaryEditorModal.value = true;
        };

        const openDiaryEditor = () => {
            editingDiaryId.value = null;
            diaryContent.value = ''; diaryTags.value = '';
            showDiaryEditorModal.value = true;
        };

        const saveDiary = async () => {
            if (!diaryContent.value.trim()) return;
            savingDiary.value = true;
            const body = JSON.stringify({ content: diaryContent.value.trim(), tags: diaryTags.value.trim() || null });
            try {
                if (editingDiaryId.value) {
                    await apiReq(`/diary/${editingDiaryId.value}`, { method: 'PUT', body });
                } else {
                    await apiReq('/diary', { method: 'POST', body });
                }
                showToast(editingDiaryId.value ? '日记已更新' : '日记已添加');
                showDiaryEditorModal.value = false;
                loadDiaries();
            } catch (e) { showToast(e.message, 'error'); }
            savingDiary.value = false;
        };

        // Books
        const books = ref([]);
        const booksLoading = ref(false);
        const currentBookIndex = ref(0);
        const currentBookViewpoints = ref([]);
        const bookViewpointsLoading = ref(false);
        const showAddBookModal = ref(false);
        const newBookTitle = ref('');
        const addingBook = ref(false);
        const currentBook = computed(() => books.value[currentBookIndex.value] || null);

        async function loadBooks() {
            booksLoading.value = true;
            try {
                const data = await apiReq('/books');
                books.value = data.items || [];
                if (books.value.length > 0) {
                    currentBookIndex.value = 0;
                    await loadViewpoints(books.value[0].id);
                }
            } catch (e) { showToast(e.message, 'error'); }
            booksLoading.value = false;
        }

        const selectBook = async (i) => {
            currentBookIndex.value = i;
            if (books.value[i]) await loadViewpoints(books.value[i].id);
        };

        async function loadViewpoints(bookId) {
            bookViewpointsLoading.value = true;
            currentBookViewpoints.value = [];
            try {
                const data = await apiReq(`/books/${bookId}/viewpoints`);
                currentBookViewpoints.value = data.items || [];
            } catch {}
            bookViewpointsLoading.value = false;
        }

        const submitNewBook = async () => {
            if (!newBookTitle.value.trim()) return;
            addingBook.value = true;
            try {
                await apiReq('/books', { method: 'POST', body: JSON.stringify({ title: newBookTitle.value.trim() }) });
                showToast('书籍已添加');
                showAddBookModal.value = false; newBookTitle.value = '';
                loadBooks();
            } catch (e) { showToast(e.message, 'error'); }
            addingBook.value = false;
        };

        // Delete
        const showDeleteModal = ref(false);
        const deleteTarget = ref({ type: '', id: 0 });
        const confirmDelete = (type, id) => { deleteTarget.value = { type, id }; showDeleteModal.value = true; };

        const executeDelete = async () => {
            const { type, id } = deleteTarget.value;
            const map = { article: 'articles', diary: 'diary', book: 'books' };
            try {
                await apiReq(`/${map[type]}/${id}`, { method: 'DELETE' });
                showToast('已删除');
                showDeleteModal.value = showDetailModal.value = showDiaryDetailModal.value = false;
                loadPage(page.value);
            } catch (e) { showToast(e.message, 'error'); }
        };

        // Image viewer
        const imageViewer = ref({ visible: false, src: '' });

        // Keyboard shortcuts
        function onKeydown(e) {
            if (e.ctrlKey && e.key === 'k') { e.preventDefault(); document.querySelector('.search-input')?.focus(); }
            if (e.key === 'Escape') {
                showDetailModal.value = showDiaryDetailModal.value = showDiaryEditorModal.value = false;
                showArticleModal.value = showAddBookModal.value = showDeleteModal.value = false;
                imageViewer.value.visible = false;
            }
            if (e.key === 'n' && e.ctrlKey && !e.target.closest('input,textarea')) {
                e.preventDefault();
                if (page.value === 'articles') showArticleModal.value = true;
                if (page.value === 'diary') openDiaryEditor();
                if (page.value === 'books') showAddBookModal.value = true;
            }
        }

        // Init
        onMounted(async () => {
            document.addEventListener('keydown', onKeydown);
            if (!isLoggedIn.value) return;
            try {
                const data = await apiReq('/auth/status');
                if (data.authenticated) {
                    checkConnection();
                    connInterval = setInterval(checkConnection, 10000);
                    loadDashboard();
                } else { isLoggedIn.value = false; localStorage.removeItem('ds_logged_in'); }
            } catch { isLoggedIn.value = false; localStorage.removeItem('ds_logged_in'); }
        });

        watch(sidebarCollapsed, v => localStorage.setItem('ds_sidebar', v ? '1' : '0'));

        return {
            isLoggedIn, connected, dark, sidebarCollapsed, toggleTheme,
            password, loading, loginError, toasts, login, logout,
            page, pageTitle, searchKeyword, doSearch, navigate,
            formatDate, truncate, formatContent,
            statsCards, recentItems, recentLoading,
            articles, articlesLoading, pagination, loadArticles, viewArticle,
            showArticleModal, articleUrl, submitting, submitArticle,
            showDetailModal, detailItem, detailLoading,
            diaries, diariesLoading, diaryPagination, loadDiaries, viewDiary,
            showDiaryDetailModal, showDiaryEditorModal, editingDiaryId,
            diaryContent, diaryTags, savingDiary, editDiary, openDiaryEditor, saveDiary,
            books, booksLoading, currentBookIndex, currentBookViewpoints, bookViewpointsLoading,
            currentBook, selectBook, loadBooks,
            showAddBookModal, newBookTitle, addingBook, submitNewBook,
            showDeleteModal, confirmDelete, executeDelete,
            imageViewer,
        };
    }
}).mount('#app');
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/assets/website/js/app.js
git commit -m "feat: rewrite web UI JavaScript with keyboard shortcuts and chart"
```

---

### Task 9: 编译验证

- [ ] **Step 1: 编译检查**

```bash
./gradlew :app:compileDebugKotlin
```

预期：编译成功，无错误。

- [ ] **Step 2: 安装到设备**

```bash
JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:installDebug
```

预期：安装成功。

- [ ] **Step 3: 启动 App**

```bash
adb shell am start -n com.dailysatori/.MainActivity
```

- [ ] **Step 4: 手动验证**

1. 进入设置 → 点击 Web 服务开关 → 应显示"启动中..."然后显示地址（不崩溃）
2. 电脑浏览器访问 `http://<设备IP>:8888` → 显示新的登录页
3. 登录后检查仪表盘、文章、日记、书籍各页面
4. 测试键盘快捷键 Ctrl+K 搜索、Ctrl+N 新建
5. 测试深色模式切换
6. 关闭 Web 服务 → 再开启 → 应正常工作
7. 使用 Token 测试 API：`curl -H "Authorization: Bearer <token>" http://<ip>:8888/api/v2/stats/overview`
```

