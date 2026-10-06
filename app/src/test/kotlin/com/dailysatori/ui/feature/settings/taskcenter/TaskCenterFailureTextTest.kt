package com.dailysatori.ui.feature.settings.taskcenter

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class TaskCenterFailureTextTest {
    @Test fun legacyOpportunityFailureExplainsContextChangeAndAcknowledgement() {
        val text = taskCenterFailureText("opportunity_analysis_failed", "关注点或思想已更新，请重新分析") { it }
        assertContains(text, "关注点或思想已更新")
        assertContains(text, "task_failure.context_changed")
        assertContains(text, "task_failure.acknowledge_hint")
        assertFalse(text.contains("opportunity_analysis_failed"))
    }

    @Test fun authenticationAndUnknownFailuresKeepTheirReasonsAndAddNextSteps() {
        val auth = taskCenterFailureText("auth_failed", "授权已过期") { it }
        assertContains(auth, "授权已过期")
        assertContains(auth, "task_failure.auth")
        val unknown = taskCenterFailureText("new_failure", "") { it }
        assertContains(unknown, "attention.task_unknown_error")
        assertContains(unknown, "task_failure.general")
    }
}
