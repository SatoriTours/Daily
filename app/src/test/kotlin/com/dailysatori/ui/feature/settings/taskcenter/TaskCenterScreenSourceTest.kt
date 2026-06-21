package com.dailysatori.ui.feature.settings.taskcenter

import java.io.File
import kotlin.test.Test
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

        assertFalse(source.contains("默认显示未完成任务"))
        assertTrue(source.contains("任务类型"))
        assertTrue(source.contains("任务状态"))
        assertTrue(source.contains("历史任务"))
        assertTrue(source.contains("showTerminal"))
    }
}
