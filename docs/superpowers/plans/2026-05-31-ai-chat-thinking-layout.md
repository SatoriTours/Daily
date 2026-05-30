# AI Chat Thinking Layout Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the approved AI chat layout: right-aligned user bubbles, full-width assistant replies, themed animated AutoAwesome thinking chip, and horizontal long-press actions.

**Architecture:** Keep the existing `AiChatScreen.kt` and `MessageBubble.kt` boundaries. Add pure UI decision helpers where useful for tests, and keep styling on existing Compose components with theme tokens.

**Tech Stack:** Kotlin, Jetpack Compose, Material Icons, Kotlin test, Gradle.

---

## File Structure

- Modify `app/src/test/kotlin/com/dailysatori/ui/feature/aichat/AiChatUiStateTest.kt`: add failing tests for the new layout and thinking visibility rules.
- Modify `app/src/main/kotlin/com/dailysatori/ui/feature/aichat/AiChatScreen.kt`: update thinking visibility helper and replace text bubble with animated AutoAwesome chip using `MaterialTheme.colorScheme.primary`.
- Modify `app/src/main/kotlin/com/dailysatori/ui/feature/aichat/MessageBubble.kt`: right-align user messages, make assistant messages full-width, and replace vertical dropdown with horizontal action row.

### Task 1: Tests

- [ ] Add tests asserting user right alignment, assistant full-width rendering, no assistant max bubble width, horizontal action row, and AutoAwesome thinking indicator.
- [ ] Run `./gradlew :app:testDebugUnitTest --tests com.dailysatori.ui.feature.aichat.AiChatUiStateTest` and confirm the new tests fail.

### Task 2: Message Layout

- [ ] Update `MessageBubble.kt` so user messages align right and assistant messages fill available width.
- [ ] Replace vertical `DropdownMenuItem` action layout with a horizontal floating `Popup` + `Row` of text actions.
- [ ] Run the targeted test and confirm message-layout tests pass.

### Task 3: Thinking Indicator

- [ ] Update `aiChatShowsThinkingBubble` to accept streaming state and return false once streaming starts.
- [ ] Use the helper in `AiChatScreen` for `showThinking`.
- [ ] Replace the text bubble with a small left-aligned animated `AutoAwesome` chip using blue theme primary color.
- [ ] Run the targeted test and confirm thinking tests pass.

### Task 4: Verify App

- [ ] Run `./gradlew :app:compileDebugKotlin`.
- [ ] Run `./gradlew :app:assembleDebug`.
- [ ] Run `JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:installDebug`.
- [ ] Run `adb shell am start -n com.dailysatori/.MainActivity`.
