# Diary Photo Wall Final Design Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the finalized diary photo-wall list and simplified editor toolbar design.

**Architecture:** Keep diary UI changes in existing diary Compose files. Add small formatting/helper functions in diary UI files where needed; avoid changing schema or repository contracts.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, existing Daily Satori theme tokens, existing DiaryViewModel state.

---

## File Structure

- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/diary/DiaryScreen.kt`
  - Month header and natural summary sentence.
  - Keep tag filter sheet and list orchestration.
- Modify: `app/src/main/kotlin/com/dailysatori/ui/component/card/DiaryCard.kt`
  - Photo-wall list card with image preview, three-dot menu, horizontal tags, body expansion.
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/diary/DiaryEditorSheet.kt`
  - Simplify editor layout and media picker/menu state.
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/diary/DiaryEditorToolbar.kt`
  - Compact icon-only toolbar and more popover trigger contract.
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/diary/DiaryImageRow.kt`
  - Horizontal all-image management row for editor.

## Tasks

- [ ] Add month summary display to diary list.
- [ ] Redesign diary cards as photo-wall cards.
- [ ] Move list card actions into a three-dot menu with edit/delete.
- [ ] Make tags horizontally scroll in cards.
- [ ] Redesign editor sheet as a single paper surface with inline image row and text.
- [ ] Make editor images all visible in a horizontal row with direct remove buttons.
- [ ] Replace editor toolbar with compact icon-only controls.
- [ ] Add media source and more-format popovers.
- [ ] Compile with `./gradlew :app:compileDebugKotlin`.
- [ ] Install and launch on the requested Android device.

## Self-Review

- Spec coverage: Covers list, editor, toolbar, images, tags, month summary, and verification.
- Scope: No database schema or persistence changes.
- Ambiguity: Month summary generation will initially use deterministic local text from current month entries; if full AI generation is required later, it can be wired to the existing AI service in a separate pass.
