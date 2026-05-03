# Article Refresh UX Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make article refresh status visible across list/detail pages and improve the detail refresh controls and processing messages.

**Architecture:** Persist processing stage in `Article.status` while also keeping the existing in-memory `processingStates` for detailed progress. Add small UI text helpers for user-facing labels, use them from both list and detail screens, and move detail actions into a single overflow menu with refresh confirmation.

**Tech Stack:** Kotlin Multiplatform shared service, Android Jetpack Compose, kotlin.test/Gradle unit tests, Android Gradle build.

---

### Task 1: User-Facing Processing Text Helpers

**Files:**
- Create: `app/src/main/kotlin/com/dailysatori/ui/feature/article/ArticleProcessingUiText.kt`
- Create: `app/src/test/kotlin/com/dailysatori/ui/feature/article/ArticleProcessingUiTextTest.kt`

- [ ] **Step 1: Write failing tests**

Test that article statuses and known progress strings map to Chinese user-facing text: `pending` -> `正在打开网页...`, `webContentFetched` -> `网页内容已获取，正在整理...`, `Generating summary` -> `正在生成摘要...`, and unknown processing text -> `正在处理文章...`.

- [ ] **Step 2: Run tests and verify failure**

Run: `./gradlew :app:testDebugUnitTest --tests "com.dailysatori.ui.feature.article.ArticleProcessingUiTextTest"`
Expected: FAIL because helper functions do not exist.

- [ ] **Step 3: Implement helpers**

Add `articleProcessingMessage(status: String?, progress: String? = null): String?` and `isArticleProcessing(status: String?): Boolean`. Return null for `completed`, blank, or unknown non-processing statuses.

- [ ] **Step 4: Run tests and verify pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.dailysatori.ui.feature.article.ArticleProcessingUiTextTest"`
Expected: PASS.

### Task 2: Persist Refresh Stage In Article Status

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/service/parser/WebpageParserService.kt`

- [ ] **Step 1: Update status during AI stages**

When entering title, summary, Markdown, and cover-image stages, update `Article.status` to `aiProcessing` while preserving existing article fields.

- [ ] **Step 2: Keep completed and error statuses unchanged**

Do not change existing final `completed` and `error` updates.

### Task 3: Detail Screen Actions And Confirmation

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/article/ArticleDetailScreen.kt`

- [ ] **Step 1: Replace three actions with overflow menu**

Show one `MoreVert` icon. The dropdown contains refresh, favorite/unfavorite, and open-in-browser actions.

- [ ] **Step 2: Add refresh confirmation dialog**

Selecting refresh shows an `AlertDialog`; confirming calls `viewModel.refreshArticle()`.

- [ ] **Step 3: Improve processing message UI**

Keep `LinearProgressIndicator`. Replace raw status text with a rounded `Surface`/text message from `articleProcessingMessage`.

### Task 4: List Screen Processing State

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/component/card/ArticleCard.kt`

- [ ] **Step 1: Show processing label in cards**

When `article.status` is processing, show the user-facing label under the title or above the footer.

### Task 5: Verify

**Files:**
- No additional source changes.

- [ ] **Step 1: Run targeted app test**

Run: `./gradlew :app:testDebugUnitTest --tests "com.dailysatori.ui.feature.article.ArticleProcessingUiTextTest"`
Expected: PASS.

- [ ] **Step 2: Compile**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Install**

Run: `JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:installDebug`
Expected: BUILD SUCCESSFUL and installed on the connected device.

- [ ] **Step 4: Launch**

Run: `adb shell am start -n com.dailysatori/.MainActivity`
Expected: App launches.
