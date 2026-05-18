# Staged Project Refactor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Refactor the Daily Satori KMP Android project in behavior-preserving stages so code is simpler, modules are clearer, files are better sized, and duplicated code is reduced.

**Architecture:** Execute one refactor stage at a time from a clean worktree. Each stage is independently buildable and commit-sized: cleanup legacy project files, extract reusable UI foundations, split large screens, simplify shared services, standardize data/migration code, then update docs and guardrails.

**Tech Stack:** Kotlin Multiplatform, Jetpack Compose Material 3, Koin, SQLDelight, Gradle, GitHub Actions.

---

## File Structure

This plan intentionally keeps the current module layout:

- `app/src/main/kotlin/com/dailysatori/ui/component/`: reusable stateless UI primitives.
- `app/src/main/kotlin/com/dailysatori/ui/feature/<feature>/`: feature entry screens, feature-specific components, ViewModels.
- `shared/src/commonMain/kotlin/com/dailysatori/service/`: shared business services and pure helpers.
- `shared/src/commonMain/kotlin/com/dailysatori/data/repository/`: SQLDelight repository wrappers.
- `shared/src/commonMain/sqldelight/`: database schema and queries.
- `docs/`: current architecture, testing, and refactor documentation.

Commit after each task unless the task is explicitly a verification-only task.

## Task 1: Baseline Verification And Worktree Setup

**Files:**
- No source files modified.

- [ ] **Step 1: Confirm clean main workspace**

Run:

```bash
git status --short
git log --oneline -5
```

Expected: no working tree changes. Recent commits include the staged refactor design.

- [ ] **Step 2: Create isolated refactor worktree**

Run:

```bash
git worktree add ".worktrees/staged-project-refactor" -b "refactor/staged-project-cleanup"
```

Expected: new worktree at `.worktrees/staged-project-refactor` on branch `refactor/staged-project-cleanup`.

- [ ] **Step 3: Run baseline compile in worktree**

Run from `.worktrees/staged-project-refactor`:

```bash
./gradlew :app:compileDebugKotlin --no-configuration-cache
```

Expected: `BUILD SUCCESSFUL`.

## Task 2: Replace Flutter CI With Gradle CI

**Files:**
- Modify: `.github/workflows/unit-tests.yml`
- Modify: `test.sh`

- [ ] **Step 1: Replace workflow with Android/KMP checks**

Replace `.github/workflows/unit-tests.yml` with:

```yaml
name: Unit Tests

on:
  push:
    branches: [main, master]
    paths-ignore:
      - 'docs/**'
      - '**.md'
  pull_request:
    branches: [main, master]
    paths-ignore:
      - 'docs/**'
      - '**.md'

jobs:
  test:
    runs-on: ubuntu-latest

    steps:
      - name: Checkout code
        uses: actions/checkout@v4.2.2

      - name: Set up JDK 21
        uses: actions/setup-java@v4.5.0
        with:
          distribution: 'temurin'
          java-version: '21'

      - name: Set up Gradle
        uses: gradle/actions/setup-gradle@v4

      - name: Grant execute permission to Gradle wrapper
        run: chmod +x ./gradlew

      - name: Compile debug Kotlin
        run: ./gradlew :app:compileDebugKotlin --no-configuration-cache

      - name: Run debug unit tests
        run: ./gradlew :app:testDebugUnitTest --no-configuration-cache

      - name: Upload test results
        if: always()
        uses: actions/upload-artifact@v4.4.3
        with:
          name: test-results
          path: |
            app/build/reports/tests/
            app/build/test-results/
            shared/build/reports/tests/
            shared/build/test-results/
          retention-days: 7
```

- [ ] **Step 2: Replace `test.sh` with Gradle wrapper commands**

Replace `test.sh` with:

