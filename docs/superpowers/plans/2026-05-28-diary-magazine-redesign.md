# Diary Magazine Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Redesign the diary page as a calmer magazine-style writing feed while keeping existing diary behavior intact.

**Architecture:** Keep changes limited to diary UI. `DiaryScreen` owns page layout, top actions, active filter chip, bottom-sheet tag filter, dialogs, and editor sheet orchestration. `DiaryCard` owns the visual treatment of each diary entry and uses existing `Diary` data without ViewModel or schema changes.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, existing Daily Satori theme tokens (`MaterialTheme`, `Spacing`, `Radius`), Koin ViewModel injection.

---

## File Structure

- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/diary/DiaryScreen.kt`
  - Convert the inline tag filter into a modal bottom sheet.
  - Show the selected tag as a quiet active filter chip above the list.
  - Tune feed spacing and floating add button hierarchy.
- Modify: `app/src/main/kotlin/com/dailysatori/ui/component/card/DiaryCard.kt`
  - Replace the current bordered card visual with a paper-like magazine card.
  - Improve metadata, content preview, image row, tags, and delete action hierarchy.
- Do not modify: `DiaryViewModel`, database schema, repository code, navigation, or editor sheet behavior.

### Task 1: Update Diary Page Layout And Tag Filter Sheet

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/diary/DiaryScreen.kt`

- [ ] **Step 1: Add Material 3 sheet imports and remove unused inline-panel imports**

Update imports so `DiaryScreen.kt` includes the bottom sheet APIs and no longer relies on the inline panel layout helpers.

```kotlin
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberModalBottomSheetState
```

Keep existing imports that are still used by the page. Remove imports that become unused after the inline filter panel is deleted, such as `background`, `clickable`, `height`, and `width` if no remaining code uses them.

- [ ] **Step 2: Opt in to Material 3 bottom sheet APIs**

Change the screen annotation to include both layout and Material 3 APIs.

```kotlin
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun DiaryScreen(onMyClick: () -> Unit = {}) {
```

- [ ] **Step 3: Tune list spacing and add active filter chip above the feed**

In the main content column, replace the existing selected-tag row with a compact composable call.

```kotlin
if (state.selectedTag != null) {
    ActiveDiaryTagFilterChip(
        tag = state.selectedTag,
        onClear = { viewModel.filterByTag(null) },
    )
}
```

Update the diary list padding and spacing.

```kotlin
LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(start = Spacing.m, end = Spacing.m, top = Spacing.s, bottom = Spacing.xxl),
    verticalArrangement = Arrangement.spacedBy(Spacing.m),
) {
```

- [ ] **Step 4: Make the add button visually quieter**

Use a surface-colored small floating action button with primary icon tint.

```kotlin
SmallFloatingActionButton(
    onClick = { editingDiary = null; showEditor = true },
    modifier = Modifier.align(Alignment.BottomEnd).padding(Spacing.m),
    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
    contentColor = MaterialTheme.colorScheme.primary,
    shape = CircleShape,
) {
    Icon(Icons.Default.Add, contentDescription = "新建日记", modifier = Modifier.size(20.dp))
}
```

- [ ] **Step 5: Replace the inline tag filter panel with a modal bottom sheet**

Delete the current `if (showTagFilter) { Column(...) }` inline panel. Replace it with this sheet block near the existing editor/dialog sheet blocks.

```kotlin
if (showTagFilter) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = { showTagFilter = false },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(topStart = Radius.xl, topEnd = Radius.xl),
    ) {
        DiaryTagFilterSheet(
            tags = state.availableTags,
            selectedTag = state.selectedTag,
            onTagSelected = { tag ->
                viewModel.filterByTag(tag)
                showTagFilter = false
            },
            onClear = { viewModel.filterByTag(null) },
            onClose = { showTagFilter = false },
        )
    }
}
```

- [ ] **Step 6: Add active filter chip composable**

Add this composable below `DiaryScreen` in the same file.

```kotlin
@Composable
private fun ActiveDiaryTagFilterChip(tag: String, onClear: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.m, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Surface(
            shape = RoundedCornerShape(Radius.circular),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
        ) {
            Row(
                modifier = Modifier.padding(start = Spacing.s, end = Spacing.xxs, top = Spacing.xxs, bottom = Spacing.xxs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
            ) {
                Text(
                    text = "#$tag",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                IconButton(onClick = onClear, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "清除筛选", modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}
```

- [ ] **Step 7: Add tag filter sheet composable**

Add this composable below `ActiveDiaryTagFilterChip`.

