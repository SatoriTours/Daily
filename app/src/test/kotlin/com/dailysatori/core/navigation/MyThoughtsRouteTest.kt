package com.dailysatori.core.navigation

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class MyThoughtsRouteTest {
    @Test fun selectedThoughtSurvivesRouteSerialization() {
        val encoded = "{\"thoughtKey\":\"selected-thought\"}"
        val restored = Json.decodeFromString<MyThoughtsRoute>(encoded)

        assertEquals(encoded, Json.encodeToString(restored))
    }

    @Test fun viewAllEntryRestoresWithoutASelectedThought() {
        assertEquals("{}", Json.encodeToString(Json.decodeFromString<MyThoughtsRoute>("{}")))
    }
}
