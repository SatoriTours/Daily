# Task 3 Report: Recording State Machine And Microphone Foreground Service

## Status

Implemented the recording state machine, Android `MediaRecorder` abstraction, microphone foreground service, recording notification, and attachment completion persistence. DiaryScreen integration remains out of scope for Task 5.

## RED / GREEN

### RED

- `./gradlew :app:testDebugUnitTest --tests '*DiaryRecording*Test'`
  - Failed in `compileDebugUnitTestKotlin` because `DiaryRecordingStore` and `DiaryRecordingState` did not exist.
- `./gradlew :shared:testDebugUnitTest --tests '*DiaryAttachmentRecordingTest'`
  - Failed in `compileDebugUnitTestKotlinAndroid` because `completeRecording` did not exist.
- `./gradlew :app:testDebugUnitTest --tests '*DiaryRecordingServiceContractTest'`
  - Failed 1 of 5 tests because handled failures could be persisted again from `onDestroy`.

### GREEN

- `./gradlew :app:testDebugUnitTest --tests '*DiaryRecording*Test' :shared:testDebugUnitTest --tests '*DiaryAttachmentRecordingTest'`
  - Passed.
- `./gradlew :app:testDebugUnitTest :app:compileDebugKotlin :shared:testDebugUnitTest`
  - Passed: app 697 tests, shared 432 tests, 0 failures, 0 errors.
- Merged debug manifest confirms `RECORD_AUDIO`, `FOREGROUND_SERVICE_MICROPHONE`, private `DiaryRecordingService`, and `foregroundServiceType="microphone"`.

## Modified Files

- `app/src/main/AndroidManifest.xml`
- `app/src/main/kotlin/com/dailysatori/core/di/ViewModelModule.kt`
- `app/src/main/kotlin/com/dailysatori/core/recording/DiaryRecordingState.kt`
- `app/src/main/kotlin/com/dailysatori/core/recording/DiaryRecorder.kt`
- `app/src/main/kotlin/com/dailysatori/core/recording/AndroidDiaryRecorder.kt`
- `app/src/main/kotlin/com/dailysatori/core/recording/DiaryRecordingStore.kt`
- `app/src/main/kotlin/com/dailysatori/core/recording/DiaryRecordingService.kt`
- `app/src/main/kotlin/com/dailysatori/core/recording/DiaryRecordingNotification.kt`
- `app/src/test/kotlin/com/dailysatori/DiaryRecordingManifestTest.kt`
- `app/src/test/kotlin/com/dailysatori/core/recording/DiaryRecordingStoreTest.kt`
- `app/src/test/kotlin/com/dailysatori/core/recording/DiaryRecordingServiceContractTest.kt`
- `shared/src/commonMain/kotlin/com/dailysatori/data/repository/DiaryAttachmentRepository.kt`
- `shared/src/commonMain/sqldelight/com/dailysatori/shared/db/DailySatori.sq`
- `shared/src/commonTest/kotlin/com/dailysatori/data/repository/DiaryAttachmentRecordingTest.kt`

## Commit

- `feat: record voice diaries in foreground service`

## Concerns / Follow-up

- Task 5 must request runtime `RECORD_AUDIO` permission while the app is visible before calling `DiaryRecordingService.startFromUserAction`; Android 12+ foreground-start and Android 14+ while-in-use restrictions are intentionally enforced by that entry-point contract.
- `ACTION_OPEN` currently opens `MainActivity` with the persisted `diaryId`. Task 5 must consume that extra to select or focus the diary UI; this task does not connect DiaryScreen.
- JVM tests cover state, persistence, manifest, and service source contracts. Real microphone capture, notification action delivery, and recoverable partial-file playback still require device/API-level validation.
- Existing Koin ViewModel DSL and Kotlin expect/actual compiler warnings remain unchanged.

## Review Remediation: Critical / Important / Minor

### RED

- Duplicate start and Starting cancellation tests failed to compile because `DiaryRecordingStartResult`, `requestStart`, and `releaseFailedSession` did not exist; the completion-retry test then failed because `Failed(PERSIST_FAILED)` could not complete.
- Attachment target tests failed to compile because recording updates did not accept `diaryId`, did not expose start/finish target validation, and required a non-null path for startup failures.
- Service contract tests failed for missing foreground-start exception mapping, FIFO action dispatch, start cancellation tokens, ordered destroy cleanup, retryable persistence state, existing-file-only partial metadata, paused ticker cancellation, and Android string resources.

### GREEN

