# External Favorites Add Page Polish Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Finish the external favorites add-page change so the UI behavior matches the design, failures stay on the add page, and regression coverage protects the intended page-based flow.

**Architecture:** Keep the scope inside the existing external favorites settings feature. `ExternalFavoritesSettingsScreen` owns the list/add-page split and browser launch result handling, while `ExternalFavoritesSettingsViewModel` continues to own persisted X OAuth Client ID state, user-facing copy helpers, and small pure decision helpers. Tests remain lightweight text/helper unit tests.

**Tech Stack:** Kotlin, Jetpack Compose Material3, Koin ViewModel, kotlin.test, Gradle Android unit tests.

---

## File Structure

- Modify `app/src/main/kotlin/com/dailysatori/ui/feature/settings/externalfavorites/ExternalFavoritesSettingsScreen.kt`: keep OAuth/browser failures on the add page, remove the non-persisted periodic-sync switch, and make the add page communicate that periodic sync is enabled after authorization.
- Modify `app/src/main/kotlin/com/dailysatori/ui/feature/settings/externalfavorites/ExternalFavoritesSettingsViewModel.kt`: add small copy helpers for the add page labels and non-editable post-authorization sync note.
- Modify `app/src/test/kotlin/com/dailysatori/ui/feature/settings/externalfavorites/ExternalFavoritesAddPageTextTest.kt`: extend text contracts for the helper title and sync note.
- Create `app/src/test/kotlin/com/dailysatori/ui/feature/settings/externalfavorites/ExternalFavoritesAddPageSourceTest.kt`: helper-based regression tests that the add page closes only after the Client ID is saved and the OAuth browser launch succeeds.
- Decide whether to keep or remove these currently untracked design artifacts before commit:
  - `docs/superpowers/specs/2026-06-08-external-favorites-add-page-design.md`
  - `docs/superpowers/mockups/book-reflection-actions-demo.html`
  - `docs/superpowers/plans/2026-06-06-reading-reflection-flow.md`
  - `docs/superpowers/plans/2026-06-06-reflection-actions-layout.md`
  - `docs/superpowers/plans/2026-06-07-book-reflection-tabs-settled.md`
  - `docs/superpowers/specs/2026-06-06-reading-reflection-demo-design.md`
  - `docs/superpowers/specs/2026-06-06-reflection-actions-layout-design.md`

---

### Task 1: Protect Add-Page Copy and Remove Misleading Editable Sync State

**Files:**
- Modify: `app/src/test/kotlin/com/dailysatori/ui/feature/settings/externalfavorites/ExternalFavoritesAddPageTextTest.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/settings/externalfavorites/ExternalFavoritesSettingsViewModel.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/settings/externalfavorites/ExternalFavoritesSettingsScreen.kt`

- [ ] **Step 1: Extend the failing text test**

Replace `ExternalFavoritesAddPageTextTest` with:

```kotlin
package com.dailysatori.ui.feature.settings.externalfavorites

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExternalFavoritesAddPageTextTest {
    @Test
    fun addPageUsesDedicatedEditorCopy() {
        assertEquals("新增外部收藏", externalFavoriteAddPageTitle())
        assertEquals("连接 X 收藏", externalFavoriteAddPageHelperTitle())
        assertEquals("保存并连接 X", externalFavoriteConnectXActionLabel())
        assertTrue(externalFavoriteAddPageHelperText().contains("浏览器"))
    }

    @Test
    fun addPageExplainsPostAuthorizationSyncWithoutPretendingToSaveIt() {
        assertEquals("授权成功后启用定期同步", externalFavoriteAddPageSyncNoteTitle())
        assertTrue(externalFavoriteAddPageSyncNoteText().contains("授权成功"))
        assertTrue(externalFavoriteAddPageSyncNoteText().contains("来源列表"))
    }
}
```

- [ ] **Step 2: Run the targeted test and verify it fails**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.dailysatori.ui.feature.settings.externalfavorites.ExternalFavoritesAddPageTextTest
```

Expected: FAIL because `externalFavoriteAddPageSyncNoteTitle()` and `externalFavoriteAddPageSyncNoteText()` do not exist.

- [ ] **Step 3: Add the new copy helpers**

In `ExternalFavoritesSettingsViewModel.kt`, after `externalFavoriteAddPageHelperText()`, add:

```kotlin
fun externalFavoriteAddPageSyncNoteTitle(): String = "授权成功后启用定期同步"

