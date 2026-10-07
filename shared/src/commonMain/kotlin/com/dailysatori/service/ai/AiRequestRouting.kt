package com.dailysatori.service.ai

import com.dailysatori.config.AiModel
import com.dailysatori.config.AiProvider
import com.dailysatori.config.AiRequestProtocol
import com.dailysatori.config.findProvider
import kotlinx.coroutines.CancellationException

const val OpenCodeGoProviderId = "opencode-go"
// Version of Daily Satori's gateway integration, not a fabricated coding-agent identity.
internal const val AiClientUserAgent = "DailySatori/1.0"

data class AiRequestRoute(
    val protocol: AiRequestProtocol,
    val clientBaseUrl: String,
    val endpoint: String,
)

enum class AiModelAvailability { Supported, RequiresResponses, Unknown }

fun aiModelAvailability(provider: String, modelName: String): AiModelAvailability {
    if (!provider.trim().equals(OpenCodeGoProviderId, ignoreCase = true)) return AiModelAvailability.Supported
    return when (findProvider(OpenCodeGoProviderId)?.models?.find { it.id == modelName.trim() }?.requestProtocol) {
        AiRequestProtocol.OpenAiChatCompletions, AiRequestProtocol.AnthropicMessages -> AiModelAvailability.Supported
        AiRequestProtocol.OpenAiResponses -> AiModelAvailability.RequiresResponses
        else -> AiModelAvailability.Unknown
    }
}

fun resolveAiRequestRoute(provider: String, modelName: String, apiAddress: String): AiRequestRoute {
    val providerId = provider.trim().lowercase()
    val protocol = if (providerId == OpenCodeGoProviderId) {
        require(aiModelAvailability(providerId, modelName) == AiModelAvailability.Supported) {
            "OpenCode Go: model protocol is unknown or unsupported; select an adapted model"
        }
        findProvider(providerId)!!.models.first { it.id == modelName.trim() }.requestProtocol!!
    } else when (providerId) {
        "anthropic" -> AiRequestProtocol.AnthropicMessages
        "gemini" -> AiRequestProtocol.Gemini
        else -> AiRequestProtocol.OpenAiChatCompletions
    }
    val base = apiAddress.trim().trimEnd('/')
    return when (protocol) {
        AiRequestProtocol.OpenAiChatCompletions -> AiRequestRoute(protocol, base, openAiChatCompletionEndpoint(base))
        AiRequestProtocol.AnthropicMessages -> {
            // LangChain4j 1.14 appends /messages, so its base must already include /v1.
            val clientBase = base.removeSuffix("/v1/messages").removeSuffix("/v1") + "/v1"
            AiRequestRoute(protocol, clientBase, "$clientBase/messages")
        }
        AiRequestProtocol.Gemini -> AiRequestRoute(protocol, base, base)
        AiRequestProtocol.OpenAiResponses -> error("Responses API is not implemented")
    }
}

/** Restore local protocol metadata after discovery or loading the ID/name-only cache. */
fun mergeAiModels(provider: AiProvider, discovered: List<AiModel>): List<AiModel> {
    val known = provider.models.associateBy { it.id }
    return (discovered + provider.models).distinctBy { it.id }.map { model ->
        val preset = known[model.id]
        model.copy(name = preset?.name ?: model.name, requestProtocol = preset?.requestProtocol)
    }
}

internal class OpenCodeGoRequestException(message: String) : IllegalStateException(message)

internal fun openCodeGoUnknownError() =
    OpenCodeGoRequestException("OpenCode Go request failed; check key, subscription, quota or network")

internal inline fun <T> withSafeGoResponse(headers: Map<String, String>, block: () -> T): T = try {
    block()
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (error: Exception) {
    if (!headers.containsKey("x-opencode-session") || error is OpenCodeGoRequestException) throw error
    // Neither a successful-but-malformed response nor a transport error may expose content or credentials.
    throw openCodeGoUnknownError()
}

internal fun openCodeGoHttpError(status: Int): OpenCodeGoRequestException {
    val guidance = when (status) {
        401 -> "check API key"
        403 -> "check Go subscription and access permissions"
        429 -> "usage limit reached; retry later or check your quota"
        else -> "request failed; try again later"
    }
    return OpenCodeGoRequestException("OpenCode Go HTTP $status: $guidance")
}
