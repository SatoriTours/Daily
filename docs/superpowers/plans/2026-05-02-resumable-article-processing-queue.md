# Resumable Article Processing Queue Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Automatically resume interrupted article processing on app startup while limiting concurrent article processing to five tasks.

**Architecture:** Use existing `article.status` as the durable recovery source. Add a bounded in-memory queue in `WebpageParserService`; all async article processing entry points enqueue work, skip duplicate IDs, and never auto-enqueue `error` articles.

**Tech Stack:** Kotlin Multiplatform shared service, SQLDelight queries, Android Application startup, kotlin.test/Gradle tests.

---

### Task 1: Recoverable Status Helpers

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/service/parser/WebpageParserService.kt`
- Modify: `shared/src/commonTest/kotlin/com/dailysatori/service/parser/ArticleProcessingContentTest.kt`

- [ ] **Step 1: Add failing tests**

Test `isRecoverableArticleStatus()` returns true for `pending`, `webContentFetched`, and `aiProcessing`, and false for `completed`, `error`, blank, and unknown values.

- [ ] **Step 2: Implement helper**

Add internal helper and use it for startup recovery filtering.

### Task 2: Repository Recovery Query

**Files:**
- Modify: `shared/src/commonMain/sqldelight/com/dailysatori/shared/db/DailySatori.sq`
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/data/repository/ArticleRepository.kt`

- [ ] **Step 1: Add SQLDelight query**

Add `selectRecoverableArticles` query for statuses `pending`, `webContentFetched`, and `aiProcessing`, ordered by `updated_at ASC`.

- [ ] **Step 2: Add repository method**

Add `getRecoverableForProcessingSync(): List<Article>`.

### Task 3: Bounded Processing Queue

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/service/parser/WebpageParserService.kt`

- [ ] **Step 1: Add queue state**

Add `pendingProcessingIds`, `activeProcessingIds`, and `maxConcurrentProcessing = 5`.

- [ ] **Step 2: Add enqueue/drain methods**

Add `enqueueArticleProcessing(articleId: Long)` and `drainProcessingQueue()`. Skip IDs already queued or active. Start up to five workers.

- [ ] **Step 3: Route async processing through queue**

Make `processAiTasksAsync(articleId, extracted)` enqueue unless `extracted` is present. For new articles with extracted content, process immediately through the queue worker path if slot is available; otherwise enqueue article ID and refetch when processed.

- [ ] **Step 4: Ensure active IDs clear on completion/failure**

Remove IDs from active in `finally` and continue draining the queue.

### Task 4: Startup Recovery

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/service/parser/WebpageParserService.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/DailySatoriApplication.kt`

- [ ] **Step 1: Add service method**

Add `resumeInterruptedProcessing()` that enqueues all repository recoverable articles.

- [ ] **Step 2: Call on app startup**

After Koin and migrations initialize, launch IO coroutine and call `resumeInterruptedProcessing()`.

### Task 5: Verify

**Files:**
- No additional source changes.

- [ ] **Step 1: Run shared targeted tests**

Run: `./gradlew :shared:testDebugUnitTest --tests "com.dailysatori.service.parser.ArticleProcessingContentTest"`
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
