# Project Refactor Phase 2 Diary Cleanup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reduce duplication and mixed responsibilities in the diary module while preserving the existing diary UI, text, grouping, filtering, editing, and image behavior.

**Architecture:** Keep the feature behavior in `DiaryViewModel` and the UI in existing Compose files, but move repeated pure formatting/parsing logic into a focused utility file. The screen and card should call shared helpers instead of each owning tag/image/date parsing rules.

**Tech Stack:** Kotlin, Android Jetpack Compose, SQLDelight generated `Diary`, Gradle, kotlin.test source and pure-function tests.

---

## Scope

This plan implements only Phase 2 from `docs/superpowers/specs/2026-05-31-project-refactor-phased-design.md`.

Do not create a git worktree. Project instructions explicitly prohibit git worktrees.

Do not deploy to an emulator. Install only to a connected phone during final verification.

Do not commit unless the user explicitly asks for commits. The implementation checkpoints below use tests and `git diff` review instead of commits.

Phase 1 changes are currently uncommitted. Do not revert or rewrite them.

## File Structure

### Create

- `app/src/main/kotlin/com/dailysatori/core/util/DiaryFormatUtils.kt`
  - Owns diary tag/image list parsing, card content cleanup, date keys/labels, Chinese number conversion, and month summary copy.
- `app/src/test/kotlin/com/dailysatori/core/util/DiaryFormatUtilsTest.kt`
  - Tests pure diary formatting/parsing behavior with deterministic timestamps.
- `app/src/test/kotlin/com/dailysatori/ui/feature/diary/DiaryModuleStructureTest.kt`
  - Guards that diary parsing/date helper logic stays centralized after the refactor.

### Modify

- `app/src/main/kotlin/com/dailysatori/ui/component/card/DiaryCard.kt`
  - Remove local tag/image parsing and local `stripInlineTags` implementation.
  - Use `DiaryFormatUtils` helpers.
- `app/src/main/kotlin/com/dailysatori/ui/feature/diary/DiaryScreen.kt`
  - Remove private date/month/summary helpers.
  - Use `DiaryFormatUtils` helpers.
  - Preserve existing UI layout and text.
- `app/src/main/kotlin/com/dailysatori/ui/feature/diary/DiaryViewModel.kt`
  - Use shared `diaryTags()` helper for available tag extraction.

---

### Task 1: Add Diary Formatting Regression Tests

**Files:**
- Create: `app/src/test/kotlin/com/dailysatori/core/util/DiaryFormatUtilsTest.kt`
- Create: `app/src/test/kotlin/com/dailysatori/ui/feature/diary/DiaryModuleStructureTest.kt`
- Test: `app/src/test/kotlin/com/dailysatori/core/util/DiaryFormatUtilsTest.kt`
- Test: `app/src/test/kotlin/com/dailysatori/ui/feature/diary/DiaryModuleStructureTest.kt`

- [ ] **Step 1: Write failing pure helper tests**

Create `app/src/test/kotlin/com/dailysatori/core/util/DiaryFormatUtilsTest.kt`:

```kotlin
package com.dailysatori.core.util

import java.util.Calendar
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

class DiaryFormatUtilsTest {
    @Test
    fun parsesDiaryTagsAndImagePathsWithExistingNullRules() {
        assertEquals(listOf("生活", "工作"), diaryTags(" 生活, null, ,工作 "))
        assertEquals(listOf("a.jpg", "b.png"), diaryImagePaths("a.jpg,, null, b.png"))
        assertEquals(emptyList(), diaryTags(null))
    }

    @Test
    fun stripsTrailingInlineTagLinesOnly() {
        val content = "# 标题\n今天很好\n\n#生活 #记录\n#daily"

        assertEquals("# 标题\n今天很好", stripDiaryInlineTags(content))
    }

    @Test
    fun formatsDiaryDateLabelsDeterministically() {
        val time = localMillis(year = 2026, month = 1, day = 15)

        assertEquals("2026-01", diaryMonthKey(time))
        assertEquals("2026-01-15", diaryDayKey(time))
        assertEquals("一月", diaryDateMonthLabel(time))
        assertEquals("15", diaryDateDayNumber(time))
        assertEquals("周四", diaryDateWeekLabel(time))
        assertEquals("1 月 15 日", diaryMonthDayLabel(time))
    }

    @Test
    fun labelsRelativeDaysFromSuppliedNow() {
        val now = localMillis(year = 2026, month = 1, day = 15)
        val yesterday = localMillis(year = 2026, month = 1, day = 14)
        val beforeYesterday = localMillis(year = 2026, month = 1, day = 13)
        val older = localMillis(year = 2026, month = 1, day = 12)

        assertEquals("今天", diaryRelativeDayLabel(now, now))
        assertEquals("昨天", diaryRelativeDayLabel(yesterday, now))
        assertEquals("前天", diaryRelativeDayLabel(beforeYesterday, now))
        assertEquals("", diaryRelativeDayLabel(older, now))
    }

    @Test
    fun formatsDiaryDateCountLabelWithRelativePrefix() {
        val now = localMillis(year = 2026, month = 1, day = 15)
        val today = localMillis(year = 2026, month = 1, day = 15)
        val older = localMillis(year = 2026, month = 1, day = 12)

        assertEquals("今天 · 2 篇", diaryDateCountLabel(today, dayDiaryCount = 2, nowMillis = now))
        assertEquals("3 篇", diaryDateCountLabel(older, dayDiaryCount = 3, nowMillis = now))
    }

    @Test
    fun convertsChineseNumbersWithExistingRules() {
        assertEquals("一", toChineseNumber(1))
        assertEquals("十", toChineseNumber(10))
        assertEquals("十一", toChineseNumber(11))
        assertEquals("二十", toChineseNumber(20))
        assertEquals("二十一", toChineseNumber(21))
    }

    private fun localMillis(year: Int, month: Int, day: Int): Long {
        return Calendar.getInstance(Locale.CHINA).apply {
            set(year, month - 1, day, 10, 30, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
}
```

