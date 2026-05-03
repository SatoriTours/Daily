# Article Add Share Background Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make article additions visible, make external share return to the source app with Toast feedback, and run article processing through durable Android background work.

**Architecture:** Add small policy helpers with unit tests, route long article saves through WorkManager, and use a transparent share receiver Activity for external `ACTION_SEND`. UI scroll behavior stays in the article list and is triggered by a one-shot ViewModel event.

**Tech Stack:** Kotlin, Jetpack Compose, Koin, Android WorkManager, SQLDelight, Kotlin coroutines.

---

### Task 1: Add Testable Article Intake Policies

**Files:**
- Modify: `app/src/test/kotlin/com/dailysatori/AppUrlIntakeTest.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/AppUrlIntake.kt`

- [ ] Add tests for share Toast messages and scroll trigger policy.
- [ ] Run `./gradlew :app:testDebugUnitTest --tests com.dailysatori.AppUrlIntakeTest`; expect unresolved references.
- [ ] Add `shareSaveStartedToastMessage()`, `shareInvalidUrlToastMessage()`, and `shouldScrollToTopAfterArticleAdded()`.
- [ ] Rerun focused tests; expect pass.

### Task 2: Add Background Processing Worker

**Files:**
- Create: `app/src/main/kotlin/com/dailysatori/core/worker/ArticleProcessingWorker.kt`
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/kotlin/com/dailysatori/core/di/AppModule.kt` or existing DI module if WorkManager factory support already exists.

- [ ] Add WorkManager dependency if missing.
- [ ] Implement `ArticleProcessingWorker` with input keys `url` and `mode`; `mode=save` calls `WebpageParserService.saveWebpage(url, null, null, null)`, `mode=resume` calls `resumeInterruptedProcessing()`.
- [ ] Add companion methods `enqueueSave(context, url)` and `enqueueResume(context)`.
- [ ] Compile to verify worker dependencies resolve.

### Task 3: Route Adds Through Worker And Scroll To Top

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/article/ArticlesViewModel.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/article/ArticleListScreen.kt`

- [ ] Replace direct `saveWebpage()` in `ArticlesViewModel.addArticle()` with worker enqueue.
- [ ] Add a one-shot `scrollToTopRequest` counter/state field after successful enqueue.
- [ ] Add `rememberLazyListState()` to `ArticleListScreen` and pass it to `LazyColumn`.
- [ ] Use `LaunchedEffect(state.scrollToTopRequest)` to call `listState.animateScrollToItem(0)` when requested and articles are non-empty.
- [ ] Run focused tests and compile.

### Task 4: Add Toast Share Receiver Activity

**Files:**
- Create: `app/src/main/kotlin/com/dailysatori/ShareReceiverActivity.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/kotlin/com/dailysatori/MainActivity.kt`

- [ ] Move `ACTION_SEND text/plain` intent filter from `MainActivity` to `ShareReceiverActivity`.
- [ ] Give receiver Activity a transparent/no-display theme.
- [ ] In receiver, extract URL, duplicate-check using `ArticleRepository`, enqueue worker for new URLs, show Toast, and call `finish()`.
- [ ] Keep `MainActivity` as normal launcher only.

### Task 5: Route Clipboard And Startup Resume Through Worker

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/AppUrlIntakeViewModel.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/DailySatoriApplication.kt`

- [ ] Clipboard confirmation enqueues save worker instead of direct `saveWebpage()`.
- [ ] App startup enqueues resume worker instead of directly calling `resumeInterruptedProcessing()` from `GlobalScope`.
- [ ] Preserve duplicate snackbar and clipboard confirmation behavior.

### Task 6: Verify

**Files:**
- No code edits.

- [ ] Run `./gradlew :app:testDebugUnitTest --tests com.dailysatori.AppUrlIntakeTest`.
- [ ] Run `./gradlew :app:compileDebugKotlin`.
- [ ] Run `JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:installDebug`.
- [ ] Run `adb shell am start -n com.dailysatori/.MainActivity`.
