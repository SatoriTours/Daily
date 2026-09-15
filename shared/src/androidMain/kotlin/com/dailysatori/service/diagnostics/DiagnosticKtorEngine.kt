package com.dailysatori.service.diagnostics

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.engine.okhttp.OkHttpConfig
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.util.AttributeKey
import io.ktor.utils.io.InternalAPI
import kotlinx.coroutines.withContext

private val requestTrace = AttributeKey<DiagnosticTrace>("DiagnosticTrace")

val DiagnosticTracePlugin = createClientPlugin("DiagnosticTrace") {
    onRequest { request, _ ->
        DiagnosticLog.currentTrace()?.let { request.attributes.put(requestTrace, it) }
    }
}

/** Use the factory overload so HttpClient owns and closes the delegated engine. */
object DiagnosticOkHttp : HttpClientEngineFactory<OkHttpConfig> {
    override fun create(block: OkHttpConfig.() -> Unit): HttpClientEngine = DiagnosticKtorEngine(OkHttp.create(block))
}

/** Ktor 3.1 creates a separate engine coroutine context; carry trace through request attributes. */
@OptIn(InternalAPI::class)
class DiagnosticKtorEngine(private val delegate: HttpClientEngine) : HttpClientEngine by delegate {
    override fun install(client: HttpClient) { super<HttpClientEngine>.install(client) }

    override suspend fun execute(data: HttpRequestData): HttpResponseData {
        val trace = data.attributes.getOrNull(requestTrace) ?: return delegate.execute(data)
        return withContext(DiagnosticLog.traceContext(trace)) { delegate.execute(data) }
    }
}
