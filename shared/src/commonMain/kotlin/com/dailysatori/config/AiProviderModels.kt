package com.dailysatori.config

data class AiProvider(
    val id: String,
    val name: String,
    val apiHost: String,
    val models: List<AiModel>,
    val modelDiscovery: AiModelDiscovery = AiModelDiscovery(AiModelDiscoveryProtocol.OpenAiCompatible),
)

data class AiModel(
    val id: String,
    val name: String,
)

data class AiModelDiscovery(
    val protocol: AiModelDiscoveryProtocol,
)

enum class AiModelDiscoveryProtocol {
    OpenAiCompatible,
    AnthropicCompatible,
    Gemini,
    None,
}

val aiProviders = listOf(
    AiProvider(
        id = "openai",
        name = "OpenAI",
        apiHost = "https://api.openai.com/v1",
        models = listOf(
            AiModel("gpt-5.5", "GPT-5.5 (旗舰)"),
            AiModel("gpt-5.4", "GPT-5.4"),
            AiModel("gpt-5.4-mini", "GPT-5.4 Mini"),
            AiModel("gpt-5.4-nano", "GPT-5.4 Nano"),
        ),
    ),
    AiProvider(
        id = "anthropic",
        name = "Anthropic",
        apiHost = "https://api.anthropic.com",
        models = listOf(
            AiModel("claude-opus-4-8", "Claude Opus 4.8 (旗舰)"),
            AiModel("claude-sonnet-4-6", "Claude Sonnet 4.6"),
            AiModel("claude-haiku-4-5", "Claude Haiku 4.5"),
        ),
        modelDiscovery = AiModelDiscovery(AiModelDiscoveryProtocol.AnthropicCompatible),
    ),
    AiProvider(
        id = "gemini",
        name = "Google Gemini",
        apiHost = "https://generativelanguage.googleapis.com",
        models = listOf(
            AiModel("gemini-3.1-pro-preview", "Gemini 3.1 Pro Preview"),
            AiModel("gemini-3.5-flash", "Gemini 3.5 Flash"),
            AiModel("gemini-3-flash-preview", "Gemini 3 Flash Preview"),
            AiModel("gemini-3.1-flash-lite", "Gemini 3.1 Flash-Lite"),
            AiModel("gemini-2.5-pro", "Gemini 2.5 Pro"),
            AiModel("gemini-2.5-flash", "Gemini 2.5 Flash"),
        ),
        modelDiscovery = AiModelDiscovery(AiModelDiscoveryProtocol.Gemini),
    ),
    AiProvider(
        id = "deepseek",
        name = "DeepSeek",
        apiHost = "https://api.deepseek.com",
        models = listOf(
            AiModel("deepseek-v4-flash", "DeepSeek V4 Flash"),
            AiModel("deepseek-v4-pro", "DeepSeek V4 Pro"),
            AiModel("deepseek-chat", "DeepSeek V3 (向下兼容)"),
        ),
    ),
    AiProvider(
        id = "grok",
        name = "xAI Grok",
        apiHost = "https://api.x.ai/v1",
        models = listOf(
            AiModel("grok-4.3", "Grok 4.3"),
            AiModel("grok-build-0.1", "Grok Build 0.1"),
            AiModel("grok-4-1-fast-reasoning", "Grok 4.1 Fast (推理)"),
            AiModel("grok-4-1-fast-non-reasoning", "Grok 4.1 Fast (快速)"),
        ),
    ),
    AiProvider(
        id = "mistral",
        name = "Mistral",
        apiHost = "https://api.mistral.ai/v1",
        models = listOf(
            AiModel("mistral-medium-2604", "Mistral Medium 3.5"),
            AiModel("mistral-small-2603", "Mistral Small 4"),
            AiModel("magistral-medium-2507", "Magistral Medium 1.1"),
            AiModel("codestral-2501", "Codestral"),
            AiModel("mistral-large-2512", "Mistral Large 3"),
        ),
    ),
    AiProvider(
        id = "zhipu",
        name = "ZhipuAI (GLM)",
        apiHost = "https://open.bigmodel.cn/api/paas/v4",
        models = listOf(
            AiModel("GLM-5.2", "GLM-5.2 (旗舰)"),
            AiModel("GLM-5.1", "GLM-5.1 (旗舰)"),
            AiModel("GLM-5", "GLM-5"),
            AiModel("GLM-4.7", "GLM-4.7"),
        ),
    ),
    AiProvider(
        id = "moonshot",
        name = "Moonshot (Kimi)",
        apiHost = "https://api.moonshot.cn/v1",
        models = listOf(
            AiModel("kimi-k2.5", "Kimi K2.5 (旗舰)"),
            AiModel("kimi-k2-0905-preview", "Kimi K2 Preview"),
            AiModel("kimi-k2-thinking", "Kimi K2 Thinking"),
        ),
    ),
    AiProvider(
        id = "dashscope",
        name = "Bailian 百炼 (Qwen)",
        apiHost = "https://dashscope.aliyuncs.com/compatible-mode/v1",
        models = listOf(
            AiModel("qwen3.6-plus", "Qwen3.6 Plus"),
            AiModel("qwen3.5-plus", "Qwen3.5 Plus"),
            AiModel("qwen3-flash", "Qwen3 Flash"),
        ),
    ),
    AiProvider(
        id = "doubao",
        name = "Doubao 豆包",
        apiHost = "https://ark.cn-beijing.volces.com/api/v3",
        models = listOf(
            AiModel("doubao-seed-2-0-pro-260215", "Seed 2.0 Pro"),
            AiModel("doubao-seed-2-0-lite-260215", "Seed 2.0 Lite"),
            AiModel("doubao-seed-1-8-251228", "Seed 1.8"),
        ),
    ),
    AiProvider(
        id = "minimax",
        name = "MiniMax",
        apiHost = "https://api.minimaxi.com/v1",
        models = listOf(
            AiModel("MiniMax-M3", "MiniMax M3"),
            AiModel("MiniMax-M2.7", "MiniMax M2.7"),
            AiModel("MiniMax-M2.7-highspeed", "MiniMax M2.7 HighSpeed"),
            AiModel("MiniMax-M2.5", "MiniMax M2.5"),
        ),
    ),
    AiProvider(
        id = "stepfun",
        name = "StepFun",
        apiHost = "https://api.stepfun.com/v1",
        models = listOf(
            AiModel("step-3.5-flash", "Step 3.5 Flash"),
            AiModel("step-1-flash", "Step 1 Flash"),
        ),
    ),
    AiProvider(
        id = "groq",
        name = "Groq",
        apiHost = "https://api.groq.com/openai/v1",
        models = listOf(
            AiModel("llama3-70b-8192", "LLaMA3 70B"),
            AiModel("llama3-8b-8192", "LLaMA3 8B"),
        ),
    ),
    AiProvider(
        id = "together",
        name = "Together AI",
        apiHost = "https://api.together.xyz/v1",
        models = listOf(
            AiModel("meta-llama/Llama-3.2-90B-Vision-Instruct-Turbo", "Llama 3.2 90B Vision"),
        ),
    ),
    AiProvider(
        id = "openrouter",
        name = "OpenRouter",
        apiHost = "https://openrouter.ai/api/v1",
        models = listOf(
            AiModel("google/gemini-2.5-flash-preview", "Gemini 2.5 Flash"),
            AiModel("deepseek/deepseek-chat", "DeepSeek V3"),
            AiModel("qwen/qwen-2.5-7b-instruct:free", "Qwen 2.5 7B (免费)"),
        ),
    ),
    AiProvider(
        id = "ollama",
        name = "Ollama (本地)",
        apiHost = "http://localhost:11434",
        models = emptyList(),
        modelDiscovery = AiModelDiscovery(AiModelDiscoveryProtocol.None),
    ),
    AiProvider(
        id = "lmstudio",
        name = "LM Studio (本地)",
        apiHost = "http://localhost:1234",
        models = emptyList(),
    ),
)

fun findProvider(id: String): AiProvider? = aiProviders.find { it.id == id }
