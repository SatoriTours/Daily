package com.dailysatori.core.service

import android.app.Application
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.dailysatori.data.repository.*
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlinx.serialization.json.*
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28], manifest = Config.NONE)
class WebServerIntegrationTest {
    @Test fun privateAttachmentAliasesRequireCurrentCredentialsAndStayInsideTheirRoots() {
        val context = RuntimeEnvironment.getApplication()
        val root = context.filesDir.resolve("DailySatori")
        val family = Files.createTempDirectory(context.filesDir.toPath(), "web-fixture").toFile()
        val relativeDir = Files.createTempDirectory(root.apply { mkdirs() }.toPath(), "web-fixture").toFile()
        val outside = family.resolve("outside.txt").apply { writeText("outside fixture") }
        val attachment = relativeDir.resolve("sample.txt").apply { writeText("private fixture") }
        Files.createSymbolicLink(relativeDir.resolve("outside-link.txt").toPath(), outside.toPath())
        AndroidSqliteDriver(DailySatoriDatabase.Schema, context, null).use { driver ->
            val db = DailySatoriDatabase(driver)
            val settings = SettingRepository(db).apply { upsert("web_server_token", "local-test-only") }
            stopKoin()
            startKoin { modules(module { single { settings }; single { SessionRepository(db) } }) }
            val service = WebServerService(context)
            try {
                val port = service.start()
                assertEquals(200, rawRequest(port, "/").first)
                assertEquals(200, rawRequest(port, "/website/css/base.css").first)
                assertEquals(200 to "pong", rawRequest(port, "/ping"))
                assertEquals(200, request(port, "/api/v2/auth/status").first)
                assertEquals(200, request(port, "/api/v2/auth/login", "POST", """{"token":"local-test-only"}""").first)
                assertTrue(lastCookie.contains("Path=/"))
                val cookie = lastCookie.substringBefore(';')
                val paths = listOf("files", "images", "diary_images").map { "/$it/${relativeDir.name}/${attachment.name}" }
                paths.forEach { path ->
                    listOf(rawRequest(port, path), rawRequest(port, path, bearer = "invalid")).forEach {
                        assertEquals(401, it.first, path)
                        assertFalse("private fixture" in it.second, path)
                    }
                    assertEquals(200 to "private fixture", rawRequest(port, path, cookie = cookie))
                    assertEquals(200 to "private fixture", rawRequest(port, path, bearer = "local-test-only"))
                }
                for (prefix in listOf("files", "images", "diary_images")) {
                    val traversal = listOf("${relativeDir.name}/outside-link.txt", "%2e%2e/${family.name}/outside.txt",
                        "%2e%2e%2f${family.name}/outside.txt", "%252e%252e/${family.name}/outside.txt")
                    traversal.forEach { suffix ->
                        val response = rawRequest(port, "/$prefix/$suffix", cookie = cookie)
                        assertEquals(404, response.first, suffix)
                        assertFalse("outside fixture" in response.second)
                    }
                }
                assertEquals(200, request(port, "/api/v2/auth/logout", "POST", cookie = cookie).first)
                paths.forEach { path ->
                    val response = rawRequest(port, path, cookie = cookie)
                    assertEquals(401, response.first)
                    assertFalse("private fixture" in response.second)
                }
            } finally {
                service.stop()
                stopKoin()
                relativeDir.resolve("outside-link.txt").delete()
                relativeDir.deleteRecursively()
                family.deleteRecursively()
            }
        }
    }

