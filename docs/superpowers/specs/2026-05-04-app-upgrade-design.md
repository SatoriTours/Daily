# App Upgrade Design

## Goal

Daily Satori should detect new GitHub releases, compare them against the installed app version, and guide the user through updating only when the release version is newer.

## Current State

- Settings calls GitHub `releases/latest` manually.
- The installed version is hardcoded as `1.0.0` in settings state.
- Release comparison uses string inequality, so older or equal tags can be treated as updates.
- There is no startup auto-check, update confirmation, latest-version feedback, or APK download/install flow.

## Target Behavior

- On app startup, check GitHub latest release in the background.
- Compare the latest release tag with the installed app version using semantic version ordering.
- Accept release tags with or without a leading `v`, such as `v5.0.2` and `5.0.2`.
- If the latest release is newer, show a confirmation dialog asking whether to update.
- In Settings, tapping update performs a forced latest-release check.
- If Settings finds no newer release, show `已经是最新版`.
- If a newer release is accepted, download the APK from release assets and launch the Android package installer.

## Architecture

- `AppUpgradeService` owns GitHub release fetching, version parsing/comparison, APK asset selection, and APK download request creation.
- `SettingsViewModel` owns update UI state, including checking, available update, latest-version messages, and download/install errors.
- `SettingsScreen` renders update dialogs and invokes Android UI actions that require `Context`, such as launching the installer.
- Startup auto-check is triggered from the application or initial UI path after dependency injection is ready, without blocking app startup.

## Error Handling

- Network or GitHub parsing failures should not crash the app.
- If no APK asset exists in the latest release, show a clear error.
- Download failures should be surfaced as a user-visible message.
- If Android requires unknown-app install permission, launching the system installer/settings is acceptable; the app should not silently fail.

## Verification

- Add focused tests for version normalization and ordering.
- Compile with `./gradlew :app:compileDebugKotlin` after implementation.
- Install and launch with the project-required Android commands when a device is available.
