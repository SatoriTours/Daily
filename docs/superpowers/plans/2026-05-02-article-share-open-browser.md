# Article Share Open Browser Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make both article share buttons open the article URL in the browser instead of showing a share sheet or doing nothing.

**Architecture:** Add one small Android intent helper in the article UI package and use it from both the list and detail screens. Keep behavior minimal: if the URL is blank, do nothing.

**Tech Stack:** Android Jetpack Compose, Android `Intent.ACTION_VIEW`, Gradle Android build.

---

### Task 1: Shared Browser Open Helper

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/article/ArticleListScreen.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/article/ArticleDetailScreen.kt`
- Create: `app/src/main/kotlin/com/dailysatori/ui/feature/article/ArticleUrlActions.kt`

- [ ] **Step 1: Create helper**

Create `openArticleUrl(context: Context, url: String?)`. Return early for blank URLs. Otherwise start `Intent(Intent.ACTION_VIEW, Uri.parse(url))`.

- [ ] **Step 2: Wire list share button**

Replace the `ACTION_SEND` chooser in `ArticleListScreen` with `openArticleUrl(context, article.url)`.

- [ ] **Step 3: Wire detail share button**

Use `LocalContext.current` in `ArticleDetailScreen` and replace the empty share action with `openArticleUrl(context, state.article?.url)`.

### Task 2: Verify

**Files:**
- No additional source changes.

- [ ] **Step 1: Compile**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Install**

Run: `JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:installDebug`
Expected: BUILD SUCCESSFUL and installed on the connected device.

- [ ] **Step 3: Launch**

Run: `adb shell am start -n com.dailysatori/.MainActivity`
Expected: App launches.
