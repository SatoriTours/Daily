package com.dailysatori.service.diary

import com.dailysatori.service.ai.AiConfigService
import com.dailysatori.service.ai.AiService

class DiaryAssistantService(
    private val knowledgeEnricher: DiaryKnowledgeEnricher,
    private val linkExtractor: DiaryLinkContentExtractor,
    private val summarize: suspend (prompt: String, systemPrompt: String) -> String,
) {
    suspend fun run(request: DiaryAssistantRequest): DiaryAssistantResult = when (request.url) {
        null -> knowledgeEnricher.enrich(request)
        else -> summarizeLink(request.copy(url = normalizeDiaryAssistantUrl(request.url)))
    }

    private suspend fun summarizeLink(request: DiaryAssistantRequest): DiaryAssistantResult {
        val url = requireNotNull(request.url)
        val material = linkExtractor.extract(url)
        val response = summarize(
            buildDiaryLinkPrompt(request, material),
            "你是 Daily Satori 的网页内容助手。只根据提供的网页材料补充内容，不要编造来源。",
        )
        val parsed = response.toDiaryAssistantParsed(emptyList())
        val original = DiaryAssistantSource(material.title.ifBlank { url }, url)
        val sources = capDiaryAssistantSources(listOf(original) + parsed.sources)
        return DiaryAssistantResult(
            content = renderDiaryAssistantMarkdown(parsed.content, sources),
            sources = sources,
            verification = DiaryAssistantVerification.PAGE_EXTRACTED,
            extraction = material.extraction,
            warnings = material.warnings,
        )
    }
}

fun diaryAssistantCompletion(
    aiConfigService: AiConfigService,
    aiService: AiService,
): suspend (prompt: String, systemPrompt: String) -> String = { prompt, systemPrompt ->
    val config = aiConfigService.getDefaultConfig()
        ?: throw IllegalStateException("AI config not set")
    if (config.api_address.isBlank() || config.api_token.isBlank() || config.model_name.isBlank()) {
        throw IllegalStateException("AI config not set")
    }
    aiService.complete(
        prompt = prompt,
        apiAddress = config.api_address.trim().trimEnd('/'),
        apiToken = config.api_token.trim(),
        modelName = config.model_name.trim(),
        provider = config.provider.trim(),
        systemPrompt = systemPrompt,
        temperature = 0.2,
    )
}

private fun buildDiaryLinkPrompt(request: DiaryAssistantRequest, material: DiaryLinkMaterial): String = buildString {
    append(buildDiaryLinkSummaryPrompt(request))
    append("\n\n网页标题：${material.title}")
    material.author?.takeIf(String::isNotBlank)?.let { append("\n作者：$it") }
    append("\n网页材料：\n${material.text}")
}

internal fun String.toDiaryAssistantParsed(fallbackSources: List<DiaryAssistantSource>): DiaryAssistantParsedAi {
    if (isBlank()) throw IllegalStateException("AI returned empty response")
    return parseDiaryAssistantAiResponse(this, fallbackSources).also {
        if (it.content.isBlank()) throw IllegalStateException("AI returned empty response")
    }
}

internal fun diaryAssistantSourcesFromText(text: String): List<DiaryAssistantSource> = capDiaryAssistantSources(
    diaryAssistantHttpUrl.findAll(text).map { match ->
        val url = normalizeDiaryAssistantUrl(match.value)
        DiaryAssistantSource(url, url)
    }.toList(),
)

internal fun List<DiaryAssistantSource>.verifiedBy(evidence: List<DiaryAssistantSource>): List<DiaryAssistantSource> {
    val evidenceByUrl = evidence.associateBy { it.url.lowercase() }
    val verified = mapNotNull { source ->
        evidenceByUrl[source.url.lowercase()]?.let { DiaryAssistantSource(source.title, it.url) }
    }
    return capDiaryAssistantSources(if (verified.isEmpty()) evidence else verified)
}

internal fun capDiaryAssistantSources(sources: List<DiaryAssistantSource>): List<DiaryAssistantSource> {
    val seen = mutableSetOf<String>()
    return sources.asSequence().mapNotNull { source ->
        val url = normalizeDiaryAssistantUrl(source.url.trim())
        if (!url.isUsableDiaryAssistantHttpUrl() || !seen.add(url.lowercase())) null
        else DiaryAssistantSource(source.title.trim().ifBlank { url }, url)
    }.take(3).toList()
}

private val diaryAssistantHttpUrl = Regex("https?://[^\\s<>\\\"']+", RegexOption.IGNORE_CASE)
private val usableDiaryAssistantHttpUrl = Regex(
    "^https?://([^\\s/?#]+)(?:[/?#].*)?$",
    RegexOption.IGNORE_CASE,
)

private fun String.isUsableDiaryAssistantHttpUrl(): Boolean {
    val match = usableDiaryAssistantHttpUrl.matchEntire(this) ?: return false
    return match.groupValues[1].substringAfterLast('@').substringBefore(':').isNotBlank()
}
