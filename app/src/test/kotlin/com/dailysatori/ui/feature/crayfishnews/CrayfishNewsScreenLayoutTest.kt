package com.dailysatori.ui.feature.crayfishnews

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CrayfishNewsScreenLayoutTest {
    @Test
    fun retiredStandaloneNewsPagesHaveNoEntryPointAndTodayUsesTheUnifiedFeed() {
        val ui = File("src/main/kotlin/com/dailysatori/ui/feature")
        assertFalse(ui.resolve("crayfishnews/CrayfishNewsScreen.kt").exists())
        assertFalse(ui.resolve("remotenews/RemoteNewsScreen.kt").exists())
        val home = ui.resolve("home/HomeScreen.kt").readText()
        val navigation = File("src/main/kotlin/com/dailysatori/core/navigation/NavHost.kt").readText()
        val active = ui.resolve("unifiednews/UnifiedNewsScreen.kt").readText()

        assertTrue(home.contains("TODAY_TAB_INDEX -> UnifiedNewsScreen("))
        assertTrue(active.contains("RemoteArticleDetailScreen("))
        assertTrue(active.contains("RemoteDigestDetailScreen("))
        listOf(home, navigation, active).forEach {
            assertFalse(it.contains("CrayfishNewsScreen("))
            assertFalse(it.contains("RemoteNewsScreen("))
        }
    }
}
