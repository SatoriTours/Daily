package com.dailysatori.service.mcp

import com.dailysatori.service.diary.DiaryThoughtChatContext
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DiaryThoughtChatMessageTest {
    @Test
    fun explicitlySelectedContextIsRetainedEvenForAStatisticalQuestion() {
        val stats = localSearch.copy(plan = AiSearchPlan(useSqlStatsPath = true))
        val content = buildMcpConversationUserMessage("该如何衡量这件事", stats, thoughts, PrivacyMasker(), explicitContext = true)
            .getValue("content").jsonPrimitive.content
        assertTrue(content.contains("先做重要的事情"))
        assertTrue(content.startsWith("该如何衡量这件事"))
    }
    private val localSearch = AiSearchResult(AiSearchPlan(), emptyList(), emptyList(), "用户问题及原有检索证据")
    private val reference = McpSearchResult(7, "diary", "日记", "先做重要的事情", "2026-09-11")
    private val thoughts = DiaryThoughtChatContext("准则：先做重要的事情 [diary_7]；用户修正：联系号码13912345678", listOf(reference))

    @Test
    fun thoughtMessageKeepsSearchEvidenceAndMasksPersonalData() {
        val masker = PrivacyMasker()
        val message = buildMcpConversationUserMessage("如何安排优先级", localSearch, thoughts, masker)
        val content = message.getValue("content").jsonPrimitive.content
        assertEquals("user", message.getValue("role").jsonPrimitive.content)
        assertTrue(content.startsWith("用户问题及原有检索证据"))
        assertTrue(content.contains("先做重要的事情 [diary_7]"))
        assertTrue(content.contains("[PHONE_1]"))
        assertFalse(content.contains("13912345678"))
        assertTrue(masker.restore(content).contains("13912345678"))
    }

    @Test
    fun disabledOrMissingThoughtsPreserveExistingSearchMessage() {
        val message = buildMcpConversationUserMessage("问题", localSearch, null, PrivacyMasker())
        assertEquals("用户问题及原有检索证据", message.getValue("content").jsonPrimitive.content)
    }

    @Test
    fun statisticsKeepOriginalQueryWithoutPersonalContext() {
        val stats = localSearch.copy(plan = AiSearchPlan(useSqlStatsPath = true))
        val message = buildMcpConversationUserMessage("日记有多少篇", stats, thoughts, PrivacyMasker())
        assertEquals("日记有多少篇", message.getValue("content").jsonPrimitive.content)
    }

    @Test
    fun citedThoughtEvidenceResolvesThroughExistingReferenceParser() {
        val refs = referencesForAnswer("先做重要的事情 <!-- refs: diary_7 -->", emptyList(), thoughts.references)
        assertEquals(listOf(reference), refs)
    }
}
