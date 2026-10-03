package com.dailysatori.service.diary

data class SpeechProvider(
    val id: String,
    val name: String,
    val apiAddress: String,
    val models: List<String>,
    val hint: String,
    val customModels: Boolean = false,
) {
    fun newConfig(): SpeechConfig = SpeechConfig(id, models.first(), apiAddress, "")
}

val speechProviders: List<SpeechProvider> = listOf(
    // Official HTTP API; the shared Beijing domain remains supported:
    // https://help.aliyun.com/zh/model-studio/fun-asr-flash-recorded-speech-recognition-http-api
    SpeechProvider(
        "dashscope", "阿里云百炼", "https://dashscope.aliyuncs.com",
        listOf("qwen-audio-3.1-asr-flash", "qwen-audio-3.0-asr-flash", "qwen3-asr-flash"),
        "单条最长 5 分钟 · 北京地域",
    ),
    // https://platform.minimax.cn/docs/api-reference/speech-to-text
    SpeechProvider(
        "minimax", "MiniMax", "https://api.minimax.cn/v1", listOf("asr-1.0"),
        "单条最长 8 分 20 秒 · 国内地域",
    ),
    // https://docs.siliconflow.cn/docs/api/audio-transcriptions-post
    SpeechProvider(
        "siliconflow", "硅基流动", "https://api.siliconflow.cn/v1",
        listOf("FunAudioLLM/SenseVoiceSmall", "TeleAI/TeleSpeechASR"),
        "单条最长 1 小时 · 最大 50 MB",
    ),
    // https://developers.openai.com/api/docs/guides/speech-to-text
    SpeechProvider(
        "openai", "OpenAI", "https://api.openai.com/v1",
        listOf("gpt-transcribe", "gpt-4o-mini-transcribe", "gpt-4o-transcribe", "whisper-1"),
        "录音最大 25 MB",
    ),
    // https://ai.google.dev/gemini-api/docs/audio
    SpeechProvider(
        "gemini", "Google Gemini", "https://generativelanguage.googleapis.com",
        listOf("gemini-2.5-flash", "gemini-2.5-pro"), "录音最大 20 MB", customModels = true,
    ),
    SpeechProvider(
        "compatible", "自定义（OpenAI 兼容）", "https://api.openai.com/v1", listOf("whisper-1"),
        "填写支持音频转写接口的服务地址、模型名称和对应 API Key。", customModels = true,
    ),
)

// Keep the compatible protocol for existing saved configurations, while the settings
// page offers only providers with built-in, documented endpoints.
val speechSettingsProviders: List<SpeechProvider> = speechProviders.filterNot { it.id == "compatible" }

fun speechModelDisplayName(model: String): String = when (model) {
    "qwen-audio-3.1-asr-flash" -> "千问 Audio 3.1 Flash"
    "qwen-audio-3.0-asr-flash" -> "千问 Audio 3.0 Flash"
    "qwen3-asr-flash" -> "千问 3 ASR Flash"
    "asr-1.0" -> "MiniMax ASR 1.0"
    "FunAudioLLM/SenseVoiceSmall" -> "SenseVoice Small"
    "TeleAI/TeleSpeechASR" -> "TeleSpeech ASR"
    "gpt-transcribe" -> "GPT Transcribe"
    "gpt-4o-mini-transcribe" -> "GPT-4o Mini Transcribe"
    "gpt-4o-transcribe" -> "GPT-4o Transcribe"
    "whisper-1" -> "Whisper 1"
    "gemini-2.5-flash" -> "Gemini 2.5 Flash"
    "gemini-2.5-pro" -> "Gemini 2.5 Pro"
    else -> model
}

fun SpeechProvider.modelHint(model: String): String = when {
    id == "dashscope" && model == "qwen3-asr-flash" -> "单条最长 3 分钟 · 北京地域"
    id == "openai" && model != "gpt-transcribe" -> "录音最大 25 MB · 此模型将于 2027 年 2 月 26 日停用"
    else -> hint
}
