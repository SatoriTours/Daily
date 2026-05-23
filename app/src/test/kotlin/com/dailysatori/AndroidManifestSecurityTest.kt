package com.dailysatori

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AndroidManifestSecurityTest {
    @Test
    fun manifestDisablesBackupForSensitiveAppData() {
        val manifest = File("src/main/AndroidManifest.xml").readText()

        assertTrue(manifest.contains("android:allowBackup=\"false\""))
        assertFalse(manifest.contains("android:allowBackup=\"true\""))
    }

    @Test
    fun manifestDoesNotAllowCleartextTrafficGlobally() {
        val manifest = File("src/main/AndroidManifest.xml").readText()

        assertFalse(manifest.contains("android:usesCleartextTraffic=\"true\""))
    }
}