```bash
#!/bin/bash

set -euo pipefail

RED='\033[0;31m'
GREEN='\033[0;32m'
BLUE='\033[0;34m'
NC='\033[0m'

print_info() { printf "%b[INFO]%b %s\n" "$BLUE" "$NC" "$1"; }
print_success() { printf "%b[OK]%b %s\n" "$GREEN" "$NC" "$1"; }
print_error() { printf "%b[ERROR]%b %s\n" "$RED" "$NC" "$1"; }

compile() {
    print_info "编译检查..."
    ./gradlew :app:compileDebugKotlin --no-configuration-cache
    print_success "编译通过"
}

unit_test() {
    print_info "单元测试..."
    ./gradlew :app:testDebugUnitTest --no-configuration-cache
    print_success "单元测试通过"
}

assemble() {
    print_info "Debug 构建..."
    ./gradlew :app:assembleDebug --no-configuration-cache
    print_success "Debug 构建通过"
}

quick() {
    print_info "=== 快速检查 ==="
    compile
    unit_test
    print_success "快速检查完成"
}

full() {
    print_info "=== 完整检查 ==="
    quick
    assemble
    print_success "完整检查完成"
}

help() {
    cat <<'EOF'
Daily Satori 测试脚本

用法: ./test.sh [命令]

命令:
  quick    编译检查 + Android debug unit tests，默认
  full     quick + assembleDebug
  compile  只运行 compileDebugKotlin
  unit     只运行 app debug unit tests
  help     显示帮助
EOF
}

main() {
    case "${1:-quick}" in
        quick) quick ;;
        full) full ;;
        compile) compile ;;
        unit) unit_test ;;
        help|-h|--help) help ;;
        *) print_error "未知命令: $1"; help; exit 1 ;;
    esac
}

main "$@"
```

- [ ] **Step 3: Verify CI script locally**

Run:

```bash
chmod +x ./test.sh
./test.sh quick
```

Expected: `compileDebugKotlin` and `testDebugUnitTest` both finish with `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit CI cleanup**

Run:

```bash
git add .github/workflows/unit-tests.yml test.sh
git commit -m "chore: replace Flutter test workflow with Gradle checks"
```

## Task 3: Remove Legacy Flutter Version Source

**Files:**
- Delete: `pubspec.yaml`
- Delete: `pubspec.lock`
- Delete: `analysis_options.yaml`
- Modify: `.opencode/skill/release-version/SKILL.md`
- Modify: `README.md`

- [ ] **Step 1: Verify Flutter metadata references**

Run:

```bash
git grep -n "pubspec\|flutter analyze\|flutter test\|analysis_options" -- . ':!docs/superpowers/**'
```

Expected: references are limited to legacy files and release/docs text that this task updates.

- [ ] **Step 2: Delete legacy Flutter metadata**

Run:

```bash
rm pubspec.yaml pubspec.lock analysis_options.yaml
```

Expected: files are removed from the worktree.

- [ ] **Step 3: Update release skill version source**

In `.opencode/skill/release-version/SKILL.md`, replace the version-source text and command with Android Gradle versionName extraction:

```markdown
1. **获取版本号** - 从 `app/build.gradle.kts` 的 `versionName` 读取当前版本
```

```bash
current_version=$(grep "versionName" app/build.gradle.kts | sed -E 's/.*versionName = "([^"]+)".*/\1/' | tr -d ' ')
```

Also replace remaining `pubspec.yaml` mentions in that skill with `app/build.gradle.kts`.

- [ ] **Step 4: Update README release source note**

Add this short note to `README.md` near existing build/release instructions:

```markdown
## Version Source

