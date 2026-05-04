package com.dailysatori.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class McpProviderCatalogTest {
    @Test
    fun includesRequestedProviders() {
        val ids = mcpProviders.map { it.id }

        assertTrue(ids.contains("glm"))
        assertTrue(ids.contains("deepseek"))
        assertTrue(ids.contains("minimax"))
        assertTrue(ids.contains("openai"))
    }

    @Test
    fun minimaxProvidesNormalAndCodingPlanTemplates() {
        val minimax = findMcpProvider("minimax")

        assertNotNull(minimax)
        assertTrue(minimax.templates.any { it.type == McpTemplateType.NORMAL })
        assertTrue(minimax.templates.any { it.type == McpTemplateType.CODING_PLAN })
    }

    @Test
    fun glmProvidesCodingPlanMcpTemplatesOnly() {
        val glm = findMcpProvider("glm")

        assertNotNull(glm)
        assertTrue(glm.templates.all { it.type == McpTemplateType.CODING_PLAN })
        assertTrue(glm.templates.any { it.serverUrl == "https://open.bigmodel.cn/api/mcp/web_search_prime/mcp" })
        assertFalse(glm.templates.any { it.serverUrl == "https://open.bigmodel.cn/api/paas/v4" })
    }

    @Test
    fun openaiProviderDoesNotUseDocumentationUrlAsTemplate() {
        val openai = findMcpProvider("openai")

        assertNotNull(openai)
        assertFalse(openai.templates.any { it.serverUrl == "https://platform.openai.com/docs/mcp" })
    }

    @Test
    fun buildsDisplayNameFromProviderAndTemplate() {
        val provider = findMcpProvider("minimax")!!
        val template = provider.templates.first { it.id == "minimax-coding-plan" }

        assertEquals("MiniMax / Coding Plan", mcpTemplateDisplayName(provider, template))
    }

    @Test
    fun rendersConfigJsonWithApiKeyPlaceholder() {
        val provider = findMcpProvider("glm")!!
        val template = provider.templates.first { it.id == "glm-web-search" }
        val json = renderMcpConfigJson(template)

        assertTrue(json.contains("https://open.bigmodel.cn/api/mcp/web_search_prime/mcp"))
        assertTrue(json.contains("Bearer "))
        assertTrue(json.contains("\${apiKey}"))
        assertFalse(json.contains("sk-real-secret"))
    }

    @Test
    fun filtersTemplatesThatAlreadyExistByServerUrl() {
        val provider = findMcpProvider("glm")!!
        val existingUrls = setOf("https://open.bigmodel.cn/api/mcp/web_reader/mcp")
        val addable = filterNewMcpTemplates(provider.templates, existingUrls)

        assertFalse(addable.any { it.serverUrl == "https://open.bigmodel.cn/api/mcp/web_reader/mcp" })
        assertTrue(addable.any { it.serverUrl == "https://open.bigmodel.cn/api/mcp/web_search_prime/mcp" })
    }
}
