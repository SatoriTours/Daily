package com.dailysatori.ui.feature.diary

import com.dailysatori.service.diary.DiaryThought
import com.dailysatori.service.diary.DiaryThoughtEvidence
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DiaryThoughtPresentationTest {
    @Test
    fun highlightsBalanceTopicsAndCountDistinctDiariesInsteadOfQuotes() {
        val repeated = thought("价值观", "同篇日记的多个引文", 1, 1, 1)
        val value = thought("价值观", "有多篇依据的观点", 1, 2, 3)
        val anotherValue = thought("价值观", "同类别的另一条", 4, 5)
        val principle = thought("做事准则", "先完成重要的事", 6, 7)
        val exploration = thought("变化与探索", "可能正在改变", 8)
        val input = listOf(repeated, value, anotherValue, principle, exploration)

        val result = diaryThoughtPresentation(input)

        assertEquals(listOf(value, principle, exploration), result.highlights)
        assertEquals(input.toSet(), result.sections.flatMap { it.thoughts }.toSet())
        assertEquals("AI归纳", result.highlights.last().basis)
        assertEquals(exploration.evidence, result.highlights.last().evidence)
    }

    @Test
    fun groupsKeepAllContentInReadingOrderWithoutEmptySections() {
        val thinking = thought("思维方式", "思考方式", 1)
        val value = thought("价值观", "我看重什么", 2)
        val principle = thought("做事准则", "做事准则", 3)
        val result = diaryThoughtPresentation(listOf(thinking, value, principle))

        assertEquals(listOf("我看重什么", "做事准则", "思考方式"), result.sections.map { it.title })
        assertEquals(listOf(value, principle, thinking), result.sections.flatMap { it.thoughts })
    }

    @Test
    fun fewerTopicsStillShowUpToThreeExistingThoughtsAndEmptyArchiveStaysEmpty() {
        val thoughts = (1L..4L).map { thought("做事准则", "观点 $it", it) }
        assertEquals(thoughts.take(3), diaryThoughtPresentation(thoughts).highlights)
        assertEquals(thoughts.take(1), diaryThoughtPresentation(thoughts.take(1)).highlights)
        assertTrue(diaryThoughtPresentation(emptyList()).highlights.isEmpty())
        assertTrue(diaryThoughtPresentation(emptyList()).sections.isEmpty())
    }

    private fun thought(category: String, statement: String, vararg diaries: Long) = DiaryThought(
        category, statement, "AI归纳", diaries.mapIndexed { index, id -> DiaryThoughtEvidence(id, "原文 $index") },
    )
}
