# Crayfish Continuous Feed Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Show full Crayfish articles inline and load older articles as the user scrolls.

**Architecture:** Reuse the existing API service. Extend `CrayfishNewsViewModel` with per-category file lists, loaded full articles, and batch loading. Replace the screen list/detail routing with a single lazy feed that triggers `loadMore()` at the end.

**Tech Stack:** Kotlin, Jetpack Compose, Koin, Ktor, mikepenz/markdown

---

## Task 1: ViewModel Batch Feed

- Modify `CrayfishNewsViewModel.kt` to store `generalFiles`, `djiFiles`, `generalArticles`, `djiArticles`, and `isLoadingMore`.
- Load `/news?category=<category>&limit=50`, then fetch the first 3 full files.
- Add `loadMore()` to fetch the next 3 files for the active category.
- Refresh clears and reloads the active category.

## Task 2: Inline Feed UI

- Modify `CrayfishNewsScreen.kt` to remove selected-news routing.
- Render loaded full articles with markdown content inline.
- Use `LazyListState` to call `viewModel.loadMore()` when the last item becomes visible.
- Show loading-more text at the bottom.

## Task 3: Verification

- Run `JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:compileDebugKotlin`.
- Run `JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:assembleDebug`.
- Run `JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:installDebug && adb shell am start -n com.dailysatori/.MainActivity`.
