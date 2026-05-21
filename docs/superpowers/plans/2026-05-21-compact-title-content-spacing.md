# Compact Title Content Spacing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Slim the shared title bar and reduce first-screen gaps between title/tab/header areas and正文 content.

**Architecture:** Keep the existing shared `AppScaffold`/`AppTopBar` architecture. Apply a global compact top app bar token through `TopAppBarDefaults.windowInsets`/height override, then tighten only article detail body padding where stacked spacing currently feels heavy.

**Tech Stack:** Kotlin, Jetpack Compose Material3, Android Gradle build.

---

## File Structure

- Modify: `app/src/main/kotlin/com/dailysatori/ui/component/appbar/AppTopBar.kt`
  - Shared top bar for screens using `AppScaffold`.
  - Add explicit compact height using existing `Height.appBar`.
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/article/ArticleDetailScreen.kt`
  - Local article detail page; reduce first body padding after tab/header stack.
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/remotenews/RemoteArticleDetailScreen.kt`
  - Remote article detail page; reduce first body padding after tab/header stack.
- Test: `app/src/test/kotlin/com/dailysatori/ui/theme/CompactTitleSpacingTest.kt`
  - Source-contract tests for compact app bar and reduced detail padding.

---

### Task 1: Compact Shared App Bar

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/component/appbar/AppTopBar.kt`
- Create: `app/src/test/kotlin/com/dailysatori/ui/theme/CompactTitleSpacingTest.kt`

- [ ] **Step 1: Add failing source-contract test**

Create `app/src/test/kotlin/com/dailysatori/ui/theme/CompactTitleSpacingTest.kt`:

```kotlin
package com.dailysatori.ui.theme

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CompactTitleSpacingTest {
    @Test
    fun appTopBarUsesCompactHeightToken() {
        val source = readProjectFile("app/src/main/kotlin/com/dailysatori/ui/component/appbar/AppTopBar.kt")

        assertTrue(source.contains("import androidx.compose.foundation.layout.height"))
        assertTrue(source.contains("import androidx.compose.ui.Modifier"))
        assertTrue(source.contains("import com.dailysatori.ui.theme.Height"))
        assertTrue(source.contains("modifier = Modifier.height(Height.appBar)"))
        assertTrue(source.contains("windowInsets = TopAppBarDefaults.windowInsets"))
        assertFalse(source.contains("height(64.dp)"))
    }
}

