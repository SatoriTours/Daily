package com.dailysatori.ui.feature.settings.taskcenter

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TaskCenterScreenSourceTest {
    private fun screen() = File("src/main/kotlin/com/dailysatori/ui/feature/settings/taskcenter/TaskCenterScreen.kt").readText()
    private fun detail() = File("src/main/kotlin/com/dailysatori/ui/feature/settings/taskcenter/TaskCenterTaskDetail.kt").readText()
    private fun jsonView() = File("src/main/kotlin/com/dailysatori/ui/feature/settings/taskcenter/TaskCenterJsonView.kt").readText()
    private fun diagnostics() = File("src/main/kotlin/com/dailysatori/ui/feature/settings/taskcenter/TaskCenterHttpDiagnostics.kt").readText()
    private fun capture() = File("src/main/kotlin/com/dailysatori/ui/feature/settings/taskcenter/TaskCenterHttpCapture.kt").readText()
    private fun viewModel() = File("src/main/kotlin/com/dailysatori/ui/feature/settings/taskcenter/TaskCenterViewModel.kt").readText()

    @Test
    fun failureAndRetryEventsExplainTheReasonAndRetryTime() {
        val summary = taskCenterLifecycleText(
            """
            2026-10-06T08:20:00Z TASK failed code=opportunity_context_changed message=关注点或思想已更新，请重新分析
            2026-10-06T08:21:00Z TASK retry code=timeout message=请求超时 runAfterMs=1791274920000
            """.trimIndent(),
            testStrings(),
        )
        assertTrue(summary.contains("关注点或思想已更新，请重新分析"))
        assertTrue(summary.contains("请求超时"))
        assertTrue(summary.contains("retry at"))
        assertFalse(summary.contains("T08:"))
    }

    @Test
    fun lifecycleSummarySeparatesEventsFromHttpAndCheckpointData() {
        val log = """
            2026-09-06T00:00:00Z TASK started type=save_article
            2026-09-06T00:00:01Z HTTP request [api] GET https://example.com
            2026-09-06T00:00:02Z TASK progress current=1 total=2 message=正在整理 checkpoint={"private":"data"}
            2026-09-06T00:00:03Z TASK retry code=network
        """.trimIndent()
        val summary = taskCenterLifecycleText(log, testStrings())
        assertTrue(summary.contains("started"))
        assertTrue(summary.contains("正在整理"))
        assertTrue(summary.contains("retry"))
        assertFalse(summary.contains("example.com"))
        assertFalse(summary.contains("private"))
    }

    @Test
    fun mySpaceKeepsTaskEntryAndBadgesFailuresNeedingAttention() {
        val navigation = File("src/main/kotlin/com/dailysatori/core/navigation/NavHost.kt").readText()
        val mySpace = File("src/main/kotlin/com/dailysatori/ui/feature/myspace/MySpaceScreen.kt").readText()
        assertTrue(mySpace.contains("MyQuickActions(profileState, onAddReminder, onChat, onFavorites, onTasks)"))
        assertFalse(mySpace.contains("management_failed_tasks"))
        assertTrue(mySpace.contains("badgeCount = state.taskAttentionCount, activeTaskCount = state.activeTaskCount"))
        assertFalse(mySpace.contains("showBadge = state.activeTaskCount > 0)"))
        assertTrue(navigation.contains("onTasks = { navController.navigate(TaskCenterRoute()) }"))
        assertTrue(navigation.contains("recentFailures = entry.toRoute<TaskCenterRoute>().recentFailures"))
    }

    @Test
    fun taskCenterUsesThreeTabsAndCollapsibleFiltersWithLocalizedCopy() {
        val source = screen()
        assertTrue(source.contains("TaskCenterTab.entries.forEach"))
        assertTrue(source.contains("viewModel::toggleFilters"))
        assertTrue(source.contains("if (state.filtersExpanded)"))
        assertTrue(source.contains("copy.filterType"))
        assertTrue(source.contains("copy.filterStatus"))
        assertTrue(source.contains("copy.tabCurrent"))
        assertTrue(source.contains("copy.loadingMore"))
        assertTrue(source.contains("TaskCenterFilterBar"))
        assertTrue(source.contains("TextButton(onClick = { expanded = true }"))
        assertTrue(source.contains("DropdownMenu("))
        assertTrue(source.contains("Checkbox("))
        assertFalse(source.contains("Switch("))
        assertFalse(source.contains("showTerminal"))
        assertTrue(viewModel().contains("selectTab"))
    }

    @Test
    fun tabCountersComeFromRealTotalsInsteadOfTheLoadedPage() {
        val vm = viewModel()
        assertTrue(vm.contains("observeTaskOverview"))
        assertTrue(vm.contains("activeTotal = overviewCounts.activeCount"))
        assertTrue(vm.contains("pendingTotal = attentionTasks.size.toLong()"))
        assertTrue(screen().contains("TaskCenterTab.History -> null"))
        assertTrue(screen().contains("taskCenterTabCountText"))
        assertFalse(screen().contains("tasks.size.toString()"))
    }

    @Test
    fun taskCenterRowsShowRealReasonAndTiming() {
        val source = screen()
        assertTrue(source.contains("taskCenterRowText(task, copy.strings)"))
        assertTrue(source.contains("taskCenterRowMetaText(task, row.retryAtMs, copy)"))
        assertTrue(source.contains("copy.metaElapsed"))
        assertTrue(source.contains("copy.metaWaited"))
        assertFalse(source.contains("LinearProgressIndicator("))
        assertFalse(source.contains("TaskCenterStatusDot"))
        assertFalse(source.contains("border = BorderStroke"))
        assertTrue(source.contains("progress.fraction"))
    }

    @Test
    fun taskCenterScreenHasTaskDetailAndDiagnosticLog() {
        val source = screen()
        val vm = viewModel()
        assertTrue(source.contains("onBack = if (state.selectedTask == null) onBack else viewModel::closeTask"))
        assertTrue(source.contains("BackHandler(enabled = state.selectedTask != null)"))
        assertTrue(source.contains("TaskCenterTaskDetail("))
        assertTrue(detail().contains("copy.detailLog"))
        assertTrue(detail().contains("copy.detailDiagnostics"))
        assertTrue(detail().contains("copy.detailSuperseded"))
        assertTrue(vm.contains("repository.observeTaskById(id)"))
        assertTrue(vm.contains("logStore.observe(id)"))
        assertTrue(vm.contains("logStore.observeHttp(id)"))
        assertTrue(vm.contains("openTask"))
        assertTrue(vm.contains("selectedTaskId"))
    }

    @Test
    fun laterSuccessfulTaskMarksOldFailureAsSuperseded() {
        assertTrue(taskFailureIsSuperseded(110, "failed", 118, "succeeded"))
        assertFalse(taskFailureIsSuperseded(118, "succeeded", 118, "succeeded"))
        assertFalse(taskFailureIsSuperseded(110, "failed", 119, "failed"))
    }

    @Test
    fun taskCenterLoadsTasksIncrementally() {
        val repository = File("../shared/src/commonMain/kotlin/com/dailysatori/data/repository/AsyncTaskRepository.kt").readText()
        val schema = File("../shared/src/commonMain/sqldelight/com/dailysatori/shared/db/DailySatori.sq").readText()
        assertTrue(schema.contains("selectAsyncTasksForTaskCenterPage:"))
        assertTrue(repository.contains("data class AsyncTaskCenterPage"))
        assertTrue(viewModel().contains("fun loadMore()"))
        assertTrue(screen().contains("LaunchedEffect(loadedCount, requestedLimit)"))
        assertTrue(screen().contains("viewModel.loadMore()"))
        assertTrue(screen().contains("copy.loadingMore"))
    }

    @Test
    fun taskDetailOffersThreePanelsWithLocalizedCopy() {
        val detail = detail()
        assertTrue(detail.contains("RequestTab.Request"))
        assertTrue(detail.contains("RequestTab.Response"))
        assertTrue(detail.contains("RequestTab.ErrorTiming"))
        assertTrue(detail.contains("copy.requestAddress"))
        assertTrue(detail.contains("copy.requestQuery"))
        assertTrue(detail.contains("copy.requestCopyCurl"))
        assertTrue(detail.contains("copy.responseCopy"))
        assertTrue(detail.contains("copy.responseCopyDiagnostic"))
        assertTrue(detail.contains("copy.errorTiming"))
        assertTrue(detail.contains("copy.errorQueueTime"))
        assertTrue(detail.contains("copy.errorNetworkTime"))
    }

    @Test
    fun captureCardReadsRealSessionAndRecordStateInsteadOfGuessingHistory() {
        val detail = detail()
        assertTrue(detail.contains("R.string.task_center_capture_confirm_title"))
        assertTrue(detail.contains("R.string.task_center_capture_confirm_body"))
        assertTrue(detail.contains("copy.captureScope"))
        assertTrue(detail.contains("copy.captureSessionArmedTerminal"))
        assertTrue(detail.contains("copy.captureNoRecords"))
        assertTrue(detail.contains("copy.captureRecordsCount"))
        assertTrue(detail.contains("captureState as? TaskHttpCaptureState.Armed"))
        assertTrue(detail.contains("taskCenterCaptureRead(httpLog).requests.size"))
        assertFalse(detail.contains("copy.captureOff"))
        assertFalse(detail.contains("copy.captureActiveNote"))
        val vm = viewModel()
        assertTrue(vm.contains("TaskHttpCapture.arm(taskId"))
        assertTrue(vm.contains("TaskHttpCapture.disarm(taskId"))
        assertTrue(vm.contains("TaskHttpCapture.state(it.id)"))
    }

    @Test
    fun taskExportUsesTheTaskScopedServiceAfterConfirmation() {
        val detail = detail()
        assertTrue(detail.contains("TaskDiagnosticsExportService"))
        assertTrue(detail.contains("copy.exportConfirmTitle"))
        assertTrue(detail.contains("copy.exportConfirmBody"))
        assertTrue(detail.contains("exportService.write(task.id, output)"))
        assertTrue(detail.contains("FileProvider.getUriForFile"))
        assertTrue(detail.contains("target.delete()"))
        assertTrue(detail.contains("withContext(Dispatchers.IO)"))
        assertFalse(detail.contains("DiagnosticExporter"))
        assertFalse(detail.contains("readHttp(taskId)"))
    }

    @Test
    fun jsonBodiesArePrettyPrintedByDefaultWithLineNumbersFoldingAndRawToggle() {
        val json = jsonView()
        assertTrue(json.contains("prettyPrintIndent = \"  \""))
        assertTrue(json.contains("Json.parseToJsonElement"))
        assertTrue(json.contains("taskCenterJsonFolds"))
        assertTrue(json.contains("taskCenterJsonVisibleLines"))
        assertTrue(json.contains("taskCenterJsonTokens"))
        assertTrue(json.contains("SelectionContainer"))
        assertTrue(json.contains("softWrap = true"))
        assertTrue(json.contains("copy.viewerFoldAll"))
        assertTrue(json.contains("copy.viewerFullscreen"))
        assertFalse(json.contains("horizontalScroll(rememberScrollState())"))
    }

    @Test
    fun httpBodyViewerKeepsTaskLifecycleLogsOutOfBodyAndFormatsJson() {
        val entries = taskCenterHttpLogEntries(
            """
            2026-06-28 10:00:00 HTTP request [x-bookmarks] GET https://api.x.com/2/users/me/bookmarks params=max_results=100
            2026-06-28 10:00:01 HTTP response [x-bookmarks] status=200 headers=content-type=application/json body={"data":[{"id":"1","text":"hello"}]}
            2026-06-28 10:00:02 TASK succeeded
            """.trimIndent(),
        )
        assertEquals(1, entries.size)
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
        assertEquals("1.2s", entries.single().durationLabel(testStrings()))
    }

    @Test
    fun httpLogEntriesAreSortedByRequestTimeAndConcurrentRequestsDoNotBleedIntoBody() {
        val entries = taskCenterHttpLogEntries(
            """
            2026-06-28T10:00:03.000Z HTTP request [external_favorite_ai] POST https://api.openai.com/v1/chat/completions params=externalId=2
            2026-06-28T10:00:00.000Z HTTP request [bookmarks] GET https://api.x.com/2/users/me/bookmarks params=max_results=100
            2026-06-28T10:00:01.000Z HTTP response [bookmarks] status=200 body={"data":[]}
            2026-06-28T10:00:02.000Z HTTP request [external_favorite_ai] POST https://api.openai.com/v1/chat/completions params=externalId=1
            2026-06-28T10:00:05.000Z HTTP response [external_favorite_ai] status=200 headers=externalId=1 body={"ok":1}
            """.trimIndent(),
        )
        assertEquals("bookmarks", entries.first().label)
        assertEquals("1", entries[1].requestParams.substringAfter("externalId="))
        assertFalse(entries[1].responseBody.contains("externalId=2"))
    }

    @Test
    fun httpLogEntriesStripNullBytesAndPageFromOldestRequest() {
        val page = taskCenterHttpLogPage(
            buildString {
                append('\u0000')
                repeat(80) { index ->
                    append("2026-06-28T10:00:00.000Z HTTP request [post_lookup] GET https://api.x.com/2/tweets/$index\n")
                    append("2026-06-28T10:00:00.250Z HTTP response [post_lookup] status=200 body={\"id\":\"$index\"}\n")
                }
            },
            pageIndex = 0,
        )
        assertNull(page.totalEntries)
        assertEquals(50, page.entries.size)
        assertFalse(page.entries.first().request.contains('\u0000'))
        assertTrue(page.hasNext)
    }

    @Test
    fun httpLogPageDoesNotBuildAllEntriesBeforePaging() {
        val pageFunction = diagnostics().substringAfter("internal fun taskCenterHttpLogPage(")
            .substringBefore("private fun taskCenterIsLifecycleLogLine")
        assertFalse(pageFunction.contains("taskCenterHttpLogEntries(taskLog)"))
        assertFalse(pageFunction.contains("maxPageIndex"))
    }

    @Test
    fun taskDetailLoadsRequestRecordsOffTheMainThread() {
        val detail = detail()
        assertTrue(detail.contains("LazyColumn("))
        assertFalse(detail.contains(".verticalScroll(rememberScrollState())"))
        assertTrue(detail.contains("withContext(Dispatchers.Default)"))
        assertTrue(detail.contains("taskCenterCaptureRead(httpLog)"))
        assertTrue(detail.contains("copy.detailRequestsMore"))
        assertFalse(detail.contains("下一页"))
    }
}
