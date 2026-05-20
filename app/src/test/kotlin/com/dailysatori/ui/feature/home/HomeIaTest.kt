package com.dailysatori.ui.feature.home

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HomeIaTest {
    @Test
    fun homeTabsUseWeChatStyleInformationArchitecture() {
        assertEquals(listOf("今日", "记录", "读书", "AI"), tabs.map { it.label })
        assertEquals(0, TODAY_TAB_INDEX)
        assertEquals(1, RECORDS_TAB_INDEX)
        assertEquals(2, READING_TAB_INDEX)
        assertEquals(3, AI_CHAT_TAB_INDEX)
        assertTrue(tabs.indices.all(::homeBottomBarVisibleForTab))
    }

    @Test
    fun homeScreenRoutesTopLevelTabsToExpectedSurfaces() {
        val source = File("src/main/kotlin/com/dailysatori/ui/feature/home/HomeScreen.kt").readText()

        assertTrue(source.contains("TODAY_TAB_INDEX -> UnifiedNewsScreen"))
        assertTrue(source.contains("RECORDS_TAB_INDEX -> RecordsScreen"))
        assertTrue(source.contains("READING_TAB_INDEX -> BooksScreen"))
        assertTrue(source.contains("AI_CHAT_TAB_INDEX -> AiChatScreen"))
        assertFalse(source.contains("TabItem(\"日记\""))
        assertFalse(source.contains("TabItem(\"新闻汇总\""))
    }

    @Test
    fun selectedBookStillSwitchesToReadingTab() {
        val source = File("src/main/kotlin/com/dailysatori/ui/feature/home/HomeScreen.kt").readText()

        assertTrue(source.contains("if (selectedBookId != null) selectedIndex = READING_TAB_INDEX"))
    }
}
