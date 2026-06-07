package com.dailysatori.service.externalfavorites

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class XOAuthCoordinatorTest {
    @Test
    fun authorizationUrlUsesPkceAndReadScopes() {
        val coordinator = XOAuthCoordinator(
            clientId = "client",
            redirectUri = "dailysatori://oauth/x",
            httpClient = null,
        )

        val url = coordinator.authorizationUrl(state = "state", codeChallenge = "challenge")

        assertTrue(url.contains("code_challenge=challenge"))
        assertTrue(url.contains("bookmark.read"))
        assertTrue(url.contains("tweet.read"))
        assertTrue(url.contains("users.read"))
        assertTrue(url.contains("offline.access"))
        assertFalse(url.contains("client_secret"))
    }

    @Test
    fun authConfigDoesNotContainClientSecret() {
        val config = XOAuthProviderConfig(clientId = "client", redirectUri = "dailysatori://oauth/x")

        assertEquals("client", config.clientId)
        assertEquals("dailysatori://oauth/x", config.redirectUri)
        assertFalse(config.toString().contains("secret", ignoreCase = true))
    }

    @Test
    fun callbackParserRequiresMatchingStateAndCode() {
        val callback = parseXOAuthCallback("dailysatori://oauth/x?code=abc&state=expected", expectedState = "expected")

        assertEquals("abc", callback.code)
        assertEquals("expected", callback.state)
    }

    @Test
    fun pkceVerifierProducesUrlSafeChallenge() {
        val verifier = xOAuthCodeVerifier(seed = "seed")
        val challenge = xOAuthCodeChallenge(verifier)

        assertTrue(verifier.length >= 43)
        assertFalse(challenge.contains("+"))
        assertFalse(challenge.contains("/"))
        assertFalse(challenge.contains("="))
    }
}