Daily Satori is now a Kotlin Multiplatform Android app. The app version source of truth is `versionName` and `versionCode` in `app/build.gradle.kts`.
```

- [ ] **Step 5: Verify legacy metadata removal**

Run:

```bash
git grep -n "pubspec\|flutter analyze\|flutter test\|analysis_options" -- . ':!docs/superpowers/**' || true
./gradlew :app:compileDebugKotlin --no-configuration-cache
```

Expected: no active build/release references to Flutter metadata; compile succeeds.

- [ ] **Step 6: Commit metadata cleanup**

Run:

```bash
git add .
git commit -m "chore: remove legacy Flutter version metadata"
```

## Task 4: Quarantine Legacy Flutter Source And Root Assets

**Files:**
- Delete or move after verification: `lib/`
- Delete or move after verification: `test/`
- Delete or move after verification: `assets/`
- Modify: `.gitignore`
- Modify: `README.md`

- [ ] **Step 1: Verify active code does not depend on Flutter source tree**

Run:

```bash
git grep -n "lib/\|test/unit_test\|objectbox.g.dart\|Riverpod\|go_router" -- . ':!docs/superpowers/**' || true
```

Expected: matches are only legacy docs/source under `lib/`, `test/`, or historical docs.

- [ ] **Step 2: Remove legacy Flutter source and tests**

Run:

```bash
rm -rf lib test
```

Expected: Flutter-only source and tests are removed.

- [ ] **Step 3: Verify root assets are not active runtime assets**

Run:

```bash
git grep -n "assets/" -- . ':!assets/**' ':!docs/superpowers/**'
```

Expected: active Android runtime uses `app/src/main/assets`; shared resources use `shared/src/commonMain/resources`.

- [ ] **Step 4: Remove root assets if verification matches expected result**

Run:

```bash
rm -rf assets
```

Expected: duplicated Flutter-era root assets are removed. If Step 3 shows an active non-legacy dependency, stop and ask before deleting `assets/`.

- [ ] **Step 5: Clean Flutter-only ignore entries**

Edit `.gitignore` to remove Flutter-only entries such as `.dart_tool/`, `.flutter-plugins`, `.flutter-plugins-dependencies`, `.packages`, `.pub-cache/`, and `pubspec.lock` if present. Keep Gradle, Android, IDE, and worktree ignores.

- [ ] **Step 6: Verify cleanup**

Run:

```bash
./gradlew :app:compileDebugKotlin --no-configuration-cache
git status --short
```

Expected: compile succeeds and status only contains intended deletions/edits.

- [ ] **Step 7: Commit legacy source cleanup**

Run:

```bash
git add .
git commit -m "chore: remove legacy Flutter source tree"
```

## Task 5: Extract Shared Markdown Tab Component

**Files:**
- Create: `app/src/main/kotlin/com/dailysatori/ui/component/content/MarkdownTabPager.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/article/ArticleDetailScreen.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/remotenews/RemoteNewsDetailScreens.kt`
- Test: `app/src/test/kotlin/com/dailysatori/UnifiedNewsBehaviorTest.kt`

- [ ] **Step 1: Add structural regression tests**

Add a test to `UnifiedNewsBehaviorTest` asserting that both local and remote detail screens use the shared component:

```kotlin
    @Test
    fun articleDetailsUseSharedMarkdownTabPager() {
        val localDetail = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/article/ArticleDetailScreen.kt").readText()
        val remoteDetail = java.io.File("src/main/kotlin/com/dailysatori/ui/feature/remotenews/RemoteNewsDetailScreens.kt").readText()
        val sharedPager = java.io.File("src/main/kotlin/com/dailysatori/ui/component/content/MarkdownTabPager.kt")

        assertTrue(sharedPager.exists())
        assertTrue(localDetail.contains("MarkdownTabPager("))
        assertTrue(remoteDetail.contains("MarkdownTabPager("))
        assertFalse(localDetail.contains("private fun ArticleTabRow"))
        assertFalse(remoteDetail.contains("private fun RemoteArticleTabRow"))
    }
```

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.dailysatori.UnifiedNewsBehaviorTest --no-configuration-cache
```

Expected: FAIL because `MarkdownTabPager.kt` does not exist and screens still define local tab rows.

- [ ] **Step 2: Create shared component**

Create `MarkdownTabPager.kt`:

