package com.dailysatori.service.diary

import com.dailysatori.service.externalfavorites.sha256Hex
import kotlinx.coroutines.ensureActive
import kotlinx.datetime.Clock
import kotlin.coroutines.coroutineContext

class DiaryThoughtGenerator(private val complete: suspend (String, String) -> String) {
    suspend fun generate(
        sources: List<DiaryThoughtSource>,
        corrections: String,
        previous: DiaryThoughtArchive,
        force: Boolean = false,
        onProgress: (String) -> Unit = {},
        onCheckpoint: (DiaryThoughtArchive) -> Unit = {},
    ): DiaryThoughtArchive {
        val diaries = sources.filter { it.content.isNotBlank() }.sortedWith(compareBy({ it.createdAt }, { it.id }))
        val fingerprint = diaryThoughtFingerprint(diaries, corrections)
        if (!force && previous.fingerprint == fingerprint) return previous
        if (diaries.isEmpty()) return DiaryThoughtArchive(fingerprint = fingerprint)
        val base = if (force) previous.copy(fingerprint = "", mergeCheckpoint = null) else previous
        val chunks = extractChunks(diaries, base, onProgress, onCheckpoint)
        val thoughts = mergeThoughts(chunks.flatMap { it.thoughts }, diaries, corrections,
            base.mergeCheckpoint?.takeIf { it.fingerprint == fingerprint }, onProgress,
        ) { checkpoint ->
            onCheckpoint(base.supportedBy(diaries).copy(fingerprint = "", chunks = chunks, mergeCheckpoint = checkpoint))
        }
        coroutineContext.ensureActive()
        return DiaryThoughtArchive(fingerprint, thoughts, chunks, diaries.size, Clock.System.now().toEpochMilliseconds())
    }

    private suspend fun extractChunks(
        diaries: List<DiaryThoughtSource>,
        previous: DiaryThoughtArchive,
        onProgress: (String) -> Unit,
        onCheckpoint: (DiaryThoughtArchive) -> Unit,
    ): List<DiaryThoughtChunk> {
        val parts = diaries.flatMap { diary -> diary.content.chunked(4_000).map { diary.copy(content = it) } }
        val keys = parts.map { diaryThoughtFingerprint(listOf(it), "") }
        val cache = previous.chunks.filter { it.key in keys }.associateBy { it.key }.toMutableMap()
        return parts.mapIndexed { index, part ->
            coroutineContext.ensureActive()
            onProgress("正在阅读日记 ${index + 1}/${parts.size}")
            val key = keys[index]
            cache[key] ?: extractChunk(part, key).also { chunk ->
                cache[key] = chunk
                onCheckpoint(previous.supportedBy(diaries).copy(chunks = cache.values.toList()))
            }
        }
    }

    private suspend fun extractChunk(source: DiaryThoughtSource, key: String): DiaryThoughtChunk {
        val response = complete(diaryThoughtExtractPrompt(source), diaryThoughtSystemPrompt)
        coroutineContext.ensureActive()
        return DiaryThoughtChunk(key, parseDiaryThoughts(response, listOf(source)))
    }

    private suspend fun mergeThoughts(
        thoughts: List<DiaryThought>,
        diaries: List<DiaryThoughtSource>,
        corrections: String,
        checkpoint: DiaryThoughtMergeCheckpoint?,
        onProgress: (String) -> Unit,
        onCheckpoint: (DiaryThoughtMergeCheckpoint) -> Unit,
    ): List<DiaryThought> {
        val batches = thoughts.chunked(6)
        val saved = checkpoint?.takeIf { it.completedBatches in 0..batches.size }
        var merged = saved?.thoughts.orEmpty()
        val fingerprint = diaryThoughtFingerprint(diaries, corrections)
        // 每批有固定上限，所有证据都会参与归纳，不截断整个日记库。
        for (index in (saved?.completedBatches ?: 0) until batches.size) {
            coroutineContext.ensureActive()
            onProgress("正在整理我的思想 ${index + 1}/${batches.size} 批 · 等待 AI 响应")
            val batch = batches[index]
            val allowedEvidence = (merged + batch).flatMap { it.evidence }.toSet()
            val response = complete(diaryThoughtMergePrompt(merged, batch, corrections), diaryThoughtSystemPrompt)
            coroutineContext.ensureActive()
            merged = parseDiaryThoughts(response, diaries)
            if (merged.any { thought -> thought.evidence.any { it !in allowedEvidence } }) {
                throw DiaryThoughtResponseException("思想整理引用了未经提取的依据，请重试")
            }
            onCheckpoint(DiaryThoughtMergeCheckpoint(fingerprint, index + 1, merged))
        }
        return merged
    }
}

fun DiaryThoughtArchive.supportedBy(sources: List<DiaryThoughtSource>): DiaryThoughtArchive =
    copy(
        thoughts = thoughts.filter { it.isSupportedBy(sources) },
        chunks = chunks.filter { chunk -> chunk.thoughts.all { it.isSupportedBy(sources) } },
        mergeCheckpoint = mergeCheckpoint?.takeIf { checkpoint -> checkpoint.thoughts.all { it.isSupportedBy(sources) } },
    )

internal fun diaryThoughtFingerprint(sources: List<DiaryThoughtSource>, corrections: String): String = sha256Hex(
    "diary-thought-v1:$corrections\n" + sources.sortedBy { it.id }.joinToString("\n") {
        "${it.id}:${it.createdAt}:${sha256Hex(it.content)}"
    },
)
