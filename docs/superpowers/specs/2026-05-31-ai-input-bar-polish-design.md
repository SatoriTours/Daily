# AI Input Bar Polish Design

## Goal

Polish the AI assistant bottom input bar so it feels native on Android:

- The placeholder is short enough to stay on one line.
- The input bar does not show an unexpected cursor before the user interacts with it.
- When the keyboard opens, the input bar moves above the keyboard instead of being covered.

## Approved Direction

Use the approved visual demo "方案 A": a compact bottom pill with a short placeholder, stable height, and IME-aware positioning.

## UI Behavior

Default state:

- Placeholder text is `问点什么`.
- Placeholder remains single-line and ellipsized if space is tight.
- The input should not request focus automatically.
- No keyboard appears until the user taps the input area.

Focused state:

- Tapping the input opens the keyboard normally.
- The full bottom bar follows the IME inset and stays visible above the keyboard.
- The focused border can remain as the existing subtle primary outline.

Typing state:

- Text remains visually stable in the compact bottom bar.
- The field should prefer a single-line presentation in the home bottom bar to avoid height jumps.
- The send/stop button remains on the right.

## Implementation Scope

Primary files:

- `app/src/main/kotlin/com/dailysatori/ui/feature/aichat/ChatInputBar.kt`
- `app/src/main/kotlin/com/dailysatori/ui/feature/home/HomeScreen.kt`

Expected changes:

- Shorten `chatInputPlaceholderText()`.
- Add a compact-mode option to `ChatInputField` so the home bottom bar can stay single-line while preserving the full chat input behavior if reused elsewhere.
- Apply IME padding at the home bottom-bar level, since the compact input currently lives in `HomeBottomBarSurface`, not in `ChatInputBar`.
- Ensure no automatic focus request is introduced.

## Testing

Manual behavior to verify on device:

- AI tab shows the compact placeholder as one line.
- Opening the AI tab does not automatically show the keyboard.
- Tapping the input shows the keyboard.
- With keyboard open, the input bar remains above the keyboard.
- Typing and sending still works.

Required build/deploy checks after code changes:

- `./gradlew :app:compileDebugKotlin`
- `./gradlew :app:assembleDebug`
- `JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:installDebug`
- `adb shell am start -n com.dailysatori/.MainActivity`
