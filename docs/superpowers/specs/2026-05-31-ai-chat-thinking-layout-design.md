# AI Chat Thinking Layout Design

## Scope

Polish the AI chat message layout and transient thinking state. This changes only presentation and does not alter message persistence, streaming, model calls, or navigation.

## Design

- User messages align to the right as compact bubbles.
- Assistant replies render as full-width readable content blocks instead of left/right chat bubbles.
- The thinking state renders as a small left-aligned chip using the same `AutoAwesome` icon as the bottom AI tab.
- The thinking chip uses the app theme, especially the existing blue primary color, instead of introducing gold/demo-only colors.
- The thinking chip appears only while processing before any streaming assistant content exists. Once streaming output starts, it disappears and the assistant reply takes over.
- Long-press actions render horizontally as `复制`, `删除`, and `重问` in a compact floating action row.

## Verification

- Add tests for layout source rules and thinking visibility logic.
- Run the AI chat UI state test suite.
- Run `./gradlew :app:compileDebugKotlin`, `./gradlew :app:assembleDebug`, install, and launch.
