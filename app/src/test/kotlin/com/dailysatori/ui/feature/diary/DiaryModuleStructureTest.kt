package com.dailysatori.ui.feature.diary

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DiaryModuleStructureTest {
    @Test
    fun diaryParsingRulesAreCentralized() {
        val cardSource = File("src/main/kotlin/com/dailysatori/ui/component/card/DiaryCard.kt").readText()
        val screenSource = File("src/main/kotlin/com/dailysatori/ui/feature/diary/DiaryScreen.kt").readText()
        val viewModelSource = File("src/main/kotlin/com/dailysatori/ui/feature/diary/DiaryViewModel.kt").readText()
        val helperSource = File("src/main/kotlin/com/dailysatori/core/util/DiaryFormatUtils.kt").readText()

        assertFalse(cardSource.contains("tags?.split(" + '"' + ","))
        assertFalse(cardSource.contains("images?.split(" + '"' + ","))
        assertFalse(screenSource.contains("tags?.split(" + '"' + ","))
        assertFalse(screenSource.contains("images?.split(" + '"' + ","))
        assertFalse(viewModelSource.contains("?.split(" + '"' + ","))
        assertTrue(helperSource.contains("fun diaryTags("))
        assertTrue(helperSource.contains("fun diaryImagePaths("))
    }

    @Test
    fun diaryScreenUsesSharedDateHelpers() {
        val screenSource = File("src/main/kotlin/com/dailysatori/ui/feature/diary/DiaryScreen.kt").readText()
        val feedSource = File("src/main/kotlin/com/dailysatori/ui/feature/diary/DiaryFeedEntries.kt").readText()
        val cardSource = File("src/main/kotlin/com/dailysatori/ui/component/card/DiaryCard.kt").readText()
        val reviewSource = File("src/main/kotlin/com/dailysatori/ui/feature/diary/DiaryMonthReview.kt").readText()

        assertFalse(screenSource.contains("private fun diaryMonthKey"))
        assertFalse(screenSource.contains("private fun diaryDayKey"))
        assertFalse(screenSource.contains("private fun diaryMonthDayLabel"))
        assertFalse(screenSource.contains("private fun diaryDateDayNumber"))
        assertFalse(screenSource.contains("private fun diaryDateMonthLabel"))
        assertFalse(screenSource.contains("private fun diaryDateWeekLabel"))
        assertFalse(screenSource.contains("private fun diaryDateCountLabel"))
        assertFalse(screenSource.contains("private fun diaryRelativeDayLabel"))
        assertFalse(screenSource.contains("private fun toChineseNumber"))
        assertTrue(reviewSource.contains("import com.dailysatori.core.util.diaryMonthKey"))
        assertTrue(cardSource.contains("import com.dailysatori.core.util.diaryCardDateTime"))
        assertFalse(cardSource.contains("fun diaryCardDateTime"))
        assertTrue(feedSource.contains("import com.dailysatori.core.util.diaryDayKey"))
        assertTrue(feedSource.contains("import com.dailysatori.core.util.diaryMonthKey"))
    }
}
