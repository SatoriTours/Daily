# Backup Repair Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make backup directory selection, encrypted backup creation, daily scheduling, password management, and restore use one consistent workflow.

**Architecture:** Keep shared backup packaging/encryption logic in `shared`, add Android-specific selected-directory and secure-password operations behind expect/actual APIs, and keep UI orchestration in Android ViewModels/screens. Use WorkManager for daily scheduling and Android SAF URI permissions for user-selected backup directories.

**Tech Stack:** Kotlin Multiplatform, Android Jetpack Compose, WorkManager, Android Storage Access Framework, Android Keystore, SQLDelight-backed settings.

---

## File Structure

- Modify `shared/src/commonMain/kotlin/com/dailysatori/platform/FileManager.kt`: add backup-directory URI file APIs and selected-directory helpers.
- Modify `shared/src/androidMain/kotlin/com/dailysatori/platform/FileManager.android.kt`: implement SAF read/write/list/delete for URI directories and keep local file encryption helpers.
- Create `shared/src/commonMain/kotlin/com/dailysatori/service/backup/BackupPasswordStore.kt`: expect API for secure current backup password storage.
- Create `shared/src/androidMain/kotlin/com/dailysatori/service/backup/BackupPasswordStore.android.kt`: Android Keystore-backed password storage.
- Modify `shared/src/commonMain/kotlin/com/dailysatori/service/backup/BackupService.kt`: remove default password, use selected directory, add filename hint helpers, and restore with explicit password.
- Modify `shared/src/commonMain/kotlin/com/dailysatori/di/SharedModule.kt`: wire `BackupPasswordStore` into `BackupService`.
- Create `app/src/main/kotlin/com/dailysatori/core/worker/BackupWorker.kt`: WorkManager worker and scheduler for daily backups.
- Modify `app/src/main/kotlin/com/dailysatori/DailySatoriApplication.kt`: ensure daily backup work is scheduled on startup.
- Modify `app/src/main/kotlin/com/dailysatori/ui/feature/settings/BackupSettingsViewModel.kt`: save selected URI, persist permission, save password, validate backup readiness, schedule work after settings changes.
- Modify `app/src/main/kotlin/com/dailysatori/ui/feature/settings/BackupSettingsScreen.kt`: add real directory picker and password UI with Autofill semantics.
- Modify `app/src/main/kotlin/com/dailysatori/ui/feature/settings/BackupRestoreViewModel.kt`: list encrypted files and restore with user-entered historical password.
- Modify `app/src/main/kotlin/com/dailysatori/ui/feature/settings/BackupRestoreScreen.kt`: show hint and password prompt.
- Add/update tests in `shared/src/commonTest/kotlin/com/dailysatori/service/backup/BackupServiceTest.kt` for filename generation and hint parsing.

## Task 1: Backup Filename Helpers

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/service/backup/BackupService.kt`
- Test: `shared/src/commonTest/kotlin/com/dailysatori/service/backup/BackupServiceTest.kt`

- [ ] **Step 1: Write failing tests for filename hint helpers**

Create `BackupServiceTest.kt` with tests for generating names and parsing hints:

```kotlin
package com.dailysatori.service.backup

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BackupServiceTest {
    @Test
    fun backupFileNameIncludesPasswordHint() {
        val name = backupFileName("2026-05-04-10-30-00", "correct horse battery")

        assertEquals("daily_satori_backup_2026-05-04-10-30-00_hint_ery.zip.enc", name)
    }

    @Test
    fun passwordHintIsParsedFromBackupFileName() {
        val hint = backupPasswordHint("daily_satori_backup_2026-05-04-10-30-00_hint_abc.zip.enc")

        assertEquals("abc", hint)
    }

    @Test
    fun passwordHintReturnsNullForOldNames() {
        val hint = backupPasswordHint("daily_satori_backup_2026-05-04-10-30-00.zip.enc")

        assertNull(hint)
    }
}
```

- [ ] **Step 2: Run tests and verify failure**

Run: `./gradlew :shared:allTests --tests com.dailysatori.service.backup.BackupServiceTest`

Expected: compile failure because `backupFileName` and `backupPasswordHint` do not exist.

- [ ] **Step 3: Add minimal helpers**

Add internal helpers in `BackupService.kt`:

```kotlin
internal const val MinBackupPasswordLength = 10

internal fun backupFileName(timestamp: String, password: String): String {
    val hint = password.takeLast(3)
    return "daily_satori_backup_${timestamp}_hint_${hint}.zip.enc"
}

