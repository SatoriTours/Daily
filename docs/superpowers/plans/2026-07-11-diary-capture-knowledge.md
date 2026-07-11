# Diary Capture And Knowledge Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add background voice diaries, transcription, video/file attachments, and knowledge extraction without changing the existing diary feed structure.

**Architecture:** Persist every capture under a real diary ID and normalized `diary_attachment` row. An Android microphone foreground service owns recording; repositories and existing async-task infrastructure own transcription and knowledge processing; Compose only observes state and renders attachments inside the existing `DiaryCard`.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Android foreground services/MediaRecorder, Activity Result APIs, Koin, SQLDelight, coroutines/Flow, existing AI and async-task services.

## Global Constraints

- Keep `DiaryScreen` month summaries, date groups, and existing `DiaryCard` hierarchy visually and behaviorally unchanged.
- Minimum SDK is 26 and target SDK is 36.
- A voice action must persist a diary and obtain its nonzero ID before recording starts.
- Background audio uses a microphone foreground service and persistent notification; background video is out of scope.
- Attachments are rows inside a diary card, never a separate inbox, filter, or card type.
- Existing `diary.images` data remains readable during the first migration.
- All icon-only controls retain at least a 48 dp touch target and a content description.

---

### Task 1: Persist Real Diary IDs And Attachments

**Files:**
- Modify: `shared/src/commonMain/sqldelight/com/dailysatori/shared/db/DailySatori.sq`
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/service/migration/DatabaseMigration.kt`
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/data/repository/DiaryRepository.kt`
- Create: `shared/src/commonMain/kotlin/com/dailysatori/data/repository/DiaryAttachmentRepository.kt`
- Test: `shared/src/commonTest/kotlin/com/dailysatori/data/repository/DiaryAttachmentSchemaTest.kt`
- Test: `shared/src/commonTest/kotlin/com/dailysatori/data/repository/DiaryRepositoryInsertTest.kt`

**Interfaces:**
- Produces: `suspend fun DiaryRepository.create(...): Long`
- Produces: `DiaryAttachmentRepository.create(diaryId: Long, draft: DiaryAttachmentDraft): Long`
- Produces: `DiaryAttachmentRepository.observeForDiary(diaryId: Long): Flow<List<Diary_attachment>>`
- Produces: `DiaryAttachmentKind` and processing-status constants shared by later tasks.

- [ ] **Step 1: Write failing schema and repository tests**

Assert the schema contains `diary_attachment`, `FOREIGN KEY(diary_id) REFERENCES diary(id) ON DELETE CASCADE`, diary/status indexes, insert/select/update/delete queries, and an `insertDiaryReturningId` transaction that calls `lastInsertRowId()`.

- [ ] **Step 2: Run the focused tests and confirm failure**

Run: `./gradlew :shared:allTests --tests '*DiaryAttachmentSchemaTest' --tests '*DiaryRepositoryInsertTest'`

Expected: FAIL because the attachment schema and returning insert API do not exist.

- [ ] **Step 3: Add the SQLDelight schema and queries**

Add columns from the design spec with non-null defaults for status fields, plus these query contracts:

```sql
insertDiaryReturningId:
INSERT INTO diary(content, tags, mood, images, created_at, updated_at)
VALUES (?, ?, ?, ?, ?, ?);

lastInsertRowId:
SELECT last_insert_rowid();

selectAttachmentsForDiary:
SELECT * FROM diary_attachment WHERE diary_id = ? ORDER BY created_at ASC, id ASC;
```

Wrap insert plus `lastInsertRowId().executeAsOne()` in `dailySatoriQueries.transactionWithResult`.

- [ ] **Step 4: Add idempotent runtime migration**

Create the table and indexes with `CREATE TABLE IF NOT EXISTS` / `CREATE INDEX IF NOT EXISTS` in `DatabaseMigration`, matching the canonical SQLDelight schema exactly.

- [ ] **Step 5: Implement `DiaryAttachmentRepository` and returning diary create**

Validate `diaryId > 0`, map generated SQLDelight rows directly, update statuses atomically, and delete app-owned file metadata only after the database transaction succeeds.

- [ ] **Step 6: Run shared tests and commit**

Run: `./gradlew :shared:allTests`

Expected: PASS.

Commit: `feat: persist diary attachments and inserted ids`

---

