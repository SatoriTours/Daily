package com.dailysatori.service.diary

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.data.repository.DiaryRepository
import com.dailysatori.data.repository.DiaryThreadRepository
import com.dailysatori.service.diary.DiaryThoughtArchive
import com.dailysatori.service.diary.DiaryThoughtGenerator
import com.dailysatori.service.diary.DiaryThoughtSource
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 「我的思想」真实链路：确认续写原文进入 AI 请求，而不是只送主日记或 AI 汇总。
 */
/**
 * 「我的思想」真实链路：经真实 SQLite 主日记+续写、DiaryThreadRepository.getSource 得到
 * 一个归属主日记的整串 Source，确认续写原文进入真实 AI 请求且档案只有一个主来源。
 */
class DiaryThreadThoughtAiLiveTest {
    @Test
    fun liveThoughtExtractionReceivesReplyTextFromThreadSource() = runBlocking {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            driver.execute(null, "PRAGMA foreign_keys=ON", 0)
            DailySatoriDatabase.Schema.create(driver)
            val db = DailySatoriDatabase(driver)
            val diaries = DiaryRepository(db, driver)
            val threads = DiaryThreadRepository(db, driver)
            val rootId = diaries.create("最初记录：我一直想学吉他，但总觉得自己没时间。")
            threads.createReply(rootId, "补充：这周我真的买了吉他并开始每天练习二十分钟。")

            // 与生产 DiaryThoughtService 相同的映射：整串原文只归属主日记 rootId。
            val threadSource = requireNotNull(threads.getSource(rootId))
            val source = DiaryThoughtSource(threadSource.rootId, threadSource.content, threadSource.createdAt)
            assertEquals(rootId, source.id)
            assertTrue(source.content.contains("买了吉他"), "整串原文必须包含续写")

            liveDiaryAi().use { live ->
                val prompts = mutableListOf<String>()
                val generator = DiaryThoughtGenerator { prompt, system ->
                    prompts.add(prompt)
                    live.complete(prompt, system)
                }

                val archive = generator.generate(
                    listOf(source),
                    corrections = "",
                    previous = DiaryThoughtArchive(),
                    force = true,
                )

                assertEquals(1, archive.diaryCount, "整串只作为一个主来源参与分析")
                assertTrue(prompts.any { it.contains("买了吉他") }, "续写原文必须进入真实请求")
                assertTrue(prompts.any { it.contains("\"diaryId\":$rootId") }, "引用必须归属主日记")
            }
        } finally {
            driver.close()
        }
    }
}
