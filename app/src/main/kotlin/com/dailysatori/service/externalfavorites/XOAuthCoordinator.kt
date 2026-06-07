package com.dailysatori.service.externalfavorites

import android.content.Context
import com.dailysatori.data.repository.ExternalFavoriteSourceRepository
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.parameters
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

data class XOAuthProviderConfig(
    val clientId: String,
    val redirectUri: String,
    val authorizationUrl: String = "https://api.x.com/2/oauth2/authorize",
    val tokenUrl: String = "https://api.x.com/2/oauth2/token",
    val currentUserUrl: String = "https://api.x.com/2/users/me",
)

data class XOAuthCallback(val code: String, val state: String)

data class XOAuthPendingSession(val state: String, val codeVerifier: String)

interface XOAuthSessionStore {
    fun save(session: XOAuthPendingSession)
    fun load(): XOAuthPendingSession?
    fun clear()
}

class SharedPreferencesXOAuthSessionStore(context: Context) : XOAuthSessionStore {
    private val prefs = context.getSharedPreferences("x_oauth_session", Context.MODE_PRIVATE)

    override fun save(session: XOAuthPendingSession) {
        prefs.edit()
            .putString(KEY_STATE, session.state)
            .putString(KEY_CODE_VERIFIER, session.codeVerifier)
            .apply()
    }

    override fun load(): XOAuthPendingSession? {
        val state = prefs.getString(KEY_STATE, null)?.takeIf { it.isNotBlank() } ?: return null
        val verifier = prefs.getString(KEY_CODE_VERIFIER, null)?.takeIf { it.isNotBlank() } ?: return null
        return XOAuthPendingSession(state, verifier)
    }

    override fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val KEY_STATE = "state"
        const val KEY_CODE_VERIFIER = "code_verifier"
    }
}

class XOAuthCoordinator(
    clientId: String,
    redirectUri: String,
    private val httpClient: HttpClient? = null,
    private val sourceRepo: ExternalFavoriteSourceRepository? = null,
    private val sessionStore: XOAuthSessionStore? = null,
    private val config: XOAuthProviderConfig = XOAuthProviderConfig(clientId, redirectUri),
) {
    fun beginAuthorization(): String {
        val verifier = xOAuthCodeVerifier()
        val state = xOAuthState()
        sessionStore?.save(XOAuthPendingSession(state = state, codeVerifier = verifier))
        return authorizationUrl(state = state, codeChallenge = xOAuthCodeChallenge(verifier))
    }

    fun authorizationUrl(state: String, codeChallenge: String): String {
        require(config.clientId.isNotBlank()) { "X OAuth client id is not configured" }
        val params = linkedMapOf(
            "response_type" to "code",
            "client_id" to config.clientId,
            "redirect_uri" to config.redirectUri,
            "scope" to X_OAUTH_SCOPES.joinToString(" "),
            "state" to state,
            "code_challenge" to codeChallenge,
            "code_challenge_method" to "S256",
        )
        return config.authorizationUrl + "?" + params.entries.joinToString("&") { (key, value) ->
            "${key.urlEncoded()}=${value.urlEncoded()}"
        }
    }

    suspend fun handleCallbackUrl(callbackUrl: String): Long {
        val pending = sessionStore?.load() ?: error("X OAuth session was not started")
        return try {
            val callback = parseXOAuthCallback(callbackUrl, pending.state)
            val token = exchangeCodeForToken(callback.code, pending.codeVerifier)
            val user = fetchCurrentUser(token.accessToken)
            val repo = sourceRepo ?: error("X OAuth source repository is not configured")
            repo.save(
                provider = ExternalFavoriteProvider.X.id,
                displayName = "X @${user.username.ifBlank { user.id }}",
                accountId = user.id,
                accountName = user.username.ifBlank { user.name },
                authJson = xOAuthAuthJson(token),
                enabled = true,
            )
        } finally {
            sessionStore?.clear()
        }
    }

    suspend fun exchangeCodeForToken(code: String, codeVerifier: String): XOAuthTokenPayload {
        val client = httpClient ?: error("X OAuth coordinator requires an HttpClient to exchange tokens")
        val response = client.submitForm(
            url = config.tokenUrl,
            formParameters = parameters {
                append("grant_type", "authorization_code")
                append("code", code)
                append("client_id", config.clientId)
                append("redirect_uri", config.redirectUri)
                append("code_verifier", codeVerifier)
            },
        )
        val body = response.bodyAsText()
        if (response.status.value !in 200..299) {
            error("X OAuth token exchange failed with HTTP ${response.status.value}")
        }
        return parseXOAuthTokenPayload(body)
    }

    suspend fun fetchCurrentUser(accessToken: String): XOAuthUser {
        val client = httpClient ?: error("X OAuth coordinator requires an HttpClient to fetch the current user")
        val response = client.get(config.currentUserUrl) {
            bearerAuth(accessToken)
            parameter("user.fields", "username,name")
        }
        val body = response.bodyAsText()
        if (response.status.value !in 200..299) {
            error("X OAuth current user request failed with HTTP ${response.status.value}")
        }
        return parseXOAuthUser(body)
    }
}

