package com.dailysatori.ui.feature.settings

import com.dailysatori.config.McpTemplateType
import com.dailysatori.config.mcpProviders
import com.dailysatori.ui.feature.settings.mcp.McpBatchSaveResult
import com.dailysatori.ui.feature.settings.mcp.mcpBatchSaveFailureMessage
import com.dailysatori.ui.feature.settings.mcp.mcpBatchSaveResultMessage
import com.dailysatori.ui.feature.settings.mcp.selectableMcpTemplatesByType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class McpServerScreenTest {
    @Test
    fun groupsOnlyTemplatesWithoutExistingServerUrls() {
        val provider = mcpProviders.first { it.id == "glm" }
        val existingServerUrls = setOf(provider.templates.first().serverUrl)

        val grouped = selectableMcpTemplatesByType(provider, existingServerUrls)
        val expectedNormalCount = provider.templates.count {
            it.type == McpTemplateType.NORMAL && it.serverUrl !in existingServerUrls
        }
        val expectedCodingPlanCount = provider.templates.count {
            it.type == McpTemplateType.CODING_PLAN && it.serverUrl !in existingServerUrls
        }

        assertFalse(grouped.values.flatten().any { it.serverUrl in existingServerUrls })
        assertEquals(expectedNormalCount, grouped.getValue(McpTemplateType.NORMAL).size)
        assertEquals(expectedCodingPlanCount, grouped.getValue(McpTemplateType.CODING_PLAN).size)
    }

    @Test
    fun formatsBatchSaveResultMessage() {
        val result = McpBatchSaveResult(added = 2, skipped = 1)

        assertEquals("已添加 2 个 MCP，跳过 1 个已存在服务", mcpBatchSaveResultMessage(result))
    }

    @Test
    fun formatsBatchSaveFailureMessageWithoutDetails() {
        assertEquals("添加 MCP 失败，请稍后重试", mcpBatchSaveFailureMessage())
    }
}
