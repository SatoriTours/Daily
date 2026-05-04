package com.dailysatori.config

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

enum class McpTemplateType(val displayName: String) {
    NORMAL("普通 MCP"),
    CODING_PLAN("Coding Plan MCP"),
}

data class McpProvider(
    val id: String,
    val name: String,
    val apiKeyPlaceholder: String,
    val templates: List<McpTemplate>,
)

data class McpTemplate(
    val id: String,
    val name: String,
    val description: String,
    val type: McpTemplateType,
    val transport: String,
    val serverUrl: String,
    val command: List<String> = emptyList(),
    val env: Map<String, String> = emptyMap(),
)

private val mcpJson = Json { prettyPrint = false }

val mcpProviders: List<McpProvider> = listOf(
    McpProvider(
        id = "glm",
        name = "GLM / 智谱",
        apiKeyPlaceholder = "请输入智谱 API Key",
        templates = listOf(
            McpTemplate(
                id = "glm-web-search",
                name = "联网搜索",
                description = "GLM Coding Plan 远程搜索 MCP，提供 webSearchPrime 工具。",
                type = McpTemplateType.CODING_PLAN,
                transport = "remote",
                serverUrl = "https://open.bigmodel.cn/api/mcp/web_search_prime/mcp",
                env = mapOf("Authorization" to "Bearer \${apiKey}"),
            ),
            McpTemplate(
                id = "glm-web-reader",
                name = "网页读取",
                description = "GLM Coding Plan 远程网页读取 MCP，提供 webReader 工具。",
                type = McpTemplateType.CODING_PLAN,
                transport = "remote",
                serverUrl = "https://open.bigmodel.cn/api/mcp/web_reader/mcp",
                env = mapOf("Authorization" to "Bearer \${apiKey}"),
            ),
            McpTemplate(
                id = "glm-zread",
                name = "开源仓库读取",
                description = "GLM Coding Plan ZRead MCP，支持搜索仓库文档、结构和文件。",
                type = McpTemplateType.CODING_PLAN,
                transport = "remote",
                serverUrl = "https://open.bigmodel.cn/api/mcp/zread/mcp",
                env = mapOf("Authorization" to "Bearer \${apiKey}"),
            ),
            McpTemplate(
                id = "glm-vision",
                name = "视觉理解",
                description = "GLM Coding Plan 本地视觉 MCP，使用 @z_ai/mcp-server。",
                type = McpTemplateType.CODING_PLAN,
                transport = "local",
                serverUrl = "npx -y @z_ai/mcp-server",
                command = listOf("npx", "-y", "@z_ai/mcp-server"),
                env = mapOf("Z_AI_API_KEY" to "\${apiKey}", "Z_AI_MODE" to "ZHIPU"),
            ),
        ),
    ),
    McpProvider(
        id = "minimax",
        name = "MiniMax",
        apiKeyPlaceholder = "请输入 MiniMax API Key",
        templates = listOf(
            McpTemplate(
                id = "minimax-multimodal",
                name = "多模态生成",
                description = "MiniMax 官方 MCP，支持语音、图片、视频、音乐等能力。",
                type = McpTemplateType.NORMAL,
                transport = "local",
                serverUrl = "uvx minimax-mcp -y",
                command = listOf("uvx", "minimax-mcp", "-y"),
                env = mapOf(
                    "MINIMAX_API_KEY" to "\${apiKey}",
                    "MINIMAX_API_HOST" to "https://api.minimax.io",
                    "MINIMAX_MCP_BASE_PATH" to "",
                    "MINIMAX_API_RESOURCE_MODE" to "url",
                ),
            ),
            McpTemplate(
                id = "minimax-coding-plan",
                name = "Coding Plan",
                description = "MiniMax Coding Plan MCP，提供 web_search 和 understand_image。",
                type = McpTemplateType.CODING_PLAN,
                transport = "local",
                serverUrl = "uvx minimax-coding-plan-mcp -y",
                command = listOf("uvx", "minimax-coding-plan-mcp", "-y"),
                env = mapOf("MINIMAX_API_KEY" to "\${apiKey}", "MINIMAX_API_HOST" to "https://api.minimax.io"),
            ),
        ),
    ),
    McpProvider(
        id = "deepseek",
        name = "DeepSeek",
        apiKeyPlaceholder = "请输入 DeepSeek MCP Token",
        templates = listOf(
            McpTemplate(
                id = "deepseek-remote",
                name = "DeepSeek MCP",
                description = "DeepSeek 远程 MCP，支持模型、补全和余额等能力。",
                type = McpTemplateType.NORMAL,
                transport = "remote",
                serverUrl = "https://deepseek-mcp.ragweld.com/mcp",
                env = mapOf("Authorization" to "Bearer \${apiKey}"),
            ),
        ),
    ),
    McpProvider(
        id = "openai",
        name = "ChatGPT / OpenAI",
        apiKeyPlaceholder = "请输入 OpenAI API Key",
        templates = emptyList(),
    ),
)

fun findMcpProvider(id: String): McpProvider? = mcpProviders.find { it.id == id }

fun mcpTemplateDisplayName(provider: McpProvider, template: McpTemplate): String =
    "${provider.name} / ${template.name}"

fun renderMcpConfigJson(template: McpTemplate): String = mcpJson.encodeToString(
    buildJsonObject {
        put("transport", template.transport)
        put("url", template.serverUrl)
        putJsonArray("command") { template.command.forEach { add(it) } }
        putJsonObject("env") { template.env.forEach { (key, value) -> put(key, value) } }
    },
)

fun filterNewMcpTemplates(
    templates: List<McpTemplate>,
    existingServerUrls: Set<String>,
): List<McpTemplate> = templates.filterNot { existingServerUrls.contains(it.serverUrl) }
