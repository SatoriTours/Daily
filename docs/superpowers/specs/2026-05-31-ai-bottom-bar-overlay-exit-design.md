# AI Bottom Bar Overlay Exit Design

## Goal

Fix the AI bottom-bar exit animation so returning to news feels stable: only the AI input row slides out, and the normal tab icons do not slide or appear one by one.

## Approved Behavior

- Enter AI: the AI input row slides in from right to left.
- Exit AI via the globe icon: the AI input row slides out from left to right.
- Normal tab icons stay in their final positions and do not participate in the horizontal slide.
- Remove the bottom bar border.
- Preserve bottom bar height, rounded shape, compact input behavior, and keyboard avoidance.

## Implementation Direction

Render the normal tab bar as the stable base layer. Render the AI compact input row as an overlay layer with `AnimatedVisibility`:

- `visible = selectedIndex == AI_CHAT_TAB_INDEX`
- `enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn()`
- `exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut()`

This avoids `AnimatedContent` swapping two full layouts during exit. Since the tab bar is already underneath, it remains stable while the AI input slides away.

## Tests

Update source-level tests to assert:

- `AnimatedVisibility` is used for the AI overlay.
- `AnimatedContent` is not used for bottom-bar switching.
- `slideInHorizontally(initialOffsetX = { it })` and `slideOutHorizontally(targetOffsetX = { it })` remain present.
- The bottom bar `Surface` has no `border =` argument.
