package com.dailysatori.ui.feature.settings.remotenews

import com.dailysatori.service.remotenews.RemoteFeedHistoryState
import com.dailysatori.service.remotenews.RemoteFeedHistoryStatus
import com.dailysatori.service.remotenews.RemoteFeedHistoryStrategy
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RemoteNewsHistorySettingsTest {
    @Test
    fun missingWalkNeverClaimsAFinishedRange() {
        val ui = remoteNewsHistoryUi(null)

        assertEquals(RemoteFeedHistoryStatus.OFF, ui.status)
        assertEquals("未补全历史", remoteNewsHistoryStatusText(ui))
        assertEquals("", remoteNewsHistoryProgressText(ui))
        assertNull(remoteNewsHistoryErrorText(ui))
    }

    @Test
    fun runningWalkShowsImportedCountAndPagesWithoutUnknownPercentage() {
        val ui = remoteNewsHistoryUi(
            RemoteFeedHistoryState(
                strategy = RemoteFeedHistoryStrategy.ALL_HISTORY,
                status = RemoteFeedHistoryStatus.RUNNING,
                importedCount = 1234,
                targetCount = 0,
                pagesRead = 12,
            ),
        )

        assertEquals("补全中", remoteNewsHistoryStatusText(ui))
        assertEquals("本次新增 1234 篇 · 已读取 12 页", remoteNewsHistoryProgressText(ui))
        assertTrue(!remoteNewsHistoryProgressText(ui).contains("%"))
    }

    @Test
    fun windowedWalkNeverClaimsFullHistory() {
        val windowed = remoteNewsHistoryUi(
            RemoteFeedHistoryState(
                strategy = RemoteFeedHistoryStrategy.ALL_HISTORY,
                status = RemoteFeedHistoryStatus.COMPLETE_WINDOW,
                importedCount = 30,
                pagesRead = 1,
            ),
        )
        val full = remoteNewsHistoryUi(
            RemoteFeedHistoryState(
                strategy = RemoteFeedHistoryStrategy.ALL_HISTORY,
                status = RemoteFeedHistoryStatus.COMPLETE,
                importedCount = 812,
                pagesRead = 20,
            ),
        )
        val window = remoteNewsHistoryUi(
            RemoteFeedHistoryState(
                strategy = RemoteFeedHistoryStrategy.LATEST_500,
                status = RemoteFeedHistoryStatus.COMPLETE,
                importedCount = 500,
                targetCount = 500,
                pagesRead = 11,
            ),
        )

        assertEquals("仅近期窗口", remoteNewsHistoryStatusText(windowed))
        assertEquals("全部历史已导入", remoteNewsHistoryStatusText(full))
        assertEquals("最近 500 条已导入", remoteNewsHistoryStatusText(window))
        assertEquals("本次新增 500/500 篇 · 已读取 11 页", remoteNewsHistoryProgressText(window))
    }

    @Test
    fun failureAndBlockedReasonsAreShown() {
        val failed = remoteNewsHistoryUi(
            RemoteFeedHistoryState(
                status = RemoteFeedHistoryStatus.RUNNING,
                importedCount = 42,
                pagesRead = 3,
                lastError = "读取失败：HTTP 503",
            ),
        )
        val blocked = remoteNewsHistoryUi(
            RemoteFeedHistoryState(
                status = RemoteFeedHistoryStatus.BLOCKED,
                blockedReason = "历史分页出现循环，已停止",
            ),
        )

        assertEquals("读取失败：HTTP 503", remoteNewsHistoryErrorText(failed))
        assertEquals("补全受阻", remoteNewsHistoryStatusText(blocked))
        assertEquals("历史分页出现循环，已停止", remoteNewsHistoryErrorText(blocked))
        assertEquals(RemoteNewsHistoryAction.RESUME, remoteNewsHistoryAction(blocked, sourceEnabled = true))
    }

    @Test
    fun actionsFollowWalkLifecycle() {
        assertEquals(RemoteNewsHistoryAction.START, remoteNewsHistoryAction(null, sourceEnabled = true))
        assertEquals(
            RemoteNewsHistoryAction.PAUSE,
            remoteNewsHistoryAction(remoteNewsHistoryUi(RemoteFeedHistoryState(status = RemoteFeedHistoryStatus.RUNNING)), true),
        )
        assertEquals(
            RemoteNewsHistoryAction.PAUSE,
            remoteNewsHistoryAction(remoteNewsHistoryUi(RemoteFeedHistoryState(status = RemoteFeedHistoryStatus.PENDING)), true),
        )
        assertEquals(
            RemoteNewsHistoryAction.RESUME,
            remoteNewsHistoryAction(remoteNewsHistoryUi(RemoteFeedHistoryState(status = RemoteFeedHistoryStatus.PAUSED)), true),
        )
        assertEquals(
            RemoteNewsHistoryAction.START,
            remoteNewsHistoryAction(
                remoteNewsHistoryUi(
                    RemoteFeedHistoryState(
                        status = RemoteFeedHistoryStatus.COMPLETE,
                        strategy = RemoteFeedHistoryStrategy.LATEST_500,
                    ),
                ),
                sourceEnabled = true,
            ),
        )
        // A disabled subscription offers no new download, but an open walk must still be pausable.
        assertEquals(RemoteNewsHistoryAction.NONE, remoteNewsHistoryAction(null, sourceEnabled = false))
        assertEquals(
            RemoteNewsHistoryAction.PAUSE,
            remoteNewsHistoryAction(remoteNewsHistoryUi(RemoteFeedHistoryState(status = RemoteFeedHistoryStatus.RUNNING)), false),
        )

        assertEquals("补全历史", remoteNewsHistoryActionLabel(RemoteNewsHistoryAction.START, null))
        assertEquals(
            "重新补全",
            remoteNewsHistoryActionLabel(
                RemoteNewsHistoryAction.START,
                remoteNewsHistoryUi(RemoteFeedHistoryState(status = RemoteFeedHistoryStatus.COMPLETE)),
            ),
        )
        assertEquals("暂停", remoteNewsHistoryActionLabel(RemoteNewsHistoryAction.PAUSE, null))
        assertEquals("继续", remoteNewsHistoryActionLabel(RemoteNewsHistoryAction.RESUME, null))
        assertEquals("", remoteNewsHistoryActionLabel(RemoteNewsHistoryAction.NONE, null))
    }

    @Test
    fun firstImportRangeIsExplicitForNewSourcesOnly() {
        assertEquals("全部历史", remoteNewsHistoryRangeText(RemoteFeedHistoryStrategy.ALL_HISTORY))
        assertEquals("最近 500 条", remoteNewsHistoryRangeText(RemoteFeedHistoryStrategy.LATEST_500))
        assertEquals(RemoteFeedHistoryStrategy.ALL_HISTORY, RemoteNewsSettingsState().historyStrategy)
        assertEquals(RemoteFeedHistoryStrategy.ALL_HISTORY, RemoteNewsDraft().historyStrategy)

        val viewModel = settingsViewModelSource()
        // The first-import range is written with the new subscription, never re-applied on edit.
        assertTrue(viewModel.contains("sourceRepo.save(editingId, name, baseUrl, legacyApiToken, enabled, form.historyStrategy.dbValue)"))
        assertTrue(viewModel.contains("historyRepo.start(id, RemoteFeedHistoryStrategy.ALL_HISTORY"))
        assertTrue(viewModel.contains("historyRepo.pause(id, nowMillis())"))
        assertTrue(viewModel.contains("historyRepo.resume(id, nowMillis())"))
    }

    @Test
    fun screenWiresHistoryStateAndActionsIntoExistingCards() {
        val screen = settingsScreenSource()

        assertTrue(screen.contains("historyUi = state.historyBySourceId[source.id]"))
        assertTrue(screen.contains("onStartHistory = { viewModel.startHistory(source.id) }"))
        assertTrue(screen.contains("onPauseHistory = { viewModel.pauseHistory(source.id) }"))
        assertTrue(screen.contains("onResumeHistory = { viewModel.resumeHistory(source.id) }"))
        assertTrue(screen.contains("RemoteNewsHistoryLine(historyUi)"))
        assertTrue(screen.contains("remoteNewsHistoryStatusText(ui)"))
        assertTrue(screen.contains("remoteNewsHistoryErrorText(historyUi)"))
        assertTrue(screen.contains("viewModel.updateHistoryStrategy(strategy)"))
    }

    private fun settingsViewModelSource(): String =
        File("src/main/kotlin/com/dailysatori/ui/feature/settings/remotenews/RemoteNewsSettingsViewModel.kt").readText()

    private fun settingsScreenSource(): String =
        File("src/main/kotlin/com/dailysatori/ui/feature/settings/remotenews/RemoteNewsSettingsScreen.kt").readText()
}
