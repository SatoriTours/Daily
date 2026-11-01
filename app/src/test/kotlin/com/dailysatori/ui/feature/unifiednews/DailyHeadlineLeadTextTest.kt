package com.dailysatori.ui.feature.unifiednews

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DailyHeadlineLeadTextTest {
    @Test
    fun fullSummaryIsPreservedWithoutEmphasizingALongOpeningSentence() {
        val opening = "今日最受关注的是安全与 AI 工具两条线。"
        val remainder = "Molly 仍包含主线已修复的漏洞；Agent 提供跨会话持久上下文。".repeat(20)
        val result = dailyHeadlineLeadText("$opening[R1]$remainder")

        assertEquals(opening + remainder, result.text)
        assertTrue(result.spanStyles.isEmpty())
    }

    @Test
    fun incompleteOrEmptySummaryIsPreservedWithoutInventingAnOpeningSentence() {
        listOf("没有句号的完整总结", "").forEach { text ->
            val result = dailyHeadlineLeadText(text)
            assertEquals(text, result.text)
            assertTrue(result.spanStyles.isEmpty())
        }
    }
}
