package com.dailysatori.core.service

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WebServerSecurityTest {
    @Test
    fun webServerKeepsRemoteAccessButDoesNotAllowAnyCorsOrigin() {
        val source = File("src/main/kotlin/com/dailysatori/core/service/WebServerService.kt").readText()

        assertTrue(source.contains("host = \"0.0.0.0\""))
        assertFalse(source.contains("anyHost()"))
    }

    @Test
    fun webServerDoesNotLogAuthorizationOrCookieValues() {
        val source = File("src/main/kotlin/com/dailysatori/core/service/WebServerService.kt").readText()
        val authPlugin = source.substringAfter("createApplicationPlugin(name = \"ApiAuth\")")
            .substringBefore("routing {")

        assertFalse(authPlugin.contains("authHeader"))
        assertFalse(authPlugin.contains("cookieHeader"))
        assertFalse(authPlugin.contains("auth="))
        assertFalse(authPlugin.contains("cookie="))
        assertFalse(authPlugin.contains("take(20)"))
        assertFalse(authPlugin.contains("take(30)"))
    }

    @Test
    fun webSessionCookieIsHttpOnlyAndLogoutRevokesServerSession() {
        val source = File("src/main/kotlin/com/dailysatori/core/service/WebServerService.kt").readText()

        assertTrue(source.contains("HttpOnly; SameSite=Strict"))
        assertTrue(source.contains("SessionRepository::class.java).delete(sessionId)"))
        assertTrue(source.contains("UUID.randomUUID().toString()"))
    }

    @Test
    fun articleMutationsExecuteRealApplicationActions() {
        val source = File("src/main/kotlin/com/dailysatori/core/service/WebServerService.kt").readText()

        assertTrue(source.contains("ArticleProcessingScheduler::class.java).enqueueSave(url)"))
        assertTrue(source.contains("repo.update("))
        assertTrue(source.contains("repo.toggleFavorite(id)"))
        assertTrue(source.contains("WebpageParserService::class.java).reprocessArticle(id)"))
    }

    @Test
    fun articleImagesUseRemoteFallbackAndExtensionlessFilesGetAnImageMimeType() {
        val source = File("src/main/kotlin/com/dailysatori/core/service/WebServerService.kt").readText()

        assertTrue(source.contains("articleCoverUrl(a.cover_image, a.cover_image_url)"))
        assertTrue(source.contains("detectFileContentType(file, path)"))
        assertTrue(source.contains("contentEquals(\"WEBP\".encodeToByteArray())"))
    }

    @Test
    fun diaryAndReadingRoutesUseRealRepositories() {
        val source = File("src/main/kotlin/com/dailysatori/core/service/WebServerService.kt").readText()

        assertTrue(source.contains("DiaryAttachmentRepository::class.java).getForDiary(id)"))
        assertTrue(source.contains("repo.update(id, content, tags, mood, existing.images)"))
        assertTrue(source.contains("insertAndReturnId("))
        assertTrue(source.contains("BookViewpointRepository::class.java).insert("))
        assertTrue(source.contains("repo.delete(id)"))
    }

    @Test
    fun aiRoutesReuseTheAppAgentAndPersistBothSidesOfTheConversation() {
        val source = File("src/main/kotlin/com/dailysatori/core/service/WebServerService.kt").readText()

        assertTrue(source.contains("McpAgentService::class.java).processQueryStreaming("))
        assertTrue(source.contains("repo.insert(sessionId, \"user\", query"))
        assertTrue(source.contains("role = \"assistant\""))
        assertTrue(source.contains("encodeMcpSearchResults(result.searchResults)"))
        assertTrue(source.contains("deleteBySession(sessionId)"))
    }

    @Test
    fun webDoesNotExposeRemoteNewsManagementAndKeepsTaskDataMinimal() {
        val source = File("src/main/kotlin/com/dailysatori/core/service/WebServerService.kt").readText()

        assertFalse(source.contains("get(\"/sources\")"))
        assertFalse(source.contains("post(\"/sources/{id}/toggle\")"))
        assertFalse(source.contains("put(\"payloadJson\""))
        assertFalse(source.contains("put(\"checkpointJson\""))
        assertTrue(source.contains("observeTaskCenter(AsyncTaskFilter(showTerminal = showTerminal)"))
    }
}