    @Test fun realHttpLoginPaginationAndVersionedWrites() {
        val context = RuntimeEnvironment.getApplication()
        AndroidSqliteDriver(DailySatoriDatabase.Schema, context, null).use { driver ->
            val db = DailySatoriDatabase(driver)
            val articles = ArticleRepository(db)
            val diaries = DiaryRepository(db, driver)
            val books = BookRepository(db)
            val notes = BookViewpointRepository(db)
            val settings = SettingRepository(db)
            settings.upsert("web_server_token", "local-test-only")
            repeat(23) { articles.insert(title = "needle $it", url = "https://example.com/$it") }
            diaries.insert("journal", "生活", "平静")
            val diary = diaries.getAllSync().single()
            val bookId = books.insertAndReturnId("book", "author", "category", "", "intro")
            notes.insert(bookId, "note", "understanding", "example")
            stopKoin()
            startKoin { modules(module {
                single { articles }; single { diaries }; single { books }; single { notes }
                single { settings }; single { SessionRepository(db) }
                single { DiaryAttachmentRepository(db, driver) }
                single { UnifiedNewsSummaryRepository(db) }
                single { AsyncTaskRepository(db) }
            }) }
            val service = WebServerService(context)
            try {
                val port = service.start()
                for (path in listOf("/articles", "/diary", "/books", "/news/summary", "/tasks", "/ai/sessions", "/stats/overview", "/auth/token")) {
                    assertEquals(401, request(port, "/api/v2$path").first, path)
                }
                assertEquals(401, request(port, "/api/v2/articles", bearer = "wrong-token").first)
                assertEquals(200, request(port, "/api/v2/articles", bearer = "local-test-only").first)
                val login = request(port, "/api/v2/auth/login", "POST", """{"token":"local-test-only"}""")
                assertEquals(200, login.first)
                val cookie = lastCookie
                assertTrue(cookie.contains("HttpOnly"))
                val cookieHeader = cookie.substringBefore(';')
                val (newsStatus, news) = request(port, "/api/v2/news/summary", cookie = cookieHeader)
                assertEquals(200, newsStatus)
                assertEquals("", news.data()["summary"]!!.jsonPrimitive.content)
                val (taskStatus, tasks) = request(port, "/api/v2/tasks", cookie = cookieHeader)
                assertEquals(200, taskStatus)
                assertTrue(tasks.data()["items"]!!.jsonArray.isEmpty())
                val (_, page) = request(port, "/api/v2/articles/search?q=needle&page=2", cookie = cookieHeader)
                assertEquals(3, page.data()["items"]!!.jsonArray.size)
                assertEquals(23, page.data()["pagination"]!!.jsonObject["totalItems"]!!.jsonPrimitive.int)
                val articleId = articles.getAllSync().first().id
                val article = request(port, "/api/v2/articles/$articleId", cookie = cookieHeader).second.data()
                val version = article["version"]!!.jsonPrimitive.content
                val body = buildJsonObject { put("title", "desktop"); put("comment", "note"); put("version", version) }.toString()
                assertEquals(200, request(port, "/api/v2/articles/$articleId", "PUT", body, cookieHeader).first)
                assertEquals(409, request(port, "/api/v2/articles/$articleId", "PUT", body, cookieHeader).first)
                assertEquals("desktop", articles.getById(articleId)!!.title)
                val entry = request(port, "/api/v2/diary/${diary.id}", cookie = cookieHeader).second.data()
                val diaryBody = buildJsonObject { put("version", entry["version"]!!); put("content", "desktop journal"); put("tags", "生活"); put("mood", "平静") }.toString()
                val saved = request(port, "/api/v2/diary/${diary.id}", "PUT", diaryBody, cookieHeader)
                assertEquals(200, saved.first)
                assertEquals("desktop journal", saved.second.data()["content"]!!.jsonPrimitive.content)
                assertEquals(409, request(port, "/api/v2/diary/${diary.id}", "PUT", diaryBody, cookieHeader).first)
                val book = request(port, "/api/v2/books/$bookId", cookie = cookieHeader).second.data()
                val bookBody = buildJsonObject { put("version", book["version"]!!); put("title", "desktop book") }.toString()
                assertEquals(200, request(port, "/api/v2/books/$bookId", "PUT", bookBody, cookieHeader).first)
                assertEquals(409, request(port, "/api/v2/books/$bookId", "PUT", bookBody, cookieHeader).first)
                val noteId = notes.getByBookSync(bookId).single().id
                val note = request(port, "/api/v2/books/viewpoints/$noteId", cookie = cookieHeader).second.data()
                val noteBody = buildJsonObject { put("version", note["version"]!!); put("title", "desktop note") }.toString()
                assertEquals(200, request(port, "/api/v2/books/viewpoints/$noteId", "PUT", noteBody, cookieHeader).first)
                assertEquals(409, request(port, "/api/v2/books/viewpoints/$noteId", "PUT", noteBody, cookieHeader).first)
                assertEquals("desktop note", notes.getById(noteId)!!.title)
                assertEquals(200, request(port, "/api/v2/auth/logout", "POST", cookie = cookieHeader).first)
                assertEquals(401, request(port, "/api/v2/articles", cookie = cookieHeader).first)
                service.stop()
                assertFalse(service.isRunning())
                java.net.Socket().use { socket ->
                    assertFails { socket.connect(java.net.InetSocketAddress("127.0.0.1", port), 500) }
                }
                assertEquals(port, service.start())
            } finally { service.stop(); stopKoin() }
        }
    }

    private var lastCookie = ""
    private fun request(port: Int, path: String, method: String = "GET", body: String? = null,
        cookie: String? = null, bearer: String? = null): Pair<Int, JsonObject> {
        val (code, text) = rawRequest(port, path, method, body, cookie, bearer)
        return code to Json.parseToJsonElement(text).jsonObject
    }

    private fun rawRequest(port: Int, path: String, method: String = "GET", body: String? = null,
        cookie: String? = null, bearer: String? = null): Pair<Int, String> {
        val connection = URI("http://127.0.0.1:$port$path").toURL().openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 5000; connection.readTimeout = 5000
            connection.requestMethod = method
            cookie?.let { connection.setRequestProperty("Cookie", it) }
            bearer?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
            if (body != null) {
                connection.doOutput = true; connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body.toByteArray()) }
            }
            val code = connection.responseCode
            connection.getHeaderField("Set-Cookie")?.let { lastCookie = it }
            val text = (if (code >= 400) connection.errorStream else connection.inputStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            return code to text
        } finally { connection.disconnect() }
    }
    private fun JsonObject.data() = getValue("data").jsonObject
}
