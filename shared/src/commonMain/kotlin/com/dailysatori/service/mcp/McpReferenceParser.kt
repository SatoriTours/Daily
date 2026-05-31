package com.dailysatori.service.mcp

internal fun parseMcpReferenceIds(refsContent: String): Map<String, Set<Long>> {
    val ids = mutableMapOf(
        "article" to mutableSetOf<Long>(),
        "diary" to mutableSetOf<Long>(),
        "book" to mutableSetOf<Long>(),
        "book_viewpoint" to mutableSetOf<Long>(),
    )
    for (ref in refsContent.split(",").map { it.trim() }) {
        val match = Regex("(article|diary|book|book_viewpoint)_(\\d+)").find(ref) ?: continue
        val type = match.groupValues[1]
        val id = match.groupValues[2].toLongOrNull() ?: continue
        ids[type]?.add(id)
    }
    return ids
}

internal fun Map<String, Set<Long>>.hasMcpReferenceIds(): Boolean = values.sumOf { it.size } > 0

internal fun McpSearchResult.matchesMcpReferenceIds(referencedIds: Map<String, Set<Long>>): Boolean = when (type) {
    "article" -> referencedIds["article"]?.contains(id) == true
    "diary" -> referencedIds["diary"]?.contains(id) == true
    "book" -> referencedIds["book"]?.contains(id) == true
    "book_viewpoint" -> referencedIds["book_viewpoint"]?.contains(id) == true
    else -> false
}

internal fun mcpReferenceContentRequestsNoResults(refsContent: String): Boolean =
    refsContent.trim().lowercase() == "none"