data class XOAuthTokenPayload(
    val accessToken: String,
    val refreshToken: String,
    val expiresInSeconds: Long?,
    val scope: String,
    val tokenType: String,
)

data class XOAuthUser(val id: String, val username: String, val name: String)

fun parseXOAuthCallback(callbackUrl: String, expectedState: String): XOAuthCallback {
    val params = URI(callbackUrl).rawQuery.orEmpty().split("&")
        .filter { it.isNotBlank() }
        .mapNotNull { part ->
            val pieces = part.split("=", limit = 2)
            val key = pieces.getOrNull(0)?.urlDecoded() ?: return@mapNotNull null
            val value = pieces.getOrNull(1)?.urlDecoded().orEmpty()
            key to value
        }
        .toMap()
    val error = params["error"].orEmpty()
    require(error.isBlank()) { "X OAuth callback returned error: $error" }
    val code = params["code"].orEmpty()
    val state = params["state"].orEmpty()
    require(code.isNotBlank()) { "X OAuth callback missing code" }
    require(state == expectedState) { "X OAuth callback state mismatch" }
    return XOAuthCallback(code = code, state = state)
}

fun xOAuthCodeVerifier(seed: String? = null): String =
    if (seed == null) {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        bytes.base64Url()
    } else {
        MessageDigest.getInstance("SHA-256").digest(seed.toByteArray()).base64Url()
    }.padEnd(43, 'A')

fun xOAuthCodeChallenge(verifier: String): String =
    MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()).base64Url()

private fun xOAuthState(): String {
    val bytes = ByteArray(24)
    SecureRandom().nextBytes(bytes)
    return bytes.base64Url()
}

private fun parseXOAuthTokenPayload(body: String): XOAuthTokenPayload {
    val root = xOAuthJson.parseToJsonElement(body).jsonObject
    return XOAuthTokenPayload(
        accessToken = root.string("access_token"),
        refreshToken = root.string("refresh_token"),
        expiresInSeconds = root["expires_in"]?.jsonPrimitive?.contentOrNull?.toLongOrNull(),
        scope = root["scope"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        tokenType = root["token_type"]?.jsonPrimitive?.contentOrNull.orEmpty(),
    )
}

private fun parseXOAuthUser(body: String): XOAuthUser {
    val data = xOAuthJson.parseToJsonElement(body).jsonObject["data"]?.jsonObject ?: error("X user response missing data")
    return XOAuthUser(
        id = data.string("id"),
        username = data["username"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        name = data["name"]?.jsonPrimitive?.contentOrNull.orEmpty(),
    )
}

private fun xOAuthAuthJson(token: XOAuthTokenPayload): String = xOAuthJson.encodeToString(
    buildJsonObject {
        put("access_token", token.accessToken)
        if (token.refreshToken.isNotBlank()) put("refresh_token", token.refreshToken)
        token.expiresInSeconds?.let { put("expires_in", it) }
        if (token.scope.isNotBlank()) put("scope", token.scope)
        if (token.tokenType.isNotBlank()) put("token_type", token.tokenType)
    },
)

private fun kotlinx.serialization.json.JsonObject.string(key: String): String =
    this[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: error("X OAuth response missing $key")

private fun ByteArray.base64Url(): String =
    Base64.getUrlEncoder().withoutPadding().encodeToString(this)

private fun String.urlEncoded(): String =
    URLEncoder.encode(this, Charsets.UTF_8.name()).replace("+", "%20")

private fun String.urlDecoded(): String =
    URLDecoder.decode(this, Charsets.UTF_8.name())

private val xOAuthJson = Json { ignoreUnknownKeys = true; isLenient = true }

private val X_OAUTH_SCOPES = listOf("bookmark.read", "tweet.read", "users.read", "offline.access")