- [ ] **Step 2: Write failing structure tests**

Create `app/src/test/kotlin/com/dailysatori/ui/feature/diary/DiaryModuleStructureTest.kt`:

```kotlin
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

        assertFalse(cardSource.contains("tags?.split(\",")"))
        assertFalse(cardSource.contains("images?.split(\",")"))
        assertFalse(screenSource.contains("tags?.split(\",")"))
        assertFalse(screenSource.contains("images?.split(\",")"))
        assertFalse(viewModelSource.contains("?.split(\",")"))
        assertTrue(helperSource.contains("fun diaryTags("))
        assertTrue(helperSource.contains("fun diaryImagePaths("))
    }

    @Test
    fun diaryScreenUsesSharedDateHelpers() {
        val screenSource = File("src/main/kotlin/com/dailysatori/ui/feature/diary/DiaryScreen.kt").readText()

        assertFalse(screenSource.contains("private fun diaryMonthKey"))
        assertFalse(screenSource.contains("private fun diaryDayKey"))
        assertFalse(screenSource.contains("private fun toChineseNumber"))
        assertTrue(screenSource.contains("import com.dailysatori.core.util.diaryMonthKey"))
        assertTrue(screenSource.contains("import com.dailysatori.core.util.diaryDayKey"))
    }
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.dailysatori.core.util.DiaryFormatUtilsTest --tests com.dailysatori.ui.feature.diary.DiaryModuleStructureTest
```

Expected: FAIL. `DiaryFormatUtils.kt` does not exist yet and the diary files still contain local parsing/private date helpers.

---

### Task 2: Add Shared Diary Formatting Helpers

**Files:**
- Create: `app/src/main/kotlin/com/dailysatori/core/util/DiaryFormatUtils.kt`
- Test: `app/src/test/kotlin/com/dailysatori/core/util/DiaryFormatUtilsTest.kt`

- [ ] **Step 1: Create `DiaryFormatUtils.kt`**

Create `app/src/main/kotlin/com/dailysatori/core/util/DiaryFormatUtils.kt`:

```kotlin
package com.dailysatori.core.util

import com.dailysatori.shared.db.Diary
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

internal fun diaryTags(value: String?): List<String> = cleanDiaryListValues(value)

internal fun diaryImagePaths(value: String?): List<String> = cleanDiaryListValues(value)

private fun cleanDiaryListValues(value: String?): List<String> =
    value?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() && it != "null" }.orEmpty()

internal fun stripDiaryInlineTags(content: String): String {
    val lines = content.lines().toMutableList()
    var i = lines.lastIndex
    while (i >= 0) {
        val line = lines[i].trim()
        if (line.isEmpty()) {
            lines.removeAt(i)
            i--
            continue
        }
        val parts = line.split("\\s+".toRegex()).filter { it.isNotBlank() }
        if (parts.all { it.startsWith("#") }) {
            lines.removeAt(i)
            i--
        } else {
            break
        }
    }
    return lines.dropLastWhile { it.isBlank() }.joinToString("\n")
}

internal fun diaryMonthTitle(diary: Diary): String = diaryMonthTitle(diary.created_at)

internal fun diaryMonthTitle(timeMillis: Long): String {
    val calendar = calendarFor(timeMillis)
    return "${toChineseNumber(calendar.get(Calendar.MONTH) + 1)}月"
}

internal fun diaryMonthKey(diary: Diary): String = diaryMonthKey(diary.created_at)

internal fun diaryMonthKey(timeMillis: Long): String = formatDiaryDate(timeMillis, "yyyy-MM")

internal fun diaryDayKey(diary: Diary): String = diaryDayKey(diary.created_at)

internal fun diaryDayKey(timeMillis: Long): String = formatDiaryDate(timeMillis, "yyyy-MM-dd")

internal fun diaryMonthDayLabel(timeMillis: Long): String {
    val calendar = calendarFor(timeMillis)
    return "${calendar.get(Calendar.MONTH) + 1} 月 ${calendar.get(Calendar.DAY_OF_MONTH)} 日"
}

internal fun diaryDateDayNumber(diary: Diary): String = diaryDateDayNumber(diary.created_at)

internal fun diaryDateDayNumber(timeMillis: Long): String =
    calendarFor(timeMillis).get(Calendar.DAY_OF_MONTH).toString()

internal fun diaryDateMonthLabel(diary: Diary): String = diaryDateMonthLabel(diary.created_at)

internal fun diaryDateMonthLabel(timeMillis: Long): String {
    val calendar = calendarFor(timeMillis)
    return "${toChineseNumber(calendar.get(Calendar.MONTH) + 1)}月"
}

internal fun diaryDateWeekLabel(diary: Diary): String = diaryDateWeekLabel(diary.created_at)

internal fun diaryDateWeekLabel(timeMillis: Long): String {
    return when (calendarFor(timeMillis).get(Calendar.DAY_OF_WEEK)) {
        Calendar.MONDAY -> "周一"
        Calendar.TUESDAY -> "周二"
        Calendar.WEDNESDAY -> "周三"
        Calendar.THURSDAY -> "周四"
        Calendar.FRIDAY -> "周五"
        Calendar.SATURDAY -> "周六"
        else -> "周日"
    }
}

internal fun diaryDateCountLabel(
    diary: Diary,
    dayDiaryCount: Int,
    nowMillis: Long = System.currentTimeMillis(),
): String = diaryDateCountLabel(diary.created_at, dayDiaryCount, nowMillis)

internal fun diaryDateCountLabel(
    timeMillis: Long,
    dayDiaryCount: Int,
    nowMillis: Long = System.currentTimeMillis(),
): String {
    val relative = diaryRelativeDayLabel(timeMillis, nowMillis)
    val count = "$dayDiaryCount 篇"
    return if (relative.isBlank()) count else "$relative · $count"
}

internal fun diaryRelativeDayLabel(diary: Diary, nowMillis: Long = System.currentTimeMillis()): String =
    diaryRelativeDayLabel(diary.created_at, nowMillis)

internal fun diaryRelativeDayLabel(timeMillis: Long, nowMillis: Long = System.currentTimeMillis()): String {
    val today = diaryDayKey(nowMillis)
    val calendar = calendarFor(nowMillis)
    calendar.add(Calendar.DAY_OF_YEAR, -1)
    val yesterday = formatDiaryDate(calendar.timeInMillis, "yyyy-MM-dd")
    calendar.add(Calendar.DAY_OF_YEAR, -1)
    val beforeYesterday = formatDiaryDate(calendar.timeInMillis, "yyyy-MM-dd")
    return when (diaryDayKey(timeMillis)) {
        today -> "今天"
        yesterday -> "昨天"
        beforeYesterday -> "前天"
        else -> ""
    }
}

internal fun diaryMonthSummary(diaries: List<Diary>): String {
    if (diaries.isEmpty()) return emptyDiaryMonthSentence()
    val tags = diaries.flatMap { diaryTags(it.tags) }.distinct().take(3)
    val tagText = tags.takeIf { it.isNotEmpty() }?.joinToString("、") ?: "一些普通但明亮的片刻"
    return "这个月的你把 $tagText 留了下来。照片负责记住画面，文字负责留下当时的心。"
}

internal fun emptyDiaryMonthSentence(monthIndex: Int = Calendar.getInstance().get(Calendar.MONTH)): String {
    val sentences = listOf(
        "这个月还没有留下文字。没关系，生活不是每天都要存档，偶尔只负责发光也很好。",
        "空白不是缺席，它只是给下一段故事留了点位置。",
        "这个月的纸页还很干净，等风、等光，也等你忽然想写的那一刻。",
    )
    return sentences[monthIndex % sentences.size]
}

internal fun toChineseNumber(value: Int): String {
    val units = listOf("零", "一", "二", "三", "四", "五", "六", "七", "八", "九", "十")
    return when (value) {
        in 0..10 -> units[value]
        in 11..19 -> "十${units[value % 10]}"
        in 20..99 -> "${units[value / 10]}十${if (value % 10 == 0) "" else units[value % 10]}"
        else -> value.toString()
    }
}

private fun formatDiaryDate(timeMillis: Long, pattern: String): String =
    SimpleDateFormat(pattern, Locale.CHINA).format(Date(timeMillis))

private fun calendarFor(timeMillis: Long): Calendar =
    Calendar.getInstance().apply { timeInMillis = timeMillis }
```

