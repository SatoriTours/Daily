package com.dailysatori.config

data class AiProvider(
    val id: String,
    val name: String,
    val apiHost: String,
    val models: List<AiModel>,
)

data class AiModel(
    val id: String,
    val name: String,
)

val aiProviders = listOf(
    AiProvider(
        id = "openai",
        name = "OpenAI",
        apiHost = "https://api.openai.com",
        models = listOf(
            AiModel("gpt-5.4", "GPT-5.4"),
            AiModel("gpt-5.4-pro", "GPT-5.4 Pro"),
            AiModel("gpt-5.2", "GPT-5.2"),
            AiModel("gpt-5.2-pro", "GPT-5.2 Pro"),
            AiModel("gpt-5.1", "GPT-5.1"),
            AiModel("gpt-5", "GPT-5"),
            AiModel("gpt-5-pro", "GPT-5 Pro"),
            AiModel("gpt-5-chat", "GPT-5 Chat"),
            AiModel("gpt-image-1", "GPT Image 1"),
        ),
    ),
    AiProvider(
        id = "anthropic",
        name = "Anthropic",
        apiHost = "https://api.anthropic.com",
        models = listOf(
            AiModel("claude-opus-4-6-20251101", "Claude Opus 4.6"),
            AiModel("claude-sonnet-4-6-20251101", "Claude Sonnet 4.6"),
            AiModel("claude-sonnet-4-5-20250929", "Claude Sonnet 4.5"),
            AiModel("claude-haiku-4-5-20251001", "Claude Haiku 4.5"),
            AiModel("claude-opus-4-5-20251101", "Claude Opus 4.5"),
        ),
    ),
    AiProvider(
        id = "gemini",
        name = "Google Gemini",
        apiHost = "https://generativelanguage.googleapis.com",
        models = listOf(
            AiModel("gemini-3.1-pro-preview", "Gemini 3.1 Pro Preview"),
            AiModel("gemini-3-pro-preview", "Gemini 3 Pro Preview"),
            AiModel("gemini-3-flash-preview", "Gemini 3 Flash Preview"),
            AiModel("gemini-2.5-pro", "Gemini 2.5 Pro"),
            AiModel("gemini-2.5-flash", "Gemini 2.5 Flash"),
            AiModel("gemini-2.5-flash-image-preview", "Gemini 2.5 Flash Image"),
        ),
    ),
    AiProvider(
        id = "deepseek",
        name = "DeepSeek",
        apiHost = "https://api.deepseek.com",
        models = listOf(
            AiModel("deepseek-chat", "DeepSeek Chat (V3)"),
            AiModel("deepseek-reasoner", "DeepSeek Reasoner (R1)"),
        ),
    ),
    AiProvider(
        id = "grok",
        name = "xAI Grok",
        apiHost = "https://api.x.ai",
        models = listOf(
            AiModel("grok-4", "Grok 4"),
            AiModel("grok-4-fast", "Grok 4 Fast"),
            AiModel("grok-3", "Grok 3"),
            AiModel("grok-3-fast", "Grok 3 Fast"),
        ),
    ),
    AiProvider(
        id = "mistral",
        name = "Mistral",
        apiHost = "https://api.mistral.ai",
        models = listOf(
            AiModel("mistral-large-latest", "Mistral Large"),
            AiModel("mistral-small-latest", "Mistral Small"),
            AiModel("pixtral-large-latest", "Pixtral Large"),
            AiModel("codestral-latest", "Codestral"),
            AiModel("ministral-8b-latest", "Ministral 8B"),
            AiModel("ministral-3b-latest", "Ministral 3B"),
        ),
    ),
    AiProvider(
        id = "zhipu",
        name = "ZhipuAI (GLM)",
        apiHost = "https://open.bigmodel.cn/api/paas/v4",
        models = listOf(
            AiModel("glm-5", "GLM-5"),
            AiModel("glm-4.7", "GLM-4.7"),
            AiModel("glm-4.6", "GLM-4.6"),
            AiModel("glm-4.6v", "GLM-4.6V"),
            AiModel("glm-4.6v-flash", "GLM-4.6V Flash"),
        ),
    ),
    AiProvider(
        id = "moonshot",
        name = "Moonshot (Kimi)",
        apiHost = "https://api.moonshot.cn",
        models = listOf(
            AiModel("kimi-k2.5", "Kimi K2.5"),
            AiModel("kimi-k2-0905-preview", "Kimi K2 Preview"),
            AiModel("kimi-k2-thinking", "Kimi K2 Thinking"),
        ),
    ),
    AiProvider(
        id = "dashscope",
        name = "Bailian 百炼 (Qwen)",
        apiHost = "https://dashscope.aliyuncs.com/compatible-mode/v1",
        models = listOf(
            AiModel("qwen3.5-plus", "Qwen3.5 Plus"),
            AiModel("qwen3.5-flash", "Qwen3.5 Flash"),
            AiModel("qwen3-max", "Qwen3 Max"),
        ),
    ),
    AiProvider(
        id = "doubao",
        name = "Doubao 豆包",
        apiHost = "https://ark.cn-beijing.volces.com/api/v3",
        models = listOf(
            AiModel("doubao-seed-2-0-pro-260215", "Seed 2.0 Pro"),
            AiModel("doubao-seed-2-0-lite-260215", "Seed 2.0 Lite"),
            AiModel("doubao-seed-2-0-code-preview-260215", "Seed 2.0 Code"),
        ),
    ),
    AiProvider(
        id = "minimax",
        name = "MiniMax",
        apiHost = "https://api.minimaxi.com/v1",
        models = listOf(
            AiModel("MiniMax-M2.7", "MiniMax M2.7"),
            AiModel("MiniMax-M2.7-highspeed", "MiniMax M2.7 HighSpeed"),
            AiModel("MiniMax-M2.5", "MiniMax M2.5"),
        ),
    ),
    AiProvider(
        id = "stepfun",
        name = "StepFun",
        apiHost = "https://api.stepfun.com",
        models = listOf(
            AiModel("step-1-8k", "Step 1 (8K)"),
            AiModel("step-1-flash", "Step 1 Flash"),
        ),
    ),
    AiProvider(
        id = "groq",
        name = "Groq",
        apiHost = "https://api.groq.com/openai",
        models = listOf(
            AiModel("llama3-70b-8192", "LLaMA3 70B"),
            AiModel("llama3-8b-8192", "LLaMA3 8B"),
            AiModel("mistral-saba-24b", "Mistral Saba 24B"),
        ),
    ),
    AiProvider(
        id = "together",
        name = "Together AI",
        apiHost = "https://api.together.xyz",
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
            AiModel("qwen/qwen-2.5-7b-instruct:free", "Qwen 2.5 7B (Free)"),
        ),
    ),
    AiProvider(
        id = "ollama",
        name = "Ollama (本地)",
        apiHost = "http://localhost:11434",
        models = emptyList(),
    ),
    AiProvider(
        id = "lmstudio",
        name = "LM Studio (本地)",
        apiHost = "http://localhost:1234",
        models = emptyList(),
    ),
)

fun findProvider(id: String): AiProvider? = aiProviders.find { it.id == id }