internal fun backupPasswordHint(name: String): String? {
    return Regex("""_hint_([^./]{3})\.zip\.enc$""").find(name)?.groupValues?.get(1)
}
```

- [ ] **Step 4: Run tests and verify pass**

Run: `./gradlew :shared:allTests --tests com.dailysatori.service.backup.BackupServiceTest`

Expected: `BUILD SUCCESSFUL`.

## Task 2: Platform APIs For Selected Directory And Secure Password

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/platform/FileManager.kt`
- Modify: `shared/src/androidMain/kotlin/com/dailysatori/platform/FileManager.android.kt`
- Create: `shared/src/commonMain/kotlin/com/dailysatori/service/backup/BackupPasswordStore.kt`
- Create: `shared/src/androidMain/kotlin/com/dailysatori/service/backup/BackupPasswordStore.android.kt`

- [ ] **Step 1: Add expect APIs**

Extend `FileManager` with selected directory operations:

```kotlin
fun displayNameForUri(uri: String): String
fun listBackupFilesInDirectory(uri: String): List<String>
fun writeFileToDirectory(uri: String, name: String, sourcePath: String): String
fun readFileFromDirectory(uri: String, name: String, destPath: String): Boolean
fun deleteFileFromDirectory(uri: String, name: String): Boolean
```

Create `BackupPasswordStore.kt`:

```kotlin
package com.dailysatori.service.backup

expect class BackupPasswordStore() {
    fun save(password: String)
    fun get(): String?
    fun hasPassword(): Boolean
}
```

- [ ] **Step 2: Implement Android SAF APIs**

In `FileManager.android.kt`, implement URI directory operations using `DocumentFile.fromTreeUri(appContext, Uri.parse(uri))`. `writeFileToDirectory` should delete an existing file with the same name, create `application/octet-stream`, stream bytes from `sourcePath`, and return the filename. `readFileFromDirectory` should find the child by name and stream it to `destPath`. `listBackupFilesInDirectory` should return child names ending in `.enc`, sorted descending. `displayNameForUri` should return the tree document name or URI string if unavailable.

- [ ] **Step 3: Implement Android Keystore password store**

In `BackupPasswordStore.android.kt`, use `AndroidKeyStore`, `AES/GCM/NoPadding`, and app-private file `backup_password.sec`. Store IV length, IV, and ciphertext. Use alias `daily_satori_backup_password`. Return `null` if the file is missing or decryption fails.

- [ ] **Step 4: Compile shared Android target**

Run: `./gradlew :shared:compileDebugKotlinAndroid`

Expected: `BUILD SUCCESSFUL`.

## Task 3: BackupService Unified Logic

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/service/backup/BackupService.kt`
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/di/SharedModule.kt`

- [ ] **Step 1: Update constructor and validation**

Inject `BackupPasswordStore` into `BackupService`. Add private helpers that read `SettingKeys.backupDir` and current password. Backup creation returns `false` with `_lastMessage` set when directory is blank, password missing, or password length is under `MinBackupPasswordLength`.

- [ ] **Step 2: Write selected-directory backup output**

Change `backupNow()` to create temporary zip/encrypted files in app-private backup/cache storage, then call `fileManager.writeFileToDirectory(backupDirUri, finalName, encPath)`. Use `backupFileName(timestamp, password)` for the output name. Delete temporary files afterward.

- [ ] **Step 3: List and clean selected-directory backups**

Change `listBackups()` to read `SettingKeys.backupDir`, call `fileManager.listBackupFilesInDirectory(uri)`, and map names to `BackupEntry`. Keep only the newest 10 by deleting extra names with `deleteFileFromDirectory`.

- [ ] **Step 4: Restore with explicit password**

Change restore signature to `suspend fun restore(name: String, password: String): Boolean`. Validate password is not blank, read selected directory file into a temp encrypted file, decrypt it with the provided password, extract zip, and restore database/images/diary images as current code does.

- [ ] **Step 5: Wire DI**

Update `SharedModule.kt` to register `single { BackupPasswordStore() }` and construct `BackupService(get(), get(), get())`.

- [ ] **Step 6: Compile shared Android target**

Run: `./gradlew :shared:compileDebugKotlinAndroid`

Expected: `BUILD SUCCESSFUL`.

## Task 4: Daily Backup Worker

**Files:**
- Create: `app/src/main/kotlin/com/dailysatori/core/worker/BackupWorker.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/DailySatoriApplication.kt`

