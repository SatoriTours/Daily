package com.dailysatori.service.hotnews

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.data.repository.ExternalFavoriteSourceRepository
import com.dailysatori.service.externalfavorites.XBookmarksConnector
import com.dailysatori.service.externalfavorites.extractXAccessToken
import com.dailysatori.shared.db.DailySatoriDatabase
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import kotlinx.coroutines.*
import kotlin.test.*

class XHotNewsAuthorizationTest {
    @Test fun onlyEnabledXSourceIsReusedAndExplicitAppBearerIsRecognized() = withSources { repo ->
        repo.save(provider = "x", displayName = "disabled", accountId = "1", accountName = "one",
            authJson = """{"access_token":"disabled-token"}""", enabled = false)
        repo.save(provider = "github", displayName = "other", accountId = "2", accountName = "two",
            authJson = """{"access_token":"other-token"}""")
        HttpClient(MockEngine { error("No refresh needed") }).use { http ->
            val connector = XBookmarksConnector(http)
            assertEquals(HotNewsError.X_AUTH_REQUIRED,
                assertFailsWith<XHotNewsException> { xHotNewsAuthorization(repo, connector) }.error)
            repo.save(provider = "x", displayName = "enabled", accountId = "3", accountName = "three",
                authJson = """{"bearer_token":"app-test-token"}""")
            val auth = xHotNewsAuthorization(repo, connector)
            assertEquals("app-test-token", auth.token)
            assertFalse(auth.userContext)
            assertFalse(auth.toString().contains("app-test-token"))
        }
    }

    @Test fun concurrentConsumersRefreshOneTokenAndPersistItBeforeReleasingOwnership() = withSources { repo ->
        val sourceId = repo.save(provider = "x", displayName = "test", accountId = "1", accountName = "one",
            authJson = """{"client_id":"test-client","access_token":"expired","refresh_token":"refresh-test","expires_at":1}""")
        var requests = 0
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        HttpClient(MockEngine { request ->
            assertEquals("/2/oauth2/token", request.url.encodedPath)
            requests++
            started.complete(Unit)
            release.await()
            respond("""{"access_token":"fresh-test-token","refresh_token":"rotated-test","expires_in":7200}""")
        }).use { http ->
            val connector = XBookmarksConnector(http, sourceRepository = repo)
            coroutineScope {
                val snapshot = repo.getById(sourceId)!!
                val first = async { xHotNewsAuthorization(repo, connector) }
                started.await()
                val second = async { connector.refreshAuth(snapshot) }
                release.complete(Unit)
                assertEquals("fresh-test-token", first.await().token)
                assertEquals("fresh-test-token", extractXAccessToken(second.await().auth_json))
            }
            assertEquals(1, requests)
            assertEquals("fresh-test-token", extractXAccessToken(repo.getById(sourceId)!!.auth_json))
        }
    }

    private fun withSources(block: suspend (ExternalFavoriteSourceRepository) -> Unit) = runBlocking {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            DailySatoriDatabase.Schema.create(driver)
            block(ExternalFavoriteSourceRepository(DailySatoriDatabase(driver)))
        }
    }
}
