# Launcher Icon Balance Design

## Goal

Refine the current Sapphire Ring launcher icon so it remains recognizable at app-icon size. The left side of the ring must be visible on dark backgrounds, and the central mark must feel smaller and more premium instead of filling too much of the icon canvas.

## Current Problems

- The left half of the foreground ring uses a dark slate stroke, which becomes hard to distinguish after launcher scaling and masking.
- The ring, hand, and center dot occupy too much of the `108dp` adaptive icon viewport, making the icon feel crowded on the launcher.
- The concept is acceptable; the fix should preserve the existing dark Sapphire Ring / compass-like identity rather than replacing it.

## Design

- Keep the existing adaptive icon structure: background, foreground, and monochrome vector drawables.
- Shrink the foreground mark by reducing the ring radius and moving all dependent foreground paths inward around the same center point.
- Brighten the base ring stroke from very dark slate to a mid slate-blue so the left side remains readable at small sizes.
- Slightly reduce stroke widths and center dot size to preserve negative space.
- Keep the bright upper arc and cyan accent, but scale them with the ring so they do not dominate the icon.
- Keep the dark blue background as-is unless foreground contrast still fails after the mark adjustment.

## Acceptance Criteria

- The left half of the ring is visibly distinguishable from the dark background.
- The central mark is smaller, with more breathing room inside the launcher safe area.
- The icon still reads as the same Daily Satori Sapphire Ring identity.
- `./gradlew :app:compileDebugKotlin` succeeds after the drawable edit.
- The debug build can be installed and launched on the connected device when available.
