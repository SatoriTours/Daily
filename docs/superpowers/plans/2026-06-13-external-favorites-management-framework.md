# External Favorites Management Framework Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Redesign the external favorites settings page into a connection health management page with clear summary, source-card actions, authorization repair guidance, and delete confirmation.

**Architecture:** Keep the implementation inside the existing external favorites settings feature. Add small pure UI helper functions in `ExternalFavoritesSettingsViewModel.kt` so state labels and summary copy can be tested without Compose instrumentation, then update `ExternalFavoritesSettingsScreen.kt` to render the new page structure using existing repository and scheduler behavior. Do not change storage, sync worker, OAuth callback architecture, or provider support.

**Tech Stack:** Kotlin, Jetpack Compose Material3, Koin ViewModel, SQLDelight-generated source models, kotlin.test, Gradle Android unit tests.

---

## File Structure

- Modify `app/src/main/kotlin/com/dailysatori/ui/feature/settings/externalfavorites/ExternalFavoritesSettingsViewModel.kt`: add pure helpers for page summary, empty state copy, source identity, primary action labels, sync summary text, rate-limit text, delete confirmation copy, and conditional auth-check visibility.
- Modify `app/src/main/kotlin/com/dailysatori/ui/feature/settings/externalfavorites/ExternalFavoritesSettingsScreen.kt`: add summary section, visible add entry, clearer empty state, reworked source cards, conditional auth-check action, delete confirmation dialog, and status-driven primary actions.
- Modify `app/src/test/kotlin/com/dailysatori/ui/feature/settings/externalfavorites/ExternalFavoritesSettingsTextTest.kt`: add text and state-helper coverage for the management framework.
- Add no new data-layer files and no database migration.

---

### Task 1: Pure Management Copy and State Helpers

**Files:**
- Modify: `app/src/test/kotlin/com/dailysatori/ui/feature/settings/externalfavorites/ExternalFavoritesSettingsTextTest.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/settings/externalfavorites/ExternalFavoritesSettingsViewModel.kt`

- [ ] **Step 1: Add failing helper tests**

Append these tests to `ExternalFavoritesSettingsTextTest`:

