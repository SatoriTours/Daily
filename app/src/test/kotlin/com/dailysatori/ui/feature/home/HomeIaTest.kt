package com.dailysatori.ui.feature.home

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HomeIaTest {
    @Test
    fun homeTabsUseWeChatStyleInformationArchitecture() {
        assertEquals(listOf("今日", "日记", "读书", "AI"), tabs.map { it.label })
        assertEquals(0, TODAY_TAB_INDEX)
        assertEquals(1, DIARY_TAB_INDEX)
        assertEquals(2, READING_TAB_INDEX)
        assertEquals(3, AI_CHAT_TAB_INDEX)
        assertTrue(tabs.indices.all(::homeBottomBarVisibleForTab))
    }

    @Test
    fun homeScreenRoutesTopLevelTabsToExpectedSurfaces() {
        val source = File("src/main/kotlin/com/dailysatori/ui/feature/home/HomeScreen.kt").readText()

        assertTrue(source.contains("TODAY_TAB_INDEX -> UnifiedNewsScreen"))
        assertTrue(source.contains("DIARY_TAB_INDEX -> DiaryScreen"))
        assertTrue(source.contains("READING_TAB_INDEX -> BooksScreen"))
        assertTrue(source.contains("AI_CHAT_TAB_INDEX -> AiChatScreen"))
        assertFalse(source.contains("RECORDS_TAB_INDEX"))
        assertFalse(source.contains("RecordsScreen"))
        assertFalse(source.contains("TabItem(\"记录\""))
        assertFalse(source.contains("TabItem(\"新闻汇总\""))
    }

    @Test
    fun selectedBookStillSwitchesToReadingTab() {
        val source = File("src/main/kotlin/com/dailysatori/ui/feature/home/HomeScreen.kt").readText()

        assertTrue(source.contains("if (selectedBookId != null) selectedIndex = READING_TAB_INDEX"))
    }

    @Test
    fun bottomBarUsesCompactFloatingLiquidGlassStyle() {
        val source = File("src/main/kotlin/com/dailysatori/ui/feature/home/HomeScreen.kt").readText()

        assertTrue(source.contains("private val HomeBottomBarHeight = Height.navBar"))
        assertTrue(source.contains("private val HomeBottomBarIconSize = IconSize.xl"))
        assertTrue(source.contains("RoundedCornerShape(Radius.circular)"))
        assertFalse(source.contains("BorderStroke("))
        assertFalse(source.contains("border ="))
        assertTrue(source.contains(".navigationBarsPadding()"))
        assertFalse(source.contains("label = { Text(tab.label"))
        assertTrue(source.contains("label = null"))
        assertTrue(source.contains("indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)"))
        assertTrue(source.contains("consumeWindowInsets(innerPadding)"))
        assertTrue(source.contains("alwaysShowLabel = false"))
        assertTrue(source.contains("contentDescription = tab.label"))
    }

    @Test
    fun tabContentKeepsFloatingBottomBarOverlayArea() {
        val source = File("src/main/kotlin/com/dailysatori/ui/feature/home/HomeScreen.kt").readText()
        val tabContent = source.extractCallBlock("Crossfade(")

        assertTrue(tabContent.contains("modifier = Modifier.fillMaxSize()"))
        assertFalse(tabContent.contains("padding(bottom ="))
        assertFalse(source.contains("homeBottomBarContentBottomPadding"))
    }

    @Test
    fun settingsPageHidesHomeBottomBarOverlay() {
        val source = File("src/main/kotlin/com/dailysatori/ui/feature/home/HomeScreen.kt").readText()
        val bottomBarGate = source.substringAfter("if (!showMy && homeBottomBarVisibleForTab(selectedIndex))")

        assertTrue(bottomBarGate.contains("HomeBottomBarSurface("))
        assertFalse(source.contains("if (homeBottomBarVisibleForTab(selectedIndex))"))
    }

    private fun String.extractCallBlock(anchor: String): String {
        assertTrue(contains(anchor), "Missing call anchor: $anchor")
        val start = indexOf(anchor)
        val bodyStart = indexOf('{', start)
        assertTrue(bodyStart >= 0, "Missing block body for call anchor: $anchor")

        var depth = 0
        for (index in bodyStart until length) {
            when (this[index]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return substring(start, index + 1)
                }
            }
        }
        throw AssertionError("Missing closing brace for call anchor: $anchor")
    }
}
