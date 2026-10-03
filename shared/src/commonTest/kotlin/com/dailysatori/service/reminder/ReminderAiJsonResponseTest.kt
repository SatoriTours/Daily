package com.dailysatori.service.reminder

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReminderAiJsonResponseTest {
    private val zone = TimeZone.UTC
    private val codec = ReminderDraftCodec({ Instant.parse("2026-09-01T00:00:00Z") }, { zone })
    private val batchCodec = ReminderBatchCodec(codec)
    private val draftJson = """{"content":"续订域名","start_date":"2026-09-02","end_date":"2026-09-02","first_reminder_time":"09:00","active_day_rule":"daily","recurrence_rule":"once"}"""
    private val indexedJson = draftJson.replaceFirst("{", "{\"source_index\":0,")

    @Test
    fun singleAiResponseAcceptsJsonCodeFence() {
        val draft = codec.decodeInterpretationResponse("```json\n$draftJson\n```", zone)

        assertEquals("续订域名", draft.content)
        assertTrue(draft.validationErrors.isEmpty())
    }

    @Test
    fun batchAiResponseAcceptsJsonCodeFence() {
        val result = batchCodec.decode("```json\n[$indexedJson]\n```", zone)

        assertNull(result.failure)
        assertEquals(listOf(0), result.drafts.map { it.sourceIndex })
        assertTrue(result.drafts.single().draft.validationErrors.isEmpty())
    }

    @Test
    fun acceptsUnlabelledFenceWithCrLfAndBom() {
        val result = batchCodec.decode("\uFEFF  ```\r\n[$indexedJson]\r\n```  ", zone)

        assertNull(result.failure)
        assertEquals("续订域名", result.drafts.single().draft.content)
    }

    @Test
    fun acceptsOneJsonBlockSurroundedByExplanation() {
        val result = batchCodec.decode("解析结果如下：\n```JSON\n[$indexedJson]\n```\n请检查提醒时间。", zone)

        assertNull(result.failure)
        assertEquals(1, result.drafts.size)
    }

    @Test
    fun rejectsMultipleBlocksInsteadOfChoosingOneReminder() {
        val result = batchCodec.decode("```json\n[$indexedJson]\n```\n```json\n[$indexedJson]\n```", zone)

        assertNotNull(result.failure)
        assertTrue(result.drafts.isEmpty())
    }

    @Test
    fun rejectsTruncatedJsonAndUnclosedFence() {
        listOf("```json\n[$indexedJson\n```", "```json\n[$indexedJson]").forEach { response ->
            assertNotNull(batchCodec.decode(response, zone).failure)
        }
    }

    @Test
    fun rejectsPlainTextAndNonJsonCodeBlocks() {
        listOf("我会提醒你续费。", "```python\n[$indexedJson]\n```", "true", "null").forEach { response ->
            assertNotNull(batchCodec.decode(response, zone).failure)
        }
    }

    @Test
    fun fencedJsonStillRejectsUnknownFieldsAndMissingSourceIndex() {
        val unknown = batchCodec.decode("```json\n[${indexedJson.replaceFirst("{", "{\"unsupported\":true,")}]\n```", zone)
        val missingIndex = batchCodec.decode("```json\n[$draftJson]\n```", zone)

        assertNull(unknown.failure)
        assertTrue(unknown.drafts.single().draft.validationErrors.any { it.contains("unsupported") })
        assertNotNull(missingIndex.failure)
    }

    @Test
    fun toolArgumentsRemainStrictJson() {
        val draft = codec.create("```json\n$draftJson\n```", zone)

        assertTrue(draft.validationErrors.any { it.contains("严格 JSON") })
    }

    @Test
    fun jsonStringValuesRetainBackticksAndEscapedDelimiters() {
        val json = draftJson.replace("续订域名", "续订 `域名` [套餐] {旧卡}")
        val draft = codec.decodeInterpretationResponse("```json\n$json\n```", zone)

        assertEquals("续订 `域名` [套餐] {旧卡}", draft.content)
        assertTrue(draft.validationErrors.isEmpty())
    }
}