- [ ] **Step 2: Run pure helper tests**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.dailysatori.core.util.DiaryFormatUtilsTest
```

Expected: PASS.

---

### Task 3: Replace Duplicate Parsing In Diary Card And ViewModel

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/component/card/DiaryCard.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/diary/DiaryViewModel.kt`
- Test: `app/src/test/kotlin/com/dailysatori/core/util/DiaryFormatUtilsTest.kt`

- [ ] **Step 1: Update `DiaryCard.kt` imports**

In `DiaryCard.kt`, add imports:

```kotlin
import com.dailysatori.core.util.diaryImagePaths
import com.dailysatori.core.util.diaryTags
import com.dailysatori.core.util.stripDiaryInlineTags
```

Remove the local `stripInlineTags()` function and keep `CONTENT_LONG_THRESHOLD`.

- [ ] **Step 2: Update `DiaryCard()` parsing calls**

Replace:

```kotlin
val tags = diary.tags?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() && it != "null" }.orEmpty()
val imagePaths = diary.images?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() && it != "null" }.orEmpty()
val contentText = stripInlineTags(diary.content)
```

with:

```kotlin
val tags = diaryTags(diary.tags)
val imagePaths = diaryImagePaths(diary.images)
val contentText = stripDiaryInlineTags(diary.content)
```

- [ ] **Step 3: Update `DiaryViewModel.kt` tag extraction**

In `DiaryViewModel.kt`, add import:

```kotlin
import com.dailysatori.core.util.diaryTags
```

Replace `refreshAvailableTags()` with:

```kotlin
private fun refreshAvailableTags() {
    val tags = diaryRepo.getAllSync()
        .flatMap { diary -> diaryTags(diary.tags) }
        .distinct()
        .sorted()
    _state.update { it.copy(availableTags = tags) }
}
```

- [ ] **Step 4: Run helper tests after call-site changes**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.dailysatori.core.util.DiaryFormatUtilsTest
```

Expected: PASS.

---

### Task 4: Replace DiaryScreen Private Formatting Helpers

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/diary/DiaryScreen.kt`
- Test: `app/src/test/kotlin/com/dailysatori/core/util/DiaryFormatUtilsTest.kt`
- Test: `app/src/test/kotlin/com/dailysatori/ui/feature/diary/DiaryModuleStructureTest.kt`

- [ ] **Step 1: Update `DiaryScreen.kt` imports**

Add imports:

```kotlin
import com.dailysatori.core.util.diaryDateCountLabel
import com.dailysatori.core.util.diaryDateDayNumber
import com.dailysatori.core.util.diaryDateMonthLabel
import com.dailysatori.core.util.diaryDateWeekLabel
import com.dailysatori.core.util.diaryDayKey
import com.dailysatori.core.util.diaryImagePaths
import com.dailysatori.core.util.diaryMonthDayLabel
import com.dailysatori.core.util.diaryMonthKey
import com.dailysatori.core.util.diaryMonthSummary
import com.dailysatori.core.util.diaryMonthTitle
```

Remove imports if they become unused:

```kotlin
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
```

- [ ] **Step 2: Update image counting in `DiaryMonthMeta()`**

Replace:

```kotlin
val imageCount = diaries.sumOf { diary ->
    diary.images?.split(",")?.count { it.trim().isNotBlank() && it.trim() != "null" } ?: 0
}
```

with:

```kotlin
val imageCount = diaries.sumOf { diary -> diaryImagePaths(diary.images).size }
```

- [ ] **Step 3: Delete private formatting helpers from `DiaryScreen.kt`**

Delete these private functions from `DiaryScreen.kt` because they are now provided by `DiaryFormatUtils.kt`:

