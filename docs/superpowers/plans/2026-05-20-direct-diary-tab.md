# Direct Diary Tab Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move diary back to a direct bottom tab so the primary navigation is `今日 / 日记 / 读书 / AI`.

**Architecture:** Update `HomeScreen` to render `DiaryScreen` as the second top-level tab and remove the records hub from active bottom navigation. Keep existing `UnifiedNewsScreen`, `BooksScreen`, and `AiChatScreen` behavior unchanged.

**Tech Stack:** Kotlin, Android Jetpack Compose, source-level Kotlin unit tests, Gradle.

---

## File Structure

- Modify `app/src/main/kotlin/com/dailysatori/ui/feature/home/HomeScreen.kt`: replace records tab with direct diary tab.
- Delete `app/src/main/kotlin/com/dailysatori/ui/feature/records/RecordsScreen.kt`: no active records hub in this phase.
- Modify `app/src/test/kotlin/com/dailysatori/ui/feature/home/HomeIaTest.kt`: update tab labels/constants/routes.
- Delete `app/src/test/kotlin/com/dailysatori/ui/feature/records/RecordsScreenTest.kt`: records hub is no longer in scope.
- Modify `app/src/test/kotlin/com/dailysatori/UnifiedNewsBehaviorTest.kt`: update source-level tab assertion from `记录` to `日记`.

## Task 1: Replace Records Tab With Diary Tab

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/home/HomeScreen.kt`
- Delete: `app/src/main/kotlin/com/dailysatori/ui/feature/records/RecordsScreen.kt`
- Modify: `app/src/test/kotlin/com/dailysatori/ui/feature/home/HomeIaTest.kt`
- Delete: `app/src/test/kotlin/com/dailysatori/ui/feature/records/RecordsScreenTest.kt`
- Modify: `app/src/test/kotlin/com/dailysatori/UnifiedNewsBehaviorTest.kt`

- [ ] **Step 1: Update failing tests first**

Edit `HomeIaTest.kt` to expect direct diary navigation:

```kotlin
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
```

In `UnifiedNewsBehaviorTest.homeTabsAreReducedAndFirstTabIsUnifiedNews()`, change the records assertion to:

```kotlin
assertTrue(home.contains("TabItem(\"日记\""))
```

Delete `RecordsScreenTest.kt` because the records hub is no longer part of this phase.

- [ ] **Step 2: Run tests to verify failure**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.dailysatori.ui.feature.home.HomeIaTest" --tests "com.dailysatori.UnifiedNewsBehaviorTest.homeTabsAreReducedAndFirstTabIsUnifiedNews"
```

Expected: FAIL because production still has `RECORDS_TAB_INDEX` and `RecordsScreen`.

- [ ] **Step 3: Update `HomeScreen.kt`**

Remove import:

```kotlin
import com.dailysatori.ui.feature.records.RecordsScreen
```

Add import if not present:

```kotlin
import com.dailysatori.ui.feature.diary.DiaryScreen
```

Replace constants:

```kotlin
const val TODAY_TAB_INDEX = 0
const val DIARY_TAB_INDEX = 1
const val READING_TAB_INDEX = 2
const val AI_CHAT_TAB_INDEX = 3
```

Replace tabs:

```kotlin
val tabs = listOf(
    TabItem("今日", Icons.Filled.Language, Icons.Outlined.Language),
    TabItem("日记", Icons.Filled.Book, Icons.Outlined.Book),
    TabItem("读书", Icons.Filled.AutoStories, Icons.Outlined.AutoStories),
    TabItem("AI", Icons.Filled.SmartToy, Icons.Outlined.SmartToy),
)
```

Replace the records branch with direct diary:

```kotlin
DIARY_TAB_INDEX -> DiaryScreen(onMyClick = openMy)
```

- [ ] **Step 4: Delete inactive records screen**

Delete `app/src/main/kotlin/com/dailysatori/ui/feature/records/RecordsScreen.kt`.

- [ ] **Step 5: Run focused tests and compile**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests "com.dailysatori.ui.feature.home.HomeIaTest" --tests "com.dailysatori.UnifiedNewsBehaviorTest.homeTabsAreReducedAndFirstTabIsUnifiedNews" --tests "com.dailysatori.ui.feature.aichat.AiChatUiStateTest.homeBottomBarRemainsVisibleOnAiTab" :app:compileDebugKotlin
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/com/dailysatori/ui/feature/home/HomeScreen.kt app/src/test/kotlin/com/dailysatori/ui/feature/home/HomeIaTest.kt app/src/test/kotlin/com/dailysatori/UnifiedNewsBehaviorTest.kt
git rm app/src/main/kotlin/com/dailysatori/ui/feature/records/RecordsScreen.kt app/src/test/kotlin/com/dailysatori/ui/feature/records/RecordsScreenTest.kt
git commit -m "feat: make diary a direct tab"
```

## Task 2: Device Verification

**Files:**
- No code changes expected.

- [ ] **Step 1: Connect phone**

Run:

```bash
adb connect 192.168.2.12:40853
adb devices
```

Expected: `192.168.2.12:40853 device`.

- [ ] **Step 2: Install and launch**

Run:

```bash
JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:installDebug
adb -s 192.168.2.12:40853 shell am start -n com.dailysatori/.MainActivity
```

Expected: install succeeds and activity starts.

- [ ] **Step 3: Manual check**

Confirm on phone:

- Bottom tabs show `今日 / 日记 / 读书 / AI`.
- Tapping `日记` opens diary directly.
- `今日`, `读书`, and `AI` still open.

## Self-Review Notes

- Spec coverage: direct diary tab, removal of records tab, unchanged Today/Reading/AI, compile/test/device verification.
- Placeholder scan: no incomplete placeholders.
- Type consistency: tests reference `DIARY_TAB_INDEX`, which Task 1 defines in `HomeScreen.kt`.
