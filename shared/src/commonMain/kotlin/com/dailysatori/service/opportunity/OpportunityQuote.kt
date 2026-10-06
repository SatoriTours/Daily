package com.dailysatori.service.opportunity

/** Only whitespace may differ. Always return the corresponding slice of the supplied original. */
internal fun resolveOpportunityQuote(content: String, quote: String): String? {
    val body = content.take(OPPORTUNITY_BODY_LIMIT)
    val trimmed = quote.trim().takeIf { it.isNotEmpty() } ?: return null
    if (body.contains(trimmed)) return trimmed
    val normalizedQuote = trimmed.map { if (it.isWhitespace()) ' ' else it }
        .joinToString("").replace(Regex(" +"), " ")
    val offsets = mutableListOf<Int>()
    val normalizedBody = buildString {
        body.forEachIndexed { index, char ->
            val normalized = if (char.isWhitespace()) ' ' else char
            if (normalized != ' ' || lastOrNull() != ' ') {
                append(normalized)
                offsets += index
            }
        }
    }
    val start = normalizedBody.indexOf(normalizedQuote)
    if (start < 0) return null
    return body.substring(offsets[start], offsets[start + normalizedQuote.length - 1] + 1)
}
