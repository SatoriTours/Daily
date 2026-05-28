# Remaining Pages Magazine Redesign

## Goal

Apply the same lightweight magazine-style direction used by the news summary and diary page to the remaining primary pages: reading, AI chat, and settings.

The implementation should improve visual consistency and interaction convenience without changing data models, persistence, navigation contracts, or feature behavior.

## Shared Direction

- Follow `docs/07-app-magazine-design-standard.md`.
- Use theme tokens only: `MaterialTheme`, `Spacing`, `Radius`, and existing sizing tokens.
- Prefer paper-like surfaces, quiet metadata, readable spacing, and contextual controls.
- Avoid dashboard-like borders, nested cards, heavy color blocks, and new architecture.
- Keep changes local to each page and the small shared components already used by those pages.

## Reading Page

- Keep the current horizontal viewpoint pager and existing add/search/filter/random/delete actions.
- Make each viewpoint feel like a reading page rather than a plain scroll column.
- Use a quiet metadata line for book title, author, and reading progress.
- Keep markdown content and examples, but separate the example section with subtle rhythm rather than a heavy card.
- Keep existing bottom sheets for add/search/book selection, with only minor spacing or surface alignment if needed.

## AI Chat Page

- Keep existing message sending, stopping, memory search, reference detail, long-press actions, delete, and re-ask behavior.
- Make the empty state more helpful with concise guidance instead of only a title.
- Keep user messages visually distinct but reduce the dashboard/chat-app heaviness.
- Make assistant messages feel like paper notes with readable markdown rhythm.
- Keep references expandable, but make reference cards quieter and closer to news/list styling.
- Keep the input bar fixed at the bottom and IME-safe.

## Settings Page

- Keep all current settings entries and navigation destinations.
- Present settings as a calm directory page, with clear section headings and paper-like section groups.
- Reduce the boxed dashboard feel by using subtle section cards and quiet row dividers.
- Keep switches, refresh buttons, update progress, and about dialog behavior unchanged.
- Do not redesign nested settings pages in this pass unless touched by shared settings row/section components.

## Verification

- Run `./gradlew :app:compileDebugKotlin` after implementation.
- Run `JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:installDebug`.
- Launch with `adb shell am start -n com.dailysatori/.MainActivity`.
- Manual smoke check should cover reading page pager/actions, AI chat empty/messages/input/references, and settings section navigation/switch rows.
