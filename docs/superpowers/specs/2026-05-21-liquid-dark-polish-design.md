# Liquid Dark Polish Design

## Goal

Fix the visual polish issues found after installing the Liquid Dark refresh on device:

- Selected bottom tab label pushes the icon upward and can overflow the bottom bar.
- News summary card borders are too bright.
- News, diary, reading, and AI chat still feel typographically inconsistent because Markdown presets differ by context.

## Design

### Bottom Navigation

Use icon-only floating navigation.

- Do not render tab labels inside the compact `52dp` bar.
- Keep each tab accessible by setting the `NavigationBarItem` semantic label through the item/content description path.
- Use icon size around `24dp`.
- Use selected icon color and subtle selected indicator only.
- Keep the floating capsule container, safe-area padding, shadow, and dark glass surface.

This removes vertical crowding and prevents labels such as `今日` from pushing icons outside the capsule.

### Borders

Tone down bright blue borders in content cards.

- Default cards should use `MaterialTheme.colorScheme.outline` or a very low-alpha outline variant.
- News summary cards should avoid `outlineVariant` at full strength.
- Use sapphire/blue border only for focused states, not normal content cards.

### Typography

Unify Markdown content typography across major app areas.

- Use one primary content preset around `15sp / 24sp` for news summaries, diary previews, reading cards, and AI chat bubbles.
- Long detail pages may keep slightly more comfortable line height but should stay visually close to the same scale.
- Avoid a visibly larger news font or smaller diary/book/AI font.
- Keep all Markdown text on `UiFontFamily`.

### Scope

Primary files:

- `HomeScreen.kt` for bottom tab label removal.
- `MarkdownStyles.kt` for unified content presets.
- `UnifiedNewsScreen.kt` and shared card styling for border tone.
- Existing source-contract tests for navigation, theme, and unified news behavior.

## Acceptance Criteria

- Bottom tab icons stay vertically centered when selected and no selected text label appears in the bar.
- News summary card border is subtle, not bright blue.
- News, diary, reading, and AI Markdown body text appear consistent in size and family.
- App compiles and focused/full unit tests pass.
- App installs and launches on `192.168.2.7:42577`.
