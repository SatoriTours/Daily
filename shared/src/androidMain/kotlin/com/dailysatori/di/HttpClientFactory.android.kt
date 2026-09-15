package com.dailysatori.di

import com.dailysatori.service.diagnostics.DiagnosticOkHttp
import com.dailysatori.service.diagnostics.DiagnosticTracePlugin
import com.dailysatori.service.diagnostics.DiagnosticSource
import com.dailysatori.service.diagnostics.diagnosticEventListenerFactory
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

actual fun createHttpClient(): HttpClient {
    return HttpClient(DiagnosticOkHttp) {
        engine { config { eventListenerFactory(diagnosticEventListenerFactory(DiagnosticSource.HTTP)) } }
        install(DiagnosticTracePlugin)
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = 10_000
        }
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
                prettyPrint = false
            })
        }
    }
}
