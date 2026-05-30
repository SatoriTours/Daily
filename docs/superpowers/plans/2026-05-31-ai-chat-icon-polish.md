# AI Chat Icon Polish Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Polish AI assistant message alignment, bottom tab icon, and launcher clock stroke weight.

**Architecture:** This is a narrow UI-only change. Existing Compose components keep their current responsibilities; only alignment/icon constants and vector stroke widths change.

**Tech Stack:** Kotlin, Jetpack Compose, Material Icons, Android vector drawables, Gradle.

---

## File Structure

- Modify `app/src/main/kotlin/com/dailysatori/ui/feature/aichat/MessageBubble.kt`: reverse user/assistant horizontal alignment and matching bubble corner emphasis.
- Modify `app/src/main/kotlin/com/dailysatori/ui/feature/home/HomeScreen.kt`: replace the AI tab icon imports and tab item with `AutoAwesome`.
- Modify `app/src/main/res/drawable/ic_launcher_foreground.xml`: reduce the clock-circle stroke widths from `8` to `6`.

### Task 1: Reverse AI Chat Bubble Alignment

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/aichat/MessageBubble.kt:100-204`

- [ ] **Step 1: Update message alignment**

Change the `Column` and `Row` alignment logic so user messages align left and assistant messages align right:

```kotlin
horizontalAlignment = if (isUser) Alignment.Start else Alignment.End,
```

```kotlin
Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = if (isUser) Arrangement.Start else Arrangement.End) {
```

- [ ] **Step 2: Update user bubble corner emphasis**

In `MutedUserMessage`, use a left-side tail for the now-left-aligned user bubble:

```kotlin
shape = RoundedCornerShape(
    topStart = Radius.m,
    topEnd = Radius.m,
    bottomStart = Radius.xs,
    bottomEnd = Radius.m,
),
```

- [ ] **Step 3: Update assistant bubble corner emphasis**

In `StructuredAssistantMessage`, use a right-side tail for the now-right-aligned assistant bubble:

```kotlin
shape = RoundedCornerShape(
    topStart = Radius.m,
    topEnd = Radius.m,
    bottomStart = Radius.m,
    bottomEnd = Radius.xs,
),
```

### Task 2: Replace AI Tab Icon

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/home/HomeScreen.kt:15-23,65-70`

- [ ] **Step 1: Replace icon imports**

Remove these imports:

```kotlin
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.outlined.SmartToy
```

Add these imports:

```kotlin
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.outlined.AutoAwesome
```

- [ ] **Step 2: Update the AI tab item**

Change the AI tab to use sparkle icons:

```kotlin
TabItem("AI", Icons.Filled.AutoAwesome, Icons.Outlined.AutoAwesome),
```

### Task 3: Thin Launcher Clock Circle

**Files:**
- Modify: `app/src/main/res/drawable/ic_launcher_foreground.xml:10-24`

- [ ] **Step 1: Reduce outer clock stroke widths**

Change the three outer white circle/accent paths from `android:strokeWidth="8"` to `android:strokeWidth="6"`.

```xml
android:strokeWidth="6"
```

Leave the clock hands at `6` so the central clock remains legible.

### Task 4: Verify

**Files:**
- No code files modified in this task.

- [ ] **Step 1: Compile Kotlin**

Run:

```bash
./gradlew :app:compileDebugKotlin
```

Expected: build succeeds.

- [ ] **Step 2: Install debug build if a device is connected**

Run:

```bash
JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:installDebug
```

Expected: install succeeds, or reports no connected device.

- [ ] **Step 3: Launch app if install succeeds**

Run:

```bash
adb shell am start -n com.dailysatori/.MainActivity
```

Expected: app launches.
