# News Pages Refactor Design

## Goal

Refactor the three news pages while preserving their current product behavior and visual identity:

- `UnifiedNewsScreen` in the Today tab.
- `RemoteNewsScreen` for configured remote news sources.
- `CrayfishNewsScreen` for crayfish, DJI, and archive news.

The work should make the code easier to understand and maintain, reduce repeated UI/state patterns, and apply small usability refinements without introducing a visible redesign.

## Non-Goals

- Do not change database schema or migration version.
- Do not replace existing navigation architecture.
- Do not merge the three ViewModels into a single generic news ViewModel.
- Do not redesign the news pages. Layout and primary interactions should remain recognizable.
- Do not introduce new dependencies.

## Approach

Use a shared-component refactor with page-local business ownership.

Shared components should live under `app/src/main/kotlin/com/dailysatori/ui/component/news/` and stay presentation-focused. They should accept simple state and callbacks instead of depending on a concrete ViewModel.

Feature-specific files should remain under their existing feature packages. Business rules, source selection, loading orchestration, and detail routing stay with the owning feature.

## Page Scope

### Unified News

`UnifiedNewsScreen.kt` is currently the largest risk area because it mixes route selection, top-level page switching, source tabs, list states, loading/error UI, and briefing card rendering in one file.

Refactor it into focused files:

- Screen and route orchestration.
- Source switcher and refresh action.
- Summary list content.
- Remote source article content.
- Briefing card and related display helpers.

Keep the current magazine-style presentation: `今日封面`, `关键要点`, and `来源覆盖` remain the core summary card structure.

### Remote News

Keep the existing mode menu and detail navigation. Reuse shared news UI for repeated states such as empty/loading/error surfaces and list padding.

Small usability improvements may include clearer refresh/failure messaging and more consistent spacing with the Today news page.

### Crayfish News

Keep current modes for crayfish news, DJI news, and archive/history news. Reuse the same shared loading/empty/list-state components where they fit naturally.

Preserve current list behavior and load-more behavior. Improve only consistency and clarity of status messages.

## Shared Components

Allowed shared components:

- A centered news state message for empty/error states with optional action.
- A refresh/failure banner for non-blocking errors while cached content is visible.
- A common `LazyColumn` content padding helper or wrapper for news lists.
- Small reusable menu/list display pieces when they are business-neutral.

Avoid shared components that encode feature-specific concepts such as unified summary windows, remote source IDs, crayfish categories, or article detail navigation.

## Interaction Requirements

- Existing navigation entry points remain unchanged.
- Existing menu actions remain available.
- Existing refresh behavior remains functionally equivalent.
- Existing detail opening behavior remains unchanged.
- Bottom content should remain reachable with the floating home bottom bar present.
- Loading, empty, and error states should be visually consistent across news pages.

## Performance Requirements

The refactor should explicitly analyze and improve scroll and recomposition performance so the news pages feel smooth during normal use.

Performance work should focus on these areas:

- Lazy lists must use stable, unique keys for summaries, remote articles, crayfish articles, and any header/status rows that can coexist with content.
- Expensive formatting or parsing should not run during every recomposition. Markdown-to-briefing parsing, date/title formatting, source grouping, and display fallback selection should be moved into `remember(...)` blocks or pure state helpers where appropriate.
- Composables should receive the narrowest stable inputs practical. Avoid passing a full `UnifiedNewsState` into deep UI components when only a small derived value is needed.
- Avoid recreating lambdas or derived collections inside frequently recomposed item rows when a simple extracted composable or remembered value is enough.
- Scroll-driven UI should use `derivedStateOf` when reading frequently changing lazy-list state, so unrelated content is not recomposed on every scroll tick.
- Loading indicators, refresh banners, and skeletons should not cause the entire page list to recompose unnecessarily.
- Large text/markdown rendering should stay isolated inside row/card composables so scrolling lists can reuse unaffected items.
- Nested scroll/list structures should be avoided unless a page genuinely needs them. Prefer a single lazy list per scrollable content area.
- Image-heavy or card-heavy lists should preserve item identity and avoid layout jumps when refreshed.

Each page batch should include a quick performance audit before editing:

- Identify scrollable containers and check whether every `items(...)` call has a stable key.
- Identify repeated parsing/formatting work in composables.
- Identify state reads that cause broad recomposition.
- Identify UI state that can be represented as a small derived model before rendering.

Target experience:

- Summary and article lists should scroll without visible stutter under normal cached data sizes.
- Switching between news sources should reuse cached content when available and avoid unnecessary full-screen loading.
- Refreshing with existing content should show an inline status instead of clearing the list whenever possible.
- Opening and closing detail pages should not reset the parent list unnecessarily unless existing behavior already requires it.

## Code Quality Requirements

- Keep individual composables small and focused.
- Prefer pure helper functions for state derivation where possible.
- Avoid adding compatibility layers unless there is a persisted-data or external-consumer need.
- Keep shared components simple; do not create a generic news framework.
- Preserve the existing theme system: use `Spacing`, `Radius`, `BorderWidth`, typography, and Material color scheme instead of hardcoded styling.

## Testing Plan

- Preserve and update existing unit/source tests for unified news content parsing, source article state, and tab styling.
- Add focused tests for any new pure helper functions.
- Add source-level regression checks for stable lazy-list keys where practical.
- Add tests for derived display models if parsing/formatting is moved out of composables.
- If source-structure tests are updated due to file splits, keep them checking behavior or intent rather than fragile file-local strings where practical.
- After each page batch, run the relevant focused tests.
- Final verification must include `./gradlew :app:compileDebugKotlin` and `./gradlew :app:assembleDebug`.
- Device install/launch should be attempted with `JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:installDebug` and `adb shell am start -n com.dailysatori/.MainActivity` when a device is connected.

## Implementation Order

1. Audit current news-page performance hotspots: list keys, broad state reads, repeated parsing/formatting, nested scroll containers, and refresh behavior.
2. Add shared news UI primitives and tests.
3. Refactor `UnifiedNewsScreen` first because it has the largest file-size and responsibility concentration.
4. Refactor `RemoteNewsScreen` to use the shared primitives.
5. Refactor `CrayfishNewsScreen` to use the shared primitives.
6. Run final compile/build verification and attempt device deployment.

## Risks

- Over-abstracting shared components could make feature code harder to follow. Mitigation: only extract presentation pieces that are clearly reused.
- Source-structure tests may become brittle after file splits. Mitigation: update them to check intent and helper behavior instead of exact old file locations.
- Visual regressions are possible because Compose layout changes can be subtle. Mitigation: keep UI changes incremental and verify each page after its batch.
- Performance changes can be hard to prove with unit tests alone. Mitigation: combine source-level regression checks, compile/build verification, and device scroll checks when a device is available.

## Approval Criteria

- Main news screens look and behave substantially the same as before.
- Repeated loading, empty, error, refresh, and list-padding patterns are consolidated.
- `UnifiedNewsScreen` responsibilities are split into clearer files.
- News lazy lists use stable keys for dynamic content.
- Repeated parsing/formatting work is removed from hot recomposition paths where practical.
- Source switching, refresh with cached content, and list scrolling feel smooth on device/emulator when verification is possible.
- Existing news behavior tests are updated and passing where applicable.
- `:app:compileDebugKotlin` and `:app:assembleDebug` pass.
