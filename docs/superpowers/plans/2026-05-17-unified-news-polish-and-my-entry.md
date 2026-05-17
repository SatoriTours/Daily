# Unified News Polish And My Entry Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Polish unified news loading, menu, Markdown reading, source details, and provide a consistent `我的` settings entry on main pages.

**Architecture:** Extend the shared app bar/scaffold with an optional main-page navigation action labeled `我的`, while detail pages continue using back navigation. Keep unified-news generation state as a skeleton overlay/card instead of text messages. Render unified-news list items with a dedicated composable for readable spacing and click targets, while retaining Markdown rendering for headings/paragraphs and source details.

**Tech Stack:** Kotlin, Jetpack Compose, Koin ViewModel, Material3, mikepenz Markdown renderer, JUnit text-based behavior tests.

---

### Task 1: App Bar `我的` Entry

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/component/appbar/AppTopBar.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/component/scaffold/AppScaffold.kt`
- Modify: top-level pages using `AppScaffold(showBack = false)`
- Test: `app/src/test/kotlin/com/dailysatori/UnifiedNewsBehaviorTest.kt`

- [ ] Add failing tests asserting `AppTopBar` supports `navigationText`, `AppScaffold` passes it through, and unified news calls `myNavigationLabel = "我的"`.
- [ ] Implement optional `myNavigationLabel: String?` and `onMyNavigationClick: (() -> Unit)?` in `AppScaffold` and `AppTopBar`.
- [ ] Render a text button in the navigation slot only when no back button is shown and both values are present.
- [ ] Pass the callback to main tab screens from `HomeScreen`; render `SettingsScreen(onBack = { showMy = false })` when `我的` is opened.

### Task 2: Unified News Loading Skeleton And Menu Cleanup

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsScreen.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsViewModel.kt`
- Test: `app/src/test/kotlin/com/dailysatori/UnifiedNewsBehaviorTest.kt`

- [ ] Add failing tests asserting the screen contains `UnifiedNewsGeneratingSkeleton`, no `新闻汇总已更新`, no `UnifiedNewsStatusBanner`, no `设置` in `UnifiedNewsMenu`, and menu label `生成/更新当日新闻`.
- [ ] Replace the text status banner with a skeleton card shown while `state.isRegenerating` is true.
- [ ] Make `manualRefreshMessage` return `null` for success and remove the persistent success banner behavior.
- [ ] Remove settings from unified-news menu and rename regenerate item.

### Task 3: Readable Summary Markdown List Rendering

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/unifiednews/CitationText.kt`
- Test: `app/src/test/kotlin/com/dailysatori/UnifiedNewsBehaviorTest.kt`

- [ ] Add failing tests asserting `CitationText` contains `UnifiedNewsBulletItem`, `SelectionContainer`, and still uses Markdown for non-list blocks.
- [ ] Split formatted content into lines. Render list lines (`-`, `*`, `+`) as dedicated clickable list rows when they contain citation links.
- [ ] Render non-list buffered content with existing Markdown styling.
- [ ] Keep citation URL routing through `citationFromUnifiedNewsUrl` and `onCitationClick`.

### Task 4: Crayfish Article Detail Markdown And Cache

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsScreen.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsViewModel.kt`
- Test: `app/src/test/kotlin/com/dailysatori/UnifiedNewsBehaviorTest.kt`

- [ ] Add failing tests asserting `CrayfishArticleDetailScreen` renders `Markdown(`, uses `article.summary`, and ViewModel caches article details by route key.
- [ ] Add `private val crayfishArticleCache = mutableMapOf<String, CrayfishArticle>()`.
- [ ] In `openCrayfishArticle`, return cached detail immediately when present; otherwise fetch, cache, and render.
- [ ] Render `article.summary` using Markdown in the detail page, not plain `Text`.

### Task 5: Verification

- [ ] Run `./gradlew :app:testDebugUnitTest --tests com.dailysatori.UnifiedNewsBehaviorTest --no-configuration-cache`.
- [ ] Run `./gradlew :app:compileDebugKotlin --no-configuration-cache`.
- [ ] Run `JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:installDebug --no-configuration-cache`.
- [ ] Run `adb shell am force-stop com.dailysatori && adb shell am start -n com.dailysatori/.MainActivity`.

---

## Self-Review

- Spec coverage: covers `我的` top-left entry, back on detail pages, skeleton generation state, menu cleanup, no persistent success text, custom summary list rendering, Markdown source detail, and Crayfish article detail caching.
- Placeholder scan: no TBD/TODO placeholders.
- Type consistency: `myNavigationLabel`, `onMyNavigationClick`, `UnifiedNewsGeneratingSkeleton`, `UnifiedNewsBulletItem`, and `crayfishArticleCache` names are used consistently.