fun externalFavoriteAddPageSyncNoteText(): String =
    "完成 X 授权后，新来源会出现在来源列表，可在那里停用定期同步、手动同步或导入历史收藏。"
```

- [ ] **Step 4: Replace the editable sync switch with a read-only note**

In `ExternalFavoritesSettingsScreen.kt`, remove the local sync state:

```kotlin
var enabled by remember { mutableStateOf(true) }
```

Remove this `Row` block:

```kotlin
Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
) {
    Text("启用定期同步", style = MaterialTheme.typography.bodyMedium)
    Switch(checked = enabled, onCheckedChange = { enabled = it })
}
```

Add this composable call in the same location:

```kotlin
ExternalFavoriteAddSyncNote()
```

Change the connect button from:

```kotlin
Button(onClick = onConnectX, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
    Icon(Icons.Default.Bookmark, contentDescription = null)
    Text(externalFavoriteConnectXActionLabel())
}
```

to:

```kotlin
Button(onClick = onConnectX, modifier = Modifier.fillMaxWidth()) {
    Icon(Icons.Default.Bookmark, contentDescription = null)
    Text(externalFavoriteConnectXActionLabel())
}
```

Keep the `Switch` import because the source-list enable toggle still uses it; only the add-page fake switch is removed.

Add this composable near `ExternalFavoriteAddHelperCard()`:

```kotlin
@Composable
private fun ExternalFavoriteAddSyncNote() {
    Surface(
        shape = RoundedCornerShape(Radius.m),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(
                externalFavoriteAddPageSyncNoteTitle(),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                externalFavoriteAddPageSyncNoteText(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
```

- [ ] **Step 5: Run the targeted test and compile**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.dailysatori.ui.feature.settings.externalfavorites.ExternalFavoritesAddPageTextTest
./gradlew :app:compileDebugKotlin
```

Expected: both commands end with `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/com/dailysatori/ui/feature/settings/externalfavorites/ExternalFavoritesSettingsScreen.kt app/src/main/kotlin/com/dailysatori/ui/feature/settings/externalfavorites/ExternalFavoritesSettingsViewModel.kt app/src/test/kotlin/com/dailysatori/ui/feature/settings/externalfavorites/ExternalFavoritesAddPageTextTest.kt
git commit -m "fix: clarify external favorite add page sync state"
```

---

### Task 2: Keep OAuth and Browser Errors on the Add Page

**Files:**
- Create: `app/src/test/kotlin/com/dailysatori/ui/feature/settings/externalfavorites/ExternalFavoritesAddPageSourceTest.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/settings/externalfavorites/ExternalFavoritesSettingsViewModel.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/settings/externalfavorites/ExternalFavoritesSettingsScreen.kt`

- [ ] **Step 1: Add failing helper-based regression tests**

Create `ExternalFavoritesAddPageSourceTest.kt`:

```kotlin
package com.dailysatori.ui.feature.settings.externalfavorites

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExternalFavoritesAddPageSourceTest {
    @Test
    fun addPageClosesOnlyAfterClientIdIsSavedAndAuthorizationLaunches() {
        assertFalse(
            externalFavoriteShouldCloseAddPageAfterConnect(
                clientIdSaved = false,
                authorizationLaunched = false,
            ),
        )
        assertFalse(
            externalFavoriteShouldCloseAddPageAfterConnect(
                clientIdSaved = false,
                authorizationLaunched = true,
            ),
        )
        assertFalse(
            externalFavoriteShouldCloseAddPageAfterConnect(
                clientIdSaved = true,
                authorizationLaunched = false,
            ),
        )
        assertTrue(
            externalFavoriteShouldCloseAddPageAfterConnect(
                clientIdSaved = true,
                authorizationLaunched = true,
            ),
        )
    }
}
```

- [ ] **Step 2: Run the new helper test and verify it fails**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.dailysatori.ui.feature.settings.externalfavorites.ExternalFavoritesAddPageSourceTest
```

Expected: FAIL because `externalFavoriteShouldCloseAddPageAfterConnect()` does not exist yet.

- [ ] **Step 3: Add the close-decision helper**

In `ExternalFavoritesSettingsViewModel.kt`, add:

```kotlin
fun externalFavoriteShouldCloseAddPageAfterConnect(
    clientIdSaved: Boolean,
    authorizationLaunched: Boolean,
): Boolean = clientIdSaved && authorizationLaunched
```

- [ ] **Step 4: Make `connectX` report launch success**

In `ExternalFavoritesSettingsScreen.kt`, replace:

```kotlin
val connectX = {
    viewModel.createXAuthorizationUrl()?.let { url ->
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }.onFailure {
            viewModel.showMessage("无法打开授权页面，请确认设备已安装浏览器")
        }
    }
    Unit
}
```

with:

```kotlin
val connectX = {
    viewModel.createXAuthorizationUrl()?.let { url ->
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }.onFailure {
            viewModel.showMessage("无法打开授权页面，请确认设备已安装浏览器")
        }.isSuccess
    } ?: false
}
```

- [ ] **Step 5: Close the add page only after success**

In the `onConnectX` callback, replace:

```kotlin
onConnectX = {
    if (viewModel.saveXOAuthClientIdForConnect()) {
        showAddPage = false
        connectX()
    }
},
```

with:

```kotlin
onConnectX = {
    val clientIdSaved = viewModel.saveXOAuthClientIdForConnect()
    val authorizationLaunched = clientIdSaved && connectX()
    if (externalFavoriteShouldCloseAddPageAfterConnect(clientIdSaved, authorizationLaunched)) {
        showAddPage = false
    }
},
```

- [ ] **Step 6: Run the helper test, text tests, and compile**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.dailysatori.ui.feature.settings.externalfavorites.ExternalFavoritesAddPageSourceTest --tests com.dailysatori.ui.feature.settings.externalfavorites.ExternalFavoritesAddPageTextTest --tests com.dailysatori.ui.feature.settings.externalfavorites.ExternalFavoritesSettingsTextTest
./gradlew :app:compileDebugKotlin
```

Expected: the helper test passes after adding the helper and screen logic, and both commands end with `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/kotlin/com/dailysatori/ui/feature/settings/externalfavorites/ExternalFavoritesSettingsScreen.kt app/src/main/kotlin/com/dailysatori/ui/feature/settings/externalfavorites/ExternalFavoritesSettingsViewModel.kt app/src/test/kotlin/com/dailysatori/ui/feature/settings/externalfavorites/ExternalFavoritesAddPageSourceTest.kt
git commit -m "fix: keep external favorite add errors in page"
```

---

### Task 3: Run Full External Favorites Verification

**Files:**
- No source edits expected.

- [ ] **Step 1: Check formatting whitespace**

Run:

```bash
git diff --check
```

Expected: no output and exit code `0`.

- [ ] **Step 2: Run app unit tests for external favorites settings**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests 'com.dailysatori.ui.feature.settings.externalfavorites.*'
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Run broader external favorites tests**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests 'com.dailysatori.core.worker.ExternalFavoriteSyncWorkerTest' --tests 'com.dailysatori.service.externalfavorites.XOAuthCoordinatorTest'
./gradlew :shared:testDebugUnitTest --tests 'com.dailysatori.service.externalfavorites.*' --tests 'com.dailysatori.data.repository.ExternalFavorite*'
```

Expected: both commands end with `BUILD SUCCESSFUL`.

- [ ] **Step 4: Compile the app**

Run:

```bash
./gradlew :app:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Build debug APK**

Run:

```bash
./gradlew :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL` and `app/build/outputs/apk/debug/app-debug.apk` exists.

- [ ] **Step 6: Commit verification-only changes only if files changed**

If no files changed, skip this step. If Gradle updated checked-in metadata, inspect it first:

```bash
git status --short
git diff --stat
```

Expected: no new source changes from verification.

---

### Task 4: Resolve Untracked Planning Artifacts

**Files:**
- Decide and modify git index only; do not change production source.

- [ ] **Step 1: Inspect untracked docs**

Run:

```bash
git status --short --untracked-files=all
```

Expected: the external favorites add-page design and book reflection planning artifacts are visible as untracked files.

- [ ] **Step 2: Keep the external favorites add-page design if it matches the final implementation**

Open:

```bash
sed -n '1,220p' docs/superpowers/specs/2026-06-08-external-favorites-add-page-design.md
```

Expected: the spec still describes a dedicated add page, no provider/storage changes, and verification commands. If it matches, include it in the final commit. If it contradicts the final implementation, edit only the inaccurate lines and keep the scope factual.

- [ ] **Step 3: Decide whether book reflection artifacts belong in this branch**

Run:

```bash
git diff -- app/src/main/kotlin/com/dailysatori/ui/feature/book/BookReflectionSheet.kt app/src/main/kotlin/com/dailysatori/ui/feature/book/BookReflectionViewModel.kt app/src/main/kotlin/com/dailysatori/ui/feature/book/BooksScreen.kt
```

Expected: no book reflection source diff in this branch. If there is no book reflection source diff, do not include the untracked book reflection docs in the external favorites commit.

- [ ] **Step 4: Move unrelated untracked book reflection docs out of the commit path**

If the book reflection docs are unrelated to the current branch, leave them untracked and explicitly mention them in the final handoff. Do not delete them unless the user asks for cleanup.

Unrelated files to leave untracked:

```text
docs/superpowers/mockups/book-reflection-actions-demo.html
docs/superpowers/plans/2026-06-06-reading-reflection-flow.md
docs/superpowers/plans/2026-06-06-reflection-actions-layout.md
docs/superpowers/plans/2026-06-07-book-reflection-tabs-settled.md
docs/superpowers/specs/2026-06-06-reading-reflection-demo-design.md
docs/superpowers/specs/2026-06-06-reflection-actions-layout-design.md
```

- [ ] **Step 5: Commit the external favorites design doc if retained**

If the external favorites design doc is accurate and not yet committed:

```bash
git add docs/superpowers/specs/2026-06-08-external-favorites-add-page-design.md
git commit -m "docs: capture external favorite add page design"
```

Expected: only the external favorites design doc is included in this docs commit.

---

### Task 5: Final Manual Smoke Check

**Files:**
- No source edits expected.

- [ ] **Step 1: Install debug APK on the target device**

Run only if the device at `192.168.2.100:38305` is available:

```bash
adb connect 192.168.2.100:38305
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Expected: `connected to 192.168.2.100:38305` and `Success`.

- [ ] **Step 2: Launch the app**

Run:

```bash
adb shell monkey -p com.dailysatori -c android.intent.category.LAUNCHER 1
```

Expected: the app opens on the device.

- [ ] **Step 3: Verify settings flow manually**

Manual checks:

```text
1. Open Settings -> 外部收藏同步.
2. Tap the floating add button or empty-state 添加服务 action.
3. Confirm the page title is 新增外部收藏.
4. Confirm there is no modal dialog.
5. Leave X OAuth Client ID empty and tap 保存并连接 X.
6. Confirm the message 请先填写 X OAuth Client ID appears on the add page.
7. Enter a value and tap 保存并连接 X.
8. If no browser is available or browser launch fails, confirm the message stays on the add page.
9. If browser launch succeeds, confirm the app leaves the add page only after the launch attempt succeeds.
```

Expected: the add page behavior matches every check above.

- [ ] **Step 4: Final status**

Run:

```bash
git status --short
```

Expected: only intentionally unrelated untracked book reflection docs remain, or the working tree is clean if those were handled separately.

---

## Self-Review

- Spec coverage: the plan covers the four identified gaps: inline add-page errors, non-persisted sync switch ambiguity, missing source-level regression coverage, and untracked planning artifact handling.
- Placeholder scan: no `TBD`, `TODO`, or vague "handle appropriately" steps remain; every code edit includes exact snippets and commands.
- Type consistency: helper names are introduced before use; `connectX` changes from `Unit` to `Boolean` and the call site checks that Boolean before closing the page.
