package com.dailysatori.ui.feature.settings.taskcenter

import com.dailysatori.core.task.AsyncTaskHttpLogWriter
import com.dailysatori.core.task.AsyncTaskLogStore
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
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
        assertTrue(source.contains("onBack = when {"))
        assertTrue(source.contains("else -> viewModel::closeTask"))
        assertTrue(source.contains("BackHandler(enabled = selected != null)"))
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
        assertTrue(taskFailureIsSuperseded(110, "cancelled", 118, "succeeded"))
        assertFalse(taskFailureIsSuperseded(118, "succeeded", 118, "succeeded"))
        assertFalse(taskFailureIsSuperseded(110, "failed", 119, "failed"))
        assertFalse(taskFailureIsSuperseded(110, "cancelled", 119, "failed"))
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
        assertTrue(detail.contains("sourceView.responseTab"))
        assertTrue(detail.contains("sourceView.errorTab"))
        assertTrue(detail.contains("sourceView.startedLabel"))
        assertTrue(detail.contains("sourceView.durationLabel"))
        val source = File("src/main/kotlin/com/dailysatori/ui/feature/settings/taskcenter/TaskCenterRecordSource.kt").readText()
        assertTrue(source.contains("R.string.task_center_request_tab_response"))
        assertTrue(source.contains("R.string.task_center_request_tab_error"))
        assertTrue(source.contains("R.string.task_center_error_requested_at"))
        assertTrue(source.contains("R.string.task_center_error_responded_at"))
        assertTrue(detail.contains("TaskCenterRecordSource.Http"))
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
            2026-06-28 10:00:00 HTTP request [x-bookmarks] GET https://api.x.com/2/users/me/bookmarks params=max_results=100 requestId=44444444-4444-4444-4444-444444444444 attempt=1
            2026-06-28 10:00:01 HTTP response [x-bookmarks] status=200 headers=content-type=application/json body={"data":[{"id":"1","text":"hello"}]} requestId=44444444-4444-4444-4444-444444444444 attempt=1
            2026-06-28 10:00:02 TASK succeeded
            """.trimIndent(),
        )
        assertEquals(1, entries.size)
        assertTrue(entries.single().paired)
        assertFalse(entries.single().responseBody.contains("TASK succeeded"))
        assertTrue(entries.single().formattedResponseBody().contains("\n  \"data\": ["))
    }

    @Test
    fun httpLogEntryShowsRequestToResponseOnlyWhenIdentityMatched() {
        val entries = taskCenterHttpLogEntries(
            """
            2026-06-28T10:00:00.000Z HTTP request [x-bookmarks] GET https://api.x.com/2/users/me/bookmarks params=max_results=100 requestId=55555555-5555-5555-5555-555555555555 attempt=1
            2026-06-28T10:00:01.250Z HTTP response [x-bookmarks] status=200 headers=content-type=application/json body={"data":[]} requestId=55555555-5555-5555-5555-555555555555 attempt=1
            """.trimIndent(),
        )
        assertEquals("1.2s", entries.single().durationLabel(testStrings()))
    }

    @Test
    fun concurrentRealFormatCallsPairByExplicitIdentityNeverByOrder() {
        val entries = taskCenterHttpLogEntries(
            """
            2026-06-28T10:00:03.000Z HTTP request {method=POST, service=api.openai.com, route=/v1/chat/completions} params=[omitted] label=external_favorite_ai requestId=66666666-6666-6666-6666-666666666666 attempt=1
            2026-06-28T10:00:00.000Z HTTP request {method=GET, service=api.x.com, route=/2/users/me/bookmarks} params=[omitted] label=bookmarks requestId=77777777-7777-7777-7777-777777777777 attempt=1
            2026-06-28T10:00:01.000Z HTTP response status=200 headers=[omitted] body=[omitted] label=bookmarks requestId=77777777-7777-7777-7777-777777777777 attempt=1
            2026-06-28T10:00:07.000Z HTTP response status=500 headers=[omitted] body=[omitted] label=external_favorite_ai requestId=66666666-6666-6666-6666-666666666666 attempt=1
            2026-06-28T10:00:09.000Z HTTP response status=204 headers=[omitted] body=[omitted]
            """.trimIndent(),
        )
        assertEquals(3, entries.size, "two requests plus one standalone unknown response")
        assertEquals("77777777-7777-7777-7777-777777777777", entries[0].requestId)
        assertEquals("200", entries[0].responseStatus)
        assertTrue(entries[0].paired)
        assertEquals("66666666-6666-6666-6666-666666666666", entries[1].requestId)
        assertEquals("500", entries[1].responseStatus)
        assertTrue(entries[1].paired)
        assertEquals("", entries[2].request, "a response without an id is a standalone record")
        assertEquals("204", entries[2].responseStatus)
        assertFalse(entries[2].paired)
    }

    @Test
    fun labelOnlyLegacyLinesAreNotMergedIntoOneRetryChain() {
        val details = taskCenterRequestDetails(
            """
            2026-06-28T10:00:00.000Z HTTP request [external_favorite_ai] POST https://api.openai.com/v1/chat/completions params=externalId=1
            2026-06-28T10:00:01.000Z HTTP response [external_favorite_ai] status=500 body={"error":"boom"}
            2026-06-28T10:00:05.000Z HTTP request [external_favorite_ai] POST https://api.openai.com/v1/chat/completions params=externalId=2
            2026-06-28T10:00:06.000Z HTTP response [external_favorite_ai] status=200 body={"ok":true}
            """.trimIndent(),
        )
        assertEquals(2, details.size)
        assertTrue(details.all { it.associationUnknown })
        assertTrue(details.all { !it.attemptKnown })
        assertTrue(details.all { it.responseStatus.isBlank() })
    }

    @Test
    fun httpLogEntriesStripNullBytesAndPageFromOldestRequest() {
        val page = taskCenterHttpLogPage(
            buildString {
                append('\u0000')
                repeat(80) { index ->
                    append("2026-06-28T10:00:00.000Z HTTP request [post_lookup] GET https://api.x.com/2/tweets/" + index + "\n")
                    append("2026-06-28T10:00:00.250Z HTTP response [post_lookup] status=200 body={\"id\":\"" + index + "\"}\n")
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
    fun httpLogPageReturnsTheRequestedSliceOfRealEntries() {
        val log = buildString {
            repeat(60) { index ->
                append("2026-06-28T10:00:00.000Z HTTP request [post_lookup] GET https://api.x.com/2/tweets/" + index + "\n")
            }
        }
        val first = taskCenterHttpLogPage(log, pageIndex = 0, pageSize = 50)
        assertEquals(50, first.entries.size)
        assertTrue(first.hasNextPage)
        val second = taskCenterHttpLogPage(log, pageIndex = 1, pageSize = 50)
        assertEquals(10, second.entries.size)
        assertFalse(second.hasNextPage)
    }

    @Test
    fun taskDetailLeadsWithNameSourceStatusProgressAndThisRunOnly() {
        val detail = detail()
        assertTrue(detail.contains("TaskCenterDetailHeader("))
        assertTrue(detail.contains("copy.detailRoundTitle"))
        assertTrue(detail.contains("copy.detailRoundEmpty"))
        assertTrue(detail.contains("taskCenterLifecycleRounds"))
        assertTrue(detail.contains("taskCenterOutcomeCounts"))
        assertTrue(detail.contains("copy.detailHistoryTitle"))
        assertTrue(detail.contains("copy.detailHistoryCount"))
        assertTrue(detail.contains("copy.detailOpenLog"))
        assertTrue(detail.contains("taskCenterTaskName"))
        assertFalse(detail.contains("taskCenterRequestDetails(taskLog)"))
        assertFalse(detail.contains("TaskCenterRequestRow"))
    }

    @Test
    fun taskLogPageOwnsRequestRecordsAndParsesThemOffTheMainThread() {
        val log = File("src/main/kotlin/com/dailysatori/ui/feature/settings/taskcenter/TaskCenterTaskLogScreen.kt").readText()
        assertTrue(log.contains("LazyColumn("))
        assertFalse(log.contains(".verticalScroll(rememberScrollState())"))
        assertTrue(log.contains("withContext(Dispatchers.Default)"))
        assertTrue(log.contains("taskCenterCaptureRead(httpLog)"))
        assertTrue(log.contains("taskCenterHttpLogParse(taskLog)"))
        assertTrue(log.contains("taskCenterLogEntries(events"))
        assertTrue(log.contains("taskCenterLogGroups"))
        assertTrue(log.contains("TaskCenterLogSortMenu"))
        assertFalse(log.contains("TaskCenterLogFilterMenu("), "the filter belongs to the app bar, not the content")
        assertTrue(log.contains("if (request == null)"))
        assertTrue(log.contains("TaskCenterRequestScreen("))
        assertFalse(log.contains("onSearchQueryChange"))
    }

    @Test
    fun taskLogSearchAndFilterLiveInTheAppBarWithSortNextToTheTaskName() {
        val screen = screen()
        assertTrue(screen.contains("onSearchQueryChange = if (logOpen)"))
        assertTrue(screen.contains("searchQuery = logQuery"))
        assertTrue(screen.contains("pageActions = if (logOpen)"))
        assertTrue(screen.contains("taskCenterLogFilterAction("))
        assertTrue(screen.contains("R.string.task_center_log_title"))
        val log = File("src/main/kotlin/com/dailysatori/ui/feature/settings/taskcenter/TaskCenterTaskLogScreen.kt").readText()
        assertTrue(log.contains("TaskCenterLogSortMenu(sort, onSort)"))
        assertFalse(log.contains("String.format(copy.logFilter"), "no filter row or repeated count in the content")
    }

    @Test
    fun logFilterActionIsAParameterizedAppBarActionWithItsOwnMenu() {
        val all = taskCenterLogFilterAction(TaskCenterLogFilter.All, "全部", "只看异常", "筛选：%1\$s") {}
        assertEquals("筛选：全部", all.label)
        assertNotNull(all.menuContent)
        assertTrue(all.alwaysVisible)
        val abnormal = taskCenterLogFilterAction(TaskCenterLogFilter.Abnormal, "全部", "只看异常", "筛选：%1\$s") {}
        assertEquals("筛选：只看异常", abnormal.label)
    }

    @Test
    fun realWriterOutputIsParsedWithMetadataAndPairing() {
        val root = kotlin.io.path.createTempDirectory("task-http-real").toFile()
        try {
            val store = AsyncTaskLogStore(root)
            val writer = AsyncTaskHttpLogWriter(store)
            val id = "88888888-8888-8888-8888-888888888888"
            writer.logRequest(90L, "bookmarks", "GET", "https://api.x.com/2/users/me/bookmarks",
                mapOf("max_results" to "100"), requestId = id, attempt = 1)
            writer.logResponse(90L, "bookmarks", 200, emptyMap(), """{"data":[]}""", requestId = id, attempt = 1)
            writer.logRequest(90L, "private-label", "GET", "https://example.com/p?key=canary", emptyMap(),
                requestId = "not-a-uuid", attempt = 1)
            val text = store.read(90L)
            assertTrue(text.contains("service=api.x.com"))
            assertTrue(text.contains("requestId=" + id))
            assertTrue(text.contains("attempt=1"))
            assertFalse(text.contains("canary"))
            assertFalse(text.contains("private-label"), "unsafe labels never enter the text log")

            val details = taskCenterRequestDetails(text)
            assertEquals(2, details.size)
            val matched = details.first()
            assertEquals("bookmarks", matched.label)
            assertEquals("GET", matched.method)
            assertEquals("200", matched.responseStatus)
            assertTrue(matched.attemptKnown)
            assertFalse(matched.associationUnknown)
            // The writer stores a sanitized service and only allow-listed routes; this path is omitted.
            assertEquals("api.x.com", matched.endpointLabel)
            assertEquals("bookmarks", taskCenterRequestTitle(matched, "request"))
            val unmatched = details.last()
            assertTrue(unmatched.associationUnknown)
            assertEquals("", unmatched.responseStatus)
            assertEquals("", unmatched.url)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun textWriterPairsOnlyMatchingIdentityAndKeepsHonestTimes() {
        val root = kotlin.io.path.createTempDirectory("task-http-attempts").toFile()
        try {
            val store = AsyncTaskLogStore(root)
            val writer = AsyncTaskHttpLogWriter(store)
            val id = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"
            writer.logRequest(91L, "bookmarks", "GET", "https://api.x.com/2/users/me/bookmarks",
                emptyMap(), requestId = id, attempt = 1)
            writer.logResponse(91L, "bookmarks", 200, emptyMap(), "{}", requestId = id, attempt = 1)
            writer.logRequest(91L, "bookmarks", "GET", "https://api.x.com/2/users/me/bookmarks",
                emptyMap(), requestId = id, attempt = 2)

            val details = taskCenterRequestDetails(store.read(91L))
            assertEquals(listOf(1, 2), details.map { it.attemptNumber })
            assertTrue(details.all { it.attemptKnown })
            assertEquals("200", details[0].responseStatus)
            assertFalse(details[0].associationUnknown)
            assertNotNull(details[0].requestedAtMs)
            assertNotNull(details[0].respondedAtMs)
            assertNotNull(details[0].durationMs)
            assertEquals("", details[1].responseStatus)
            assertTrue(details[1].associationUnknown)
            assertNotNull(details[1].requestedAtMs)
            assertNull(details[1].respondedAtMs)
            assertNull(details[1].durationMs)
            assertEquals(listOf(details[1]), taskCenterRelatedAttempts(details[0], details))
            assertEquals(emptyList(), taskCenterRelatedAttempts(details[0].copy(requestId = ""), details))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun logPageShowsSearchAndFilterDirectlyWhileOtherPagesKeepDefaults() {
        val screen = screen()
        assertTrue(screen.contains("inlinePageActions = logOpen"))
        assertTrue(screen.contains("searchActionLabel = if (logOpen)"))
        assertTrue(screen.contains("showAssistant = !logOpen"))
        assertTrue(screen.contains("logCopy.logSearchAction"))
        val log = File("src/main/kotlin/com/dailysatori/ui/feature/settings/taskcenter/TaskCenterTaskLogScreen.kt").readText()
        assertTrue(log.contains("alwaysVisible = true"))
        val settings = File("src/main/kotlin/com/dailysatori/ui/component/settings/SettingsScaffold.kt").readText()
        assertTrue(settings.contains("inlinePageActions: Boolean = false"))
        assertTrue(settings.contains("searchActionLabel: String? = null"))
        assertTrue(settings.contains("showAssistant: Boolean = true"))
        assertTrue(settings.contains("alwaysVisible = inlinePageActions"))
        val appBar = File("src/main/kotlin/com/dailysatori/ui/component/appbar/AppTopBar.kt").readText()
        assertTrue(appBar.contains("showAssistant: Boolean = true"))
        val pageAction = File("src/main/kotlin/com/dailysatori/ui/component/appbar/PageAction.kt").readText()
        assertTrue(pageAction.contains("alwaysVisible: Boolean = false"))
        assertTrue(pageAction.contains("actions.filter { it.alwaysVisible }"))
    }

}
