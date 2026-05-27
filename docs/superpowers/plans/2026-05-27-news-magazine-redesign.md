# News Magazine Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the approved borderless magazine redesign for news summary, source tabs, remote/local lists, and article detail pages.

**Architecture:** Keep existing repositories, navigation targets, and generation services. Add small shared UI helpers for magazine list rows and detail reading bodies, then adapt unified news, remote article detail, and local article list/detail screens to use those helpers.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Gradle Android, existing Daily Satori theme tokens.

---

## Current Workspace Note

There are uncommitted code experiment changes from the previous briefing iteration in:

- `app/src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsBriefingContent.kt`
- `app/src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsScreen.kt`
- `app/src/test/kotlin/com/dailysatori/UnifiedNewsBehaviorTest.kt`
- `app/src/test/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsBriefingContentTest.kt`

Do not preserve old briefing/stat-card behavior. Rework these files toward the magazine spec. Do not use `git checkout` or destructive reset; edit files forward with `apply_patch`.

## File Structure

- Create: `app/src/main/kotlin/com/dailysatori/ui/component/news/MagazineNewsCard.kt`
  - Shared equal-height magazine list card for remote and local news.
- Create: `app/src/main/kotlin/com/dailysatori/ui/component/news/MagazineArticleDetail.kt`
  - Shared borderless article detail header/body helpers.
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsBriefingContent.kt`
  - Keep parsing helpers, rename/adjust semantics to magazine summary content if useful.
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsScreen.kt`
  - Magazine summary, unified tabs, inline refresh, local news tab, remote list display, menu simplification.
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/remotenews/RemoteArticleCards.kt`
  - Replace remote list item visual with shared magazine card.
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/remotenews/RemoteArticleDetailScreen.kt`
  - Remove bordered markdown body; use shared magazine detail style.
- Modify: `app/src/main/kotlin/com/dailysatori/ui/component/card/ArticleCard.kt`
  - Use shared equal-height magazine card for local articles.
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/article/ArticleDetailScreen.kt`
  - Use shared borderless magazine detail style for local article details.
- Modify/Create tests under `app/src/test/kotlin/com/dailysatori/` and `app/src/test/kotlin/com/dailysatori/ui/feature/...`.

## Task 1: Magazine Summary Content Model

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsBriefingContent.kt`
- Modify: `app/src/test/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsBriefingContentTest.kt`

- [ ] **Step 1: Write failing tests for magazine summary semantics**

Update tests so the parser expects:

```kotlin
@Test
fun `magazine content uses stable cover title and story points`() {
    val model = unifiedNewsBriefingContent(
        """
        今天 AI 工具开始从功能展示走向团队治理。

        - **AI 编程工具强调治理** [R1]
        - 消费电子新品集中发布 [R2]
        """.trimIndent(),
    )

    assertEquals("今日封面", model.title)
    assertEquals("今天 AI 工具开始从功能展示走向团队治理。", model.lead)
    assertEquals(2, model.points.size)
    assertEquals("AI 编程工具强调治理", model.points[0].text)
}
```

- [ ] **Step 2: Run failing tests**

Run: `./gradlew :app:testDebugUnitTest --tests com.dailysatori.ui.feature.unifiednews.UnifiedNewsBriefingContentTest`

Expected: FAIL if title still says `今日简报` or old stat language exists.

- [ ] **Step 3: Implement minimal parser update**

Change `unifiedNewsBriefingContent` to return `title = "今日封面"` and keep lead/points extraction. Do not add statistics or counts.

- [ ] **Step 4: Verify tests pass**

Run: `./gradlew :app:testDebugUnitTest --tests com.dailysatori.ui.feature.unifiednews.UnifiedNewsBriefingContentTest`

Expected: PASS.

## Task 2: Shared Magazine News List Card

**Files:**
- Create: `app/src/main/kotlin/com/dailysatori/ui/component/news/MagazineNewsCard.kt`
- Modify: `app/src/test/kotlin/com/dailysatori/ui/feature/remotenews/RemoteNewsUiBehaviorTest.kt` or create `app/src/test/kotlin/com/dailysatori/ui/component/news/MagazineNewsCardSourceTest.kt`