### Task 2: Fix Diary Saving And Knowledge Source IDs

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/diary/DiaryViewModel.kt`
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/service/memory/MemoryExtractService.kt`
- Test: `app/src/test/kotlin/com/dailysatori/ui/feature/diary/DiaryViewModelSourceIdTest.kt`

**Interfaces:**
- Consumes: `DiaryRepository.create(...): Long`
- Produces: `suspend fun DiaryViewModel.saveDiary(...): Long?` with the actual persisted ID.

- [ ] **Step 1: Write a failing source-ID test**

Use fakes to create a new diary, capture `MemoryExtractService.extractAndSave(sourceType, sourceId, ...)`, and assert `sourceType == "diary"` and `sourceId == 42L`, never zero.

- [ ] **Step 2: Run the focused test and confirm failure**

Run: `./gradlew :app:testDebugUnitTest --tests '*DiaryViewModelSourceIdTest'`

Expected: FAIL because new entries currently use `existingId ?: 0L`.

- [ ] **Step 3: Save first, then extract with the returned ID**

For edits, preserve `existingId`; for creates, call the new returning repository method. Only invoke extraction after persistence succeeds and return the ID to capture callers.

- [ ] **Step 4: Run tests and commit**

Run: `./gradlew :app:testDebugUnitTest --tests '*DiaryViewModelSourceIdTest'`

Expected: PASS.

Commit: `fix: bind diary knowledge to persisted ids`

---

### Task 3: Build The Recording State Machine And Foreground Service

**Files:**
- Create: `app/src/main/kotlin/com/dailysatori/core/recording/DiaryRecordingState.kt`
- Create: `app/src/main/kotlin/com/dailysatori/core/recording/DiaryRecorder.kt`
- Create: `app/src/main/kotlin/com/dailysatori/core/recording/AndroidDiaryRecorder.kt`
- Create: `app/src/main/kotlin/com/dailysatori/core/recording/DiaryRecordingStore.kt`
- Create: `app/src/main/kotlin/com/dailysatori/core/recording/DiaryRecordingService.kt`
- Create: `app/src/main/kotlin/com/dailysatori/core/recording/DiaryRecordingNotification.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/kotlin/com/dailysatori/core/di/ViewModelModule.kt`
- Test: `app/src/test/kotlin/com/dailysatori/core/recording/DiaryRecordingStoreTest.kt`
- Test: `app/src/test/kotlin/com/dailysatori/DiaryRecordingManifestTest.kt`

**Interfaces:**
- Produces: `StateFlow<DiaryRecordingState>` where state is `Idle`, `Starting`, `Recording`, `Paused`, `Stopping`, or `Failed`.
- Produces service actions: `ACTION_START`, `ACTION_PAUSE`, `ACTION_RESUME`, `ACTION_STOP`, `ACTION_OPEN`.
- Consumes a persisted `diaryId` and `attachmentId` in `ACTION_START`.

- [ ] **Step 1: Write failing state transition and manifest tests**

Cover `Idle -> Starting -> Recording -> Paused -> Recording -> Stopping -> Idle`, invalid transitions, elapsed-time stability while paused, microphone service type, `RECORD_AUDIO`, `FOREGROUND_SERVICE`, and `FOREGROUND_SERVICE_MICROPHONE` declarations.

- [ ] **Step 2: Run tests and confirm failure**

Run: `./gradlew :app:testDebugUnitTest --tests '*DiaryRecording*Test'`

Expected: FAIL because recording classes and manifest entries do not exist.

- [ ] **Step 3: Implement the state store and recorder abstraction**

Keep Android `MediaRecorder` calls behind `DiaryRecorder`; write to `filesDir/DailySatori/diary/audio/<diaryId>/`; finalize partial output on recoverable interruption; expose errors as stable user-facing codes rather than exception strings.

- [ ] **Step 4: Implement the foreground service and notification actions**

Call `startForegroundService` only from the visible user action, then call `startForeground` immediately with `ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE`. The notification shows elapsed state and Pause/Resume, Stop, and Open Diary actions.

- [ ] **Step 5: Persist recording completion**

On stop, probe duration, update attachment path/size/duration and `transcript_status = queued`; on failure, retain playable partial media where possible and mark a clear failure.

- [ ] **Step 6: Run tests and commit**

Run: `./gradlew :app:testDebugUnitTest --tests '*DiaryRecording*Test'`

