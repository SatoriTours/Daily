package com.dailysatori.service.diary

import com.dailysatori.service.externalfavorites.sha256Hex
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
    fun oldAbstractStyleArchiveIsRegeneratedInsteadOfReusingItsChunks() = runBlocking {
        val legacyFingerprint = sha256Hex("diary-thought-v1:\n1:100:${sha256Hex(source.content)}")
        val old = thought.copy(statement = "以价值排序实现行动聚焦")
        val previous = DiaryThoughtArchive(
            fingerprint = legacyFingerprint, thoughts = listOf(old),
            chunks = listOf(DiaryThoughtChunk(legacyFingerprint, listOf(old))), diaryCount = 1,
            mergeCheckpoint = DiaryThoughtMergeCheckpoint(legacyFingerprint, 1, listOf(old)),
        )
        val clearer = thought.copy(statement = "你今天决定先把重要的事做好，再处理琐事。这是这次安排事情的选择，不代表你一直如此。")
        var calls = 0
        val generator = DiaryThoughtGenerator { _, _ ->
            calls++
            Json.encodeToString(DiaryThoughtBatch(listOf(clearer)))
        }

        val result = generator.generate(listOf(source), "", previous)

        assertEquals(listOf(clearer), result.thoughts)
        assertEquals(thought.evidence, result.thoughts.single().evidence)
        assertEquals(2, calls, "旧版本的抽取和合并缓存都需要更新")
        assertEquals(null, result.mergeCheckpoint)
        assertEquals(result, generator.generate(listOf(source), "", result))
        assertEquals(2, calls, "更新后的档案不应重复生成")
    }

    @Test
    fun mergerResolvesEvidenceReferencesWithoutAskingAiToCopyQuotes() = runBlocking {
        val generator = DiaryThoughtGenerator { prompt, system ->
            if (prompt.contains("新增证据：")) {
                assertTrue(prompt.contains("\"evidenceId\":1"))
                assertTrue(system.contains("evidenceId"))
                """{"thoughts":[{"category":"做事准则","statement":"先完成重要的事情","basis":"明确表达","evidence":[{"evidenceId":1}]}]}"""
            } else response
        }
        val result = generator.generate(listOf(source), "", DiaryThoughtArchive())
        assertEquals(listOf(thought), result.thoughts)
    }

    @Test
    fun mergerRejectsUnknownEvidenceReferencesWithoutSavingInvalidBatch() = runBlocking {
        var checkpoint = DiaryThoughtArchive()
        val generator = DiaryThoughtGenerator { prompt, _ ->
            if (prompt.contains("新增证据：")) {
                """{"thoughts":[{"category":"做事准则","statement":"先完成重要的事情","basis":"明确表达","evidence":[{"evidenceId":99}]}]}"""
            } else response
        }
        assertFailsWith<DiaryThoughtResponseException> {
            generator.generate(listOf(source), "", checkpoint, onCheckpoint = { checkpoint = it })
        }
        assertEquals(1, checkpoint.chunks.size)
        assertEquals(null, checkpoint.mergeCheckpoint)
    }

    @Test
    fun finalFortiethBatchResumesOldCheckpointUsingEvidenceReferences() = runBlocking {
        val sources = (1L..40L).map { source.copy(id = it, createdAt = it) }
        val fingerprint = diaryThoughtFingerprint(sources, "")
        val chunks = sources.map { diary ->
            val extracted = (1..6).map { index -> thought.copy(
                statement = "观点 $index", evidence = listOf(DiaryThoughtEvidence(diary.id, "先把重要的事情做好")),
            ) }
            DiaryThoughtChunk(diaryThoughtFingerprint(listOf(diary), ""), extracted)
        }
        val checkpoint = DiaryThoughtArchive(
            chunks = chunks,
            mergeCheckpoint = DiaryThoughtMergeCheckpoint(fingerprint, 39, listOf(thought)),
        )
        var calls = 0
        var progress = ""
        val generator = DiaryThoughtGenerator { _, _ ->
            calls++
            assertTrue(progress.contains("已整理 39/40"), progress)
            """{"thoughts":[{"category":"做事准则","statement":"先完成重要的事情","basis":"明确表达","evidence":[{"evidenceId":2}]}]}"""
        }
        val result = generator.generate(sources, "", checkpoint, onProgress = { progress = it })
        assertEquals(1, calls, "不能重做前 39 批或重复提取日记")
        assertEquals(40, result.diaryCount)
        assertEquals(40L, result.thoughts.single().evidence.single().diaryId)
        assertEquals(fingerprint, result.fingerprint)
        assertEquals(null, result.mergeCheckpoint)
    }

    @Test
    fun progressCountsCompletedWorkWhileLastRequestIsStillPending() = runBlocking {
        var progress = ""
        val generator = DiaryThoughtGenerator { prompt, _ ->
            if (prompt.contains("新增证据：")) {
                assertTrue(progress.contains("已整理 0/1"), progress)
                assertTrue(progress.contains("第 1 批"), progress)
            } else {
                assertTrue(progress.contains("已阅读 0/1"), progress)
            }
            response
        }
        generator.generate(listOf(source), "", DiaryThoughtArchive(), onProgress = { progress = it })
        assertTrue(progress.contains("已整理 1/1"), progress)
    }

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
    fun retryResumesCompletedMergeBatchesAndReportsBatchProgress() = runBlocking {
        val sources = (1L..7L).map { source.copy(id = it, createdAt = it) }
        var checkpoint = DiaryThoughtArchive()
        var merges = 0
        val failing = DiaryThoughtGenerator { prompt, _ ->
            if (prompt.contains("新增证据：")) {
                merges++
                if (merges == 2) error("连接中断")
                response
            } else {
                val diary = sources.first { prompt.contains("\"diaryId\":${it.id},") }
                Json.encodeToString(DiaryThoughtBatch(listOf(thought.copy(
                    evidence = listOf(DiaryThoughtEvidence(diary.id, "先把重要的事情做好")),
                ))))
            }
        }
        assertFailsWith<IllegalStateException> {
            failing.generate(sources, "", checkpoint, onCheckpoint = { checkpoint = it })
        }
        assertTrue(checkpoint.thoughts.isEmpty(), "中间归纳不能作为完整档案发布")
        val restored = Json.decodeFromString<DiaryThoughtArchive>(Json.encodeToString(checkpoint))
        var retryCalls = 0
        val progress = mutableListOf<String>()
        val retry = DiaryThoughtGenerator { _, _ -> retryCalls++; response }
        val result = retry.generate(sources, "", restored, onProgress = { progress += it })
        assertEquals(1, retryCalls, "恢复时只重做失败的合并批次")
        assertTrue(progress.any { it.contains("2/2") && it.contains("整理") })
        assertEquals(listOf(thought), result.thoughts)
        retryCalls = 0
        retry.generate(sources, "修正后的理解", restored)
        assertEquals(2, retryCalls, "修正变化必须重新合并，不能复用旧理解")
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