- [ ] **Step 1: Write source-level guardrail test**

Create a test that reads `MagazineNewsCard.kt` and asserts:

```kotlin
assertTrue(source.contains("height(articleCardHeightDp.dp)"))
assertTrue(source.contains("maxLines = 2"))
assertTrue(source.contains("maxLines = articleCardSummaryMaxLines"))
assertTrue(source.contains("Spacer(modifier = Modifier.weight(1f))"))
assertFalse(source.contains("BorderStroke"))
```

- [ ] **Step 2: Run test and verify red**

Run the new test. Expected: FAIL because file does not exist.

- [ ] **Step 3: Implement shared card**

Create a composable:

```kotlin
@Composable
fun MagazineNewsCard(
    title: String,
    summary: String?,
    meta: String?,
    coverUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailingActions: @Composable RowScope.() -> Unit = {},
)
```

Use a fixed-height `CustomCard`, fixed thumbnail width, title max 2 lines, summary max `articleCardSummaryMaxLines`, metadata at bottom, no border. Use existing `articleCardHeightDp`, `articleCardContentVerticalPaddingDp`, `Radius`, `Spacing`, and `MaterialTheme`.

- [ ] **Step 4: Verify test passes**

Run the new guardrail test. Expected: PASS.

## Task 3: Remote And Local Lists Use Shared Card

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/remotenews/RemoteArticleCards.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/component/card/ArticleCard.kt`
- Modify tests for remote/local list card behavior.

- [ ] **Step 1: Write failing tests**

Add source tests asserting:

```kotlin
assertTrue(remoteCards.contains("MagazineNewsCard("))
assertTrue(localCard.contains("MagazineNewsCard("))
assertTrue(remoteCards.contains("remoteArticleSummaryText(article)"))
assertTrue(localCard.contains("article.ai_content"))
```

- [ ] **Step 2: Run tests and verify red**

Expected: FAIL until both cards use shared component.

- [ ] **Step 3: Adapt remote card**

Change `RemoteArticleSummaryCard` to call `MagazineNewsCard` with title, `remoteArticleSummaryText(article)`, `remoteArticleTimeText(article)` plus feed/domain metadata, cover URL, and click.

- [ ] **Step 4: Adapt local card**

Change `ArticleCard` to call `MagazineNewsCard`, passing local title, `ai_content`, domain/time meta, cover image, click, and existing favorite/share actions as trailing actions.

- [ ] **Step 5: Verify tests pass**

Run focused tests. Expected: PASS.

## Task 4: Unified News Tabs, Refresh, Menu, And Local News Tab

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsViewModel.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsScreen.kt`
- Modify: `app/src/test/kotlin/com/dailysatori/UnifiedNewsBehaviorTest.kt`

- [ ] **Step 1: Write failing source tests**

Add tests asserting:

```kotlin
assertTrue(screen.contains("本地新闻"))
assertTrue(screen.contains("UnifiedNewsPage.LOCAL_ARTICLES"))
assertFalse(menuBody.contains("本地文章"))
assertFalse(menuBody.contains("生成/更新当日新闻"))
assertTrue(sourceSwitcher.contains("Icons.Default.Refresh"))
```

Also assert remote list header strings are gone:

```kotlin
assertFalse(screen.contains("今日文章"))
assertFalse(screen.contains("共 ${articles.size} 篇"))
```

- [ ] **Step 2: Run tests and verify red**

Expected: FAIL until UI changes are made.

- [ ] **Step 3: Implement tab row**

Update `UnifiedNewsSourceSwitcher` to include `汇总`, remote sources, `本地新闻`, and a refresh `IconButton` at row end. Refresh dispatches:

- summary selected: `viewModel.regenerateCurrentWindow()`
- remote selected: `viewModel.refreshSelectedRemoteSource()`
- local selected/page: reload or switch to `LOCAL_ARTICLES` with existing article list behavior.

- [ ] **Step 4: Simplify menu**

Remove `本地文章` and `生成/更新当日新闻` from `UnifiedNewsMenu`. Keep favorites/settings if still useful.