- `./gradlew :app:testDebugUnitTest --tests '*DiaryRecording*Test' :shared:testDebugUnitTest --tests '*DiaryAttachmentRecordingTest'`
  - Passed: 29 focused tests, 0 failures, 0 errors.
- `./gradlew :shared:testDebugUnitTest`
  - Passed: 434 tests, 0 failures, 0 errors.
- `./gradlew :app:testDebugUnitTest`
  - Passed: 712 tests, 0 failures, 0 errors.
- `./gradlew :app:compileDebugKotlin`
  - Passed.
- `git diff --check`
  - Passed.

### Remediation Summary

- START is classified atomically before foreground or recorder work. Same-session requests are idempotent; different sessions return stable busy and cannot release or reassign the active recorder/output.
- A FIFO action worker, recorder mutex, and invalidatable start token serialize lifecycle work and prevent a late Starting-to-Recording transition after STOP.
- Both service foreground entry and the external user-action helper map API 31+ foreground-start denial and `SecurityException` to stable errors. A created service stops itself after foreground entry failure and records the target attachment failure.
- Destruction closes the action gate, closes/cancels and joins service jobs, then finalizes under `recorderMutex`. Terminal persistence is guarded against duplicate complete/fail writes.
- Complete/fail persistence errors are logged and retain `Failed(PERSIST_FAILED)`, attachment identity, existing output path, and pending persistence for retry. Active identity is cleared only after a successful database write.
- Recording completion/failure validates attachment existence, owning diary, and `kind=audio` in one transaction. Startup failures without an actual partial file preserve existing path and media metadata.
- Recording notification copy and channel description use Chinese Android string resources. Pausing cancels the elapsed ticker and resuming starts it again.

### Remaining Device Risk

- JVM tests cannot exercise OEM `MediaRecorder` timing, actual API 31+ foreground-start denial delivery, Android 14 microphone while-in-use enforcement, process death during a failed database retry, or notification action delivery. These paths still require device tests across API 31 and API 34+.

## Second Review Remediation: Failure Recovery Completion

### RED

- Recovery/session behavior tests initially failed compilation because the bounded persistence retry, cross-API launch mapping, and session output coordinator did not exist.
- Service wiring tests then failed for missing retry action, foreground retention, bounded destroy cleanup, session-owned partial filtering, and Starting STOP decisions.
- A focused regression test reproduced the prepare-before-cancel race, and another reproduced delayed `stopSelf()` after a failed foreground entry.

### GREEN

- `./gradlew :app:testDebugUnitTest --tests '*DiaryRecording*Test' :shared:testDebugUnitTest --tests '*DiaryAttachmentRecordingTest'`
  - Passed: 38 app recording tests and 3 shared attachment recording tests.
- `./gradlew :shared:testDebugUnitTest :app:testDebugUnitTest :app:compileDebugKotlin`
  - Passed: app 724 tests, shared 434 tests, 0 failures, 0 errors; debug Kotlin compilation succeeded.
- `git diff --check`
  - Passed.

### Remediation Summary

- Added a pure Kotlin persistence retry coordinator with one initial write plus bounded 1s/2s/4s retries. Success clears identity and stops the service; exhaustion keeps `Failed(PERSIST_FAILED)` in foreground with explicit `ACTION_RETRY_PERSIST`.
- Replaced start-token cancellation with a session coordinator that records user STOP, recorder start attempt/completion, and current-session output ownership. STOP before start persists `recording_user_cancelled` without media metadata; completed or usable output is finalized as a successful recording and queued for transcription.
- Existing same-name output is deleted before recorder start or rejected when deletion fails. Partial persistence and destroy recovery require a current-session regular file with non-zero length.
- `onDestroy` cleanup is bounded to 1.5 seconds. Timeout only cancels the service job and safely releases the recorder; it does not start another database write.
- API 26-30 background-start `IllegalStateException`, API 31+ foreground-start denial, and `SecurityException` use stable launch failure codes. Foreground-entry failure records pending metadata and calls `stopSelf()` immediately rather than waiting through retry delays.
- Repeated foreground starts for the active/pending session refresh `startForeground()` immediately, avoiding the platform foreground-service timeout.

### Remaining Device Risk

- JVM tests do not validate OEM `MediaRecorder` output timing, notification `PendingIntent` delivery, actual API 26-30 background-start exceptions, Android 14+ while-in-use microphone enforcement, or process death after retry exhaustion. Exercise API 26/30/31/34+ devices before release.
