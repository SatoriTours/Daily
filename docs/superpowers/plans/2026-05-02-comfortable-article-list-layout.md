# Comfortable Article List Layout Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the article list feel less crowded by increasing card height, internal padding, summary visibility, and spacing between articles.

**Architecture:** Keep the existing single-column `LazyColumn` and `ArticleCard` structure. Add small testable layout constants for article list/card density, then wire those constants into `ArticleListScreen` and `ArticleCard`.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, existing Daily Satori spacing/theme system.

---

### Task 1: Test Comfortable Density Constants

**Files:**
- Create: `app/src/main/kotlin/com/dailysatori/ui/feature/article/ArticleListLayout.kt`
- Create: `app/src/test/kotlin/com/dailysatori/ui/feature/article/ArticleListLayoutTest.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/article/ArticleListScreen.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/component/card/ArticleCard.kt`

- [ ] Write a failing test that expects article card height `128`, list item spacing `16`, vertical text padding `14`, and summary max lines `2`.
- [ ] Run `./gradlew :app:testDebugUnitTest --tests "com.dailysatori.ui.feature.article.ArticleListLayoutTest"` and verify it fails because constants do not exist.
- [ ] Add `ArticleListLayout.kt` with internal constants.
- [ ] Update `ArticleListScreen` to use `articleListItemSpacing` for `Arrangement.spacedBy`.
- [ ] Update `ArticleCard` to use `articleCardHeight`, `articleCardContentVerticalPadding`, and `articleCardSummaryMaxLines`.
- [ ] Run the focused unit test and verify it passes.
- [ ] Run `./gradlew :app:compileDebugKotlin`, install, and launch the app.
