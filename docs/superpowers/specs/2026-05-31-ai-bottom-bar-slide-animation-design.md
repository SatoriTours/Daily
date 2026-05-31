# AI Bottom Bar Slide Animation Design

## Goal

Replace the current morphing AI bottom-bar animation with a directional slide that feels continuous and predictable.

## Problem

The current bottom bar animates layout weights, alpha, and container background at the same time. This makes the input row and tab row remeasure and compete for space during the transition, so the animation looks discontinuous and visually unstable.

## Approved Behavior

- Enter AI: the AI input row slides in from right to left.
- Exit AI via the globe icon: the AI input row slides out from left to right, returning to the news summary tab.
- Bottom bar height, rounded shape, keyboard avoidance, and compact input behavior remain unchanged.
- Avoid animating layout weights for the AI/tab transition.

## Implementation Direction

Use a fixed-size bottom bar container with directional content transitions instead of weight morphing:

- Show the normal tab navigation when `selectedIndex != AI_CHAT_TAB_INDEX`.
- Show the AI compact input row when `selectedIndex == AI_CHAT_TAB_INDEX`.
- Animate AI row entry with `slideInHorizontally(initialOffsetX = { it })`.
- Animate AI row exit with `slideOutHorizontally(targetOffsetX = { it })`.
- Use simple fade only as support if needed; the primary movement is horizontal slide.

## Tests

Update source-level UI behavior tests to assert:

- The bottom bar uses slide-in/slide-out transitions.
- The old `inputWeight` and `tabsWeight` morphing animation is removed.
- The globe icon still switches to `TODAY_TAB_INDEX`.
- `imePadding()` and compact AI input behavior remain present.