```kotlin
private fun diaryMonthTitle(diary: Diary): String
private fun diaryMonthKey(diary: Diary): String
private fun diaryDayKey(diary: Diary): String
private fun diaryMonthDayLabel(time: Long): String
private fun diaryDateDayNumber(diary: Diary): String
private fun diaryDateMonthLabel(diary: Diary): String
private fun diaryDateWeekLabel(diary: Diary): String
private fun diaryDateCountLabel(diary: Diary, dayDiaryCount: Int): String
private fun diaryRelativeDayLabel(diary: Diary): String
private fun toChineseNumber(value: Int): String
private fun diaryMonthSummary(diaries: List<Diary>): String
private fun emptyMonthSentence(): String
```

Do not change the existing Composable functions, layout modifiers, Chinese UI text, or list grouping logic.

- [ ] **Step 4: Run diary tests**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.dailysatori.core.util.DiaryFormatUtilsTest --tests com.dailysatori.ui.feature.diary.DiaryModuleStructureTest
```

Expected: PASS.

---

### Task 5: Phase 2 Regression And Diff Review

**Files:**
- Test: `app/src/test/kotlin/com/dailysatori/core/util/DiaryFormatUtilsTest.kt`
- Test: `app/src/test/kotlin/com/dailysatori/ui/feature/diary/DiaryModuleStructureTest.kt`
- Test: existing app tests that touch diary card/screen source

- [ ] **Step 1: Run focused Phase 2 tests**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.dailysatori.core.util.DiaryFormatUtilsTest --tests com.dailysatori.ui.feature.diary.DiaryModuleStructureTest --tests com.dailysatori.ui.feature.diary.DiaryEditorSheetBehaviorTest --tests com.dailysatori.ui.theme.MainContentRhythmTest --tests com.dailysatori.ui.theme.PolishTypographyUsageTest
```

Expected: PASS.

- [ ] **Step 2: Compile debug Kotlin**

Run:

```bash
./gradlew :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Review Phase 2 diff and untracked files**

Run:

```bash
git diff -- app/src/main/kotlin/com/dailysatori/core/util/DiaryFormatUtils.kt app/src/main/kotlin/com/dailysatori/ui/component/card/DiaryCard.kt app/src/main/kotlin/com/dailysatori/ui/feature/diary/DiaryScreen.kt app/src/main/kotlin/com/dailysatori/ui/feature/diary/DiaryViewModel.kt app/src/test/kotlin/com/dailysatori/core/util/DiaryFormatUtilsTest.kt app/src/test/kotlin/com/dailysatori/ui/feature/diary/DiaryModuleStructureTest.kt
```

Run:

```bash
git status --short
```

Expected: tracked diff plus untracked Phase 2 files only contain diary formatting/helper extraction and tests. It must not change UI text, layout constants, database schema, navigation, or feature behavior. Existing Phase 1 files and pre-existing untracked docs may still appear in `git status`; do not modify or delete unrelated files.

---

### Task 6: Full Phase 2 Verification On Phone

**Files:**
- Modify: none
- Test: Gradle build and connected phone install/start

- [ ] **Step 1: Run full app unit tests**

Run:

```bash
./gradlew :app:testDebugUnitTest
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Build debug APK**

Run:

```bash
./gradlew :app:assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Verify a phone is connected**

Run:

```bash
adb devices
```

Expected: at least one physical phone appears with `device` status. Do not start or deploy to an emulator. If an emulator is also connected, pin the physical phone serial for install/start.

- [ ] **Step 4: Install debug build to phone**

Run with the physical phone serial from `adb devices`. If the phone is still `ba5e2328`, run:

```bash
JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ANDROID_SERIAL=ba5e2328 ./gradlew :app:installDebug
```

Expected: BUILD SUCCESSFUL and install succeeds on the connected phone.

- [ ] **Step 5: Start the app on phone**

Run with the same physical phone serial. If the phone is still `ba5e2328`, run:

```bash
adb -s ba5e2328 shell am start -n com.dailysatori/.MainActivity
```

Expected: command succeeds and Daily Satori opens on the phone.

- [ ] **Step 6: Report verification results**

Report the exact commands that passed. If any command fails, stop and diagnose the failure before claiming Phase 2 is complete.

---

## Self-Review Notes

- Spec coverage: Phase 2 covers shared diary tag/image parsing, card content cleanup, date/month label extraction, duplicate removal from `DiaryScreen`, `DiaryCard`, and `DiaryViewModel`, and behavior-preserving verification.
- Placeholder scan: no placeholder tasks remain.
- Type consistency: helper names are `diaryTags`, `diaryImagePaths`, `stripDiaryInlineTags`, `diaryMonthKey`, `diaryDayKey`, `diaryMonthSummary`, and related date label helpers; all call sites and tests use these names.
