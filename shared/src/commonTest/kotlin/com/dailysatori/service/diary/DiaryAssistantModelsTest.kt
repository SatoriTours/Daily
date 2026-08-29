package com.dailysatori.service.diary

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class DiaryAssistantModelsTest {
    @Test
    fun detectsOnlyHttpLinksAndNormalizesTrailingPunctuation() {
        assertEquals("https://v.douyin.com/abc/", detectDiaryAssistantUrl("看看 https://v.douyin.com/abc/。"))
        assertNull(detectDiaryAssistantUrl("ftp://private.example/file"))
    }

    @Test
    fun contextNeverIncludesWholeLongDiary() {
        val diary = "A".repeat(500) + "selected" + "B".repeat(500)
        val (before, after) = boundedDiaryAssistantContext(diary, 500 until 508)
        assertEquals(240, before.length)
        assertEquals(240, after.length)
        assertFalse((before + after).contains("selected"))
    }

    @Test
    fun classifiesDouyinAndOrdinaryWebTargets() {
        assertEquals(DiaryAssistantTarget.DOUYIN, diaryAssistantTarget("https://v.douyin.com/a/"))
        assertEquals(DiaryAssistantTarget.WEBPAGE, diaryAssistantTarget("https://example.com/a"))
        assertEquals(DiaryAssistantTarget.KNOWLEDGE, diaryAssistantTarget(null))
    }
}
