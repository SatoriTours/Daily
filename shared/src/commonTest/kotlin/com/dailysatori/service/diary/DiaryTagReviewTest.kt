package com.dailysatori.service.diary

import kotlinx.coroutines.runBlocking
import kotlin.test.*

class DiaryTagReviewTest {
    @Test
    fun independentReviewMapsSynonymToExistingTag() = runBlocking {
        var calls = 0
        val generator = DiaryTagGenerator { _, _ ->
            calls++
            if (calls == 1) """{"tags":[{"name":"职场选择","newTopic":true}]}"""
            else """{"reviews":[{"name":"职场选择","decision":"equivalent","equivalentTo":"职业选择"}]}"""
        }
        val result = generator.generate("考虑换工作", listOf("职业选择"), emptyMap())
        assertEquals(2, calls)
        assertEquals(listOf("职业选择"), result.tags)
        assertEquals(listOf(DiaryTagMerge("职场选择", "职业选择")), result.merges)
    }

    @Test
    fun uncertainNovelTagRemainsCandidateInsteadOfExpandingVocabulary() = runBlocking {
        var calls = 0
        val result = DiaryTagGenerator { _, _ ->
            calls++
            if (calls == 1) """{"tags":[{"name":"自我探索","newTopic":true}]}"""
            else """{"reviews":[{"name":"自我探索","decision":"uncertain"}]}"""
        }.generate("思考人生", listOf("自我成长"), emptyMap())
        assertTrue(result.tags.isEmpty())
        assertEquals(listOf("自我探索"), result.pendingTags)
    }

    @Test
    fun knownTagsDoNotNeedASecondRequest() = runBlocking {
        var calls = 0
        val result = DiaryTagGenerator { _, _ -> calls++; """{"tags":[{"name":"读书"}]}""" }
            .generate("读书笔记", listOf("读书"), emptyMap())
        assertEquals(listOf("读书"), result.tags)
        assertEquals(1, calls)
    }

    @Test
    fun incompleteOrInvalidReviewNeverApprovesNewTags(): Unit = runBlocking {
        var calls = 0
        val result = DiaryTagGenerator { _, _ ->
            calls++
            if (calls == 1) """{"tags":[{"name":"园艺","newTopic":true}]}""" else """{"reviews":[]}"""
        }.generate("种花", listOf("健身"), emptyMap())
        assertTrue(result.tags.isEmpty())
        assertEquals(listOf("园艺"), result.pendingTags)
        calls = 0
        assertFailsWith<IllegalArgumentException> {
            DiaryTagGenerator { _, _ ->
                calls++
                if (calls == 1) """{"tags":[{"name":"园艺","newTopic":true}]}""" else "not json"
            }.generate("种花", listOf("健身"), emptyMap())
        }
    }

    @Test
    fun newSynonymsWithinOneResponseAreDeduplicated() = runBlocking {
        var calls = 0
        val result = DiaryTagGenerator { _, _ ->
            calls++
            if (calls == 1) """{"tags":[{"name":"园艺","newTopic":true},{"name":"种花","newTopic":true}]}"""
            else """{"reviews":[{"name":"园艺","decision":"distinct"},{"name":"种花","decision":"equivalent","equivalentTo":"园艺"}]}"""
        }.generate("种花", emptyList(), emptyMap())
        assertEquals(listOf("园艺"), result.tags)
    }

    @Test
    fun historyRestoresFullDraftAfterDeleteReplaceAndGenerate() {
        val original = DiaryTagDraft(listOf("读书", "旅行"), listOf("读书", "旅行"))
        val deleted = original.remove("读书")
        val replaced = deleted.add("健身", "旅行")
        val generated = replaced.withGenerated("正文", listOf("工作"))
        val history = DiaryTagHistory(original).change(deleted).change(replaced).change(generated)
        assertEquals(replaced, history.undo().current)
        assertEquals(deleted, history.undo().undo().current)
        val restored = history.undo().undo().undo()
        assertEquals(original, restored.current)
        assertFalse(restored.canUndo)
    }

    @Test
    fun undoRestoresUserDeletionIntentAndPendingCandidates() {
        val draft = DiaryTagDraft(listOf("旅行"), listOf("旅行")).remove("旅行")
        val generated = draft.withGenerated("正文", DiaryTagResult(listOf("读书"), pendingTags = listOf("园艺")))
        assertEquals(draft, DiaryTagHistory(draft).change(generated).undo().current)
    }
}