```kotlin
    @Test
    fun managementSummaryTextPrioritizesConnectionHealth() {
        assertEquals("还没有连接外部收藏来源", externalFavoriteManagementSummaryTitle(emptyList()))
        assertEquals(
            "外部收藏同步已暂停",
            externalFavoriteManagementSummaryTitle(
                listOf(sourceUi("paused", enabled = false), sourceUi("paused", enabled = false)),
            ),
        )
        assertEquals(
            "2 个来源需要处理",
            externalFavoriteManagementSummaryTitle(
                listOf(sourceUi("healthy"), sourceUi("needs_auth"), sourceUi("failing")),
            ),
        )
        assertEquals(
            "所有外部收藏来源同步正常",
            externalFavoriteManagementSummaryTitle(listOf(sourceUi("healthy"), sourceUi("never_synced"))),
        )
        assertEquals(
            "收藏会定期同步到本地收藏，可手动同步或导入历史收藏。",
            externalFavoriteManagementSummarySubtitle(),
        )
    }

    @Test
    fun emptyStateGuidesFirstConnection() {
        assertEquals("连接外部收藏", externalFavoriteEmptyStateTitle())
        assertEquals("连接 X 收藏", externalFavoriteAddServiceActionLabel(hasSources = false))
        assertEquals("连接新来源", externalFavoriteAddServiceActionLabel(hasSources = true))
        assertTrue(externalFavoriteEmptyStateSubtitle().contains("当前先支持 X 收藏"))
        assertTrue(externalFavoriteEmptyStateSubtitle().contains("本地收藏"))
    }

    @Test
    fun primaryActionsFollowHealthState() {
        assertEquals("同步", externalFavoritePrimaryActionLabel("healthy"))
        assertEquals("开始同步", externalFavoritePrimaryActionLabel("never_synced"))
        assertEquals("启用同步", externalFavoritePrimaryActionLabel("paused"))
        assertEquals("重新连接", externalFavoritePrimaryActionLabel("needs_auth"))
        assertEquals("稍后自动恢复", externalFavoritePrimaryActionLabel("limited"))
        assertEquals("重试同步", externalFavoritePrimaryActionLabel("failing"))
    }

    @Test
    fun deleteConfirmationExplainsLocalFavoritesAreKept() {
        assertEquals("删除外部收藏来源？", externalFavoriteDeleteDialogTitle())
        assertEquals("删除来源", externalFavoriteDeleteConfirmLabel())
        assertEquals("取消", externalFavoriteDeleteCancelLabel())
        assertTrue(externalFavoriteDeleteDialogText().contains("授权信息和同步记录"))
        assertTrue(externalFavoriteDeleteDialogText().contains("本地收藏的内容不会被删除"))
    }

    @Test
    fun syncSummaryDoesNotExposeRawEpochMilliseconds() {
        assertEquals("尚未同步", externalFavoriteSyncAttemptText(null, null, nowMillis = 1_700_000_000_000))
        assertEquals(
            "上次成功：刚刚",
            externalFavoriteSyncAttemptText(
                lastAttemptAt = null,
                lastSuccessAt = 1_700_000_000_000,
                nowMillis = 1_700_000_030_000,
            ),
        )
        assertEquals(
            "上次成功：12 分钟前",
            externalFavoriteSyncAttemptText(
                lastAttemptAt = null,
                lastSuccessAt = 1_700_000_000_000,
                nowMillis = 1_700_000_720_000,
            ),
        )
        assertFalse(
            externalFavoriteSyncAttemptText(
                lastAttemptAt = null,
                lastSuccessAt = 1_700_000_000_000,
                nowMillis = 1_700_000_720_000,
            ).contains("1700000"),
        )
    }

    @Test
    fun syncCountsUseUserFacingText() {
        assertEquals(null, externalFavoriteSeenCountText(itemsSeen = 0, pagesSeen = 0))
        assertEquals("上次看到 18 条收藏", externalFavoriteSeenCountText(itemsSeen = 18, pagesSeen = 1))
        assertEquals("上次看到 18 条收藏 · 读取 3 页", externalFavoriteSeenCountText(itemsSeen = 18, pagesSeen = 3))
        assertEquals("读取 3 页", externalFavoriteSeenCountText(itemsSeen = 0, pagesSeen = 3))
    }

    @Test
    fun rateLimitTextUsesResetTimeWhenAvailable() {
        assertEquals("平台限流中，稍后自动恢复", externalFavoriteRateLimitText(null, nowMillis = 1_700_000_000_000))
        assertEquals(
            "平台限流中，预计 60 分钟后恢复",
            externalFavoriteRateLimitText(
                resetAt = 1_700_003_600_000,
                nowMillis = 1_700_000_000_000,
            ),
        )
    }
```

Add this helper to the bottom of the test class:

```kotlin
    private fun sourceUi(health: String, enabled: Boolean = true): ExternalFavoriteSourceUi =
        ExternalFavoriteSourceUi(
            source = com.dailysatori.shared.db.External_favorite_source(
                id = health.hashCode().toLong(),
                provider = "x",
                display_name = "X 收藏",
                account_id = "account-$health",
                account_name = "",
                enabled = if (enabled) 1L else 0L,
                sync_interval_minutes = 720,
                last_sync_started_at = null,
                last_sync_completed_at = null,
                last_success_at = null,
                last_sync_window_started_at = null,
                last_items_seen_count = 0,
                last_pages_seen_count = 0,
                last_error = "",
                last_error_code = "",
                last_error_message = "",
                status = "idle",
                last_sync_mode = "recent",
                rate_limit_reset_at = null,
                auth_json = "",
                config_json = "",
                capabilities_json = "",
                created_at = 0,
                updated_at = 0,
            ),
            health = health,
        )
```

