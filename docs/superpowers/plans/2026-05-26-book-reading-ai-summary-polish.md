# Book Reading AI Summary Polish Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the book reading page visually cleaner and route all viewpoint card generation through AI using WeRead data as source material.

**Architecture:** Keep the existing book UI and service boundaries. `ViewpointCard` handles reading layout only; `WeReadSkillService` fetches WeRead metadata and delegates structured viewpoint writing to `BookAiFallbackGenerator` instead of local templates.

**Tech Stack:** Kotlin Multiplatform, Android Compose, Ktor, kotlinx.serialization, Koin, Gradle JVM tests.

---

## Files

- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/book/ViewpointCard.kt` for centered title and no inline progress.
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/book/BooksScreen.kt` to stop passing visible progress.
- Modify: `app/src/test/kotlin/com/dailysatori/ui/feature/book/BooksScreenUiTextTest.kt` for progress expectations.
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/service/book/WeReadSkillService.kt` to always use AI generation after WeRead fetch.
- Modify: `shared/src/commonTest/kotlin/com/dailysatori/service/book/BookIntelligenceServiceTest.kt` for generation behavior and prompt rules.

## Task 1: Reading Layout

- [ ] Add failing tests asserting reader hides progress and centers title.
- [ ] Run `./gradlew :app:testDebugUnitTest --tests com.dailysatori.ui.feature.book.BooksScreenUiTextTest` and confirm the new assertions fail.
- [ ] Update `ViewpointCard` to center title, center secondary book line, and default `showProgress` to false.
- [ ] Update `BooksScreen` to pass `showProgress = false` or omit progress display.
- [ ] Rerun the focused UI text test.

## Task 2: AI-First Viewpoint Generation

- [ ] Add tests asserting `selectWeReadOrAiViewpoints` invokes AI even with sufficient WeRead material.
- [ ] Add prompt tests for risk, condition, boundary, direct-story cases, and no “书中情境” phrasing.
- [ ] Run `./gradlew :shared:allTests --tests com.dailysatori.service.book.BookIntelligenceServiceTest` and confirm failures.
- [ ] Replace local WeRead draft selection with AI generation from WeRead metadata.
- [ ] Strengthen the AI prompt and JSON parser minimums so content is explanatory and examples are story-like.
- [ ] Rerun focused shared tests.

## Task 3: Verification

- [ ] Run `./gradlew :app:compileDebugKotlin`.
- [ ] Run `./gradlew :app:assembleDebug`.
- [ ] Run `JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:installDebug`.
- [ ] Run `adb shell am start -n com.dailysatori/.MainActivity`.
