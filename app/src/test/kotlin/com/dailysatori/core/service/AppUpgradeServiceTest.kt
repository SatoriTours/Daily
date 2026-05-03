package com.dailysatori.core.service

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppUpgradeServiceTest {
    @Test
    fun `version comparison ignores leading v`() {
        assertTrue(AppUpgradeService.isNewerVersion("v5.0.2", "5.0.1"))
        assertFalse(AppUpgradeService.isNewerVersion("v5.0.1", "5.0.1"))
    }

    @Test
    fun `version comparison uses numeric ordering`() {
        assertTrue(AppUpgradeService.isNewerVersion("5.10.0", "5.2.9"))
        assertFalse(AppUpgradeService.isNewerVersion("5.0.0", "5.0.1"))
    }

    @Test
    fun `apk asset selection prefers apk files`() {
        val assets = listOf(
            ReleaseAsset("notes.txt", "https://example.com/notes.txt"),
            ReleaseAsset("daily-satori.apk", "https://example.com/app.apk"),
        )
        assertEquals("https://example.com/app.apk", AppUpgradeService.findApkAsset(assets)?.downloadUrl)
    }
}
