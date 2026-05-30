# AI Bottom Bar Morph Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the bottom navigation morph smoothly into the AI input bar and back.

**Architecture:** Move the AI compact input out of `AiChatScreen` and into `HomeScreen` so one shared bottom bar surface can animate between normal navigation and AI input states. Keep `ChatInputField` reusable for the embedded input.

**Tech Stack:** Kotlin, Jetpack Compose animation, Material Icons, Kotlin test, Gradle.

---

## File Structure

- Modify `app/src/main/kotlin/com/dailysatori/ui/feature/home/HomeScreen.kt`: implement a single animated bottom bar using `updateTransition`, `animateFloat`, and `AnimatedVisibility` or equivalent width/alpha animation.
- Modify `app/src/main/kotlin/com/dailysatori/ui/feature/aichat/AiChatScreen.kt`: remove `AiChatCompactBottomBar` and expose input state/actions through a callback to `HomeScreen`.
- Modify `app/src/main/kotlin/com/dailysatori/ui/feature/aichat/ChatInputBar.kt`: continue exposing `ChatInputField` for reuse.
- Modify `app/src/test/kotlin/com/dailysatori/ui/feature/aichat/AiChatUiStateTest.kt`: update tests for the single shared morphing bar.

### Task 1: Red Tests

- [ ] Add tests requiring `HomeScreen.kt` to contain `updateTransition`, shared `HomeBottomBarSurface`, `ChatInputField`, `Icons.Filled.Language`, and an AI-mode transition state.
- [ ] Add tests rejecting `AiChatCompactBottomBar` and `bottomBar = {` in `AiChatScreen.kt`.
- [ ] Run targeted tests and confirm failure.

### Task 2: Shared Morphing Bar

- [ ] Move AI input state/actions to `HomeScreen` via `AiChatScreen` callback registration.
- [ ] Replace separate bottom bar branches with one shared `Surface`.
- [ ] Animate input width/alpha and tab icon alpha/offset inside the shared surface.
- [ ] Use `Icons.Filled.Language` for the compact return icon.

### Task 3: Verification

- [ ] Run targeted unit tests.
- [ ] Run `./gradlew :app:compileDebugKotlin`.
- [ ] Run `./gradlew :app:assembleDebug`.
- [ ] Run install and launch commands.
