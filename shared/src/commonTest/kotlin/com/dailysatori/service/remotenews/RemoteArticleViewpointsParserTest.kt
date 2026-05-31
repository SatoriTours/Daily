package com.dailysatori.service.remotenews

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlin.test.Test
import kotlin.test.assertEquals

class RemoteArticleViewpointsParserTest {
    @Test
    fun parsesJsonArrayViewpointsAndDropsBlankEntries() {
        val element = Json.parseToJsonElement("""["观点 A", "", "  ", "观点 B"]""")

        assertEquals(listOf("观点 A", "观点 B"), remoteArticleViewpointsFromJsonElement(element))
    }

    @Test
    fun parsesJsonArrayViewpointsPreservingNonBlankWhitespace() {
        val element = Json.parseToJsonElement("""["  观点 A  ", "\t观点 B\t", " \n "]""")

        assertEquals(listOf("  观点 A  ", "\t观点 B\t"), remoteArticleViewpointsFromJsonElement(element))
    }

    @Test
    fun parsesJsonArrayViewpointsIgnoringNonPrimitiveItems() {
        val element = Json.parseToJsonElement("""["观点 A", {"text":"观点 B"}, ["观点 C"], "观点 D"]""")

        assertEquals(listOf("观点 A", "观点 D"), remoteArticleViewpointsFromJsonElement(element))
    }

    @Test
    fun parsesNewlineSeparatedStringViewpoints() {
        val element = Json.parseToJsonElement(""""观点 A\n\n 观点 B \n """")

        assertEquals(listOf("观点 A", "观点 B"), remoteArticleViewpointsFromJsonElement(element))
    }

    @Test
    fun unsupportedViewpointShapesReturnEmptyList() {
        val jsonObject = Json.parseToJsonElement("""{"text":"观点"}""")
        val jsonNull = Json.parseToJsonElement("null")

        assertEquals(emptyList(), remoteArticleViewpointsFromJsonElement(jsonObject))
        assertEquals(emptyList(), remoteArticleViewpointsFromJsonElement(jsonNull))
    }

    @Test
    fun serializerUsesParserForArrayAndStringPayloads() {
        val arrayArticle = Json.decodeFromString<RemoteArticle>(
            """{"id":1,"viewpoints":["观点 A","观点 B"]}""",
        )
        val stringArticle = Json.decodeFromString<RemoteArticle>(
            """{"id":2,"viewpoints":"观点 A\n观点 B"}""",
        )

        assertEquals(listOf("观点 A", "观点 B"), arrayArticle.viewpoints)
        assertEquals(listOf("观点 A", "观点 B"), stringArticle.viewpoints)
    }
}
