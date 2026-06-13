# External Favorites Management Framework Design

## Goal

Redesign the external favorites settings page as a connection health management page. The page should help users understand which external accounts are connected, whether authorization and sync are healthy, and what action to take next.

## Product Framing

The page is not a content inbox, AI organization dashboard, or real-time job monitor. It owns:

- Connecting external favorite sources.
- Showing authorization and sync health.
- Enabling, pausing, deleting, and repairing sources.
- Starting manual recent sync and history import.

It does not own:

- Showing imported favorite articles.
- Showing AI organization progress.
- Real-time sync progress tracking.
- Multi-provider selection beyond the current X flow.
- A source detail page.

## Page Structure

### Header Summary

Below the `外部收藏同步` title, show a compact status summary before the source list.

Summary states:

- No sources: `还没有连接外部收藏来源`
- All paused: `外部收藏同步已暂停`
- Any source needs attention: `{n} 个来源需要处理`
- Otherwise: `所有外部收藏来源同步正常`

Supporting copy:

`收藏会定期同步到本地收藏，可手动同步或导入历史收藏。`

The summary should not look like a marketing card. Use a quiet full-width surface or existing settings section styling.

### Primary Add Entry

Keep the floating add button, but do not make it the only add affordance.

Add a visible action near the summary or empty state:

- With no sources: `连接 X 收藏`
- With existing sources: `连接新来源`

The add page remains the existing dedicated page:

- Helper card.
- `X OAuth Client ID` field.
- Read-only sync note.
- `保存并连接 X`.
- `取消`.

The helper copy should clearly say that authorization opens the browser and returns to Daily Satori after completion.

## Empty State

The empty state should explain the first connection clearly:

Title: `连接外部收藏`

Body:

`当前先支持 X 收藏。连接后，收藏会同步到本地收藏，并保留手动同步和历史导入入口。`

Action: `连接 X 收藏`

## Source Card Model

Each source card should read as identity, health, then actions.

### Identity Row

Show:

- Display name, for example `X 收藏`.
- Account identity, preferably `@account`; fallback to account id.
- Provider can be included as a small label only when useful.

Do not show raw provider/account data as the primary content if a human-readable account name exists.

### Health Label

Map existing health/status to user-facing labels:

- `healthy`: `正常`
- `never_synced`: `未同步`
- `needs_auth`: `需要授权`
- `limited`: `限流中`
- `paused`: `已暂停`
- `failing`: `异常`

The health label should be visible in the card header.

### Sync Summary

Show one or two short lines derived from existing source fields:

- If `last_success_at` exists: `上次成功：{relativeOrLocalTime}`
- Else if `last_sync_started_at` exists: `上次尝试：{relativeOrLocalTime}`
- Else: `尚未同步`

If `last_items_seen_count > 0`, append or show a second line:

- `上次看到 {n} 条收藏`

If `last_pages_seen_count > 1`, include:

- `读取 {n} 页`

Do not show database field names or raw millisecond timestamps.

### Error Summary

For failed or auth-related sources, show at most one error line in the card:

- Prefer `last_error_message` when present.
- Fall back to a short derived message from `last_error_code`.
- Long messages should be constrained to one or two lines.

## Actions by State

Actions should change based on source health.

### Healthy

Primary: `同步`

Secondary: `导入历史`

Other: enable switch, delete entry.

### Never Synced

Primary: `开始同步`

Secondary: `导入历史`

### Paused

Primary: `启用同步`

Secondary actions should be disabled or hidden until re-enabled.

### Needs Auth

Primary: `重新连接`

Secondary sync actions should be disabled.

First implementation can route `重新连接` to the existing add/connect flow if source-scoped reauthorization is not available. If source-scoped reauthorization is not implemented, the UI copy must not imply that it updates the existing source automatically.

### Auth Check Required

Primary: `检查授权`

This state is usually caused by restored credentials. The page should explain that the app needs to verify restored authorization before syncing.

### Rate Limited

Primary sync action disabled.

Show: `平台限流中，稍后自动恢复`

If `rate_limit_reset_at` is available, show an approximate recovery time:

`预计 {time} 后恢复`

### Failing

Primary: `重试同步`

Secondary: `导入历史` only if the source is enabled and not auth-blocked.

Show the error summary.

## Authorization Repair

Authorization repair is part of the management page contract.

Required first-version behavior:

- A source with `auth_required` should not leave the user with only a passive warning.
- It must show a clear action: `重新连接`.
- If existing OAuth plumbing can update the matching source after callback, use it.
- If it cannot, route to the add page and use copy that says the user may reconnect the account. Do not promise in-place repair until the callback behavior supports it.

The global top-bar action `重新验证授权` should not be shown as a permanent refresh icon. It is not a normal refresh action.

Recommended first-version handling:

- Hide it when no source is in `auth_check_required`.
- When needed, show an inline action near the summary or affected card: `检查已恢复授权`.

## Delete Confirmation

Deleting a source must require confirmation.

Dialog copy:

Title: `删除外部收藏来源？`

Body:

`这会删除该来源的授权信息和同步记录。已经导入到本地收藏的内容不会被删除。`

Confirm: `删除来源`

Cancel: `取消`

The confirm action uses the existing `deleteSource(sourceId)` behavior.

## Sync Queue Semantics

The current UI should avoid pretending to show real-time worker progress unless the ViewModel actively observes source status updates.

Manual sync click behavior:

- On successful enqueue: show `已加入同步队列`.
- History import click: show `已加入历史收藏导入队列`.
- Button can briefly disable while enqueueing through `syncingSourceId`.

Do not keep long-lived `同步中` copy unless it is backed by persisted `source.status == syncing` or a live refresh strategy.

## Time and Count Formatting

Add small pure helpers for user-facing sync summary text.

Expected examples:

- `尚未同步`
- `上次成功：刚刚`
- `上次成功：12 分钟前`
- `上次成功：今天 09:30`
- `上次看到 18 条收藏`
- `读取 3 页`

The first implementation can use simple local formatting if the project lacks a shared relative-time utility. It must not show raw epoch milliseconds.

## Testing Strategy

Use lightweight unit/source tests consistent with the current project style.

Cover:

- Header summary text for no sources, all paused, needs attention, and all healthy.
- Empty-state copy and action label.
- Health label mapping.
- Primary action label by health/state.
- Delete confirmation copy.
- Sync summary formatting without raw timestamps.
- Rate-limit copy with and without reset time.
- Existing add-page text tests remain valid.

Compile verification:

- `./gradlew :app:testDebugUnitTest --tests 'com.dailysatori.ui.feature.settings.externalfavorites.*'`
- `./gradlew :app:compileDebugKotlin`

Build verification:

- `./gradlew :app:assembleDebug`

Manual verification:

- Open settings with no sources and confirm the empty state guides connection.
- Open settings with a healthy source and confirm `同步` / `导入历史`.
- Check paused, auth-required, rate-limited, failed, and never-synced fixture states if available.
- Delete a source and confirm the dialog explains what is and is not deleted.

## Out of Scope

- Source detail page.
- Real-time worker progress UI.
- AI organization status.
- Imported favorite article browsing.
- Multi-provider selection UI.
- OAuth callback architecture rewrite unless required for source-scoped reauthorization.
