package com.dailysatori.ui.icon

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class LauncherIconSourceTest {
    @Test
    fun launcherIconUsesSapphireRingPaletteAndLayers() {
        val background = File("src/main/res/drawable/ic_launcher_background.xml").readText()
        val foreground = File("src/main/res/drawable/ic_launcher_foreground.xml").readText()

        assertTrue(background.contains("#030712"))
        assertTrue(background.contains("#0F172A"))
        assertTrue(background.contains("#1E293B"))
        assertTrue(foreground.contains("#7DD3FC"))
        assertTrue(foreground.contains("#E2E8F0"))
        assertTrue(foreground.contains("#050816"))
        assertTrue(foreground.contains("android:strokeLineCap=\"round\""))
        assertTrue(foreground.contains("android:strokeAlpha=\"0.95\""))
    }

    @Test
    fun monochromeIconKeepsRingShape() {
        val monochrome = File("src/main/res/drawable/ic_launcher_monochrome.xml").readText()

        assertTrue(monochrome.contains("M54,26"))
        assertTrue(monochrome.contains("android:strokeWidth=\"8\""))
        assertTrue(monochrome.contains("android:strokeLineCap=\"round\""))
    }
}
