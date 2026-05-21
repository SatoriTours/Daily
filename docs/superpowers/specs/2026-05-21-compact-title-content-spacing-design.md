# Compact Title And Content Spacing Design

## Goal

Make Daily Satori feel lighter by reducing the visual bulk of the shared title bar and tightening the first gap between the title bar, tabs, headers, and article body content.

## Current Problems

- `AppTopBar` uses the default Material3 center-aligned top app bar height, which feels too thick in the current dark visual system.
- `AppScaffold` passes the full scaffold inset directly to page content, so any page-level top padding stacks on top of the app bar height.
- Article detail pages add `TabRow`, header, and `Spacing.m` body padding below the title bar, creating an oversized first-screen gap.
- The issue is not limited to one screen; the global title bar itself should feel slimmer.

## Design

- Keep the existing `AppScaffold` and `AppTopBar` architecture so all top-level screens stay consistent.
- Make `AppTopBar` explicitly use the existing `Height.appBar` token (`54.dp`) instead of relying on the taller default Material3 top app bar height.
- Keep centered titles, back buttons, account icon, and action icons unchanged in behavior and accessibility.
- Reduce article/detail first-content padding where content immediately follows the title/tab/header stack:
  - Use `Spacing.s` for first body padding where `Spacing.m` currently makes the gap feel heavy.
  - Preserve horizontal readability padding for long-form text.
- Avoid aggressive immersive or collapsing toolbar behavior in this pass.

## Acceptance Criteria

- Shared title bars feel slimmer across screens that use `AppScaffold`.
- The first visible gap between the title bar area and正文 content is reduced on local article detail and remote article detail pages.
- Navigation icons and action icons remain tappable and accessible.
- Existing dark theme style remains unchanged apart from spacing density.
- `./gradlew :app:compileDebugKotlin` succeeds.
- If the device is reachable, install and launch the debug build on `192.168.2.11:39027`.
