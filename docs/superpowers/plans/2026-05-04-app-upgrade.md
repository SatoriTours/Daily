# App Upgrade Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add GitHub release auto-checking, correct installed-version comparison, update confirmation, latest-version feedback, and in-app APK download/install.

**Architecture:** Keep release parsing and comparison in `AppUpgradeService`, keep UI state in `SettingsViewModel`, and let `SettingsScreen` perform Android context-bound actions such as install intents. Startup auto-check is triggered from the settings screen once per composition, which covers the main tab without blocking app startup.

**Tech Stack:** Kotlin, Android SDK, Ktor `HttpClient`, kotlinx.serialization JSON, Jetpack Compose Material 3, Android `DownloadManager`, `FileProvider`.

---

## Files

- Modify `app/src/main/kotlin/com/dailysatori/core/service/AppUpgradeService.kt`: release model, version comparison, APK asset selection, download request, install intent.
- Modify `app/src/main/kotlin/com/dailysatori/ui/feature/settings/SettingsViewModel.kt`: real current version, update state, auto/manual checking, download completion tracking.
- Modify `app/src/main/kotlin/com/dailysatori/ui/feature/settings/SettingsScreen.kt`: dialogs, one-shot auto-check, snackbar messages, launch installer.
- Modify `app/src/main/AndroidManifest.xml`: add APK install permission and package visibility for install settings if needed.
- Modify `app/src/main/res/xml/file_paths.xml`: expose external files/downloads path for downloaded APK install URI.
- Add `app/src/test/kotlin/com/dailysatori/core/service/AppUpgradeServiceTest.kt`: version comparison and APK asset selection tests.

## Task 1: Version Comparison And Release Parsing

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/core/service/AppUpgradeService.kt`
- Test: `app/src/test/kotlin/com/dailysatori/core/service/AppUpgradeServiceTest.kt`

- [ ] **Step 1: Write tests for version ordering and APK selection**

```kotlin
package com.dailysatori.core.service

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppUpgradeServiceTest {
    @Test
    fun `version comparison ignores leading v`() {
        assertTrue(AppUpgradeService.isNewerVersion("v5.0.2", "5.0.1"))
        assertFalse(AppUpgradeService.isNewerVersion("v5.0.1", "5.0.1"))
    }

    @Test
    fun `version comparison uses numeric ordering`() {
        assertTrue(AppUpgradeService.isNewerVersion("5.10.0", "5.2.9"))
        assertFalse(AppUpgradeService.isNewerVersion("5.0.0", "5.0.1"))
    }

    @Test
    fun `apk asset selection prefers apk files`() {
        val assets = listOf(
            ReleaseAsset("notes.txt", "https://example.com/notes.txt"),
            ReleaseAsset("daily-satori.apk", "https://example.com/app.apk"),
        )
        assertEquals("https://example.com/app.apk", AppUpgradeService.findApkAsset(assets)?.downloadUrl)
    }
}
```

- [ ] **Step 2: Run failing tests**

Run: `./gradlew :app:testDebugUnitTest --tests com.dailysatori.core.service.AppUpgradeServiceTest`

Expected: fails because `ReleaseAsset`, `isNewerVersion`, and `findApkAsset` do not exist.

- [ ] **Step 3: Implement release types and pure helpers**

Add to `AppUpgradeService.kt`:

```kotlin
data class AppRelease(
    val version: String,
    val releaseUrl: String,
    val apkAsset: ReleaseAsset?,
)

data class ReleaseAsset(
    val name: String,
    val downloadUrl: String,
)

companion object {
    fun isNewerVersion(latestVersion: String, currentVersion: String): Boolean {
        val latest = versionParts(latestVersion)
        val current = versionParts(currentVersion)
        val size = maxOf(latest.size, current.size)
        for (index in 0 until size) {
            val latestPart = latest.getOrElse(index) { 0 }
            val currentPart = current.getOrElse(index) { 0 }
            if (latestPart != currentPart) return latestPart > currentPart
        }
        return false
    }

    fun findApkAsset(assets: List<ReleaseAsset>): ReleaseAsset? =
        assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }

    private fun versionParts(version: String): List<Int> =
        version.trim()
            .removePrefix("v")
            .removePrefix("V")
            .substringBefore("-")
            .split('.')
            .map { it.toIntOrNull() ?: 0 }
}
```

- [ ] **Step 4: Run tests until green**

Run: `./gradlew :app:testDebugUnitTest --tests com.dailysatori.core.service.AppUpgradeServiceTest`

Expected: `BUILD SUCCESSFUL`.

## Task 2: GitHub Latest Release Check

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/core/service/AppUpgradeService.kt`

