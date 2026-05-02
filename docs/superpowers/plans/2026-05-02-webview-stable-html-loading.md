# WebView Stable HTML Loading Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Wait for JavaScript-rendered article pages to stabilize before capturing WebView HTML.

**Architecture:** Keep the existing `WebViewLoader` API. Change the Android actual implementation so `onPageFinished` starts polling `document.documentElement.outerHTML` every 2 seconds and completes only after two consecutive unchanged reads, or fails on the existing timeout.

**Tech Stack:** Kotlin, Android WebView, Android Handler/Looper, Gradle Android build.

---

### Task 1: Stable HTML Polling

**Files:**
- Modify: `shared/src/androidMain/kotlin/com/dailysatori/platform/WebViewLoader.android.kt`

- [ ] **Step 1: Add polling state**

Use local variables in `loadContent`: `finished`, `lastHtml`, and `stableReadCount`. Reuse the main-thread `Handler`.

- [ ] **Step 2: Start polling after `onPageFinished`**

Replace the immediate `evaluateJavascript` callback with a polling function. The function reads `outerHTML`, decodes the JSON string returned by `evaluateJavascript`, compares it to the last read, and schedules another check after 2 seconds unless stable.

- [ ] **Step 3: Complete after two unchanged reads**

When the current decoded HTML equals the previous decoded HTML twice consecutively, call `callback(Result.success(currentHtml))` and destroy the WebView.

- [ ] **Step 4: Preserve timeout behavior**

Keep `timeoutMs` as the upper bound. On timeout, stop loading, call failure, and destroy WebView once.

### Task 2: Verify

**Files:**
- No additional source changes.

- [ ] **Step 1: Compile**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Install**

Run: `JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:installDebug`
Expected: BUILD SUCCESSFUL and installed on the connected device.

- [ ] **Step 3: Launch**

Run: `adb shell am start -n com.dailysatori/.MainActivity`
Expected: App launches.
