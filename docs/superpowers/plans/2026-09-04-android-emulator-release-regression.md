# Android Emulator Release Regression Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to execute this plan task-by-task.

**Goal:** Verify that a clean install and representative upgrade of Daily Satori start reliably and that every reachable top-level feature survives basic user interaction without crashes.

**Architecture:** Combine JVM regression suites with an API 35 KVM-backed emulator. Use ADB/UIAutomator for black-box navigation and inspect Android crash/ANR buffers after each phase; preserve production-like upgrade data separately from clean-install data.

**Tech Stack:** Android Emulator API 35, ADB, Jetpack Compose, SQLDelight, Gradle/JUnit.

**Spec:** User request on 2026-09-04 for whole-app emulator regression before phone use.

## Global Constraints

- Do not use or expose real API tokens.
- Do not claim remote provider success without valid credentials.
- Preserve repository user changes and emulator evidence until reporting.

---

### Task 1: Automated regression baseline

**Files:**
- Verify: `app/src/test`
- Verify: `shared/src/commonTest`

- [ ] Run all JVM tests and build the APK.

```bash
./gradlew :shared:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug
```

- [ ] Reject the build if Gradle returns non-zero or the test report contains failures.

### Task 2: Clean-install startup

**Files:**
- Test: `app/build/outputs/apk/debug/app-debug.apk`

- [ ] Install, clear data, launch, and verify `Status: ok`.

```bash
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 shell pm clear com.dailysatori
adb -s emulator-5554 shell am start -W -n com.dailysatori/.MainActivity
```

- [ ] Verify no `AndroidRuntime` crash or application ANR appears.

### Task 3: Reachable-screen smoke regression

**Files:**
- Verify: `app/src/main/kotlin/com/dailysatori/core/navigation/NavHost.kt`
- Verify: `app/src/main/kotlin/com/dailysatori/ui/feature`

- [ ] Exercise Home tabs: 汇总、本地新闻、日记、读书、AI 助手.
- [ ] Exercise Profile destinations: 提醒、收藏、外部收藏、远程新闻、任务中心、设置、隐私.
- [ ] Exercise safe create/edit forms without sending paid network requests.
- [ ] After each group, capture UI hierarchy and inspect crash/ANR logs.

### Task 4: Upgrade and process-lifecycle regression

**Files:**
- Verify: `shared/src/commonMain/kotlin/com/dailysatori/service/migration/DatabaseMigration.kt`

- [ ] Preserve a populated pre-update database, reinstall with `-r`, and relaunch without clearing data.
- [ ] Force-stop/relaunch and background/foreground the app.
- [ ] Verify data remains readable and no crash/ANR is recorded.

### Task 5: Release assessment

**Files:**
- Verify: current Git diff and generated APK.

- [ ] Run `git diff --check` and inspect only task-related changes.
- [ ] Report passed, failed, blocked, and credential-dependent cases separately.
