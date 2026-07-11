package com.dailysatori.platform

import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FileManagerPathSafetyTest {
    @Test
    fun appDataContainmentRejectsSiblingPrefixesAndParentEscapes() {
        val parent = createTempDirectory().toFile()
        val root = parent.resolve("DailySatori").apply { mkdirs() }

        assertTrue(isPathWithinDirectory(root.path, root.resolve("diary_images/recording.m4a").path))
        assertFalse(isPathWithinDirectory(root.path, parent.resolve("DailySatori-backup/recording.m4a").path))
        assertFalse(isPathWithinDirectory(root.path, root.resolve("../outside/recording.m4a").path))
    }
}
