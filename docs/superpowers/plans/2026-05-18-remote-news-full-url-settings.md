# Remote News Full URL Settings Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make remote news sources use user-provided full endpoint URLs, reject invalid tokens during connection tests, and move remote news settings to a list/detail editing flow.

**Architecture:** Keep remote source persistence unchanged and reinterpret `base_url` as the full top-articles endpoint URL for unified summary. Legacy base URLs are normalized to the full endpoint during migration and settings load/save. Settings UI becomes a small state machine with a list page and an edit page.

**Tech Stack:** Kotlin Multiplatform, SQLDelight, Ktor, Jetpack Compose, Koin, Gradle unit tests.

---

### Task 1: Full URL Remote Requests

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/service/remotenews/RemoteNewsService.kt`
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/service/migration/DatabaseMigration.kt`
- Test: `app/src/test/kotlin/com/dailysatori/UnifiedNewsBehaviorTest.kt`

- [ ] Add failing assertions that `fetchTopArticlesToday` uses a full configured URL and does not append `/api/v1/external/top_articles_today` to it.
- [ ] Add failing assertions that migration normalizes old root URLs to the full endpoint.
- [ ] Implement `normalizeTopArticlesTodayUrl()`.
- [ ] Update `buildTopArticlesTodayUrl()` to treat its input as the endpoint URL and only append query parameters.
- [ ] Update migration insert to use the normalized full endpoint.
- [ ] Run `./gradlew :app:testDebugUnitTest --tests com.dailysatori.UnifiedNewsBehaviorTest --no-configuration-cache`.

### Task 2: Strict Connection Test

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/service/remotenews/RemoteNewsService.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/settings/remotenews/RemoteNewsSettingsViewModel.kt`
- Test: `app/src/test/kotlin/com/dailysatori/UnifiedNewsBehaviorTest.kt`

- [ ] Add failing assertions that connection success requires `RemoteArticlesResponse.articles` to parse from the authenticated endpoint.
- [ ] Add failing assertions that `ClientRequestException` reports token invalid and is not converted to success.
- [ ] Implement a helper message based on `fetchTopArticlesToday(config, page = 1, limit = 1)` result count.
- [ ] Keep HTTP 401/403 mapped to `Token 无效，请检查远程新闻设置`.
- [ ] Run focused and full unified news tests.

### Task 3: List/Detail Settings UI

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/settings/remotenews/RemoteNewsSettingsViewModel.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/settings/remotenews/RemoteNewsSettingsScreen.kt`
- Test: `app/src/test/kotlin/com/dailysatori/UnifiedNewsBehaviorTest.kt`

- [ ] Add failing assertions for a list page and detail page state, including `isEditing`, `openAdd`, `openEdit`, and `closeEditor`.
- [ ] Add failing assertions that the list page contains source rows and the detail page contains fields for `名称`, `完整 URL`, `Token`, `启用`, `测试连接`, `保存`, and `删除`.
- [ ] Refactor state to show list by default and editor after add/edit.
- [ ] Move fields and actions into the editor page.
- [ ] Use existing theme tokens and `AppScaffold` navigation patterns.
- [ ] Run focused and full unified news tests.

### Task 4: Verification and Install

**Files:**
- Verify only.

- [ ] Run `./gradlew :app:testDebugUnitTest --tests com.dailysatori.UnifiedNewsBehaviorTest --no-configuration-cache`.
- [ ] Run `./gradlew :app:compileDebugKotlin --no-configuration-cache`.
- [ ] If a device is connected, run `JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:installDebug --no-configuration-cache`.
- [ ] If install succeeds, run `adb shell am force-stop com.dailysatori && adb shell am start -n com.dailysatori/.MainActivity`.

Self-review: The plan covers full URL semantics, legacy migration normalization, strict token testing, list/detail settings UI, and verification. No placeholders remain.
