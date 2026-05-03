# AI Reference Detail Design

## Goal

AI assistant search references should be actionable. When a user taps a reference card below an AI answer, the app should show the referenced content using existing article, diary, and book UI where practical.

## Scope

- Article references open the existing article detail route.
- Diary references open an in-place bottom sheet in the AI assistant and reuse the existing diary card display.
- Book references open an in-place bottom sheet in the AI assistant and reuse the existing viewpoint/book display component when possible. Current MCP book-note results are also emitted as `type = "book"`, so this design handles them through the same target.
- Missing or deleted content shows a small empty/error state instead of crashing.

This design does not add new persistent navigation destinations for diary or book references unless the existing code already has one suitable for direct reuse.

## Architecture

Reference cards remain rendered by the AI message UI, but the click callback should pass the full `McpSearchResult` instead of only an article ID. A small mapping layer decides the open target from `result.type`:

- `article`: navigate to `ArticleDetailRoute(result.id)`.
- `diary`: load the diary by ID and show it in an AI-local bottom sheet.
- `book`: load the matching book/viewpoint content by ID and show it in an AI-local bottom sheet.

The mapping should be implemented as a small pure function so behavior can be tested without Compose.

## Components

- `MessageBubble`: keeps rendering reference cards and makes all supported reference cards clickable.
- `AiChatScreen`: owns selected reference state and opens either navigation or a bottom sheet.
- Existing article detail screen: remains the article detail presentation.
- Existing `DiaryCard`: used for diary detail display, with a minimal option to hide destructive actions if needed.
- Existing book/viewpoint UI: reused for book or reading-note detail display, with the smallest adapter needed to fit bottom-sheet usage.

## Data Flow

1. AI response includes persisted `McpSearchResult` objects.
2. `MessageBubble` renders each result card.
3. User taps a supported card.
4. `AiChatScreen` receives the full result and maps it to an open target.
5. Article references navigate immediately.
6. Diary and book references fetch full local data by ID and render it in a bottom sheet.
7. If the fetch returns no content, the bottom sheet shows that the content no longer exists.

## Error Handling

- Unsupported reference types are not clickable.
- Missing referenced records show a user-facing empty state.
- Repository loading failures should not crash the AI chat screen; they should close over into the same empty/error state.

## Testing

- Add shared unit tests for supported reference types and target mapping.
- Add or update tests proving article, diary, and book reference types are considered openable.
- Run `./gradlew :app:compileDebugKotlin` after implementation.
- If code changes are installed to device, run `JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:installDebug` and launch `com.dailysatori/.MainActivity`.

## Non-Goals

- No database schema changes.
- No new delete/edit behavior inside AI reference detail sheets.
- No redesign of AI message layout beyond making supported references clickable.