```kotlin
package com.dailysatori.ui.component.content

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.dailysatori.ui.theme.MarkdownStyles
import com.dailysatori.ui.theme.Spacing
import com.mikepenz.markdown.m3.Markdown
import kotlinx.coroutines.launch

@Composable
fun MarkdownTabPager(
    modifier: Modifier = Modifier,
    tabs: List<MarkdownTabPage>,
) {
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(pagerState.currentPage) {
        if (selectedTabIndex != pagerState.currentPage) selectedTabIndex = pagerState.currentPage
    }

    LaunchedEffect(selectedTabIndex) {
        if (pagerState.currentPage != selectedTabIndex) pagerState.animateScrollToPage(selectedTabIndex)
    }

    androidx.compose.foundation.layout.Column(modifier = modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedTabIndex) {
            tabs.forEachIndexed { index, tab ->
                Tab(selected = selectedTabIndex == index, onClick = { coroutineScope.launch { pagerState.animateScrollToPage(index) } }, text = { Text(tab.title) })
            }
        }
        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f), beyondViewportPageCount = 1) { page ->
            val listState = rememberLazyListState()
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                item(key = "markdown-tab-$page") {
                    Box(modifier = Modifier.padding(Spacing.m)) {
                        SelectionContainer {
                            Markdown(content = tabs[page].content, typography = MarkdownStyles.typography(), padding = MarkdownStyles.padding())
                        }
                    }
                }
            }
        }
    }
}

data class MarkdownTabPage(
    val title: String,
    val content: String,
)
```

- [ ] **Step 3: Use component in remote article detail**

In `RemoteNewsDetailScreens.kt`, replace the local pager/tab/markdown helpers in `RemoteArticleDetailScreen` with:

```kotlin
MarkdownTabPager(
    modifier = Modifier.weight(1f),
    tabs = listOf(
        MarkdownTabPage("AI 摘要", remoteArticleDetailPageContent(0, article.summary, article.viewpoints, article.content)),
        MarkdownTabPage("原文", remoteArticleDetailPageContent(1, article.summary, article.viewpoints, article.content)),
    ),
)
```

Remove now-unused `RemoteArticleTabRow` and `RemoteArticleMarkdownContent`. Import:

```kotlin
import com.dailysatori.ui.component.content.MarkdownTabPage
import com.dailysatori.ui.component.content.MarkdownTabPager
```

- [ ] **Step 4: Use component in local article detail**

In `ArticleDetailScreen.kt`, keep cover/nested-scroll behavior unchanged. Replace only the tab row and Markdown body with `MarkdownTabPager` when the article is not refreshing and has no cover nested-scroll dependency. If nested scroll must remain, stop and split this task into a component that accepts page content composable instead of Markdown strings.

The target local detail content should still produce two tabs:

```kotlin
MarkdownTabPage("AI 摘要", articleDetailPageContent(0, article.ai_content, article.ai_markdown_content, listOfNotNull(article.cover_image_url)))
MarkdownTabPage("原文", articleDetailPageContent(1, article.ai_content, article.ai_markdown_content, listOfNotNull(article.cover_image_url)))
```

- [ ] **Step 5: Verify shared component**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.dailysatori.UnifiedNewsBehaviorTest --no-configuration-cache
./gradlew :app:compileDebugKotlin --no-configuration-cache
```

Expected: both commands succeed. If local article cover collapse changes are required, revert local detail changes and keep this commit remote-only plus shared component; create a follow-up plan for local detail.

- [ ] **Step 6: Commit Markdown tab extraction**

Run:

```bash
git add app/src/main/kotlin/com/dailysatori/ui/component/content/MarkdownTabPager.kt app/src/main/kotlin/com/dailysatori/ui/feature/article/ArticleDetailScreen.kt app/src/main/kotlin/com/dailysatori/ui/feature/remotenews/RemoteNewsDetailScreens.kt app/src/test/kotlin/com/dailysatori/UnifiedNewsBehaviorTest.kt
git commit -m "refactor: extract shared markdown tab pager"
```

## Task 6: Split Remote News Detail Screens

**Files:**
- Create: `app/src/main/kotlin/com/dailysatori/ui/feature/remotenews/RemoteDigestDetailScreen.kt`
- Create: `app/src/main/kotlin/com/dailysatori/ui/feature/remotenews/RemoteArticleDetailScreen.kt`
- Create: `app/src/main/kotlin/com/dailysatori/ui/feature/remotenews/RemoteArticleCards.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/remotenews/RemoteNewsDetailScreens.kt`

- [ ] **Step 1: Move digest detail code**

Move `RemoteDigestDetailScreen`, `DigestBody`, `remoteDigestTimestampText`, and `timeText` from `RemoteNewsDetailScreens.kt` into `RemoteDigestDetailScreen.kt`. Keep package `com.dailysatori.ui.feature.remotenews` and public function names unchanged.

- [ ] **Step 2: Move remote article detail code**

Move `RemoteArticleDetailScreen`, `RemoteArticleHeroCard`, `RemoteArticleMetaChips`, `remoteArticleDetailPageContent`, and `remoteArticleSummaryPageContent` into `RemoteArticleDetailScreen.kt`. Keep `remoteArticleDetailPageContent` `internal` for tests.

- [ ] **Step 3: Move remote article cards**

Move `RemoteArticleSummaryCard`, `RemoteArticleCover`, `RemoteArticleDefaultCover`, `remoteArticleSummaryText`, and related constants into `RemoteArticleCards.kt`.

- [ ] **Step 4: Delete or shrink original file**

If `RemoteNewsDetailScreens.kt` is empty after moves, delete it. If imports depend on file name only, leave a short file with no declarations removed by the compiler.

- [ ] **Step 5: Verify split**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.dailysatori.UnifiedNewsBehaviorTest --no-configuration-cache
./gradlew :app:compileDebugKotlin --no-configuration-cache
```