private fun readProjectFile(path: String): String = java.io.File(path).readText()
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests com.dailysatori.ui.theme.CompactTitleSpacingTest`

Expected: FAIL because `AppTopBar.kt` does not yet import `height`, `Modifier`, or use `Height.appBar`.

- [ ] **Step 3: Implement compact app bar**

Update `app/src/main/kotlin/com/dailysatori/ui/component/appbar/AppTopBar.kt`:

```kotlin
package com.dailysatori.ui.component.appbar

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.dailysatori.ui.theme.Height

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    showBack: Boolean = onBack != null,
    myNavigationLabel: String? = null,
    onMyNavigationClick: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    CenterAlignedTopAppBar(
        modifier = Modifier.height(Height.appBar),
        windowInsets = TopAppBarDefaults.windowInsets,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
            )
        },
        navigationIcon = {
            if (showBack && onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            } else if (myNavigationLabel != null && onMyNavigationClick != null) {
                IconButton(onClick = onMyNavigationClick) {
                    Icon(
                        Icons.Default.AccountCircle,
                        contentDescription = myNavigationLabel,
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            scrolledContainerColor = MaterialTheme.colorScheme.background,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
            actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    )
}
```

- [ ] **Step 4: Run focused test and compile**

Run: `./gradlew :app:testDebugUnitTest --tests com.dailysatori.ui.theme.CompactTitleSpacingTest`

Expected: PASS.

Run: `./gradlew :app:compileDebugKotlin`

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit app bar change**

Run:

```bash
git add app/src/main/kotlin/com/dailysatori/ui/component/appbar/AppTopBar.kt app/src/test/kotlin/com/dailysatori/ui/theme/CompactTitleSpacingTest.kt
git commit -m "fix: slim shared app top bar"
```

---

### Task 2: Tighten Article Detail First Body Padding

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/article/ArticleDetailScreen.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/remotenews/RemoteArticleDetailScreen.kt`
- Modify: `app/src/test/kotlin/com/dailysatori/ui/theme/CompactTitleSpacingTest.kt`

- [ ] **Step 1: Extend source-contract tests**

Append these tests inside `CompactTitleSpacingTest` before the closing brace:

```kotlin
    @Test
    fun localArticleDetailUsesCompactFirstBodyPadding() {
        val source = readProjectFile("app/src/main/kotlin/com/dailysatori/ui/feature/article/ArticleDetailScreen.kt")

        assertTrue(source.contains("Modifier.padding(horizontal = Spacing.m, vertical = Spacing.s)"))
        assertFalse(source.contains("Box(modifier = Modifier.padding(Spacing.m))"))
    }

    @Test
    fun remoteArticleDetailUsesCompactFirstBodyPadding() {
        val source = readProjectFile("app/src/main/kotlin/com/dailysatori/ui/feature/remotenews/RemoteArticleDetailScreen.kt")

        assertTrue(source.contains("Box(modifier = Modifier.padding(horizontal = Spacing.m, vertical = Spacing.s))"))
        assertFalse(source.contains("Box(modifier = Modifier.padding(Spacing.m))"))
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests com.dailysatori.ui.theme.CompactTitleSpacingTest`

Expected: FAIL because both detail screens still contain `Box(modifier = Modifier.padding(Spacing.m))` around first body content.

- [ ] **Step 3: Update local article detail padding**

In `app/src/main/kotlin/com/dailysatori/ui/feature/article/ArticleDetailScreen.kt`, replace the first body content box padding inside the `LazyColumn` item from:

```kotlin
Box(modifier = Modifier.padding(Spacing.m)) {
```

to:

```kotlin
Box(modifier = Modifier.padding(horizontal = Spacing.m, vertical = Spacing.s)) {
```

- [ ] **Step 4: Update remote article detail padding**

In `app/src/main/kotlin/com/dailysatori/ui/feature/remotenews/RemoteArticleDetailScreen.kt`, replace:

```kotlin
Box(modifier = Modifier.padding(Spacing.m)) {
```

with:

```kotlin
Box(modifier = Modifier.padding(horizontal = Spacing.m, vertical = Spacing.s)) {
```

- [ ] **Step 5: Run focused test and compile**

Run: `./gradlew :app:testDebugUnitTest --tests com.dailysatori.ui.theme.CompactTitleSpacingTest`

Expected: PASS.

Run: `./gradlew :app:compileDebugKotlin`

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit detail spacing change**

Run:

```bash
git add app/src/main/kotlin/com/dailysatori/ui/feature/article/ArticleDetailScreen.kt app/src/main/kotlin/com/dailysatori/ui/feature/remotenews/RemoteArticleDetailScreen.kt app/src/test/kotlin/com/dailysatori/ui/theme/CompactTitleSpacingTest.kt
git commit -m "fix: tighten article detail body spacing"
```

---

### Task 3: Final Verification And Device Install

**Files:**
- Verify: `app/src/main/kotlin/com/dailysatori/ui/component/appbar/AppTopBar.kt`
- Verify: `app/src/main/kotlin/com/dailysatori/ui/feature/article/ArticleDetailScreen.kt`
- Verify: `app/src/main/kotlin/com/dailysatori/ui/feature/remotenews/RemoteArticleDetailScreen.kt`
- Verify: `app/src/test/kotlin/com/dailysatori/ui/theme/CompactTitleSpacingTest.kt`

- [ ] **Step 1: Run focused test**

Run: `./gradlew :app:testDebugUnitTest --tests com.dailysatori.ui.theme.CompactTitleSpacingTest`

Expected: PASS.

- [ ] **Step 2: Run full unit tests**

Run: `./gradlew :app:testDebugUnitTest`

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Compile app**

Run: `./gradlew :app:compileDebugKotlin`

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Connect target device**

Run: `adb connect 192.168.2.11:39027`

Expected: output contains `connected to 192.168.2.11:39027` or `already connected`.

- [ ] **Step 5: Install debug build**

Run: `JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:installDebug`

Expected: `BUILD SUCCESSFUL` and `Installed on 1 device.`

- [ ] **Step 6: Launch app**

Run: `adb -s 192.168.2.11:39027 shell am start -n com.dailysatori/.MainActivity`

Expected: output contains `Starting: Intent`.

- [ ] **Step 7: Check git status**

Run: `git status --short`

Expected: no output.

---

## Self-Review

- Spec coverage: Task 1 slims shared title bars; Task 2 reduces detail first-body gaps; Task 3 verifies tests, compile, install, and launch.
- Placeholder scan: no TODO/TBD placeholders remain.
- Type/path consistency: file paths and symbol names match current project files.
