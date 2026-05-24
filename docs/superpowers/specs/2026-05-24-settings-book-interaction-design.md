# Settings And Book Add Interaction Design

## Goal

Improve two Daily Satori interaction areas with a WeChat-like style: simple, low-friction, and easy to understand on mobile.

1. Settings configuration pages for AI config, MCP config, Skills config, and plugin config should keep separate menu entries but share the same interaction model.
2. The add-book result card should show more book introduction text while keeping WeRead and add/analyze actions easy to access.

## Settings Interaction

Each configuration section remains under its existing settings menu entry:

- AI 配置
- MCP 服务
- Skills
- 插件中心

The list page pattern is unified:

- Use `AppScaffold` with the section title and a single add action when the section supports adding items.
- Show an empty state with a short title and one clear instruction.
- Show a simple list of cards when items exist.
- Keep list cards focused on identity and status: name, subtitle, enabled/default state, and optional switch.
- Do not show test actions on list cards.

The detail/edit page pattern is unified:

- Use a full-page editor, not a bottom sheet, because the forms can be long.
- Put primary actions in a fixed bottom action row.
- Left action: test connection or test availability.
- Right action: save or update.
- Disable actions while the corresponding operation is running.
- Show test result and validation errors as inline text near the bottom action area or above the form content. Avoid large blocking dialogs.

For each section:

- AI config: add test support in the detail page if backend support exists or can be added with the existing AI service. List remains clean and does not include a test button.
- MCP config: preserve preset add and manual add flows, but align the manual/edit page action area with test + save. Existing list switch remains for enabled state.
- Skills config: keep existing test behavior but move it into the same bottom action pattern as other editors.
- Plugin config: keep plugin list separate. Where plugin server/config testing exists, expose it in a detail/config page using the same bottom action pattern. If plugins only support refresh/update in current code, keep refresh/update as list-level actions and do not invent unrelated add fields.

## Add Book Interaction

The selected result card layout is:

- Book cover on the left.
- Title and author on the right.
- Book introduction shown under author with up to three lines.
- Bottom action row under introduction.
- Left action: low-emphasis WeRead link, displayed as small external-link icon plus `微信读书` text.
- Right action: green primary `添加并分析` button.

The intent is to make the introduction the main content while keeping the primary action visually clear. The WeRead link behaves like an auxiliary WeChat-style text action, not a competing full-width button.

## Visual Style

- Preserve the existing Daily Satori theme tokens: `Spacing`, `Radius`, typography, and color scheme.
- Avoid hardcoded colors in implementation except where existing theme APIs provide the equivalent color.
- Use compact spacing, rounded cards, and restrained emphasis.
- Avoid heavy shadows, large button groups, and dashboard-like controls.
- Ensure content remains usable on narrow Android screens.

## Error Handling

- Test failures should show short actionable inline messages.
- Save failures should use existing error state patterns and remain visible after failure.
- Book source open failure keeps the existing lightweight error text behavior.
- Add/analyze progress keeps the existing analysis status component.

## Testing

- Add or update unit tests for text helpers affected by renamed actions or layout labels.
- Compile with `./gradlew :app:compileDebugKotlin`.
- After code changes, install and launch on connected device with the project-required commands.

## Out Of Scope

- Redesigning settings navigation hierarchy.
- Changing database schema unless a missing persisted setting is required by implementation.
- Adding a new plugin marketplace or new plugin backend behavior beyond aligning existing actions.
- Changing the book search backend or analysis pipeline.
