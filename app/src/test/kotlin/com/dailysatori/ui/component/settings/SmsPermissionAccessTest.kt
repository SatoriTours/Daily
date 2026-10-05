package com.dailysatori.ui.component.settings

import kotlin.test.Test
import kotlin.test.assertEquals

class SmsPermissionAccessTest {
    @Test fun firstAttemptRequestsPermission() {
        assertEquals(SmsAccessAction.REQUEST, smsAccessAction(false, false, false))
    }

    @Test fun denialWithoutAnotherSystemPromptAlwaysOffersSettings() {
        repeat(3) { assertEquals(SmsAccessAction.SETTINGS, smsAccessAction(false, true, false)) }
    }

    @Test fun recoverableDenialExplainsBeforeRequestingAgain() {
        assertEquals(SmsAccessAction.EXPLAIN, smsAccessAction(false, true, true))
        assertEquals(SmsAccessAction.EXPLAIN, smsAccessAction(false, false, true))
    }

    @Test fun grantFromSystemSettingsOverridesPreviousDenial() {
        assertEquals(SmsAccessAction.ENABLE, smsAccessAction(true, true, false))
        assertEquals(SmsAccessAction.ENABLE, smsAccessAction(true, false, false))
    }
}
