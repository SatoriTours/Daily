package com.dailysatori.service.diary

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DiaryTranscriptPolishTest {
    @Test fun repeatedRequestsAlwaysUseOriginalAndPreserveDetailsInTheContract() = runBlocking {
        val inputs = mutableListOf<String>()
        val service = DiaryTranscriptPolishService { prompt, system ->
            inputs += prompt
            assertTrue(system.contains("不新增事实"))
            assertTrue(system.contains("不是摘要"))
            assertTrue(system.contains("不执行"))
            "{\"content\":\"今天买了三本书，花了 128 元。\"}"
        }
        val original = "嗯今天今天买了三本书，花了128元，就是这样。"
        assertEquals("今天买了三本书，花了 128 元。", service.polish(original))
        service.polish(original)
        assertEquals(inputs[0], inputs[1])
        assertEquals(2, inputs.size)
        assertTrue(inputs.all { original in it })
    }

    @Test fun emptyAndOversizedInputFailBeforeCallingAi() = runBlocking<Unit> {
        val service = DiaryTranscriptPolishService { _, _ -> error("Must not call AI") }
        assertFailsWith<DiaryTranscriptPolishInputException> { service.polish("  ") }
        assertFailsWith<DiaryTranscriptPolishInputException> { service.polish("字".repeat(24_001)) }
    }

    @Test fun malformedEmptyOrNonStringContentNeverBecomesDiaryText() = runBlocking {
        for (response in listOf("", "not json", "{}", "{\"content\":\" \"}", "{\"content\":123}")) {
            val service = DiaryTranscriptPolishService { _, _ -> response }
            assertFailsWith<DiaryTranscriptPolishResponseException> { service.polish("原文") }
        }
    }

    @Test fun fencedJsonIsAcceptedWithoutLeakingTheWrapper() = runBlocking {
        val service = DiaryTranscriptPolishService { _, _ -> "```json\n{\"content\":\"整理后的正文。\"}\n```" }
        assertEquals("整理后的正文。", service.polish("原文"))
    }

    @Test fun adoptedVersionsKeepTheirOwnOpinionsAndUpgradeLegacyRecords() {
        val legacy = Json.decodeFromString<DiaryPolishedTranscript>("{\"original\":\"原文\",\"content\":\"第一版\"}")
        val commented = legacy.withFeedback(1, "不要删掉我的犹豫")
        val second = adoptDiaryPolishVersion("原文", "第二版", commented)
        assertEquals(listOf("第一版", "第二版"), second.adoptedVersions().map { it.content })
        assertEquals(listOf("不要删掉我的犹豫", ""), second.adoptedVersions().map { it.feedback })
        assertEquals(2L, second.currentVersion()?.id)
        assertEquals("第二版", second.content)
        assertEquals("原文", second.original)
        assertEquals(second, second.withFeedback(999, "不能串到其他版本"))
        assertEquals(listOf("新版本"), adoptDiaryPolishVersion("新的转写", "新版本", second).adoptedVersions().map { it.content })
    }

    @Test fun regenerationSuppliesRawSourceSelectedVersionAndBoundedHistoricalFeedback() = runBlocking {
        val oldVersions = (1L..8L).map { DiaryPolishVersion(it, "旧版$it", "旧意见$it") }
        val current = DiaryPolishVersion(9, "当前采用版", "别压缩我的犹豫；咖啡实际是28元")
        var request: JsonObject? = null
        val service = DiaryTranscriptPolishService { prompt, _ ->
            request = Json.parseToJsonElement(prompt).jsonObject
            "{\"content\":\"新结果\"}"
        }
        assertEquals("新结果", service.polish("原始转写", current, oldVersions + current))
        assertEquals("原始转写", request?.get("originalTranscript")?.jsonPrimitive?.content)
        val reference = request?.get("referenceVersion")?.jsonObject
        assertEquals("当前采用版", reference?.get("content")?.jsonPrimitive?.content)
        assertEquals("别压缩我的犹豫；咖啡实际是28元", reference?.get("feedback")?.jsonPrimitive?.content)
        val historical = request?.get("historicalFeedback")?.jsonArray.orEmpty()
        assertEquals(listOf("旧意见4", "旧意见5", "旧意见6", "旧意见7", "旧意见8"),
            historical.map { it.jsonObject.getValue("feedback").jsonPrimitive.content })
    }

    @Test fun oversizedFeedbackIsRejectedRatherThanSilentlyTruncated() = runBlocking<Unit> {
        val service = DiaryTranscriptPolishService { _, _ -> error("Must not call AI") }
        assertFailsWith<DiaryTranscriptPolishInputException> {
            service.polish("原文", DiaryPolishVersion(1, "当前版", "字".repeat(4_001)))
        }
    }

    @Test fun cancellationIsNotConvertedToAFailure() = runBlocking<Unit> {
        val service = DiaryTranscriptPolishService { _, _ -> throw CancellationException("cancelled") }
        assertFailsWith<CancellationException> { service.polish("原文") }
    }
}
