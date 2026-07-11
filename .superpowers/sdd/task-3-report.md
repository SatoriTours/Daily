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
