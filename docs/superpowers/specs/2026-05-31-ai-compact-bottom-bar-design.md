# AI Compact Bottom Bar Design

## Scope

When the user enters the AI assistant tab, replace the normal four-tab bottom navigation with a compact bar containing a home button and the AI input. This is a presentation and navigation change only; AI message streaming, persistence, and model calls remain unchanged.

## Design

- Non-AI tabs keep the existing four-icon bottom navigation.
- The AI tab hides the four-icon navigation and shows a compact bottom bar.
- The compact AI bar contains a left home icon and a right input field with the same placeholder, send, stop, IME padding, rounded shape, and blue theme colors as the current AI input.
- Tapping the home icon switches to the news summary tab and restores the four-icon bottom navigation.
- `AiChatScreen` no longer renders its own separate bottom input in AI mode; the input is supplied by the home-level compact bar.

## Verification

- Add tests for AI tab compact mode, home fallback behavior, and single input ownership.
- Run AI chat and home UI tests.
- Run Android compile, assemble, install, and launch commands.
