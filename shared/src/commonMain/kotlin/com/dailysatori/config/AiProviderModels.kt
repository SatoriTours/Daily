package com.dailysatori.config

data class AiModelPreset(
    val id: String,
    val displayName: String,
    val provider: String,
    val apiAddress: String,
    val modelName: String,
)

val modelPresets = listOf(
    AiModelPreset("openai-gpt-4.1", "GPT-4.1 · OpenAI", "openai", "https://api.openai.com", "gpt-4.1"),
    AiModelPreset("openai-gpt-4o", "GPT-4o · OpenAI", "openai", "https://api.openai.com", "gpt-4o"),
    AiModelPreset("openai-gpt-4o-mini", "GPT-4o Mini · OpenAI", "openai", "https://api.openai.com", "gpt-4o-mini"),
    AiModelPreset("openai-o4-mini", "o4-mini · OpenAI", "openai", "https://api.openai.com", "o4-mini"),
    AiModelPreset("openai-o3-mini", "o3-mini · OpenAI", "openai", "https://api.openai.com", "o3-mini"),
    AiModelPreset("anthropic-claude-opus-4.1", "Claude Opus 4.1 · Anthropic", "anthropic", "https://api.anthropic.com", "claude-opus-4-1-20250304"),
    AiModelPreset("anthropic-claude-sonnet-4", "Claude Sonnet 4 · Anthropic", "anthropic", "https://api.anthropic.com", "claude-sonnet-4-20250514"),
    AiModelPreset("anthropic-claude-haiku-3.5", "Claude Haiku 3.5 · Anthropic", "anthropic", "https://api.anthropic.com", "claude-3-5-haiku-20241022"),
    AiModelPreset("deepseek-chat", "DeepSeek Chat (V3) · DeepSeek", "deepseek", "https://api.deepseek.com", "deepseek-chat"),
    AiModelPreset("deepseek-reasoner", "DeepSeek Reasoner (R1) · DeepSeek", "deepseek", "https://api.deepseek.com", "deepseek-reasoner"),
    AiModelPreset("grok-4.1-fast", "Grok 4.1 Fast · xAI", "grok", "https://api.x.ai", "grok-4.1-fast"),
    AiModelPreset("gemini-2.5-flash", "Gemini 2.5 Flash · Google", "gemini", "https://generativelanguage.googleapis.com", "gemini-2.5-flash"),
    AiModelPreset("gemini-2.5-pro", "Gemini 2.5 Pro · Google", "gemini", "https://generativelanguage.googleapis.com", "gemini-2.5-pro"),
    AiModelPreset("mistral-large", "Mistral Large · Mistral", "mistral", "https://api.mistral.ai", "mistral-large-latest"),
    AiModelPreset("kimi-k2", "Kimi K2 · Moonshot", "kimi", "https://api.moonshot.cn", "kimi-k2-0905-preview"),
    AiModelPreset("glm-4-flash", "GLM-4 Flash · ZhipuAI", "glm", "https://open.bigmodel.cn", "glm-4-flash"),
    AiModelPreset("glm-4", "GLM-4 · ZhipuAI", "glm", "https://open.bigmodel.cn", "glm-4-0520"),
    AiModelPreset("ollama-custom", "自定义 · Ollama (本地)", "ollama", "http://localhost:11434", ""),
    AiModelPreset("custom", "自定义 · 自建服务", "openai", "", ""),
)