```kotlin
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DiaryTagFilterSheet(
    tags: List<String>,
    selectedTag: String?,
    onTagSelected: (String) -> Unit,
    onClear: () -> Unit,
    onClose: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(start = Spacing.m, end = Spacing.m, bottom = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                Text("按标签筛选", style = MaterialTheme.typography.titleMedium)
                Text("选择一个标签，只看相关日记", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = onClose) { Text("关闭") }
        }

        if (selectedTag != null) {
            TextButton(onClick = onClear) { Text("清除当前筛选") }
        }

        if (tags.isEmpty()) {
            Text(
                text = "暂无标签",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = Spacing.s),
            )
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                tags.forEach { tag ->
                    FilterChip(
                        selected = selectedTag == tag,
                        onClick = { onTagSelected(tag) },
                        label = { Text("#$tag") },
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 8: Compile the page change**

Run: `./gradlew :app:compileDebugKotlin`

Expected: Kotlin compilation succeeds. If there are unused imports, remove only the imports reported by the compiler/IDE and rerun the command.

### Task 2: Redesign Diary Card As Magazine Entry

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/component/card/DiaryCard.kt`

- [ ] **Step 1: Replace CustomCard usage with Material 3 Card imports**

Add imports:

```kotlin
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.text.font.FontWeight
```

Remove the `CustomCard` call from the implementation; the function remains available for other pages.

- [ ] **Step 2: Use a paper-like card container**

Replace the `CustomCard` block opening with this card.

```kotlin
Card(
    onClick = onClick,
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(Radius.xl),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
) {
    Column(
        modifier = Modifier.padding(Spacing.l),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
```

Keep the existing closing braces aligned with the new `Card` and `Column`.

- [ ] **Step 3: Restyle the metadata row**

Replace the current date/delete row with this quieter metadata row.

```kotlin
Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(Spacing.s),
) {
    Text(
        text = TimeUtils.formatShortDateTime(diary.created_at),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.weight(1f),
    )
    diary.mood?.takeIf { it.isNotBlank() && it != "null" }?.let { mood ->
        Text(
            text = mood,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
    if (showDelete) {
        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = "删除",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}
```

- [ ] **Step 4: Restyle content preview**

Keep the existing markdown preview and expansion behavior, but remove the extra top spacer before the content. Use the larger card padding from Step 2 as the content rhythm.

The Markdown block should remain:

```kotlin
Markdown(
    content = contentText,
    typography = MarkdownStyles.cardTypography(),
    padding = MarkdownStyles.cardPadding(),
)
```

The long-content button should remain a full-width text button with primary text.

- [ ] **Step 5: Add a quiet divider before supporting context**

Before rendering image previews or tags, insert a divider only when either supporting context exists.

```kotlin
if (imagePaths.isNotEmpty() || tags.isNotEmpty()) {
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
}
```

- [ ] **Step 6: Keep image previews but align them with magazine spacing**

Keep the existing `LazyRow` and `SmartImage` behavior. Remove the old `Spacer` before images because the parent column already uses `Arrangement.spacedBy(Spacing.m)`.

- [ ] **Step 7: Restyle tag chips**

Keep the `FlowRow`, but use circular quiet chips.

```kotlin
FlowRow(
    horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    verticalArrangement = Arrangement.spacedBy(Spacing.s),
) {
    tags.forEach { tag ->
        Text(
            text = "#$tag",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .clip(RoundedCornerShape(Radius.circular))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .padding(horizontal = Spacing.s, vertical = Spacing.xxs),
        )
    }
}
```

- [ ] **Step 8: Remove unused code and imports**

Remove unused imports after the redesign. If `moodToEmoji` remains unused, delete the function because the card now displays the existing mood text directly.

- [ ] **Step 9: Compile the card change**

Run: `./gradlew :app:compileDebugKotlin`

Expected: Kotlin compilation succeeds.

### Task 3: Verify The Diary Redesign End To End

**Files:**
- No source modifications expected unless verification exposes compile or runtime issues.

- [ ] **Step 1: Run required compile verification**

Run: `./gradlew :app:compileDebugKotlin`

Expected: build successful.

- [ ] **Step 2: Build and install debug app if a device is available**

Run: `JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:installDebug`

Expected: install successful, or a clear no-device/ADB error if no Android device is connected.

- [ ] **Step 3: Launch the app if install succeeds**

Run: `adb shell am start -n com.dailysatori/.MainActivity`

Expected: Android reports the launch intent was started.

- [ ] **Step 4: Manual smoke check**

Open the diary tab and verify:

- Empty state still appears when there are no diaries.
- Existing diaries show as magazine-style entries.
- Search opens and closes below the app bar.
- Filter opens as a bottom sheet.
- Selecting a tag closes the sheet and shows the active chip.
- Clearing the active chip removes the filter.
- Tapping a diary opens the editor sheet.
- Add button opens a blank editor sheet.
- Delete action still opens the confirmation dialog.

## Self-Review

- Spec coverage: The plan covers the page structure, diary feed, tag-filter bottom sheet, floating action button hierarchy, no ViewModel/schema changes, and required compile/install verification.
- Placeholder scan: No placeholders or deferred implementation steps remain.
- Type consistency: All referenced files and composables are existing Kotlin/Compose files; new helper composables are private and local to `DiaryScreen.kt`.
