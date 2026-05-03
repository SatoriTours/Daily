# Collapsible Article Cover Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make article detail content use the full screen while collapsing the cover image upward during vertical scroll and expanding it again when scrolled back down.

**Architecture:** Keep `HorizontalPager` for AI summary/original tabs. Move each page to a `LazyColumn` that contains the optional cover, `TabRow`, and Markdown content so vertical scroll owns the whole page instead of only a small pager viewport. Add small pure functions/constants for cover collapse configuration and test them.

**Tech Stack:** Kotlin, Jetpack Compose Foundation pager/lazy list, Material 3, existing Daily Satori theme system.

---

### Task 1: Collapsible Cover Helpers

**Files:**
- Create: `app/src/main/kotlin/com/dailysatori/ui/feature/article/ArticleDetailLayout.kt`
- Create: `app/src/test/kotlin/com/dailysatori/ui/feature/article/ArticleDetailLayoutTest.kt`

- [ ] Write failing tests for cover height clamping: `0 -> 260`, `120 -> 140`, `300 -> 0`.
- [ ] Run `./gradlew :app:testDebugUnitTest --tests "com.dailysatori.ui.feature.article.ArticleDetailLayoutTest"` and verify unresolved helper failure.
- [ ] Add `articleCoverMaxHeightDp = 260` and `articleCollapsedCoverHeight(scrollOffsetPx, density)`.
- [ ] Run the focused test and verify pass.

### Task 2: Detail Page Scroll Layout

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/article/ArticleDetailScreen.kt`

- [ ] Replace the small `Box.verticalScroll` inside pager with a page-level `LazyColumn`.
- [ ] Place cover image as first lazy item with dynamic height based on the page scroll offset.
- [ ] Place `TabRow` as the next lazy item so it naturally moves to the top after the cover collapses.
- [ ] Place Markdown content as the final lazy item with normal padding.
- [ ] Keep `beyondViewportPageCount = 1` so AI summary/original are precomposed.
- [ ] Run article detail UI tests, `:app:compileDebugKotlin`, install, and launch.
