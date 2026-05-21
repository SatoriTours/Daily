# Launcher Icon Balance Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the Daily Satori launcher icon more readable by brightening the left ring and shrinking the foreground mark.

**Architecture:** The launcher icon is an Android adaptive icon made from vector drawables. Update only the foreground and monochrome vector geometry; keep the background unchanged unless the resized foreground still fails contrast in visual inspection.

**Tech Stack:** Android adaptive icons, vector drawable XML, Gradle Android build.

---

## File Structure

- Modify: `app/src/main/res/drawable/ic_launcher_foreground.xml`
  - Owns the full-color foreground Sapphire Ring mark.
  - Shrink ring radius from `28` to `23`, reduce stroke widths from `10/7` to `8/6`, brighten the base ring stroke from dark slate to mid slate-blue.
- Modify: `app/src/main/res/drawable/ic_launcher_monochrome.xml`
  - Owns themed monochrome foreground geometry.
  - Mirror the resized ring, hands, and center dot so themed icons match the full-color silhouette.
- Verify only: `app/src/main/res/drawable/ic_launcher_background.xml`
  - Keep unchanged unless foreground contrast still looks too dark.

---

### Task 1: Full-Color Foreground Balance

**Files:**
- Modify: `app/src/main/res/drawable/ic_launcher_foreground.xml`

- [ ] **Step 1: Inspect current foreground geometry**

Read `app/src/main/res/drawable/ic_launcher_foreground.xml` and confirm it contains:

```xml
android:pathData="M54,26a28,28 0,1 1,0 56a28,28 0,1 1,0 -56"
android:strokeColor="#334155"
android:strokeWidth="10"
```

- [ ] **Step 2: Replace the foreground vector paths**

Replace the contents of `ic_launcher_foreground.xml` with this exact vector:

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:fillColor="@android:color/transparent"
        android:pathData="M0,0h108v108h-108z" />
    <path
        android:pathData="M54,31a23,23 0,1 1,0 46a23,23 0,1 1,0 -46"
        android:strokeColor="#64748B"
        android:strokeWidth="8"
        android:strokeAlpha="0.9"
        android:strokeLineCap="round" />
    <path
        android:pathData="M54,31a23,23 0,0 1,22 29"
        android:strokeColor="#E2E8F0"
        android:strokeWidth="8"
        android:strokeAlpha="0.95"
        android:strokeLineCap="round" />
    <path
        android:pathData="M54,31a23,23 0,0 1,10 2.2"
        android:strokeColor="#7DD3FC"
        android:strokeWidth="8"
        android:strokeAlpha="0.95"
        android:strokeLineCap="round" />
    <path
        android:fillColor="#050816"
        android:pathData="M54,42a12,12 0,1 1,0 24a12,12 0,1 1,0 -24" />
    <path
        android:fillColor="#0F172A"
        android:fillAlpha="0.72"
        android:pathData="M45,49a12,12 0,0 1,21 0c-4,-2.2 -8,-3 -12,-3s-7,0.8 -9,3z" />
    <path
        android:pathData="M54,27v22"
        android:strokeColor="#7DD3FC"
        android:strokeWidth="6"
        android:strokeAlpha="0.98"
        android:strokeLineCap="round" />
    <path
        android:pathData="M57,56l14,15"
        android:strokeColor="#CBD5E1"
        android:strokeWidth="6"
        android:strokeAlpha="0.96"
        android:strokeLineCap="round" />
    <path
        android:fillColor="#E2E8F0"
        android:pathData="M54,47a7,7 0,1 1,0 14a7,7 0,1 1,0 -14" />
    <path
        android:fillColor="#94A3B8"
        android:fillAlpha="0.55"
        android:pathData="M49,56a7,7 0,0 0,12 4.5a7,7 0,0 1,-12 -4.5z" />
</vector>
```

- [ ] **Step 3: Compile resources through Kotlin build**

Run: `./gradlew :app:compileDebugKotlin`

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit foreground edit**

Run:

```bash
git add app/src/main/res/drawable/ic_launcher_foreground.xml
git commit -m "fix: rebalance launcher foreground icon"
```

---

### Task 2: Monochrome Silhouette Match

**Files:**
- Modify: `app/src/main/res/drawable/ic_launcher_monochrome.xml`

- [ ] **Step 1: Inspect current monochrome geometry**

Read `app/src/main/res/drawable/ic_launcher_monochrome.xml` and confirm it contains the old larger ring:

```xml
android:pathData="M54,26a28,28 0,1 1,0 56a28,28 0,1 1,0 -56"
```

- [ ] **Step 2: Replace monochrome vector paths**

Replace the contents of `ic_launcher_monochrome.xml` with this exact vector:

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:fillColor="@android:color/transparent"
        android:pathData="M0,0h108v108h-108z" />
    <path
        android:pathData="M54,31a23,23 0,1 1,0 46a23,23 0,1 1,0 -46"
        android:strokeColor="#FFFFFFFF"
        android:strokeWidth="7"
        android:strokeLineCap="round" />
    <path
        android:pathData="M54,27v22"
        android:strokeColor="#FFFFFFFF"
        android:strokeWidth="6"
        android:strokeLineCap="round" />
    <path
        android:pathData="M57,56l14,15"
        android:strokeColor="#FFFFFFFF"
        android:strokeWidth="6"
        android:strokeLineCap="round" />
    <path
        android:fillColor="#FFFFFFFF"
        android:pathData="M54,47a7,7 0,1 1,0 14a7,7 0,1 1,0 -14" />
</vector>
```

- [ ] **Step 3: Compile resources through Kotlin build**

Run: `./gradlew :app:compileDebugKotlin`

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit monochrome edit**

Run:

```bash
git add app/src/main/res/drawable/ic_launcher_monochrome.xml
git commit -m "fix: match themed launcher icon silhouette"
```

---

### Task 3: Device Verification

**Files:**
- Verify: `app/src/main/res/drawable/ic_launcher_foreground.xml`
- Verify: `app/src/main/res/drawable/ic_launcher_monochrome.xml`

- [ ] **Step 1: Run final compile**

Run: `./gradlew :app:compileDebugKotlin`

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Connect to the known wireless device**

Run: `adb connect 192.168.2.11:39027`

Expected: output contains `connected to 192.168.2.11:39027` or `already connected`.

- [ ] **Step 3: Install debug build**

Run: `JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:installDebug`

Expected: `BUILD SUCCESSFUL` and `Installed on 1 device.`

- [ ] **Step 4: Launch app**

Run: `adb -s 192.168.2.11:39027 shell am start -n com.dailysatori/.MainActivity`

Expected: output contains `Starting: Intent`.

- [ ] **Step 5: Check git status**

Run: `git status --short`

Expected: no output.

---

## Self-Review

- Spec coverage: Task 1 brightens and shrinks the full-color foreground; Task 2 keeps monochrome silhouette aligned; Task 3 verifies compile, install, and launch.
- Placeholder scan: no TODO/TBD placeholders remain.
- Type/path consistency: file paths match current Android vector drawable locations.