Expected: both commands succeed.

- [ ] **Step 6: Commit remote detail split**

Run:

```bash
git add app/src/main/kotlin/com/dailysatori/ui/feature/remotenews app/src/test/kotlin/com/dailysatori/UnifiedNewsBehaviorTest.kt
git commit -m "refactor: split remote news detail components"
```

## Task 7: Create Stage-Specific Follow-Up Plans

**Files:**
- Create: `docs/superpowers/plans/2026-05-18-ui-large-screen-refactor.md`
- Create: `docs/superpowers/plans/2026-05-18-shared-service-refactor.md`
- Create: `docs/superpowers/plans/2026-05-18-data-layer-refactor.md`

- [ ] **Step 1: Create UI large-screen plan**

Create `docs/superpowers/plans/2026-05-18-ui-large-screen-refactor.md` with tasks for `McpServerScreen.kt`, `BooksScreen.kt`, `UnifiedNewsScreen.kt`, `DataImportScreen.kt`, and `NavHost.kt`. Each task must keep the public screen composable stable and split only internal UI components.

- [ ] **Step 2: Create shared service plan**

Create `docs/superpowers/plans/2026-05-18-shared-service-refactor.md` with tasks for `WebpageParserService.kt`, `UnifiedNewsSummaryService.kt`, `ImportService.kt`, and shared HTTP URL/auth helpers.

- [ ] **Step 3: Create data layer plan**

Create `docs/superpowers/plans/2026-05-18-data-layer-refactor.md` with tasks for repository timestamp/boolean helpers, SQL count/search query improvements, and a separate migration-safety sub-stage.

- [ ] **Step 4: Verify docs and commit**

Run:

```bash
git diff --check
git add docs/superpowers/plans/2026-05-18-ui-large-screen-refactor.md docs/superpowers/plans/2026-05-18-shared-service-refactor.md docs/superpowers/plans/2026-05-18-data-layer-refactor.md
git commit -m "docs: add follow-up refactor stage plans"
```

## Task 8: Final Verification For First Refactor Batch

**Files:**
- No source edits expected.

- [ ] **Step 1: Run full first-batch verification**

Run:

```bash
./test.sh quick
./gradlew :app:assembleDebug --no-configuration-cache
git status --short
```

Expected: Gradle commands succeed and working tree is clean.

- [ ] **Step 2: Report remaining stages**

Report the completed commits and the next available stage plans:

```text
First refactor batch completed:
- CI migrated to Gradle.
- Legacy Flutter metadata/source removed after verification.
- Shared Markdown tabs extracted.
- Remote news detail file split.

Next stage plans:
- UI large-screen refactor.
- Shared service refactor.
- Data layer and migration safety refactor.
```

## Self-Review

- Spec coverage: This plan implements the first executable batch from the staged design and creates dedicated plans for the larger later stages instead of mixing high-risk changes into one batch.
- Red-flag scan: The plan avoids incomplete requirement markers and uses the word placeholder only as a UI term for image fallback behavior.
- Type consistency: `MarkdownTabPager` and `MarkdownTabPage` are defined before use; `remoteArticleDetailPageContent` keeps its current signature for existing tests.