Expected: PASS.

Commit: `feat: record voice diaries in foreground service`

---

### Task 4: Add Transcription And Knowledge Tasks

**Files:**
- Create: `shared/src/commonMain/kotlin/com/dailysatori/service/diary/SpeechTranscriptionClient.kt`
- Create: `shared/src/commonMain/kotlin/com/dailysatori/service/diary/OpenAiCompatibleSpeechTranscriptionClient.kt`
- Create: `shared/src/commonMain/kotlin/com/dailysatori/service/diary/DiaryTranscriptionCoordinator.kt`
- Create: `shared/src/commonMain/kotlin/com/dailysatori/service/diary/DiaryKnowledgeCoordinator.kt`
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/service/asynctask/AsyncTaskModels.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/core/worker/AsyncTaskWorker.kt`
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/di/SharedModule.kt`
- Test: `shared/src/commonTest/kotlin/com/dailysatori/service/diary/DiaryCaptureTaskTest.kt`

**Interfaces:**
- Produces task types `diary_attachment_transcribe` and `diary_knowledge_extract`.
- Produces unique keys `diary-transcribe:<attachmentId>` and `diary-knowledge:<diaryId>:<updatedAt>`.

- [ ] **Step 1: Write failing idempotency and failure tests**

Assert duplicate enqueue returns the active task, successful transcription stores text and `completed`, failure stores `failed` without deleting audio, and knowledge extraction receives the real diary ID plus diary/transcript text.

- [ ] **Step 2: Run tests and confirm failure**

Run: `./gradlew :shared:allTests --tests '*DiaryCaptureTaskTest'`

Expected: FAIL because task types/coordinators do not exist.

- [ ] **Step 3: Implement transcription orchestration**

Use an OpenAI-compatible multipart `POST <configured-api-base>/audio/transcriptions` client with the configured token and speech model setting (default `whisper-1`). Set `processing`, persist transcript atomically, replace only the exact auto-created body `这篇日记正在转写…`, and otherwise append under `## 语音转写` without overwriting user edits; then enqueue knowledge extraction.

- [ ] **Step 4: Implement knowledge orchestration**

Combine current diary content with completed attachment transcripts, call `MemoryExtractService.extractAndSave("diary", diaryId, ...)`, and update each participating attachment's knowledge status.

- [ ] **Step 5: Register worker handlers, run tests, and commit**

Run: `./gradlew :shared:allTests :app:testDebugUnitTest --tests '*AsyncTaskWorkerSourceTest'`

Expected: PASS.

Commit: `feat: transcribe and index diary captures`

---

### Task 5: Add Compact Capture UI Without Redesigning The Feed

**Files:**
- Create: `app/src/main/kotlin/com/dailysatori/ui/feature/diary/DiaryCaptureMenu.kt`
- Create: `app/src/main/kotlin/com/dailysatori/ui/feature/diary/DiaryRecordingController.kt`
- Create: `app/src/main/kotlin/com/dailysatori/ui/feature/diary/DiaryAttachmentList.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/diary/DiaryScreen.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/diary/DiaryViewModel.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/component/card/DiaryCard.kt`
- Test: `app/src/test/kotlin/com/dailysatori/ui/feature/diary/DiaryCaptureUiTest.kt`
- Test: `app/src/test/kotlin/com/dailysatori/ui/feature/diary/DiaryFeedRegressionTest.kt`

**Interfaces:**
- Consumes recording `StateFlow` and attachment flows.
- Produces callbacks `onVoice`, `onText`, `onCapture`, `onFile`, `onPauseResume`, `onStop`, `onOpenDiary`.

- [ ] **Step 1: Write failing UI source/behavior tests**

Assert the existing `DiaryMonthHeader`, `DiaryDateHeader`, and `DiaryCard` call path remains; no category filter strings are introduced; the menu contains exactly four ordered actions; the visual add circle is 36 dp inside a 48 dp target; and attachments render between body and footer.

- [ ] **Step 2: Run tests and confirm failure**

Run: `./gradlew :app:testDebugUnitTest --tests '*DiaryCaptureUiTest' --tests '*DiaryFeedRegressionTest'`

Expected: FAIL because capture components do not exist.

- [ ] **Step 3: Implement the anchored menu**