- [ ] **Step 2: Run the tests and verify they fail**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.dailysatori.ui.feature.settings.externalfavorites.ExternalFavoritesSettingsTextTest
```

Expected: FAIL because the new helper functions do not exist and `externalFavoriteAddServiceActionLabel` does not yet accept `hasSources`.

- [ ] **Step 3: Add helper functions**

In `ExternalFavoritesSettingsViewModel.kt`, replace:

```kotlin
fun externalFavoriteEmptyStateTitle(): String = "添加外部收藏服务"

fun externalFavoriteEmptyStateSubtitle(message: String? = null): String =
    listOfNotNull(
        "添加 X 等平台后，收藏会定期同步到本地收藏，并由 AI 整理内容。",
        message?.takeIf { it.isNotBlank() },
    ).joinToString("\n")

fun externalFavoriteAddServiceActionLabel(): String = "添加服务"
```

with:

```kotlin
fun externalFavoriteManagementSummaryTitle(sources: List<ExternalFavoriteSourceUi>): String {
    if (sources.isEmpty()) return "还没有连接外部收藏来源"
    if (sources.all { !it.enabled || it.health == "paused" }) return "外部收藏同步已暂停"
    val attentionCount = sources.count { it.health in setOf("needs_auth", "limited", "failing") }
    return if (attentionCount > 0) "${attentionCount} 个来源需要处理" else "所有外部收藏来源同步正常"
}

fun externalFavoriteManagementSummarySubtitle(): String =
    "收藏会定期同步到本地收藏，可手动同步或导入历史收藏。"

fun externalFavoriteEmptyStateTitle(): String = "连接外部收藏"

fun externalFavoriteEmptyStateSubtitle(message: String? = null): String =
    listOfNotNull(
        "当前先支持 X 收藏。连接后，收藏会同步到本地收藏，并保留手动同步和历史导入入口。",
        message?.takeIf { it.isNotBlank() },
    ).joinToString("\n")

fun externalFavoriteAddServiceActionLabel(hasSources: Boolean = false): String =
    if (hasSources) "连接新来源" else "连接 X 收藏"
```

After `externalFavoriteConnectXActionLabel()`, add:

```kotlin
fun externalFavoritePrimaryActionLabel(health: String): String = when (health) {
    "never_synced" -> "开始同步"
    "paused" -> "启用同步"
    "needs_auth" -> "重新连接"
    "limited" -> "稍后自动恢复"
    "failing" -> "重试同步"
    else -> "同步"
}

fun externalFavoriteDeleteDialogTitle(): String = "删除外部收藏来源？"

fun externalFavoriteDeleteDialogText(): String =
    "这会删除该来源的授权信息和同步记录。已经导入到本地收藏的内容不会被删除。"

fun externalFavoriteDeleteConfirmLabel(): String = "删除来源"

fun externalFavoriteDeleteCancelLabel(): String = "取消"

fun externalFavoriteSyncAttemptText(
    lastAttemptAt: Long?,
    lastSuccessAt: Long?,
    nowMillis: Long = kotlinx.datetime.Clock.System.now().toEpochMilliseconds(),
): String = when {
    lastSuccessAt != null -> "上次成功：${externalFavoriteRelativeTimeText(lastSuccessAt, nowMillis)}"
    lastAttemptAt != null -> "上次尝试：${externalFavoriteRelativeTimeText(lastAttemptAt, nowMillis)}"
    else -> "尚未同步"
}

