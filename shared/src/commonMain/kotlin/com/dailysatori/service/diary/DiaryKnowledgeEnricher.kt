package com.dailysatori.service.diary

class DiaryAssistantFallbackRequiredException : IllegalStateException(
    "Web sources are unavailable; explicit model-knowledge fallback is required",
)

class DiaryKnowledgeEnricher(
    private val collectWebNotes: suspend (String) -> String,
    private val complete: suspend (prompt: String, systemPrompt: String) -> String,
) {
    suspend fun enrich(request: DiaryAssistantRequest): DiaryAssistantResult {
        val notes = collectWebNotes(request.selectedText)
        val evidence = diaryAssistantSourcesFromText(notes)
        if (evidence.isEmpty() && !request.allowModelKnowledgeFallback) {
            throw DiaryAssistantFallbackRequiredException()
        }

        val response = complete(
            buildDiaryKnowledgePrompt(request),
            buildDiaryKnowledgeSystemPrompt(notes),
        )
        val parsed = response.toDiaryAssistantParsed(evidence)
        val sources = if (evidence.isEmpty()) parsed.sources else parsed.sources.verifiedBy(evidence)
        val verification = if (evidence.isEmpty()) {
            DiaryAssistantVerification.MODEL_ONLY
        } else {
            DiaryAssistantVerification.WEB_VERIFIED
        }
        return DiaryAssistantResult(
            content = renderDiaryAssistantMarkdown(parsed.content, sources),
            sources = sources,
            verification = verification,
        )
    }
}

private fun buildDiaryKnowledgeSystemPrompt(notes: String): String = buildString {
    append("你是 Daily Satori 的知识补充助手。仅将下列外部检索资料作为可验证来源；不要编造来源。")
    if (notes.isNotBlank()) append("\n\n外部检索资料：\n${notes.trim()}")
}
