package com.dailysatori.service.memory

interface MemoryExtractor {
    suspend fun extractAndSave(
        sourceType: String,
        sourceId: Long,
        title: String,
        content: String,
    )
}