fun externalFavoriteSeenCountText(itemsSeen: Long, pagesSeen: Long): String? {
    val parts = buildList {
        if (itemsSeen > 0) add("上次看到 ${itemsSeen} 条收藏")
        if (pagesSeen > 1) add("读取 ${pagesSeen} 页")
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

fun externalFavoriteRateLimitText(resetAt: Long?, nowMillis: Long = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()): String =
    resetAt?.takeIf { it > nowMillis }?.let {
        "平台限流中，预计 ${externalFavoriteFutureDurationText(it, nowMillis)} 后恢复"
    } ?: "平台限流中，稍后自动恢复"

private fun externalFavoriteRelativeTimeText(timestampMillis: Long, nowMillis: Long): String {
    val diffMinutes = ((nowMillis - timestampMillis).coerceAtLeast(0L) / 60_000L)
    if (diffMinutes < 1) return "刚刚"
    if (diffMinutes < 60) return "${diffMinutes} 分钟前"
    val instant = kotlinx.datetime.Instant.fromEpochMilliseconds(timestampMillis)
    val local = instant.toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault())
    return "${local.hour.toString().padStart(2, '0')}:${local.minute.toString().padStart(2, '0')}"
}

private fun externalFavoriteFutureDurationText(timestampMillis: Long, nowMillis: Long): String {
    val diffMinutes = ((timestampMillis - nowMillis).coerceAtLeast(0L) / 60_000L).coerceAtLeast(1L)
    if (diffMinutes < 60) return "${diffMinutes} 分钟后"
    val hours = diffMinutes / 60L
    val minutes = diffMinutes % 60L
    return if (minutes == 0L) "${hours} 小时后" else "${hours} 小时 ${minutes} 分钟后"
}
```

- [ ] **Step 4: Run the targeted test**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.dailysatori.ui.feature.settings.externalfavorites.ExternalFavoritesSettingsTextTest
```

Expected: PASS.

- [ ] **Step 5: Compile**

Run:

```bash
./gradlew :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/com/dailysatori/ui/feature/settings/externalfavorites/ExternalFavoritesSettingsViewModel.kt app/src/test/kotlin/com/dailysatori/ui/feature/settings/externalfavorites/ExternalFavoritesSettingsTextTest.kt
git commit -m "feat: add external favorite management copy helpers"
```

---

### Task 2: Page Summary, Empty State, and Conditional Auth Check Action

**Files:**
- Modify: `app/src/test/kotlin/com/dailysatori/ui/feature/settings/externalfavorites/ExternalFavoritesSettingsTextTest.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/settings/externalfavorites/ExternalFavoritesSettingsViewModel.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/settings/externalfavorites/ExternalFavoritesSettingsScreen.kt`

- [ ] **Step 1: Add failing tests for conditional auth-check copy**

Append these tests to `ExternalFavoritesSettingsTextTest`:

```kotlin
    @Test
    fun authCheckActionOnlyShowsForRestoredAuthState() {
        assertFalse(externalFavoriteShouldShowAuthCheckAction(emptyList()))
        assertFalse(externalFavoriteShouldShowAuthCheckAction(listOf(sourceUi("healthy"))))
        assertTrue(externalFavoriteShouldShowAuthCheckAction(listOf(sourceUi("needs_auth", status = "auth_check_required"))))
        assertEquals("检查已恢复授权", externalFavoriteAuthCheckActionLabel())
    }
```

Update the existing `sourceUi` helper signature and body to accept status:

```kotlin
    private fun sourceUi(
        health: String,
        enabled: Boolean = true,
        status: String = "idle",
    ): ExternalFavoriteSourceUi =
```

and change the `status = "idle"` constructor argument to:

```kotlin
                status = status,
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.dailysatori.ui.feature.settings.externalfavorites.ExternalFavoritesSettingsTextTest
```

Expected: FAIL because `externalFavoriteShouldShowAuthCheckAction` and `externalFavoriteAuthCheckActionLabel` do not exist.

- [ ] **Step 3: Add conditional auth-check helpers**

In `ExternalFavoritesSettingsViewModel.kt`, after `externalFavoriteManagementSummarySubtitle()`, add:

```kotlin
fun externalFavoriteShouldShowAuthCheckAction(sources: List<ExternalFavoriteSourceUi>): Boolean =
    sources.any { it.source.status == ExternalSourceStatus.auth_check_required.name }

fun externalFavoriteAuthCheckActionLabel(): String = "检查已恢复授权"
```

- [ ] **Step 4: Update the list page scaffold actions**

In `ExternalFavoritesSettingsScreen.kt`, replace the permanent top-bar action:

```kotlin
actions = {
    IconButton(onClick = viewModel::markRestoredSourcesAuthCheckRequired) {
        Icon(Icons.Default.Refresh, contentDescription = "重新验证授权")
    }
},
```

with:

```kotlin
actions = {
    if (externalFavoriteShouldShowAuthCheckAction(state.sources)) {
        IconButton(onClick = viewModel::markRestoredSourcesAuthCheckRequired) {
            Icon(Icons.Default.Refresh, contentDescription = externalFavoriteAuthCheckActionLabel())
        }
    }
},
```

- [ ] **Step 5: Add management summary composable**

In `ExternalFavoritesSettingsScreen.kt`, add this composable before `ExternalFavoriteSourceList`:

```kotlin
@Composable
private fun ExternalFavoriteManagementSummary(
    state: ExternalFavoritesSettingsState,
    onAdd: () -> Unit,
    onAuthCheck: () -> Unit,
) {
    SettingsSectionCard("连接状态") {
        Column(
            modifier = Modifier.fillMaxWidth().padding(Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Text(
                externalFavoriteManagementSummaryTitle(state.sources),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                externalFavoriteManagementSummarySubtitle(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = onAdd) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text(externalFavoriteAddServiceActionLabel(hasSources = state.sources.isNotEmpty()))
                }
                if (externalFavoriteShouldShowAuthCheckAction(state.sources)) {
                    OutlinedButton(onClick = onAuthCheck) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Text(externalFavoriteAuthCheckActionLabel())
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 6: Render summary in list and empty states**

In the empty state branch of `ExternalFavoriteSourceListPage`, replace the direct `EmptyState(...)` call with a `LazyColumn` so the summary and empty state both show:

```kotlin
LazyColumn(
    modifier = modifier.fillMaxSize(),
    contentPadding = PaddingValues(Spacing.m),
    verticalArrangement = Arrangement.spacedBy(Spacing.m),
) {
    item {
        ExternalFavoriteManagementSummary(
            state = state,
            onAdd = openAddPage,
            onAuthCheck = viewModel::markRestoredSourcesAuthCheckRequired,
        )
    }
    item {
        EmptyState(
            icon = Icons.Default.Bookmark,
            title = externalFavoriteEmptyStateTitle(),
            subtitle = externalFavoriteEmptyStateSubtitle(state.message),
            modifier = Modifier.fillMaxWidth(),
            actionLabel = externalFavoriteAddServiceActionLabel(hasSources = false),
            onAction = openAddPage,
        )
    }
}
```

In `ExternalFavoriteSourceList`, add the summary before the message:

```kotlin
item {
    ExternalFavoriteManagementSummary(
        state = state,
        onAdd = openAddPage,
        onAuthCheck = viewModel::markRestoredSourcesAuthCheckRequired,
    )
}
```

This requires changing the function signature from:

```kotlin
private fun ExternalFavoriteSourceList(
    state: ExternalFavoritesSettingsState,
    viewModel: ExternalFavoritesSettingsViewModel,
    modifier: Modifier = Modifier,
)
```

to:

```kotlin
private fun ExternalFavoriteSourceList(
    state: ExternalFavoritesSettingsState,
    viewModel: ExternalFavoritesSettingsViewModel,
    openAddPage: () -> Unit,
    modifier: Modifier = Modifier,
)
```

and updating the call site:

```kotlin
ExternalFavoriteSourceList(state = state, viewModel = viewModel, openAddPage = openAddPage, modifier = modifier)
```

- [ ] **Step 7: Run tests and compile**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.dailysatori.ui.feature.settings.externalfavorites.ExternalFavoritesSettingsTextTest
./gradlew :app:compileDebugKotlin
```

Expected: both commands pass.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/kotlin/com/dailysatori/ui/feature/settings/externalfavorites/ExternalFavoritesSettingsScreen.kt app/src/main/kotlin/com/dailysatori/ui/feature/settings/externalfavorites/ExternalFavoritesSettingsViewModel.kt app/src/test/kotlin/com/dailysatori/ui/feature/settings/externalfavorites/ExternalFavoritesSettingsTextTest.kt
git commit -m "feat: add external favorite management summary"
```

---

### Task 3: Source Card Actions and Sync Summary

**Files:**
- Modify: `app/src/test/kotlin/com/dailysatori/ui/feature/settings/externalfavorites/ExternalFavoritesSettingsTextTest.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/settings/externalfavorites/ExternalFavoritesSettingsScreen.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/settings/externalfavorites/ExternalFavoritesSettingsViewModel.kt`

- [ ] **Step 1: Add failing tests for action enablement and identity**

Append these tests to `ExternalFavoritesSettingsTextTest`:

```kotlin
    @Test
    fun sourceIdentityPrefersAccountName() {
        assertEquals("@jim", externalFavoriteAccountIdentity(accountName = "@jim", accountId = "123"))
        assertEquals("123", externalFavoriteAccountIdentity(accountName = "", accountId = "123"))
    }

    @Test
    fun syncActionsDisableForBlockedStates() {
        assertTrue(externalFavoriteCanRunSyncAction("healthy", enabled = true))
        assertTrue(externalFavoriteCanRunSyncAction("never_synced", enabled = true))
        assertTrue(externalFavoriteCanRunSyncAction("failing", enabled = true))
        assertFalse(externalFavoriteCanRunSyncAction("paused", enabled = false))
        assertFalse(externalFavoriteCanRunSyncAction("needs_auth", enabled = true))
        assertFalse(externalFavoriteCanRunSyncAction("limited", enabled = true))
    }
```

- [ ] **Step 2: Run tests to verify failure**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.dailysatori.ui.feature.settings.externalfavorites.ExternalFavoritesSettingsTextTest
```

Expected: FAIL because `externalFavoriteAccountIdentity` and `externalFavoriteCanRunSyncAction` do not exist.

- [ ] **Step 3: Add the helpers**

In `ExternalFavoritesSettingsViewModel.kt`, after `externalFavoritePrimaryActionLabel`, add:

```kotlin
fun externalFavoriteAccountIdentity(accountName: String, accountId: String): String =
    accountName.ifBlank { accountId }

fun externalFavoriteCanRunSyncAction(health: String, enabled: Boolean): Boolean =
    enabled && health !in setOf("paused", "needs_auth", "limited")
```

- [ ] **Step 4: Update source card identity and sync summary**

In `ExternalFavoriteSourceCard`, replace the account text:

```kotlin
Text(
    listOf(source.provider.uppercase(), source.account_name.ifBlank { source.account_id }).joinToString(" / "),
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
)
```

with:

```kotlin
Text(
    externalFavoriteAccountIdentity(source.account_name, source.account_id),
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
)
```

Replace the health chip row:

```kotlin
Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
    ExternalFavoriteChip(externalFavoriteHealthLabel(item.health))
    ExternalFavoriteChip(externalFavoritePeriodicSyncSubtitle(item.health))
}
Text(
    externalFavoriteLastSyncText(source.last_sync_started_at, source.last_success_at),
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
)
```

with:

```kotlin
Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
    ExternalFavoriteChip(externalFavoriteHealthLabel(item.health))
    ExternalFavoriteChip(externalFavoritePeriodicSyncSubtitle(item.health))
}
Text(
    externalFavoriteSyncAttemptText(source.last_sync_started_at, source.last_success_at),
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
)
externalFavoriteSeenCountText(source.last_items_seen_count, source.last_pages_seen_count)?.let { countText ->
    Text(
        countText,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
if (item.health == "limited") {
    Text(
        externalFavoriteRateLimitText(source.rate_limit_reset_at),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
```

- [ ] **Step 5: Update source card primary action behavior**

In `ExternalFavoriteSourceCard`, replace the first `OutlinedButton`:

```kotlin
OutlinedButton(onClick = onSyncNow, enabled = item.enabled && !syncing) {
    Icon(Icons.Default.Refresh, contentDescription = null)
    Text(if (syncing) "同步中" else "同步")
}
```

with:

```kotlin
OutlinedButton(
    onClick = if (item.health == "paused") {
        { onToggleEnabled(true) }
    } else {
        onSyncNow
    },
    enabled = when (item.health) {
        "limited", "needs_auth" -> false
        else -> !syncing
    },
) {
    Icon(Icons.Default.Refresh, contentDescription = null)
    Text(if (syncing) "已加入队列" else externalFavoritePrimaryActionLabel(item.health))
}
```

Replace the history button:

```kotlin
OutlinedButton(onClick = onImportOlder, enabled = item.enabled && !syncing) {
```

with:

```kotlin
OutlinedButton(
    onClick = onImportOlder,
    enabled = externalFavoriteCanRunSyncAction(item.health, item.enabled) && !syncing,
) {
```

- [ ] **Step 6: Remove or stop using raw timestamp helper**

If `externalFavoriteLastSyncText` is no longer used, delete it from `ExternalFavoritesSettingsScreen.kt`.

- [ ] **Step 7: Run tests and compile**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.dailysatori.ui.feature.settings.externalfavorites.ExternalFavoritesSettingsTextTest
./gradlew :app:compileDebugKotlin
```

Expected: both commands pass.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/kotlin/com/dailysatori/ui/feature/settings/externalfavorites/ExternalFavoritesSettingsScreen.kt app/src/main/kotlin/com/dailysatori/ui/feature/settings/externalfavorites/ExternalFavoritesSettingsViewModel.kt app/src/test/kotlin/com/dailysatori/ui/feature/settings/externalfavorites/ExternalFavoritesSettingsTextTest.kt
git commit -m "feat: improve external favorite source cards"
```

---

### Task 4: Delete Confirmation Dialog

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/settings/externalfavorites/ExternalFavoritesSettingsScreen.kt`
- Verify existing tests in `ExternalFavoritesSettingsTextTest.kt`

- [ ] **Step 1: Add dialog imports**

In `ExternalFavoritesSettingsScreen.kt`, add:

```kotlin
import androidx.compose.material3.AlertDialog
```

- [ ] **Step 2: Add pending delete state**

In `ExternalFavoriteSourceList`, before `LazyColumn`, add:

```kotlin
var pendingDeleteSourceId by remember { mutableStateOf<Long?>(null) }
```

After the `LazyColumn` block, add:

```kotlin
pendingDeleteSourceId?.let { sourceId ->
    AlertDialog(
        onDismissRequest = { pendingDeleteSourceId = null },
        title = { Text(externalFavoriteDeleteDialogTitle()) },
        text = { Text(externalFavoriteDeleteDialogText()) },
        confirmButton = {
            TextButton(
                onClick = {
                    pendingDeleteSourceId = null
                    viewModel.deleteSource(sourceId)
                },
            ) {
                Text(externalFavoriteDeleteConfirmLabel(), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = { pendingDeleteSourceId = null }) {
                Text(externalFavoriteDeleteCancelLabel())
            }
        },
    )
}
```

- [ ] **Step 3: Route delete icon through confirmation**

In the `ExternalFavoriteSourceCard` call inside `ExternalFavoriteSourceList`, replace:

```kotlin
onDelete = { viewModel.deleteSource(source.id) },
```

with:

```kotlin
onDelete = { pendingDeleteSourceId = source.id },
```

- [ ] **Step 4: Run tests and compile**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.dailysatori.ui.feature.settings.externalfavorites.ExternalFavoritesSettingsTextTest
./gradlew :app:compileDebugKotlin
```

Expected: both commands pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/dailysatori/ui/feature/settings/externalfavorites/ExternalFavoritesSettingsScreen.kt
git commit -m "feat: confirm external favorite source deletion"
```

---

### Task 5: Add Page Copy and Verification

**Files:**
- Modify: `app/src/test/kotlin/com/dailysatori/ui/feature/settings/externalfavorites/ExternalFavoritesAddPageTextTest.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/settings/externalfavorites/ExternalFavoritesSettingsViewModel.kt`

- [ ] **Step 1: Add failing add-page copy assertion**

In `ExternalFavoritesAddPageTextTest.addPageUsesDedicatedEditorCopy`, add:

```kotlin
assertTrue(externalFavoriteAddPageHelperText().contains("回到 Daily Satori"))
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests com.dailysatori.ui.feature.settings.externalfavorites.ExternalFavoritesAddPageTextTest
```

Expected: FAIL because the helper text does not mention returning to Daily Satori.

- [ ] **Step 3: Update helper copy**

In `ExternalFavoritesSettingsViewModel.kt`, replace:

```kotlin
fun externalFavoriteAddPageHelperText(): String = "填写 OAuth Client ID 后，会打开浏览器完成 X 授权。授权成功后，收藏会定期同步到本地收藏。"
```

with:

```kotlin
fun externalFavoriteAddPageHelperText(): String =
    "填写 OAuth Client ID 后，会打开浏览器完成 X 授权。授权完成后回到 Daily Satori，新来源会出现在列表里。"
```

- [ ] **Step 4: Run focused tests and compile**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests 'com.dailysatori.ui.feature.settings.externalfavorites.*'
./gradlew :app:compileDebugKotlin
```

Expected: both commands pass.

- [ ] **Step 5: Build APK**

Run:

```bash
./gradlew :app:assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/com/dailysatori/ui/feature/settings/externalfavorites/ExternalFavoritesSettingsViewModel.kt app/src/test/kotlin/com/dailysatori/ui/feature/settings/externalfavorites/ExternalFavoritesAddPageTextTest.kt
git commit -m "fix: clarify external favorite oauth return copy"
```

---

### Task 6: Final Smoke Check and Review

**Files:**
- No source edits expected.

- [ ] **Step 1: Run whitespace check**

Run:

```bash
git diff --check
```

Expected: no output and exit code `0`.

- [ ] **Step 2: Run full focused verification**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests 'com.dailysatori.ui.feature.settings.externalfavorites.*'
./gradlew :app:compileDebugKotlin
./gradlew :app:assembleDebug
```

Expected: all commands end with `BUILD SUCCESSFUL`.

- [ ] **Step 3: Try device install and launch if reachable**

Run:

```bash
adb connect 192.168.2.100:38305
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell monkey -p com.dailysatori -c android.intent.category.LAUNCHER 1
```

Expected when the device is reachable: connect succeeds, install prints `Success`, and monkey launches one event. If `adb connect` returns `No route to host`, report the smoke check as blocked by device reachability.

- [ ] **Step 4: Check final status**

Run:

```bash
git status --short
```

Expected: only unrelated pre-existing book reflection untracked docs remain, or the working tree is otherwise clean.

---

## Self-Review

- Spec coverage: tasks cover header summary, visible add entry, empty state, source identity, health labels, sync summaries, state-based actions, auth-check action, delete confirmation, queue semantics, add-page return copy, testing, compile, build, and device smoke check.
- Placeholder scan: no `TBD`, `TODO`, or vague implementation placeholders remain.
- Type consistency: helper names are introduced in `ExternalFavoritesSettingsViewModel.kt` before screen tasks use them; tests use the same helper names and `ExternalFavoriteSourceUi` shape already present in the feature.
