# Crayfish News History Rendering Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Crayfish News show scrollable historical news by default and render details using only the original markdown content.

**Architecture:** Keep the existing Crayfish API service. Change `CrayfishNewsViewModel` so LATEST and DJI modes load category history lists instead of one latest item, and change the UI to render those lists. Simplify detail rendering to markdown `content` only.

**Tech Stack:** Kotlin, Jetpack Compose, Ktor, Koin, mikepenz/markdown

---

## Task 1: ViewModel History Flow

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/crayfishnews/CrayfishNewsViewModel.kt`

- [ ] Replace `loadLatest()` so `CrayfishNewsMode.LATEST` calls `fetchNewsList(config, category = "general", limit = 20)` and stores `result.value.general` into `archiveGeneral`.
- [ ] Replace `loadDji()` so `CrayfishNewsMode.DJI` calls `fetchNewsList(config, category = "dji", limit = 20)` and stores `result.value.dji` into `archiveDji`.
- [ ] Change `loadInitial()` and `switchMode()` so LATEST needs `archiveGeneral`, DJI needs `archiveDji`.
- [ ] Keep `openArchiveItem(filename, category)` for fetching full content.

## Task 2: List UI

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/crayfishnews/CrayfishNewsScreen.kt`

- [ ] Remove the latest-news intro card path for LATEST and DJI modes.
- [ ] Render `archiveGeneral` for LATEST and `archiveDji` for DJI using `ArchiveItemCard`.
- [ ] Remove the user-facing ARCHIVE menu item, because each mode is already a history feed.
- [ ] Keep `返回远程新闻` and `刷新` menu actions.

## Task 3: Detail Rendering

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/crayfishnews/CrayfishNewsDetailScreen.kt`

- [ ] Remove generated timestamp row.
- [ ] Remove `sections` rendering.
- [ ] Render only `news.content` through `Markdown`.
- [ ] Use a stable title like `小龙虾新闻` or `大疆新闻` instead of filename-derived title.

## Task 4: Verification

- [ ] Run `JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:compileDebugKotlin` and confirm BUILD SUCCESSFUL.
- [ ] Run `JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:assembleDebug` and confirm BUILD SUCCESSFUL.
- [ ] Run `JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:installDebug && adb shell am start -n com.dailysatori/.MainActivity` and confirm install + launch.