- [ ] **Step 1: Replace `checkForUpdate` return type**

Change `checkForUpdate(currentVersion: String): String?` to `checkForUpdate(currentVersion: String): AppRelease?`.

- [ ] **Step 2: Parse GitHub release fields**

Implement parsing of `tag_name`, `html_url`, and `assets[].name/browser_download_url`. Return `null` when `tag_name` is missing or not newer.

- [ ] **Step 3: Preserve failure behavior**

Keep `try/catch`, log `Failed to check for updates`, and return `null` on errors so auto-check never crashes startup.

## Task 3: Download And Install Plumbing

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/core/service/AppUpgradeService.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/res/xml/file_paths.xml`

- [ ] **Step 1: Add install permission**

Add to manifest root permissions: `<uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />`.

- [ ] **Step 2: Add FileProvider path for downloaded APKs**

Add to `file_paths.xml`: `<external-files-path name="downloads" path="Download/" />`.

- [ ] **Step 3: Add download request method**

Add `enqueueApkDownload(context: Context, release: AppRelease): Long` that requires `release.apkAsset`, creates a `DownloadManager.Request`, saves to `Environment.DIRECTORY_DOWNLOADS/DailySatori-${release.version}.apk`, and returns the download id.

- [ ] **Step 4: Add install intent method**

Add `createInstallIntent(context: Context, file: File): Intent` using `FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)`, MIME `application/vnd.android.package-archive`, `Intent.ACTION_VIEW`, and `FLAG_GRANT_READ_URI_PERMISSION`.

## Task 4: Settings State And Actions

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/settings/SettingsViewModel.kt`

- [ ] **Step 1: Replace hardcoded current version**

Initialize `currentVersion` from `BuildConfig.VERSION_NAME`.

- [ ] **Step 2: Add update state**

Add `availableRelease: AppRelease?`, `showUpdateDialog: Boolean`, `updateMessage: String?`, `downloadId: Long?`, and `pendingInstallFilePath: String?` to `SettingsState`.

- [ ] **Step 3: Add auto-check method**

Add `checkUpdateAutomatically()` that calls `appUpgradeService.checkForUpdate(currentVersion)` and only sets `showUpdateDialog = true` when a release exists.

- [ ] **Step 4: Update manual check method**

Make `checkUpdate()` set `updateMessage = "已经是最新版"` when no release exists, otherwise show the update dialog.

- [ ] **Step 5: Add dialog and message clear methods**

Add `dismissUpdateDialog()`, `clearUpdateMessage()`, and `markInstallLaunched()`.

## Task 5: Settings UI Dialogs And Snackbar

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/settings/SettingsScreen.kt`

- [ ] **Step 1: Trigger auto-check once**

Use `LaunchedEffect(Unit) { viewModel.checkUpdateAutomatically() }` inside `SettingsScreen`.

- [ ] **Step 2: Add snackbar host**

Wrap main content in `Scaffold` with `SnackbarHost`, and show `state.updateMessage` in a `LaunchedEffect` before clearing it.

- [ ] **Step 3: Add update confirmation dialog**

When `state.showUpdateDialog && state.availableRelease != null`, show `AlertDialog` with title `发现新版本`, text including current/latest version, cancel `稍后`, and confirm `立即更新`.

- [ ] **Step 4: Start APK download on confirm**

On confirm, call the view model method that enqueues download through `AppUpgradeService` and sets a user-visible `正在下载更新...` message.

- [ ] **Step 5: Launch installer after download completion**

Register a `BroadcastReceiver` in a `DisposableEffect` for `DownloadManager.ACTION_DOWNLOAD_COMPLETE`, match the stored download id, and start the install intent for the downloaded APK path.

## Task 6: Verification

**Files:**
- All modified files.

- [ ] **Step 1: Run unit tests**

Run: `./gradlew :app:testDebugUnitTest --tests com.dailysatori.core.service.AppUpgradeServiceTest`

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Run Kotlin compile check**

Run: `./gradlew :app:compileDebugKotlin`

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Install and launch when device is available**

Run: `JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:installDebug`

Run: `adb shell am start -n com.dailysatori/.MainActivity`

Expected: app launches and settings update flow is reachable.
