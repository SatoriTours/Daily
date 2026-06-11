# External Favorites Add Page Design

## Goal

Make adding an external favorite source feel like adding a remote news source: open a dedicated editor page instead of a compact dialog.

## Scope

- Replace the "add service" dialog in `ExternalFavoritesSettingsScreen` with a dedicated add page.
- Keep the existing X OAuth flow and saved Client ID behavior.
- Keep the source list, manual sync, history import, enable switch, delete, and auth recheck behavior unchanged.
- Do not add new providers or change sync/storage logic.

## UX

The external favorites settings root page shows existing sources and a floating add button. Empty state copy points to the same add action.

The add page uses `AppScaffold` with a back button and title "新增外部收藏". It contains:

- A helper card explaining that X OAuth authorization opens in the browser.
- The existing `X OAuth Client ID` field.
- A read-only note explaining that periodic sync is enabled after authorization and can be managed from the source list.
- A full-width primary button "保存并连接 X".
- Inline status/error message if validation or browser launch fails.

## Implementation Notes

- Use local page state in `ExternalFavoritesSettingsScreen` for showing the add page, matching `RemoteNewsSettingsScreen`'s list/editor split.
- Preserve `ExternalFavoritesSettingsViewModel.saveXOAuthClientIdForConnect()` and `createXAuthorizationUrl()`.
- Add small text helper functions for the new page so unit tests can cover the user-facing contract without Compose UI instrumentation.

## Verification

- Unit test new helper text defaults.
- Run `./gradlew :app:testDebugUnitTest`.
- Run `JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:compileDebugKotlin`.
- Install and launch the debug app on `192.168.2.100:38305`.
