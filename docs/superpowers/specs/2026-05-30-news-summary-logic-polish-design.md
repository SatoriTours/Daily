# News Summary Logic Polish Design

## Purpose

Polish the behavior behind the news summary page without a UI redesign. The first pass focuses on correctness, predictable refresh behavior, robust source-detail opening, and graceful error handling for the news summary page and its embedded remote-source article lists.

## Scope

In scope:

- `UnifiedNewsScreen` and `UnifiedNewsViewModel` behavior for the summary tab, remote-source tabs, and local-news tab.
- Remote-source article loading through `fetchTopArticlesToday`.
- Citation/source detail opening from key points and summary content.
- Minimal UI state feedback needed to make loading, empty, and error states understandable.
- Regression tests for refresh routing, duplicate request protection, stale request protection, detail fallback, and error-state separation.

Out of scope:

- Redesigning the news summary visual layout.
- Reworking the standalone remote news page beyond behavior shared by news-summary flows.
- Changing the remote news API contract.
- Adding source health dashboards, diagnostics, or new settings.

## Current Context

The news summary page already separates source selection into summary, remote source, and local articles. Summary refresh calls daily summary generation. Remote-source refresh calls `fetchTopArticlesToday` for the selected source. Local-news refresh increments a request key for the embedded article list.

Recent work made remote article detail opening use list payloads instead of hidden detail APIs. Remaining polish should tighten the state model around refresh requests, stale responses, missing content, and user-facing failure messages.

## Refresh Behavior

The refresh button is contextual:

- Summary tab: regenerate the current daily news summary only.
- Remote-source tab: refresh only the currently selected remote source's today's article list. It must not regenerate the summary.
- Local-news tab: refresh only the embedded local article list.

Remote-source refresh behavior:

- If the same source is already loading, a second refresh tap should not start a duplicate request.
- Switching sources invalidates the previous source request so a late response cannot overwrite the new source state.
- Successful refresh replaces that source/date cache and clears the source-specific error.
- Failed refresh with cached articles keeps the old articles visible and shows a non-blocking failure message.
- Failed refresh without cached articles shows a retryable error state.

Summary refresh behavior:

- Generation state must always stop on success, empty result, known failure, cancellation-safe exception handling, or unexpected exception.
- Existing readable summaries remain visible when generation fails.
- Empty generation results should show a clear message rather than looking like a broken load.

## Detail Opening Behavior

Citation opening:

- Citation targets that resolve to local articles navigate directly to the local article detail.
- Remote-article citation targets first use local persisted/cache content. They do not call a hidden remote article detail endpoint.
- If persisted content is unavailable, show a clear detail error: the article content is unavailable and the user should refresh the current source or summary.

Remote-source list article opening:

- Opens directly from the loaded `RemoteArticle` object.
- Checks whether the remote article already exists locally to set favorite state.
- Does not block detail display on favorite-state lookup failure; favorite lookup failure should leave the article readable and treat favorite as false.
- If the article has no readable title, summary, viewpoints, or content, the detail screen should show explicit fallback text instead of an empty page.

Remote digest article opening inside the summary flow:

- Uses the `RemoteArticle` object supplied by the digest payload.
- Applies the same favorite-state and content-fallback behavior as remote-source list articles.

## Error Handling

Errors are scoped to the flow that produced them:

- Summary-generation failures use `error` or manual refresh message on the summary page.
- Remote-source list failures use `sourceArticlesError` and do not replace summary-generation errors.
- Detail-opening failures use detail state and should not clear or replace list refresh state.
- Favorite failures show a concise favorite-specific message and do not mark list loading as failed.

Expected user-facing states:

- Missing global remote config or source-specific config: prompt the user to configure remote news service.
- Deleted or disabled selected source: reset to summary or show that the source no longer exists.
- Network/service failure with cached source articles: keep cached list and show “refresh failed, showing previous result”.
- Network/service failure without cache: show error and retry action.
- Empty source response: show “this source has no news today” and retry action.
- Missing article content: show readable fallback in detail, not a blank detail page.

## Minimal UI Adjustments

UI changes are limited to behavior clarity:

- Disable or ignore contextual refresh while the same target is already refreshing.
- Keep existing layout and magazine card style.
- Preserve current remote-source list structure.
- Adjust only small messages, empty states, loading labels, and detail fallbacks where needed.

## Testing Strategy

Add or update source-level regression tests where possible, following existing project style:

- Contextual refresh routes summary, remote source, and local articles to separate handlers.
- Remote-source duplicate refresh requests are guarded.
- Stale remote-source responses are ignored after switching sources.
- Remote-source refresh failure keeps cached content and records a scoped error.
- Remote-source article opening uses loaded `RemoteArticle` object and avoids hidden detail fetches.
- Remote article detail fallback handles missing summary, viewpoints, and content.
- Citation remote article fallback does not call hidden detail fetch and reports unavailable content when no local content exists.

Run verification after implementation:

- Targeted unit tests for the changed behavior.
- `./gradlew :app:compileDebugKotlin`.
- If code changes affect runtime flows, install and launch the app on the connected device as required by project instructions.

## Acceptance Criteria

- Refresh button behavior is predictable for summary, selected remote source, and local-news tabs.
- Refreshing a remote source updates only that source's article list and never regenerates the summary.
- Duplicate and stale refresh requests do not corrupt state.
- Details opened from key points, citations, remote-source lists, and remote digest article lists either show readable content or a clear fallback/error.
- Errors are scoped and do not leak between summary refresh, source refresh, detail loading, and favorite toggling.
- No broad UI redesign is introduced.
