# News And Books Density Design

## Goal

Remove the remaining oversized gaps below the title bar on the news and books tabs, and make books reading text match the readable body size used by diary and news content.

## Current Problems

- The news tab still adds `Spacing.m` as top list padding, so the first summary card starts too far below the title bar.
- News refresh and generating states use symmetric vertical padding, which can also create a heavy gap under the title bar.
- The books tab composes `AppTopBar` directly in a `Column`, not through `AppScaffold`, so the title bar still feels visually thick on that page.
- Books cards add `Spacing.m` top padding outside the card and large `Spacing.l` gaps inside the card, delaying the body text.
- Books body Markdown should be at least as readable as diary/news body content, using the shared card/body rhythm rather than a smaller-feeling treatment.

## Design

- Keep the global app bar behavior intact for other tabs.
- Tighten the news tab only:
  - Use `PaddingValues(start = Spacing.m, end = Spacing.m, top = Spacing.xs, bottom = Spacing.m)` for the summary list.
  - Reduce top-heavy refresh/generating state spacing so messages/cards sit closer to the title bar.
- Tighten the books tab only:
  - Use a compact reading top bar for Books with about `46.dp` visual height while preserving accessible icon hit targets.
  - Reduce the `ViewpointCard` outer top padding from `Spacing.m` to `Spacing.xs`.
  - Reduce internal title/metadata/body gaps so body text appears sooner.
  - Use `MarkdownStyles.cardTypography()` and `MarkdownStyles.cardPadding()` for body and example content, and ensure this maps to the same `bodyMedium` rhythm used by news/diary card content.
- Do not introduce collapsing/immersive scrolling behavior in this pass.

## Acceptance Criteria

- News summary content starts much closer to the title bar while retaining side padding and card separation.
- Books title bar appears slimmer than before on the reading tab.
- Books card body text is visually comparable to diary/news body text and no longer feels too small.
- Books card body appears sooner on the first screen due to reduced vertical gaps.
- Existing navigation/actions remain usable and accessible.
- `./gradlew :app:testDebugUnitTest` and `./gradlew :app:compileDebugKotlin` pass.
- If reachable, install and launch on `192.168.2.11:38915` or the current provided device port.
