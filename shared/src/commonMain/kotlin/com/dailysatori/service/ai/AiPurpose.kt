package com.dailysatori.service.ai

/** Stable persisted IDs; display names and descriptions belong to the UI's translations. */
enum class AiPurpose(val id: String) {
    INTERACTIVE("interactive"),
    EXTERNAL_CONTENT("external_content"),
    REFLECTION("reflection"),
}

/** Only emitted for providers whose DeepSeek request protocol supports these controls. */
enum class AiThinkingMode { DEFAULT, FAST, DEEP }

internal fun aiPurposeThinkingMode(
    provider: String, model: String, purpose: AiPurpose?, disableThinking: Boolean = false,
): AiThinkingMode {
    val official = provider.trim().equals("deepseek", ignoreCase = true)
    val goDeepSeek = provider.trim().equals("opencode-go", ignoreCase = true) && model.trim().startsWith("deepseek-", ignoreCase = true)
    if (!official && !goDeepSeek) return AiThinkingMode.DEFAULT
    return when (purpose) {
        AiPurpose.REFLECTION -> AiThinkingMode.DEEP
        AiPurpose.INTERACTIVE, AiPurpose.EXTERNAL_CONTENT -> AiThinkingMode.FAST
        null -> if (disableThinking && official) AiThinkingMode.FAST else AiThinkingMode.DEFAULT
    }
}
