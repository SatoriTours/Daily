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
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28], manifest = Config.NONE)
class WebServerIntegrationTest {
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
            }) }
            val service = WebServerService(context)
            try {
                val port = service.start()
                assertEquals(401, request(port, "/api/v2/articles").first)
                val login = request(port, "/api/v2/auth/login", "POST", """{"token":"local-test-only"}""")
                assertEquals(200, login.first)
                val cookie = lastCookie
                assertTrue(cookie.contains("HttpOnly"))
                val cookieHeader = cookie.substringBefore(';')
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
    private fun request(port: Int, path: String, method: String = "GET", body: String? = null, cookie: String? = null): Pair<Int, JsonObject> {
        val connection = URI("http://127.0.0.1:$port$path").toURL().openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 5000; connection.readTimeout = 5000
            connection.requestMethod = method
            cookie?.let { connection.setRequestProperty("Cookie", it) }
            if (body != null) {
                connection.doOutput = true; connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body.toByteArray()) }
            }
            val code = connection.responseCode
            connection.getHeaderField("Set-Cookie")?.let { lastCookie = it }
            val text = (if (code >= 400) connection.errorStream else connection.inputStream).bufferedReader().use { it.readText() }
            return code to Json.parseToJsonElement(text).jsonObject
        } finally { connection.disconnect() }
    }
    private fun JsonObject.data() = getValue("data").jsonObject
}
