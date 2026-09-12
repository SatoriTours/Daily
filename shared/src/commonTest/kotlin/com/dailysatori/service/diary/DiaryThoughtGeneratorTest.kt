package com.dailysatori.service.diary

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DiaryThoughtGeneratorTest {
    private val source = DiaryThoughtSource(1, "今天我决定先把重要的事情做好，再处理琐事。", 100)
    private val thought = DiaryThought(
        "做事准则", "先完成重要的事情", "明确表达",
        listOf(DiaryThoughtEvidence(1, "先把重要的事情做好")),
    )
    private val response get() = Json.encodeToString(DiaryThoughtBatch(listOf(thought)))

    @Test
    fun generatesGroundedArchiveAndReusesUnchangedEvidence() = runBlocking {
        var calls = 0
        val generator = DiaryThoughtGenerator { _, _ -> calls++; response }
        val first = generator.generate(listOf(source), "", DiaryThoughtArchive())
        assertEquals(listOf(thought), first.thoughts)
        assertEquals(1, first.diaryCount)
        val previousCalls = calls
        val second = generator.generate(listOf(source), "", first)
        assertEquals(first, second)
        assertEquals(previousCalls, calls)
        generator.generate(listOf(source), "不要把一次决定当成长期习惯", first)
        assertEquals(previousCalls + 1, calls)
    }

    @Test
    fun fabricatedQuotesAndUnknownDiaryIdsAreRejected() = runBlocking {
        listOf(DiaryThoughtEvidence(99, "先把重要的事情做好"), DiaryThoughtEvidence(1, "不存在的原文")).forEach { evidence ->
            val invalid = thought.copy(evidence = listOf(evidence))
            val generator = DiaryThoughtGenerator { _, _ -> Json.encodeToString(DiaryThoughtBatch(listOf(invalid))) }
            assertFailsWith<IllegalArgumentException> { generator.generate(listOf(source), "", DiaryThoughtArchive()) }
        }
    }

    @Test
    fun deletingAllDiariesClearsThoughtsWithoutCallingAi() = runBlocking {
        val generator = DiaryThoughtGenerator { _, _ -> error("不应调用 AI") }
        val result = generator.generate(emptyList(), "", DiaryThoughtArchive(thoughts = listOf(thought)))
        assertTrue(result.thoughts.isEmpty())
        assertTrue(result.chunks.isEmpty())
        assertEquals(0, result.diaryCount)
    }

    @Test
    fun editingOrDeletingEvidenceHidesObsoleteConclusions() {
        val archive = DiaryThoughtArchive(thoughts = listOf(thought))
        assertTrue(archive.supportedBy(emptyList()).thoughts.isEmpty())
        assertTrue(archive.supportedBy(listOf(source.copy(content = "我改变了想法"))).thoughts.isEmpty())
        assertEquals(listOf(thought), archive.supportedBy(listOf(source)).thoughts)
    }

    @Test
    fun longDiaryIsFullyProcessedIncludingItsEnd() = runBlocking {
        val longSource = source.copy(content = "记录".repeat(6000) + "最后的原则")
        val prompts = mutableListOf<String>()
        val generator = DiaryThoughtGenerator { prompt, _ -> prompts += prompt; "{\"thoughts\":[]}" }
        generator.generate(listOf(longSource), "", DiaryThoughtArchive())
        assertTrue(prompts.size > 1)
        assertTrue(prompts.any { it.contains("最后的原则") })
        assertTrue(prompts.all { it.length < 16_000 })
    }

    @Test
    fun cancellationIsPropagated() = runBlocking {
        val generator = DiaryThoughtGenerator { _, _ -> throw CancellationException() }
        assertFailsWith<CancellationException> { generator.generate(listOf(source), "", DiaryThoughtArchive()) }
        Unit
    }

    @Test
    fun mergerCannotIntroduceQuotesThatWereNotInExtractedEvidence() = runBlocking {
        var calls = 0
        val generator = DiaryThoughtGenerator { _, _ ->
            calls++
            if (calls == 1) response else Json.encodeToString(DiaryThoughtBatch(listOf(
                thought.copy(evidence = listOf(DiaryThoughtEvidence(1, "今天我决定"))),
            )))
        }
        assertFailsWith<IllegalArgumentException> { generator.generate(listOf(source), "", DiaryThoughtArchive()) }
        Unit
    }

    @Test
    fun correctionsAreKeptSeparateFromDiaryEvidence() = runBlocking {
        val prompts = mutableListOf<String>()
        val generator = DiaryThoughtGenerator { prompt, _ -> prompts += prompt; response }
        generator.generate(listOf(source), "我更重视家人", DiaryThoughtArchive())
        assertFalse(prompts.first().contains("我更重视家人"))
        assertTrue(prompts.last().contains("我更重视家人"))
    }

    @Test
    fun retryResumesExtractedChunksWithoutPublishingPartialArchive() = runBlocking {
        val second = source.copy(id = 2, createdAt = 200)
        val secondThought = thought.copy(evidence = listOf(DiaryThoughtEvidence(2, "先把重要的事情做好")))
        var checkpoint = DiaryThoughtArchive()
        var calls = 0
        val failing = DiaryThoughtGenerator { _, _ ->
            calls++
            if (calls == 2) error("暂时无法连接") else response
        }
        assertFailsWith<IllegalStateException> {
            failing.generate(listOf(source, second), "", checkpoint, onCheckpoint = { checkpoint = it })
        }
        assertTrue(checkpoint.thoughts.isEmpty())
        assertEquals(1, checkpoint.chunks.size)
        val prompts = mutableListOf<String>()
        val retry = DiaryThoughtGenerator { prompt, _ ->
            prompts += prompt
            Json.encodeToString(DiaryThoughtBatch(listOf(secondThought)))
        }
        val archive = retry.generate(listOf(source, second), "", checkpoint)
        assertEquals(2, prompts.size)
        assertEquals(2, archive.diaryCount)
        assertEquals(2, archive.chunks.size)
    }

    @Test
    fun cancelledGenerationCannotCheckpointEvenIfAiReturnsLate() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var writes = 0
        val generator = DiaryThoughtGenerator { _, _ ->
            started.complete(Unit)
            withContext(NonCancellable) { release.await() }
            response
        }
        val generation = launch {
            generator.generate(listOf(source), "", DiaryThoughtArchive(), onCheckpoint = { writes++ })
        }
        started.await()
        generation.cancel()
        release.complete(Unit)
        generation.join()
        assertEquals(0, writes)
    }
}
