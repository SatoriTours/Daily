# Remote Article Reader Cards Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make remote summary detail Markdown feel like an article reading page with lightweight information-card styling.

**Architecture:** Add remote-article-specific Markdown typography and padding in the existing theme object, then render remote article content through a dedicated card wrapper. Keep shared `MarkdownContent` unchanged so local articles, chat, and diary cards are not affected.

**Tech Stack:** Kotlin, Jetpack Compose Material3, multiplatform-markdown-renderer, Gradle unit tests.

---

### Task 1: Remote Reader Markdown Style

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/theme/MarkdownStyles.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/remotenews/RemoteArticleDetailScreen.kt`
- Test: `app/src/test/kotlin/com/dailysatori/ui/feature/remotenews/RemoteArticleDetailLayoutTest.kt`

- [ ] **Step 1: Write failing tests**

Assert `MarkdownStyles.remoteArticleTypography()`, `MarkdownStyles.remoteArticlePadding()`, and a remote-only `RemoteArticleMarkdownContent` wrapper are used in `RemoteArticleDetailScreen.kt` instead of the generic `MarkdownContent` call.

- [ ] **Step 2: Run focused test**

Run: `./gradlew :app:testDebugUnitTest --tests com.dailysatori.ui.feature.remotenews.RemoteArticleDetailLayoutTest`

Expected: FAIL because the remote detail currently uses generic `MarkdownContent` and no remote style exists.

- [ ] **Step 3: Implement remote Markdown style**

Add `remoteArticleTypography()` and `remoteArticlePadding()` to `MarkdownStyles.kt`, tuned for card-like summary reading: clear headings, comfortable body, compact lists, and modest quote/code spacing.

- [ ] **Step 4: Implement remote content card wrapper**

In `RemoteArticleDetailScreen.kt`, add `RemoteArticleMarkdownContent(content: String)` that wraps Markdown in `SelectionContainer` and `Surface(shape = RoundedCornerShape(Radius.l), color = MaterialTheme.colorScheme.surfaceContainerLow)` with padding. Use `MarkdownStyles.remoteArticleTypography()` and `MarkdownStyles.remoteArticlePadding()`.

- [ ] **Step 5: Run focused test**

Expected: PASS.

### Task 2: Verification And Device Install

**Files:**
- No additional files.

- [ ] **Step 1: Run focused tests**

Run: `./gradlew :app:testDebugUnitTest --tests com.dailysatori.ui.feature.remotenews.RemoteArticleDetailLayoutTest`

Expected: PASS.

- [ ] **Step 2: Run required compile**

Run: `JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:compileDebugKotlin`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Connect, install, and launch**

Run: `adb connect 192.168.2.12:37343`

Run: `JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:installDebug`

Run: `adb shell am start -n com.dailysatori/.MainActivity`

Expected: device connects, APK installs, app starts.

---

Self-review: The plan is limited to remote article summary/detail reading style, preserves shared Markdown behavior, and includes exact verification commands.
