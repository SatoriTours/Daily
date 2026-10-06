package com.dailysatori.ui.component.settings

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsSearchTest {
    @Test fun matchesTrimmedTermsAcrossPublicFields() {
        assertTrue(matchesSettingsQuery(" OPENAI  mini ", "OpenAI", "gpt-mini"))
        assertTrue(matchesSettingsQuery(" ", "any source"))
        assertFalse(matchesSettingsQuery("openai missing", "OpenAI", "gpt-mini"))
    }
}
