# Remaining Pages Magazine Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Redesign reading, AI chat, and settings pages with the same lightweight magazine style as news summary and diary.

**Architecture:** Keep changes local to existing Compose UI files. Reading changes live in `ViewpointCard.kt` and minor `BooksScreen.kt` feedback surfaces; AI chat changes live in `AiChatScreen.kt`, `MessageBubble.kt`, and `ChatInputBar.kt`; settings directory changes live in `SettingsScreen.kt` plus shared settings row/section components.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, existing Daily Satori theme tokens, Koin ViewModel injection.

---

## File Structure

- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/book/ViewpointCard.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/book/BooksScreen.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/aichat/AiChatScreen.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/aichat/MessageBubble.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/aichat/ChatInputBar.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/component/settings/SettingsSectionCard.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/component/settings/SettingsRow.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/settings/SettingsScreen.kt`

## Tasks

- [ ] Reading: make `ViewpointCard` a paper-like reading page with quiet metadata and subtle example separation.
- [ ] Reading: restyle temporary analysis/return banners in `BooksScreen` as compact paper notices.
- [ ] AI chat: improve empty state copy and list spacing in `AiChatScreen`.
- [ ] AI chat: restyle user/assistant messages and reference cards in `MessageBubble`.
- [ ] AI chat: make `ChatInputBar` use a quieter paper input surface.
- [ ] Settings: restyle `SettingsSectionCard` and `SettingsRow` as a calm directory with subtle dividers.
- [ ] Settings: tune `SettingsScreen` spacing and section labels without removing entries.
- [ ] Verify with `./gradlew :app:compileDebugKotlin`.
- [ ] Install with `JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:installDebug`.
- [ ] Launch with `adb shell am start -n com.dailysatori/.MainActivity`.

## Self-Review

- Spec coverage: Covers reading, AI chat, settings, shared visual direction, and verification.
- Scope: No ViewModel, database, navigation, or persistence changes.
- Ambiguity: This plan intentionally avoids large component extraction and applies the approved lightweight per-page approach.
