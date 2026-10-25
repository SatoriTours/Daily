package com.dailysatori.ui.component.appbar

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PageActionTest {
    private fun action(label: String, priority: PageActionPriority = PageActionPriority.Secondary) =
        PageAction(label, Icons.Default.Add, priority = priority) {}

    @Test fun noActionDoesNotInventAMenu() {
        assertEquals(emptyList(), arrangePageActions(emptyList()).direct)
        assertEquals(emptyList(), arrangePageActions(emptyList()).overflow)
    }

    @Test fun soleActionStaysDirectInsteadOfBeingHiddenInMore() {
        val settings = action("设置")
        assertEquals(listOf(settings), arrangePageActions(listOf(settings)).direct)
        assertEquals(emptyList(), arrangePageActions(listOf(settings)).overflow)
    }

    @Test fun multipleBrowsingActionsShareOneNamedOverflow() {
        val actions = listOf(action("添加"), action("搜索"), action("筛选"))
        assertEquals(emptyList(), arrangePageActions(actions).direct)
        assertEquals(actions, arrangePageActions(actions).overflow)
    }

    @Test fun saveRemainsDirectAndEditorToolsCannotCompeteWithIt() {
        val save = action("保存", PageActionPriority.Primary)
        val layout = arrangePageActions(listOf(save))
        assertEquals(listOf(save), layout.direct)
        assertEquals(emptyList(), layout.overflow)
        assertTrue(runCatching { arrangePageActions(listOf(save, action("附件"))) }.isFailure)
    }

    @Test fun twoPrimaryActionsAreRejectedRatherThanSilentlyHidingSave() {
        assertTrue(runCatching {
            arrangePageActions(listOf(action("保存", PageActionPriority.Primary), action("提交", PageActionPriority.Primary)))
        }.isFailure)
    }

    @Test fun primarySaveCannotBeReplacedByAnOverflowMenu() {
        val save = PageAction("保存", Icons.Default.Add, priority = PageActionPriority.Primary, menuContent = {}) {}
        assertTrue(runCatching { arrangePageActions(listOf(save)) }.isFailure)
    }

    @Test fun disabledActionDoesNotDispatchAndEnabledActionDispatchesOnce() {
        var calls = 0
        PageAction("生成", Icons.Default.Add, enabled = false) { calls++ }.perform()
        assertEquals(0, calls)
        val enabled = PageAction("生成", Icons.Default.Add) { calls++ }
        assertFalse(enabled.destructive)
        enabled.perform()
        assertEquals(1, calls)
    }
}
