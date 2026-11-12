
package com.dailysatori.ui.feature.settings.taskcenter

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TaskCenterCopyContractTest {
    private fun strings() = testStrings()

    @Test
    fun plainLogTextIsShownVerbatimWithoutFakeJsonErrors() {
        val log = "2026-11-12T02:15:43.946397Z TASK started type=remote_news_fetch"
        val document = taskCenterJsonDocument(log)
        assertNull(document.errorMessage)
        assertEquals(log, document.text)
        assertFalse(document.formatted)
        assertFalse(document.isJsonLike)
    }

    @Test
    fun plainProseAndSingleNumbersAreNotTreatedAsBrokenJson() {
        val prose = taskCenterJsonDocument("连接中断，30秒后自动重试")
        assertNull(prose.errorMessage)
        assertEquals("连接中断，30秒后自动重试", prose.text)

        val number = taskCenterJsonDocument("20261112")
        assertNull(number.errorMessage)
        assertEquals("20261112", number.text)
    }

    @Test
    fun damagedJsonShapeReportsTheOffsetOfTheRealDamage() {
        val document = taskCenterJsonDocument("""{"a": [1, 2""")
        assertTrue(document.isJsonLike)
        assertNotNull(document.errorMessage)
        assertNotNull(document.errorOffset)
        assertEquals("""{"a": [1, 2""", document.text)
    }

    @Test
    fun nestedJsonStringStaysIntactAndIsNotRepaired() {
        val nested = """"{\"a\":1}""""
        val document = taskCenterJsonDocument(nested)
        assertNull(document.errorMessage)
        assertEquals(nested, document.text)
    }

    @Test
    fun blankPayloadHasNoErrorButIsNotFormatted() {
        val document = taskCenterJsonDocument("   ")
        assertNull(document.errorMessage)
        assertFalse(document.formatted)
    }

    @Test
    fun emptyPayloadAndCheckpointAreReportedAsGenuinelyEmptyNotAsNotCollected() {
        assertEquals(TaskCenterFieldState.Empty, taskCenterFieldState(""))
        assertEquals(TaskCenterFieldState.Empty, taskCenterFieldState("   \n"))
        assertEquals(TaskCenterFieldState.Recorded, taskCenterFieldState("{}"))
        assertEquals(TaskCenterFieldState.Recorded, taskCenterFieldState("""{"stage":"done"}"""))
    }

    @Test
    fun omittedFieldIsTheOnlyThingTreatedAsNotCollected() {
        assertEquals(TaskCenterFieldState.NotCollected, taskCenterFieldState("[omitted]"))
        assertEquals(TaskCenterFieldState.Recorded, taskCenterFieldState("空正文"))
    }

    @Test
    fun requestEmptyCopyDistinguishesMissingLogFromMissingHttpLines() {
        assertEquals(TaskCenterRequestEmptyKind.NoLog, taskCenterRequestEmptyKind(""))
        assertEquals(
            TaskCenterRequestEmptyKind.NoHttpLines,
            taskCenterRequestEmptyKind("2026-11-12T02:15:43Z TASK started type=remote_news_fetch"),
        )
        assertEquals(
            TaskCenterRequestEmptyKind.NoHttpLines,
            taskCenterRequestEmptyKind("2026-11-12T02:15:43Z TASK started\n2026-11-12T02:15:57Z TASK failed code=x"),
        )
    }

    @Test
    fun noCaptureCopyNeverClaimsTheTaskMadeNoRequest() {
        val zh = File("src/main/res/values/strings.xml").readText()
        val en = File("src/main/res/values-en/strings.xml").readText()
        val zhNoCapture = zh.substringAfter("name=\"task_center_requests_empty_no_capture\">").substringBefore("<")
        val enNoCapture = en.substringAfter("name=\"task_center_requests_empty_no_capture\">").substringBefore("<")
        assertTrue(zhNoCapture.contains("并不等于"))
        assertFalse(zhNoCapture.contains("此任务未发起"))
        assertTrue(enNoCapture.contains("do not prove"))
        assertFalse(enNoCapture.contains("made no requests"))
    }

    @Test
    fun everyNewStringKeyExistsInBothLanguages() {
        val zh = File("src/main/res/values/strings.xml").readText()
        val en = File("src/main/res/values-en/strings.xml").readText()
        val zhKeys = Regex("""name="(task_center_[a-z0-9_]+)"""").findAll(zh).map { it.groupValues[1] }.toSet()
        val enKeys = Regex("""name="(task_center_[a-z0-9_]+)"""").findAll(en).map { it.groupValues[1] }.toSet()
        assertTrue(zhKeys.size >= 60, "expected the task center copy to be localized, found " + zhKeys.size)
        assertEquals(emptySet(), zhKeys - enKeys)
        assertEquals(emptySet(), enKeys - zhKeys)
        assertTrue("task_center_tab_current" in zhKeys)
        assertTrue("task_center_detail_checkpoint_empty" in zhKeys)
        assertTrue("task_center_requests_empty_no_capture" in zhKeys)
        assertTrue("task_center_viewer_uncollected" in zhKeys)
    }

    @Test
    fun detailUsesDistinctEmptyTextsForPayloadCheckpointAndResult() {
        val detail = File("src/main/kotlin/com/dailysatori/ui/feature/settings/taskcenter/TaskCenterTaskDetail.kt").readText()
        assertTrue(detail.contains("copy.detailPayloadEmpty"))
        assertTrue(detail.contains("copy.detailCheckpointEmpty"))
        assertTrue(detail.contains("copy.detailResultEmpty"))
        assertTrue(detail.contains("copy.detailCheckpoint"))
        assertFalse(detail.contains("\"Checkpoint\""))
    }

    @Test
    fun viewerOnlyClaimsNotCollectedWhenTheCaptureReallySaysOmitted() {
        val source = File("src/main/kotlin/com/dailysatori/ui/feature/settings/taskcenter/TaskCenterJsonView.kt").readText()
        assertTrue(source.contains("TaskCenterFieldState.Empty"))
        assertTrue(source.contains("TaskCenterFieldState.NotCollected"))
        assertFalse(source.contains("body.isBlank() -> \"未采集\""))
    }
}
