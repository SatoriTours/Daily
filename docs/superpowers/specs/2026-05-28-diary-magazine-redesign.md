# Diary Magazine Redesign

## Goal

Unify the diary page with the existing news summary magazine style while preserving the current diary creation, editing, search, tag filtering, deletion, and image support behavior.

The page should feel like a calm personal writing feed rather than a utility list. The first implementation scope is only `DiaryScreen` and the diary list/card presentation used by that screen.

## Visual Direction

- Use the app magazine standard from `docs/07-app-magazine-design-standard.md`.
- Prefer paper-like surfaces, readable text rhythm, and quiet metadata over dense controls.
- Avoid heavy borders, nested panels, dashboard-style stats, and hard-coded colors or spacing.
- Continue using `MaterialTheme`, `Spacing`, and `Radius` values from the theme system.

## Page Structure

- Keep the existing top app bar title `我的日记`, `我的` navigation, search action, and filter action.
- When search is active, keep the search bar directly below the app bar so it remains obvious and reversible.
- Show the active tag filter as a quiet chip above the list with a clear remove action.
- Replace the current inline tag filter panel with a modal bottom sheet so filtering does not push the diary feed down or appear detached from the filter button.
- Keep the floating add button at the bottom end, but use the theme surface/primary hierarchy so it does not dominate the reading feed.

## Diary Feed

- Display entries as magazine-style cards with comfortable padding and vertical spacing.
- Each entry should prioritize:
  - date or time metadata;
  - diary content preview;
  - optional mood, tags, and image count as quiet supporting context.
- Entries should feel tappable without relying on heavy outlines.
- Delete remains available from the card action path and must still show the confirmation dialog.
- Empty and loading states keep existing behavior, with concise copy matching the calmer page style.

## Interaction

- Search stays a top-level action because it is frequent and reversible.
- Tag filtering opens a bottom sheet with all available tags, selected state, clear action, and close behavior.
- Selecting a tag closes the sheet and updates the active filter chip.
- Clearing a tag should be available from both the chip and the bottom sheet.
- Tapping a diary opens the existing editor sheet for editing.
- The add button opens the existing editor sheet for creation.

## Data And Architecture

- Do not change `DiaryViewModel` state shape or persistence behavior.
- Do not change database schema or migration files.
- Keep implementation local to diary UI components unless a tiny reusable helper is clearly justified.
- Prefer minimal edits over extracting a full cross-page design system in this first page pass.

## Testing And Verification

- Compile after changes with `./gradlew :app:compileDebugKotlin`.
- If compilation passes, build/install with `JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:installDebug` and launch `adb shell am start -n com.dailysatori/.MainActivity` when a device is available.
- Manual verification should cover empty diary state, list state, search visibility, tag filter open/select/clear, edit existing diary, add new diary, and delete confirmation.
