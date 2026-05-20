# Final Visual Consistency Design

## Goal

Bring Daily Satori closer to an Apple-level product finish by removing the remaining visual fragmentation across the four main tabs.

This pass fixes:

- AI chat input placeholder/text not vertically centered.
- Body text and Markdown line height differing across news, diary, reading, and AI.
- Main content cards using different radii, surfaces, borders, padding, and title levels.
- Metadata using inconsistent typography, color, and emphasis.
- Feature screens drifting back into ad-hoc typography choices.

## Design Principles

### One Text Rhythm

Use one shared rhythm for normal content:

- Standard body: `15sp / 24sp`.
- Long-form reading body: `16sp / 26sp`.
- Card title: `titleMedium`, semibold.
- Secondary title: `titleSmall`, semibold.
- Metadata: `labelSmall` or `bodySmall`, `onSurfaceVariant`.

No feature screen should hardcode `fontSize` or `.sp`. Typography must come from `MaterialTheme.typography` or `MarkdownStyles`.

### Two Markdown Modes Only

Markdown should not expose many visually different presets.

- `cardTypography/cardPadding`: used by news summaries, diary previews, book viewpoint cards, AI chat bubbles, and reference previews.
- `readingTypography/readingPadding`: used only for long-form detail pages.

`summaryTypography` and `compactTypography` may remain as compatibility aliases, but they should resolve to the same card visual rhythm.

### One Main Content Card Language

Main feed/read cards should converge:

- Shape: `Radius.l`.
- Surface: shared dark card surface.
- Border: subtle outline.
- Inner padding: `Spacing.m`.
- Title: `titleMedium`, semibold, start-aligned.
- Metadata: `labelSmall`, `onSurfaceVariant`.

Apply this to news summary cards, diary cards, and book viewpoint cards. AI chat bubbles can keep bubble shape but should align typography and nested reference cards with this system.

### Input Alignment Standard

Inputs should separate container height from text layout.

- Outer input surface owns min height, radius, border, and background.
- Text field content uses a centered decoration container for single-line placeholder/text.
- Placeholder and input text share `bodyMedium`.
- Multi-line input can grow to 3 lines but should not make the first line feel top-stuck.

## Scope

Primary files:

- `Typography.kt`
- `MarkdownStyles.kt`
- `ChatInputBar.kt`
- `CustomCard.kt`
- `DiaryCard.kt`
- `ViewpointCard.kt`
- `MessageBubble.kt`
- `CitationText.kt`
- `UnifiedNewsScreen.kt`
- relevant source-contract tests under `app/src/test`

## Acceptance Criteria

- AI chat input placeholder and single-line text are vertically centered in the input capsule.
- News, diary, book, and AI card/preview Markdown share the same body size and line height.
- Long-form article/detail reading remains only slightly larger and more spacious, not a separate visual world.
- Main feed cards share card shape, padding, border, and title/metadata hierarchy.
- No polish-sensitive feature file uses hardcoded `fontSize` or `.sp`.
- Full unit tests pass, debug Kotlin compiles, and the app installs/launches on `192.168.2.7:42577`.
