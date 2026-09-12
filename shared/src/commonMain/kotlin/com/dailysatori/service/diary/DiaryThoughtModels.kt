package com.dailysatori.service.diary

import kotlinx.serialization.Serializable

data class DiaryThoughtSource(val id: Long, val content: String, val createdAt: Long)

@Serializable
data class DiaryThoughtEvidence(val diaryId: Long, val quote: String)

@Serializable
data class DiaryThought(
    val category: String,
    val statement: String,
    val basis: String,
    val evidence: List<DiaryThoughtEvidence>,
)

@Serializable
data class DiaryThoughtBatch(val thoughts: List<DiaryThought>)

@Serializable
data class DiaryThoughtChunk(val key: String, val thoughts: List<DiaryThought>)

@Serializable
data class DiaryThoughtMergeCheckpoint(
    val fingerprint: String,
    val completedBatches: Int,
    val thoughts: List<DiaryThought>,
)

@Serializable
data class DiaryThoughtArchive(
    val fingerprint: String = "",
    val thoughts: List<DiaryThought> = emptyList(),
    val chunks: List<DiaryThoughtChunk> = emptyList(),
    val diaryCount: Int = 0,
    val generatedAt: Long = 0,
    val mergeCheckpoint: DiaryThoughtMergeCheckpoint? = null,
)

data class DiaryThoughtState(
    val archive: DiaryThoughtArchive = DiaryThoughtArchive(),
    val corrections: String = "",
    val useInChat: Boolean = true,
    val isUpdating: Boolean = false,
    val isPaused: Boolean = false,
    val isStale: Boolean = false,
    val progress: String = "",
    val error: String? = null,
)
