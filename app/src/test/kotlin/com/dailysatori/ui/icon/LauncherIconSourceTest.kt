package com.dailysatori.ui.icon

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class LauncherIconSourceTest {
    @Test
    fun launcherIconUsesBlackBackgroundAndWhiteClockLayers() {
        val background = File("src/main/res/drawable/ic_launcher_background.xml").readText()
        val foreground = File("src/main/res/drawable/ic_launcher_foreground.xml").readText()

        assertTrue(background.contains("#000000"))
        assertTrue(foreground.contains("#FFFFFF"))
        assertTrue(foreground.contains("#000000"))
        assertTrue(foreground.contains("M54,31"))
        assertTrue(foreground.contains("M54,27v22"))
        assertTrue(foreground.contains("M57,56l14,15"))
        assertTrue(foreground.contains("android:strokeLineCap=\"round\""))
    }

    @Test
    fun monochromeIconKeepsRingShape() {
        val monochrome = File("src/main/res/drawable/ic_launcher_monochrome.xml").readText()

        assertTrue(monochrome.contains("M54,31"))
        assertTrue(monochrome.contains("android:strokeWidth=\"7\""))
        assertTrue(monochrome.contains("android:strokeLineCap=\"round\""))
    }
}
