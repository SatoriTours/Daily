package com.dailysatori.ui.feature.settings.taskcenter

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TaskCenterScreenSourceTest {
    @Test
    fun settingsPageExposesTaskCenterEntry() {
        val source = File("src/main/kotlin/com/dailysatori/ui/feature/settings/SettingsScreen.kt").readText()

        assertTrue(source.contains("TASK_CENTER"))
        assertTrue(source.contains("TaskCenterScreen(onBack = { currentPage = SettingsPage.MAIN })"))
        assertTrue(source.contains("title = \"任务\""))
        assertTrue(source.contains("subtitle = \"查看异步任务进度和状态\""))
    }

    @Test
    fun taskCenterScreenHasTypeAndStatusFilters() {
        val source = File("src/main/kotlin/com/dailysatori/ui/feature/settings/taskcenter/TaskCenterScreen.kt").readText()
        val viewModel = File("src/main/kotlin/com/dailysatori/ui/feature/settings/taskcenter/TaskCenterViewModel.kt").readText()
        val models = File("../shared/src/commonMain/kotlin/com/dailysatori/service/asynctask/AsyncTaskModels.kt").readText()
        val schema = File("../shared/src/commonMain/sqldelight/com/dailysatori/shared/db/DailySatori.sq").readText()

        assertFalse(source.contains("默认显示未完成任务"))
        assertTrue(source.contains("任务类型"))
        assertTrue(source.contains("任务状态"))
        assertFalse(source.contains("历史"))
        assertFalse(source.contains("Switch("))
        assertFalse(source.contains("showTerminal"))
        assertTrue(source.contains("TaskCenterFilterBar"))
        assertFalse(source.substringAfter("private fun TaskCenterFilterBar").substringBefore("@Composable\nprivate fun TaskCenterMultiSelectDropdown").contains("Card("))
        assertTrue(source.contains("TextButton(onClick = { expanded = true }"))
        assertTrue(source.contains("DropdownMenu("))
        assertTrue(source.contains("Checkbox("))
        assertTrue(viewModel.contains("toggleType"))
        assertTrue(viewModel.contains("toggleStatus"))
        assertTrue(models.contains("val types: Set<String> = emptySet()"))
        assertTrue(models.contains("val statuses: Set<String> = emptySet()"))
        assertTrue(schema.contains("created_at, started_at, finished_at, updated_at"))
    }

    @Test
    fun taskCenterRowsShowTimingFields() {
        val source = File("src/main/kotlin/com/dailysatori/ui/feature/settings/taskcenter/TaskCenterScreen.kt").readText()
        val models = File("../shared/src/commonMain/kotlin/com/dailysatori/service/asynctask/AsyncTaskModels.kt").readText()

        assertTrue(models.contains("val createdAt: Long"))
        assertTrue(models.contains("val startedAt: Long?"))
        assertTrue(models.contains("val finishedAt: Long?"))
        assertTrue(models.contains("val checkpointJson: String"))
        assertTrue(source.contains("执行时间"))
        assertTrue(source.contains("耗时"))
        assertFalse(source.contains("LinearProgressIndicator("))
        assertTrue(source.contains("TaskCenterSummaryBand"))
        assertTrue(source.contains("TaskCenterTaskMetric"))
        assertFalse(source.substringAfter("private fun TaskCenterSummaryBand").substringBefore("@Composable\nprivate fun TaskCenterTaskMetric").contains("border = BorderStroke"))
        assertFalse(source.substringAfter("private fun TaskCenterTaskCard").substringBefore("@Composable\nprivate fun TaskCenterStatusPill").contains("border = BorderStroke"))
        assertFalse(source.contains("TaskCenterStatusDot"))
        assertTrue(source.contains("taskCenterListSummary"))
        assertTrue(source.contains("读取到"))
    }

    @Test
    fun taskCenterScreenHasTaskDetailAndDiagnosticLog() {
        val screen = File("src/main/kotlin/com/dailysatori/ui/feature/settings/taskcenter/TaskCenterScreen.kt").readText()
        val viewModel = File("src/main/kotlin/com/dailysatori/ui/feature/settings/taskcenter/TaskCenterViewModel.kt").readText()

        assertTrue(screen.contains("TaskCenterTaskDetail"))
        assertTrue(screen.contains("onOpen"))
        assertTrue(screen.contains("诊断日志"))
        assertTrue(viewModel.contains("selectedTask"))
        assertTrue(viewModel.contains("taskLog"))
        assertTrue(viewModel.contains("openTask"))
        assertTrue(viewModel.contains("selectedTaskId"))
        assertTrue(viewModel.contains("repository.observeTaskById(id)"))
        assertTrue(viewModel.contains("logStore.observe(id)"))
        assertFalse(viewModel.contains("selected.value = repository.getById(taskId) to logStore.read(taskId)"))
    }

    @Test
    fun taskDetailUsesTopBackAndSupportsFormattedJsonAndSplitHttpLogs() {
        val screen = File("src/main/kotlin/com/dailysatori/ui/feature/settings/taskcenter/TaskCenterScreen.kt").readText()

        assertTrue(screen.contains("onBack = if (state.selectedTask == null) onBack else viewModel::closeTask"))
        assertTrue(screen.contains("BackHandler(enabled = state.selectedTask != null)"))
        assertFalse(screen.contains("IconButton(onClick = onBack)"))
        assertTrue(screen.contains("TaskCenterJsonSection"))
        assertTrue(screen.contains("格式化"))
        assertTrue(screen.contains("Json.parseToJsonElement"))
        assertTrue(screen.contains("prettyPrintIndent = \"  \""))
        assertTrue(screen.contains("TaskCenterHttpLogSection"))
        assertTrue(screen.contains("TaskCenterHttpLogCard"))
        assertTrue(screen.contains("SelectionContainer"))
        assertTrue(screen.contains("FontFamily.Monospace"))
        assertTrue(screen.contains("horizontalScroll(rememberScrollState())"))
        assertFalse(screen.contains("格式化 JSON"))
        assertFalse(screen.contains("Text(if (expandedResponse)"))
        assertTrue(screen.contains("TaskCenterHttpMetaGrid"))
        assertTrue(screen.contains("TaskCenterHttpParamsGrid"))
        assertTrue(screen.contains("requestParamRows"))
        assertFalse(screen.contains("\"Params\" to entry.requestParams"))
        assertFalse(screen.contains("TaskCenterHttpBodyBlock"))
        assertTrue(screen.contains("TaskCenterHttpBodyViewer"))
        assertTrue(screen.contains("查看 Body"))
        assertTrue(screen.contains("bodyDisplayLines"))
        assertTrue(screen.contains("responseBody"))
        assertTrue(screen.contains("if (entry.responseBody.isNotBlank())"))
        assertTrue(screen.contains("rememberScrollState()"))
        assertTrue(screen.contains("taskCenterHttpLogEntries"))
        assertTrue(screen.contains("HTTP 请求"))
        assertTrue(screen.contains("HTTP 响应"))
        assertTrue(screen.contains("\"耗时\" to entry.durationText"))
    }

    @Test
    fun httpBodyViewerWrapsLongLinesInsteadOfHorizontalScrolling() {
        val screen = File("src/main/kotlin/com/dailysatori/ui/feature/settings/taskcenter/TaskCenterScreen.kt").readText()
        val viewer = screen.substringAfter("private fun TaskCenterHttpBodyViewer")
            .substringBefore("private fun bodyDisplayLines")

        assertFalse(viewer.contains("horizontalScroll(rememberScrollState())"))
        assertTrue(viewer.contains("modifier = Modifier.fillMaxWidth()"))
    }

    @Test
    fun httpBodyViewerKeepsTaskLifecycleLogsOutOfBodyAndFormatsJson() {
        val entries = taskCenterHttpLogEntries(
            """
            2026-06-28 10:00:00 HTTP request [x-bookmarks] GET https://api.x.com/2/users/me/bookmarks params=max_results=100
            2026-06-28 10:00:01 HTTP response [x-bookmarks] status=200 headers=content-type=application/json body={"data":[{"id":"1","text":"hello"}],"meta":{"next_token":"n"}}
            2026-06-28 10:00:02 TASK succeeded
            """.trimIndent(),
        )

        assertEquals(1, entries.size)
        assertEquals("""{"data":[{"id":"1","text":"hello"}],"meta":{"next_token":"n"}}""", entries.single().responseBody)
        assertFalse(entries.single().responseBody.contains("TASK succeeded"))
        assertTrue(entries.single().formattedResponseBody().contains("\n  \"data\": ["))
    }

    @Test
    fun httpLogEntryShowsRequestToResponseDuration() {
        val entries = taskCenterHttpLogEntries(
            """
            2026-06-28T10:00:00.000Z HTTP request [x-bookmarks] GET https://api.x.com/2/users/me/bookmarks params=max_results=100
            2026-06-28T10:00:01.250Z HTTP response [x-bookmarks] status=200 headers=content-type=application/json body={"data":[]}
            """.trimIndent(),
        )

        assertEquals("1.2s", entries.single().durationText)
    }
}
