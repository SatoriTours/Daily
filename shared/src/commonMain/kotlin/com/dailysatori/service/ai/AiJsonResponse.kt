package com.dailysatori.service.ai

/** Strip one unambiguous Markdown wrapper; JSON syntax and schema remain caller responsibilities. */
internal fun unwrapAiJsonResponse(response: String): String {
    val trimmed = response.trim().removePrefix("\uFEFF").trim()
    if (trimmed.startsWith('{') || trimmed.startsWith('[')) return trimmed
    val blocks = aiJsonCodeFence.findAll(trimmed).toList()
    if (blocks.size != 1) return trimmed
    val block = blocks.single()
    val outside = trimmed.removeRange(block.range)
    if (outside.contains("```") || outside.any { it in "{}[]" }) return trimmed
    return block.groupValues[1].trim()
}

private val aiJsonCodeFence = Regex(
    """^[ \t]*```(?:json)?[ \t]*\r?\n([\s\S]*?)\r?\n[ \t]*```[ \t]*(?=\r?$)""",
    setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE),
)
