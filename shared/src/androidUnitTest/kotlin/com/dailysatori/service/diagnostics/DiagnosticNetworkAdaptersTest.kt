package com.dailysatori.service.diagnostics

import com.dailysatori.di.createHttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import java.net.ServerSocket
import java.util.Collections
import kotlin.concurrent.thread
import kotlin.test.*
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.serialization.json.Json

class DiagnosticNetworkAdaptersTest {
    @Test
    fun clientOwnsAndClosesItsEngine() = runBlocking<Unit> {
        val client = createHttpClient()
        val job = checkNotNull(client.engine.coroutineContext[kotlinx.coroutines.Job])
        client.close()
        kotlinx.coroutines.withTimeout(2000) { job.join() }
        assertTrue(job.isCompleted)
    }

    @Test
    fun observerDoesNotConsumeBodyAndDistinguishesHeadersFromCompletion() {
        val events = Collections.synchronizedList(mutableListOf<SafeDiagnosticEvent>())
        val diagnostics = Diagnostics(DiagnosticSink { events.add(it) })
        ServerSocket(0).use { server ->
            val worker = respond(server, "private-response-canary")
            val client = OkHttpClient.Builder().eventListenerFactory(diagnosticEventListenerFactory(DiagnosticSource.HTTP, diagnostics)).build()
            client.newCall(Request.Builder().url("http://127.0.0.1:${server.localPort}/token-canary?key=query-canary").build()).execute().use {
                assertTrue(events.any { it.eventCode == DiagnosticCode.HTTP_HEADERS })
                assertEquals("private-response-canary", it.body!!.string())
            }
            worker.join(2000)
            assertEquals(1, events.count { it.eventCode == DiagnosticCode.HTTP_START })
            assertEquals(1, events.count { it.eventCode == DiagnosticCode.HTTP_END })
            assertEquals("200", events.first { it.eventCode == DiagnosticCode.HTTP_HEADERS }.attributes["status"])
            val text = Json.encodeToString(events.toList())
            assertFalse(text.contains("canary"))
        }
    }

    @Test
    fun ktorTransportRetainsBusinessTrace() = runBlocking<Unit> {
        val events = Collections.synchronizedList(mutableListOf<SafeDiagnosticEvent>())
        val previous = DiagnosticLog.diagnostics
        DiagnosticLog.diagnostics = Diagnostics(DiagnosticSink { events.add(it) })
        try {
            ServerSocket(0).use { server ->
                val worker = respond(server, "OK")
                createHttpClient().use { client ->
                    DiagnosticLog.diagnostics.operation(DiagnosticSource.AI) {
                        assertEquals("OK", client.get("http://127.0.0.1:${server.localPort}/test").bodyAsText())
                    }
                }
                worker.join(2000)
            }
            val start = events.first { it.eventCode == DiagnosticCode.OPERATION_START }
            val requests = events.filter { it.source == DiagnosticSource.HTTP }
            assertTrue(requests.isNotEmpty())
            assertTrue(requests.all { it.traceId == start.traceId })
        } finally { DiagnosticLog.diagnostics = previous }
    }

    @Test
    fun failedConnectionHasFailureTerminalWithoutSensitiveExceptionMessage() {
        val events = mutableListOf<SafeDiagnosticEvent>()
        val port = ServerSocket(0).use { it.localPort }
        val client = OkHttpClient.Builder().retryOnConnectionFailure(false)
            .eventListenerFactory(diagnosticEventListenerFactory(DiagnosticSource.HTTP, Diagnostics(DiagnosticSink { events.add(it) }))).build()
        assertFails { client.newCall(Request.Builder().url("http://127.0.0.1:$port/private").build()).execute() }
        assertEquals(1, events.count { it.eventCode == DiagnosticCode.HTTP_FAILED })
        assertFalse(events.any { it.eventCode == DiagnosticCode.HTTP_END })
    }

    @Test
    fun redirectProducesTwoLinkedAttemptsWithoutSkippingNumbers() {
        val events = Collections.synchronizedList(mutableListOf<SafeDiagnosticEvent>())
        ServerSocket(0).use { server ->
            val worker = thread(isDaemon = true) {
                repeat(2) { attempt -> server.accept().use { socket ->
                    val reader = socket.getInputStream().bufferedReader()
                    while (!reader.readLine().isNullOrEmpty()) Unit
                    val response = if (attempt == 0) "HTTP/1.1 302 Found\r\nLocation: http://127.0.0.1:${server.localPort}/next\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
                    else "HTTP/1.1 200 OK\r\nContent-Length: 2\r\nConnection: close\r\n\r\nOK"
                    socket.getOutputStream().write(response.toByteArray())
                } }
            }
            val client = OkHttpClient.Builder().eventListenerFactory(diagnosticEventListenerFactory(DiagnosticSource.HTTP,
                Diagnostics(DiagnosticSink { events.add(it) }))).build()
            client.newCall(Request.Builder().url("http://127.0.0.1:${server.localPort}/start").build()).execute().use {
                assertEquals("OK", it.body!!.string())
            }
            worker.join(2000)
            assertEquals(listOf("1", "2"), events.filter { it.eventCode == DiagnosticCode.HTTP_START }.map { it.attributes["attempt"] })
            assertEquals(2, events.count { it.eventCode == DiagnosticCode.HTTP_END })
        }
    }

    @Test
    fun truncatedStreamAndCancellationAreNotReportedAsSuccessfulCompletion() {
        val events = Collections.synchronizedList(mutableListOf<SafeDiagnosticEvent>())
        val client = OkHttpClient.Builder().eventListenerFactory(diagnosticEventListenerFactory(DiagnosticSource.HTTP,
            Diagnostics(DiagnosticSink { events.add(it) }))).build()
        ServerSocket(0).use { server ->
            val worker = thread(isDaemon = true) {
                server.accept().use { socket ->
                    val reader = socket.getInputStream().bufferedReader()
                    while (!reader.readLine().isNullOrEmpty()) Unit
                    socket.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Length: 100\r\nConnection: close\r\n\r\nx".toByteArray())
                }
            }
            assertFails { client.newCall(Request.Builder().url("http://127.0.0.1:${server.localPort}/stream").build()).execute().use { it.body!!.string() } }
            worker.join(2000)
            assertEquals(1, events.count { it.eventCode == DiagnosticCode.HTTP_FAILED })
            assertFalse(events.any { it.eventCode == DiagnosticCode.HTTP_END })
            events.clear()
            val call = client.newCall(Request.Builder().url("http://127.0.0.1:${server.localPort}/cancel").build())
            call.cancel()
            assertFails { call.execute() }
            assertEquals(1, events.count { it.eventCode == DiagnosticCode.HTTP_CANCELLED })
            assertFalse(events.any { it.eventCode == DiagnosticCode.HTTP_END })
        }
    }

    private fun respond(server: ServerSocket, body: String) = thread(isDaemon = true) {
        server.accept().use { socket ->
            val reader = socket.getInputStream().bufferedReader()
            while (!reader.readLine().isNullOrEmpty()) Unit
            socket.getOutputStream().write(("HTTP/1.1 200 OK\r\nContent-Length: ${body.length}\r\nConnection: close\r\n\r\n$body").toByteArray())
            socket.getOutputStream().flush()
        }
    }
}