Use Material icons for microphone, edit, video camera, and attach file. Use one compact surface, 44 dp rows, fade/vertical enter-exit animation, outside/Back dismissal, and no large CTA.

- [ ] **Step 4: Implement recording status UI**

Place the quiet status strip below the app bar and the compact controller above bottom navigation. Use a fixed-width elapsed-time field so timer updates never remeasure the list. Render no persistent text composer.

- [ ] **Step 5: Render flat attachment rows in `DiaryCard`**

Preserve current card shape, header, photo wall, Markdown body, tags, and expansion behavior. Show at most two rows and a `查看全部 N 个附件` affordance.

- [ ] **Step 6: Run tests and commit**

Run: `./gradlew :app:testDebugUnitTest --tests '*DiaryCaptureUiTest' --tests '*DiaryFeedRegressionTest'`

Expected: PASS.

Commit: `feat: add compact diary capture controls`

---

### Task 6: Add Permissions, Video, And File Intake

**Files:**
- Create: `app/src/main/kotlin/com/dailysatori/ui/feature/diary/DiaryCaptureLauncher.kt`
- Create: `app/src/main/kotlin/com/dailysatori/core/file/DiaryAttachmentFileStore.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/diary/DiaryScreen.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/diary/DiaryEditorSheet.kt`
- Test: `app/src/test/kotlin/com/dailysatori/ui/feature/diary/DiaryCaptureLauncherTest.kt`

**Interfaces:**
- Produces copied app-owned attachment metadata only after picker/camera success.
- Consumes `DiaryAttachmentRepository.create` and the existing editor open/edit callbacks.

- [ ] **Step 1: Write failing cancellation and file-safety tests**

Assert cancelled camera/file operations create no diary, unsafe display names are normalized, source streams close, copy failures clean partial files, and confirmed media creates one diary plus one attachment.

- [ ] **Step 2: Run focused tests and confirm failure**

Run: `./gradlew :app:testDebugUnitTest --tests '*DiaryCaptureLauncherTest'`

Expected: FAIL because intake classes do not exist.

- [ ] **Step 3: Implement Activity Result launchers and permission flow**

Request microphone at voice start, request `POST_NOTIFICATIONS` on API 33+, use system document selection for files, and foreground camera/video capture. Do not create a draft until non-voice media is confirmed.

- [ ] **Step 4: Copy files safely and create attachments**

Store under per-diary directories, preserve MIME/display name separately, reject files larger than 500 MB before copy where size is known, enforce the same 500 MB limit while streaming when size is unknown, and open the existing editor after successful attachment creation.

- [ ] **Step 5: Run tests and commit**

Run: `./gradlew :app:testDebugUnitTest --tests '*DiaryCaptureLauncherTest'`

Expected: PASS.

Commit: `feat: attach videos and files to diaries`

---

### Task 7: Integration, Accessibility, And Device Verification

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/diary/DiaryCaptureMenu.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/diary/DiaryRecordingController.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/diary/DiaryAttachmentList.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/diary/DiaryScreen.kt`
- Test: `app/src/test/kotlin/com/dailysatori/ui/feature/diary/DiaryCaptureAccessibilityTest.kt`

**Interfaces:**
- Verifies the complete feature; introduces no new production boundary.

- [ ] **Step 1: Add accessibility and overlap regression tests**

Assert icon descriptions, 48 dp targets, no fixed-height text containers in attachment rows, stable recording controller dimensions, and reduced-motion fallback.

- [ ] **Step 2: Run all automated tests**

Run: `./gradlew :shared:allTests :app:testDebugUnitTest :app:compileDebugKotlin`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Install and exercise device flows**

Run: `./gradlew :app:installDebug` and `adb shell am start -n com.dailysatori/.MainActivity`.

Verify voice start, screen-off recording, app background/foreground, notification pause/resume/stop/open, transcript retry, file/video cancellation, deletion cleanup, and unchanged existing diary/photo entries.

- [ ] **Step 4: Capture visual checks**

Check compact and large font scales in dark mode. Confirm the menu/controller do not cover the last diary card, bottom navigation, or each other, and the timer does not shift layout.

- [ ] **Step 5: Run final verification and commit fixes**

Run: `./gradlew :shared:allTests :app:testDebugUnitTest :app:assembleDebug`

Expected: BUILD SUCCESSFUL.

Commit: `test: verify diary capture workflow`
