# Article Analysis Prompts Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make article original-content conversion produce faithful readable Markdown, and make AI summaries use title, core-content summary, and concise viewpoints.

**Architecture:** Keep the existing article processing pipeline in `WebpageParserService`. Add named prompt helpers so the two AI tasks have separate, explicit contracts: summary analysis from extracted text and original Markdown conversion from HTML.

**Tech Stack:** Kotlin Multiplatform, kotlin.test, Gradle Android/KMP build.

---

### Task 1: Add Prompt Contract Tests

**Files:**
- Modify: `shared/src/commonTest/kotlin/com/dailysatori/service/parser/ArticleProcessingContentTest.kt`
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/service/parser/WebpageParserService.kt`

- [ ] **Step 1: Write failing tests**

Add tests that assert `articleSummaryPrompt()` requires title, core content, max 10 viewpoints, and that `htmlToReadableMarkdownPrompt()` forbids metadata extraction and summaries.

- [ ] **Step 2: Run tests and verify failure**

Run: `./gradlew :shared:allTests --tests "com.dailysatori.service.parser.ArticleProcessingContentTest"`
Expected: FAIL because prompt helper functions do not exist.

### Task 2: Implement Prompt Helpers and Wire Calls

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/service/parser/WebpageParserService.kt`

- [ ] **Step 1: Add helpers**

Add internal functions in the parser package:
`articleSummaryPrompt()` returns a Chinese Markdown prompt requiring `# 标题`, `## 核心内容`, and `## 核心观点` with up to 10 concise numbered points.
`htmlToReadableMarkdownPrompt()` returns a Chinese prompt requiring faithful HTML-to-Markdown conversion only.

- [ ] **Step 2: Wire existing calls**

Replace the inline summary prompt with `articleSummaryPrompt()` and replace the empty HTML conversion prompt with `htmlToReadableMarkdownPrompt()`.

- [ ] **Step 3: Run targeted tests**

Run: `./gradlew :shared:allTests --tests "com.dailysatori.service.parser.ArticleProcessingContentTest"`
Expected: PASS.

### Task 3: Verify Build

**Files:**
- No source changes.

- [ ] **Step 1: Compile Android Kotlin**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Install if device is available**

Run: `JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:installDebug`
Expected: BUILD SUCCESSFUL when an Android device/emulator is connected.

- [ ] **Step 3: Launch if install succeeds**

Run: `adb shell am start -n com.dailysatori/.MainActivity`
Expected: Activity start command succeeds.
