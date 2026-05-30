# AI Bottom Bar Morph Design

## Scope

Improve the AI assistant tab transition so the bottom bar morphs continuously instead of swapping between separate bars.

## Design

- `HomeScreen` owns one shared bottom bar surface for both normal tabs and the AI assistant.
- Entering AI mode animates the AI input from left to right inside the same bar.
- Leaving AI mode animates the input back to the right, revealing the four normal tab icons.
- The compact left icon uses the news summary tab icon (`Language`) rather than a generic home icon.
- `AiChatScreen` no longer owns any bottom input bar; it receives bottom padding from the home-level bar.

## Verification

- Add tests that require shared Home-owned bottom bar animation primitives.
- Add tests that reject the standalone AI compact bottom bar in `AiChatScreen`.
- Run targeted unit tests and Android build/deploy checks.
