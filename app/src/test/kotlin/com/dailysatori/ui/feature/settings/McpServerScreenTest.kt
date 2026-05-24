package com.dailysatori.ui.feature.settings

import com.dailysatori.config.McpTemplateType
import com.dailysatori.config.mcpProviders
import com.dailysatori.ui.feature.settings.mcp.McpBatchSaveResult
import com.dailysatori.ui.feature.settings.mcp.McpServerUiState
import com.dailysatori.ui.feature.settings.mcp.McpServerViewModel
import com.dailysatori.ui.feature.settings.mcp.mcpBatchSaveFailureMessage
import com.dailysatori.ui.feature.settings.mcp.mcpBatchSaveResultMessage
import com.dailysatori.ui.feature.settings.mcp.mcpConnectionSuccessMessage
import com.dailysatori.ui.feature.settings.mcp.mcpConnectionValidationMessage
import com.dailysatori.ui.feature.settings.mcp.mcpTestButtonText
import com.dailysatori.ui.feature.settings.mcp.selectableMcpTemplatesByType
import java.lang.reflect.Field
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow

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

    @Test
    fun formatsMcpConnectionTestMessages() {
        assertEquals("测试连接", mcpTestButtonText(isTesting = false))
        assertEquals("测试中...", mcpTestButtonText(isTesting = true))
        assertEquals("连接成功，发现 3 个工具", mcpConnectionSuccessMessage(toolCount = 3))
        assertEquals("连接成功，未发现工具", mcpConnectionSuccessMessage(toolCount = 0))
        assertEquals("请输入服务地址", mcpConnectionValidationMessage(name = "搜索", serverUrl = ""))
        assertEquals(null, mcpConnectionValidationMessage(name = "搜索", serverUrl = "https://mcp.example.com"))
    }

    @Test
    fun clearTestMessageCancelsActiveTestAndInvalidatesResult() {
        val viewModel = unsafeMcpServerViewModel()
        val state = MutableStateFlow(
            McpServerUiState(isTesting = true, testMessage = "旧结果", testSucceeded = true),
        )
        val testJob = Job()
        viewModel.setPrivateField("_state", state)
        viewModel.setPrivateField("testJob", testJob)
        viewModel.setPrivateField("testRequestId", 7L)

        viewModel.clearTestMessage()

        assertFalse(testJob.isActive)
        assertEquals(8L, viewModel.privateField<Long>("testRequestId"))
        assertEquals(McpServerUiState(), state.value)
    }

    private fun unsafeMcpServerViewModel(): McpServerViewModel {
        val unsafe = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe").run {
            isAccessible = true
            get(null)
        }
        return unsafe.javaClass
            .getMethod("allocateInstance", Class::class.java)
            .invoke(unsafe, McpServerViewModel::class.java) as McpServerViewModel
    }

    private fun Any.setPrivateField(name: String, value: Any?) {
        privateField(name).set(this, value)
    }

    private fun Any.privateField(name: String): Field = javaClass.getDeclaredField(name).apply {
        isAccessible = true
    }

    private inline fun <reified T> Any.privateField(name: String): T = privateField(name).get(this) as T
}
