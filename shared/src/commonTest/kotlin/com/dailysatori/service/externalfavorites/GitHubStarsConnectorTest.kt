package com.dailysatori.service.externalfavorites

import com.dailysatori.shared.db.External_favorite_source
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class GitHubStarsConnectorTest {
    @Test
    fun githubAiInputHashIncludesTheCurrentProcessingVersion() {
        val legacyHash = sha256Hex("456\nowner/project\nuseful repository metadata\nowner")

        assertTrue(githubAiInputHash("456", "owner/project", "useful repository metadata", "owner") != legacyHash)
    }

    @Test
    fun resolvesAuthenticatedAccountWithoutExposingToken() = runBlocking {
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    assertEquals("/user", request.url.encodedPath)
                    assertEquals("Bearer secret-token", request.headers[HttpHeaders.Authorization])
                    respond("""{"id":42,"login":"daily","name":"Daily User"}""", headers = jsonHeaders())
                }
            }
        }

        val account = GitHubStarsConnector(client).resolveAccount("secret-token")

        assertEquals(GitHubAccount("42", "daily", "Daily User"), account)
    }

    @Test
    fun richStarMetadataSkipsOneReadmeRequestPerRepository() = runBlocking {
        val requestedPaths = mutableListOf<String>()
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    requestedPaths += request.url.encodedPath
                    when (request.url.encodedPath) {
                        "/user/starred" -> respond(
                            content = STAR_RESPONSE,
                            headers = headersOf(
                                HttpHeaders.ContentType to listOf("application/json"),
                                "Link" to listOf("<https://api.github.com/user/starred?page=2>; rel=\"next\""),
                            ),
                        )
                        else -> error("Unexpected path ${request.url.encodedPath}")
                    }
                }
            }
        }

        val page = GitHubStarsConnector(client).fetchPage(gitHubSource(), pageSize = 20)
        val item = page.items.single()

        assertEquals("123", item.externalId)
        assertEquals("owner/project", item.title)
        assertEquals("https://github.com/owner/project", item.canonicalUrl)
        assertEquals(1_788_220_800_000L, item.favoritedAt)
        assertTrue(item.text.contains("主要语言：Kotlin"))
        assertTrue(hasCurrentGitHubAiProcessingVersion(item.normalizedJson))
        assertEquals(false, hasCurrentGitHubAiProcessingVersion("""{"full_name":"owner/project"}"""))
        assertEquals("2", page.nextCursor)
        assertEquals(listOf("/user/starred"), requestedPaths)
    }

    @Test
    fun sparseStarFetchesReadmeForAiSearch() = runBlocking {
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    when (request.url.encodedPath) {
                        "/user/starred" -> respond(
                            """[{"starred_at":"2026-09-01T00:00:00Z","repo":{"id":456,"full_name":"owner/sparse","html_url":"https://github.com/owner/sparse","description":"","updated_at":"2026-08-31T00:00:00Z","owner":{"login":"owner"}}}]""",
                            headers = jsonHeaders(),
                        )
                        "/repos/owner/sparse/readme" -> respond("# Sparse\nUseful setup and usage details")
                        else -> error("Unexpected path ${request.url.encodedPath}")
                    }
                }
            }
        }

        val item = GitHubStarsConnector(client).fetchPage(gitHubSource(), pageSize = 20).items.single()

        assertTrue(item.text.contains("Useful setup and usage details"))
        assertEquals(false, hasEnoughGitHubMetadata("short"))
        assertEquals(true, hasEnoughGitHubMetadata("useful repository metadata"))
    }

    @Test
    fun mapsInvalidTokenToSharedAuthFailure() = runBlocking {
        val client = HttpClient(MockEngine) {
            engine { addHandler { respond("""{"message":"Bad credentials"}""", HttpStatusCode.Unauthorized, jsonHeaders()) } }
        }

        assertFailsWith<FavoriteAuthException> {
            GitHubStarsConnector(client).resolveAccount("bad-token")
        }
        Unit
    }

    @Test
    fun validatesReplacementTokenForTheExistingAccount() = runBlocking {
        val client = HttpClient(MockEngine) {
            engine { addHandler { respond("""{"id":42,"login":"daily-new","name":"Daily User"}""", headers = jsonHeaders()) } }
        }

        val replacement = GitHubStarsConnector(client).validatedReplacementAuth(gitHubSource(), "new-token")

        assertEquals(githubAuthJson("new-token"), replacement.authJson)
        assertEquals("daily-new", replacement.accountName)
    }

    @Test
    fun rejectsReplacementTokenForAnotherAccount() = runBlocking {
        val client = HttpClient(MockEngine) {
            engine { addHandler { respond("""{"id":99,"login":"other","name":"Other"}""", headers = jsonHeaders()) } }
        }

        val error = assertFailsWith<IllegalArgumentException> {
            GitHubStarsConnector(client).validatedReplacementAuth(gitHubSource(), "new-token")
        }

        assertTrue(error.message.orEmpty().contains("当前账号"))
    }

    @Test
    fun rejectsReminderBatchIdWithoutMakingARequest() = runBlocking {
        val client = HttpClient(MockEngine) {
            engine { addHandler { error("Network must not be called for an invalid credential") } }
        }

        assertFailsWith<IllegalArgumentException> {
            GitHubStarsConnector(client).validatedReplacementAuth(gitHubSource(), "reminder_ai_123_deadbeef")
        }
        Unit
    }

    @Test
    fun rejectsTaskFieldNameWithoutMakingARequest() = runBlocking {
        val client = HttpClient(MockEngine) {
            engine { addHandler { error("Network must not be called for an invalid credential") } }
        }

        assertFailsWith<IllegalArgumentException> {
            GitHubStarsConnector(client).validatedReplacementAuth(gitHubSource(), "task_id")
        }
        Unit
    }

    @Test
    fun rejectsNumericTaskIdWithoutMakingARequest() = runBlocking {
        val client = HttpClient(MockEngine) {
            engine { addHandler { error("Network must not be called for an invalid credential") } }
        }

        assertFailsWith<IllegalArgumentException> {
            GitHubStarsConnector(client).validatedReplacementAuth(gitHubSource(), "12345")
        }
        Unit
    }

    private fun gitHubSource() = External_favorite_source(
        id = 1,
        provider = ExternalFavoriteProvider.GITHUB.id,
        display_name = "GitHub Stars",
        account_id = "42",
        account_name = "daily",
        enabled = 1,
        sync_interval_minutes = 720,
        last_sync_started_at = null,
        last_sync_completed_at = null,
        last_success_at = null,
        last_sync_window_started_at = null,
        last_items_seen_count = 0,
        last_pages_seen_count = 0,
        last_error = "",
        last_error_code = "",
        last_error_message = "",
        status = "idle",
        last_sync_mode = "",
        rate_limit_reset_at = null,
        auth_json = githubAuthJson("secret-token"),
        config_json = "{}",
        capabilities_json = "{}",
        created_at = 0,
        updated_at = 0,
    )

    private fun jsonHeaders() = headersOf(HttpHeaders.ContentType, "application/json")

    private companion object {
        val STAR_RESPONSE = """
            [{
              "starred_at":"2026-09-01T00:00:00Z",
              "repo":{
                "id":123,
                "full_name":"owner/project",
                "html_url":"https://github.com/owner/project",
                "description":"A useful project",
                "language":"Kotlin",
                "topics":["android","ai"],
                "stargazers_count":99,
                "updated_at":"2026-08-31T00:00:00Z",
                "owner":{"login":"owner"}
              }
            }]
        """.trimIndent()
    }
}
