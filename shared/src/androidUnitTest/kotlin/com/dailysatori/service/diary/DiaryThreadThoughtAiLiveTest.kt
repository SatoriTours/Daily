package com.dailysatori.service.diary

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 「我的思想」真实链路：确认续写原文进入 AI 请求，而不是只送主日记或 AI 汇总。
 */
class DiaryThreadThoughtAiLiveTest {
    @Test
    fun liveThoughtExtractionReceivesReplyText() = runBlocking {
        liveDiaryAi().use { live ->
            val prompts = mutableListOf<String>()
            val generator = DiaryThoughtGenerator { prompt, system ->
                prompts.add(prompt)
                live.complete(prompt, system)
            }
            val sources = listOf(
                DiaryThoughtSource(1, "最初记录：我一直想学吉他，但总觉得自己没时间。", 1_700_000_000_000),
                DiaryThoughtSource(2, "补充：这周我真的买了吉他并开始每天练习二十分钟。", 1_700_100_000_000),
            )

            val archive = generator.generate(sources, corrections = "", previous = DiaryThoughtArchive(), force = true)

            assertEquals(2, archive.diaryCount, "两个线程记录都必须参与思想分析")
            assertTrue(prompts.any { it.contains("买了吉他") }, "续写原文必须进入思想提取请求")
        }
    }
}
