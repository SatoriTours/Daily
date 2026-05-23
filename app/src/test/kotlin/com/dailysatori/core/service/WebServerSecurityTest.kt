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
}