- [ ] **Step 5: Remove remote list header**

Delete the `"${selection.name} · 今日文章"` and count header from `UnifiedNewsSourceArticleList`. The list should directly show article cards and optional compact refresh error notice.

- [ ] **Step 6: Verify focused tests pass**

Run: `./gradlew :app:testDebugUnitTest --tests com.dailysatori.UnifiedNewsBehaviorTest`

Expected: PASS for relevant source tests.

## Task 5: Magazine Summary Card

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/unifiednews/UnifiedNewsScreen.kt`
- Modify: `app/src/test/kotlin/com/dailysatori/UnifiedNewsBehaviorTest.kt`

- [ ] **Step 1: Write failing visual guardrails**

Assert summary card no longer contains `UnifiedNewsBriefingStats`, stat labels `来源`, `重点`, `引用`, or nested `BorderStroke` around point rows/fallback.

- [ ] **Step 2: Run and verify red**

Expected: FAIL if old briefing/stat-card implementation remains.

- [ ] **Step 3: Implement magazine summary**

Replace old briefing hero with magazine cover structure: date chip, editorial title/lead, story rows, source chips as quiet metadata. Remove `UnifiedNewsBriefingStats` and `UnifiedNewsBriefingStatTile`.

- [ ] **Step 4: Verify tests pass**

Run focused source tests. Expected: PASS.

## Task 6: Borderless Magazine Detail Pages

**Files:**
- Create/Modify: `app/src/main/kotlin/com/dailysatori/ui/component/news/MagazineArticleDetail.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/remotenews/RemoteArticleDetailScreen.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/article/ArticleDetailScreen.kt`
- Add/modify detail layout tests.

- [ ] **Step 1: Write failing detail guardrails**

Add source tests asserting:

```kotlin
assertFalse(remoteDetailBody.contains("border = BorderStroke"))
assertFalse(localDetailBody.contains("border = BorderStroke"))
assertTrue(remoteDetail.contains("MagazineArticle"))
assertTrue(localDetail.contains("MagazineArticle"))
```

- [ ] **Step 2: Run and verify red**

Expected: FAIL while remote detail still wraps markdown in a bordered `Surface`.

- [ ] **Step 3: Implement shared borderless detail helpers**

Create helpers for metadata chips, large title, intro, cover image slot, and borderless markdown body. Use `SelectionContainer`, `MarkdownStyles.remoteArticleTypography()` or existing detail styles, no visible border around body.

- [ ] **Step 4: Adapt remote detail**

Remove `RemoteArticleMarkdownContent` bordered `Surface` and use shared borderless body. Keep AI summary/original tabs and favorite/open actions.

- [ ] **Step 5: Adapt local detail**

Add title/meta header above tab/body and use shared borderless body around `MarkdownContent` or a new markdown helper. Keep refresh/favorite/open/delete actions.

- [ ] **Step 6: Verify tests pass**

Run focused detail tests. Expected: PASS.

## Task 7: Full Verification And Install

**Files:**
- Verify only.

- [ ] **Step 1: Run focused tests**

Run all tests added/changed for news magazine redesign.

- [ ] **Step 2: Compile**

Run: `./gradlew :app:compileDebugKotlin`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Assemble**

Run: `./gradlew :app:assembleDebug`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Install to target device**

Run:

```bash
adb connect 192.168.2.11:37585
adb -s 192.168.2.11:37585 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s 192.168.2.11:37585 shell am start -n com.dailysatori/.MainActivity
```

Expected: install `Success` and app starts.

- [ ] **Step 5: Manual visual checks**

Check summary, remote source list, local news list, remote detail from list, remote detail from citation, local detail from list, local detail from citation, light mode, and dark mode.

## Self-Review

Spec coverage:

- Summary redesign: Task 5.
- Source tabs/refresh/menu: Task 4.
- Remote/local equal-height lists: Tasks 2 and 3.
- Detail consistency: Task 6.
- Verification/install: Task 7.

Placeholder scan:

- No TBD/TODO placeholders remain.

Type consistency:

- Shared card/helper names are consistent: `MagazineNewsCard`, `MagazineArticleDetail`.
