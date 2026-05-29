# AI Assistant Structured Chat Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Redesign the AI assistant chat page so user messages are visually quiet and AI replies read like structured editorial notes in both light and dark mode.

**Architecture:** Keep the existing `AiChatScreen`, `MessageBubble`, and `ChatInputBar` flow. Add small pure UI policy helpers in `MessageBubble.kt` and `ChatInputBar.kt` so tests can lock visual intent without Compose instrumentation.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, existing Daily Satori theme tokens, kotlin.test.

---

### Task 1: Lock UI Intent With Tests

**Files:**
- Modify: `app/src/test/kotlin/com/dailysatori/ui/feature/aichat/AiChatUiStateTest.kt`

- [ ] Add tests that require user messages to use a muted container, assistant messages to be structured, assistant replies to use a leading editorial rail, and input suggestions to match the approved design.
- [ ] Run: `./gradlew :app:testDebugUnitTest --tests 'com.dailysatori.ui.feature.aichat.AiChatUiStateTest'`
- [ ] Expected before implementation: tests fail because helper functions do not exist yet.

### Task 2: Implement Structured Message Presentation

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/aichat/MessageBubble.kt`

- [ ] Add enum/helpers for message treatment: muted user note, structured assistant note, error note.
- [ ] Replace the current high-emphasis user bubble color with `surfaceContainerHighest` and `onSurfaceVariant`.
- [ ] Replace the current assistant bubble surface with a borderless editorial note: rail on the left, kicker, extracted title, markdown body, and structured source section.
- [ ] Keep long-press actions and reference opening behavior.

### Task 3: Polish Input For Mobile App Feel

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/aichat/ChatInputBar.kt`

- [ ] Add compact suggestion chips: `整理今天`, `提炼主题`, `搜索记忆`.
- [ ] Update placeholder to `继续追问今天的新闻、日记或文章...`.
- [ ] Use `surfaceContainerLow` / `surfaceContainerHighest` and theme-only colors so dark mode stays calm.

### Task 4: Verify And Deploy

**Files:**
- No source changes expected.

- [ ] Run targeted test: `./gradlew :app:testDebugUnitTest --tests 'com.dailysatori.ui.feature.aichat.AiChatUiStateTest'`.
- [ ] Run required compile: `./gradlew :app:compileDebugKotlin`.
- [ ] Run full build: `./gradlew :app:assembleDebug`.
- [ ] Install to device: `JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:installDebug`.
- [ ] Launch app: `adb shell am start -n com.dailysatori/.MainActivity`.