- [ ] **Step 1: Add worker and scheduler**

Create `BackupWorker` as a `CoroutineWorker` that resolves `BackupService` from Koin and calls `backupNow()`. Return `Result.success()` for both completed and skipped backups, and `Result.retry()` only for unexpected exceptions.

- [ ] **Step 2: Schedule unique daily work**

Add `BackupScheduler(context).ensureScheduled()` using `PeriodicWorkRequestBuilder<BackupWorker>(24, TimeUnit.HOURS)`, `ExistingPeriodicWorkPolicy.UPDATE`, and unique name `daily-backup`.

- [ ] **Step 3: Schedule on app startup**

Call `BackupScheduler(this).ensureScheduled()` from `DailySatoriApplication.onCreate()` after dependency initialization.

- [ ] **Step 4: Compile app**

Run: `./gradlew :app:compileDebugKotlin`

Expected: `BUILD SUCCESSFUL`.

## Task 5: Backup Settings UI And ViewModel

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/settings/BackupSettingsViewModel.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/settings/BackupSettingsScreen.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/core/di/ViewModelModule.kt` if constructor dependencies change.

- [ ] **Step 1: Extend state and ViewModel actions**

Add `backupDirectoryDisplay`, `hasBackupPassword`, `passwordInput`, and `message` to state. Implement `saveBackupDirectory(uri, activity)` to persist URI permission, save `SettingKeys.backupDir`, set display name, and call `BackupScheduler(activity.applicationContext).ensureScheduled()`. Implement `saveBackupPassword(password)` with length validation and `BackupPasswordStore.save(password)`.

- [ ] **Step 2: Use system directory picker**

In `BackupSettingsScreen`, add `rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree())`. The choose directory button launches it. Remove the current fake choose-directory behavior that calls `startBackup()`.

- [ ] **Step 3: Add password UI**

Add `OutlinedTextField` with `PasswordVisualTransformation`, keyboard password options, and autofill-friendly semantics where supported by existing Compose APIs. Add a save password button. Show “已设置” when `hasBackupPassword` is true.

- [ ] **Step 4: Validate immediate backup**

Disable or reject immediate backup until selected directory and password are present. Show messages from ViewModel for validation errors.

- [ ] **Step 5: Compile app**

Run: `./gradlew :app:compileDebugKotlin`

Expected: `BUILD SUCCESSFUL`.

## Task 6: Restore UI And ViewModel

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/settings/BackupRestoreViewModel.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/settings/BackupRestoreScreen.kt`

- [ ] **Step 1: Use BackupService for list and restore**

Inject `BackupService` into `BackupRestoreViewModel`. Load `backupService.listBackups().map { it.name }`. Add `restoreBackup(password: String)` that passes the selected filename and entered password to `backupService.restore(name, password)`.

- [ ] **Step 2: Display password hint**

Use `backupPasswordHint(path.substringAfterLast("/")) ?: "无提示"` for the selected file and list item subtitle.

- [ ] **Step 3: Prompt for historical password**

In `BackupRestoreScreen`, show an `AlertDialog` with a password field after the user taps restore. The confirm button calls `restoreBackup(inputPassword)`.

- [ ] **Step 4: Compile app**

Run: `./gradlew :app:compileDebugKotlin`

Expected: `BUILD SUCCESSFUL`.

## Task 7: Final Verification

**Files:**
- Verify only; no edits unless failures require fixes.

- [ ] **Step 1: Run focused tests**

Run: `./gradlew :shared:allTests --tests com.dailysatori.service.backup.BackupServiceTest`

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Run required compile check**

Run: `./gradlew :app:compileDebugKotlin`

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Install debug build if device is available**

Run: `JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:installDebug`

Expected: install succeeds if an Android device is connected; if no device is connected, report the exact Gradle/ADB output.

- [ ] **Step 4: Launch app if install succeeds**

Run: `adb shell am start -n com.dailysatori/.MainActivity`

Expected: Activity launch command succeeds.

## Self-Review

- Spec coverage: directory picker, selected directory writes, password length, no default password, filename hint, restore password prompt, WorkManager daily backup, 1Password via Autofill, and verification are covered.
- Placeholder scan: no `TBD`, `TODO`, or unspecified “appropriate handling” steps remain.
- Type consistency: `BackupPasswordStore`, `backupFileName`, `backupPasswordHint`, `BackupWorker`, and `BackupScheduler` names are consistent across tasks.
