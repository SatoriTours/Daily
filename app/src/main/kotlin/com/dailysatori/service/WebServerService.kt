package com.dailysatori.service

import co.touchlab.kermit.Logger
import com.dailysatori.config.WebServiceConfig
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.routing.*
import io.ktor.server.response.*
import io.ktor.server.plugins.cors.routing.*

class WebServerService {
    private val log = Logger.withTag("WebServer")
    private var server: ApplicationEngine? = null

    fun start() {
        if (server != null) return
        server = embeddedServer(Netty, port = WebServiceConfig.httpPort) {
            install(CORS) { anyHost() }
            routing {
                get("/ping") { call.respondText("pong") }
                get("/") { call.respondText("Daily Satori Web Server", io.ktor.http.ContentType.Text.Plain) }
            }
        }.also { it.start(wait = false) }
        log.i { "Web server started on port ${WebServiceConfig.httpPort}" }
    }

    fun stop() {
        server?.stop(1000, 2000)
        server = null
        log.i { "Web server stopped" }
    }
}
