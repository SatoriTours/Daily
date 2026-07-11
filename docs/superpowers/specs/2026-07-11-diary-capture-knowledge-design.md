# Diary Capture And Knowledge Design

## Goal

Extend the existing diary experience with background audio recording, transcription, video capture, and file attachments. Every capture starts or updates a normal diary entry. The diary remains the primary object, and confirmed diary text and attachment-derived text can enter the personal knowledge base alongside articles and books.

The existing diary feed presentation is a fixed constraint. Month summaries, date groups, `DiaryCard`, photo walls, mood, tags, Markdown content, expansion, editing, filtering, and deletion retain their current visual and behavioral structure.

## Product Decisions

- Starting a voice recording immediately creates a persisted diary draft and begins recording.
- There is no separate recording inbox and no `全部 / 录音 / 待整理 / 已入库` navigation.
- Audio, video, images, and files belong to a diary as attachments.
- A voice diary appears in the list as the same `DiaryCard` used by every other diary.
- Transcription is editable diary content, not a separate knowledge item the user must manage.
- Knowledge extraction is asynchronous. Failure never removes the diary or its attachment.
- Original media remains linked to the diary after transcription and knowledge extraction.

## Refined UI

### Diary Feed

Keep the current `DiaryScreen` hierarchy exactly:

1. `AppScaffold` with title, search, and tag filter.
2. Existing month title, generated month summary, diary/photo counts, and latest update metadata.
3. Existing date header with day number, month, weekday, divider, and daily entry count.
4. Existing `DiaryCard` with time dot, mood, overflow menu, photo wall, Markdown body, tags, and expand action.

Do not introduce alternate card colors, left-edge status rails, type labels, or recording-specific list sections.

### Add Entry Control

- Keep the existing 48 dp touch target and render a 36 dp circular visual button inside it.
- Tapping opens one compact anchored action surface above the button.
- Order: `语音日记`, `文字日记`, `拍摄`, `添加文件`.
- Use the app theme surface, one outline, 12-14 dp corner radius, and 44 dp minimum row touch targets.
- `语音日记` receives only a quiet primary-tinted icon/background. It must not become a large primary CTA.
- Tapping outside, pressing Back, or selecting an action closes the menu.

### Attachment Presentation

Attachments render inside the existing card between the Markdown body and tag footer.

- Use one flat attachment row per item, not nested cards.
- Row height is content-driven with a 44 dp minimum touch target.
- Show type icon, ellipsized file name, and one metadata line.
- Metadata examples: `09:24 · 已转写`, `2.4 MB · 已加入知识库`, `18 秒 · 转写失败`.
- Audio rows provide play/pause and duration after recording ends.
- Video rows use a real thumbnail with a play affordance.
- Generic files show the file type, size, and processing status.
- More than two attachments collapse behind `查看全部 N 个附件` to preserve reading density.
- Status color is secondary: primary tint for completed, muted text for pending, error color only for actionable failures.

### Recording State

- Starting recording persists the diary first, then starts audio capture.
- A quiet status strip below the app bar says `正在录音 · 已创建日记` and exposes `打开`.
- A compact recording controller floats above bottom navigation only while recording.
- The controller contains elapsed time, a restrained waveform/activity indicator, and a 32 dp stop control within a 48 dp touch target.
- The controller does not contain a text field, title field, or duplicate explanatory copy.
- The in-progress diary uses the normal `DiaryCard`; its audio attachment metadata says `正在录音 · 后台继续`.
- When the app moves to the background, the foreground-service notification becomes the primary control and offers `暂停/继续`, `停止`, and `打开日记`.

### Motion And Accessibility

- Add-menu open/close uses a short fade plus 8-12 dp vertical movement.
- Recording status changes crossfade; the diary list must not jump when the timer updates.
- Respect reduced-motion settings by removing waveform animation and translation.
- All icon-only controls have content descriptions and at least 48 dp touch targets.
- Text uses theme typography and supports font scaling without fixed-height text containers.
- Dark-mode surfaces and state colors must meet readable contrast; color is never the only status signal.

## User Flows

### Voice Diary

1. User taps the small add button and selects `语音日记` while the app is visible.
2. The app requests microphone and notification permission when required.
3. The repository creates a diary draft and returns its real ID.
4. The recording service starts with that diary ID and creates an audio attachment.
5. UI observes the recording session and shows the status strip, normal diary card, and compact controller.
6. Backgrounding the app keeps recording through a microphone foreground service and persistent notification.
7. Stopping finalizes the file, queues transcription, and opens the existing diary editor with the transcript draft when appropriate.
8. Saving queues knowledge extraction using the real diary ID.

If draft creation fails, recording does not start. If recording startup fails, keep the empty draft only when the user has already added content; otherwise remove it and show an actionable error.

### Text, Video, And File

- `文字日记` opens the existing editor unchanged.
- `拍摄` creates a diary draft only after the user confirms captured media, attaches the video/image, and opens the editor.
- `添加文件` uses the system document picker, copies the selected file into app-owned storage, attaches it to a diary, and opens the editor.
- Cancelling camera or file picker creates no empty diary.

## Data Model

Add a normalized attachment table rather than extending the comma-separated `diary.images` field:

```text
diary_attachment
  id
  diary_id
  kind                 audio | video | image | file
  local_path
  display_name
  mime_type
  size_bytes
  duration_ms
  transcript
  transcript_status    none | queued | processing | completed | failed
  knowledge_status     none | queued | processing | completed | failed
  error_message
  created_at
  updated_at
```

- `diary_id` is required and attachments are deleted with their diary.
- Existing `diary.images` remains readable for backward compatibility in the first migration.
- New captured images use `diary_attachment`; a later migration may normalize old image paths.
- Repository insert operations must return the actual diary ID. Never enqueue memory extraction with `sourceId = 0`.
- Transcription and knowledge ingestion use the existing asynchronous task mechanism, extended with explicit attachment task types and retry-safe identifiers.

## Components And Boundaries

- `DiaryCaptureMenu`: presentation and action dispatch only.
- `DiaryAttachmentList`: renders attachment models inside `DiaryCard`.
- `RecordingStatusController`: observes session state and sends pause/resume/stop/open commands.
- `DiaryRecordingService`: owns recorder lifecycle, foreground notification, and file finalization.
- `DiaryAttachmentRepository`: attachment persistence and app-owned file management.
- `TranscriptionCoordinator`: queues transcription and writes transcript/status atomically.
- `DiaryKnowledgeCoordinator`: combines confirmed diary content and completed attachment text, then invokes memory extraction using the real diary ID.

`DiaryScreen` coordinates UI state but does not own recorder, file, transcription, or knowledge-processing logic.

## Android Platform Requirements

- Declare microphone permission and microphone foreground-service permissions/types required by the target SDK.
- Start recording from a user action while the activity is visible. Android 12+ restricts starting foreground services from the background.
- Promote the service immediately with a persistent notification and the microphone foreground-service type.
- Handle permission denial, notification permission denial, recorder initialization failure, storage failure, and service process recreation explicitly.
- Camera recording is user-initiated in the foreground. Background video capture is out of scope.

## Error Handling

- Audio file is the durable source of truth; transcription may be retried without recording again.
- Interrupted recordings finalize a playable partial file when possible and mark it as interrupted.
- Transcription/knowledge errors appear on the attachment row with a retry action in attachment detail.
- Unsupported or oversized files remain attached but skip text extraction with a clear status.
- Deleting a diary cancels pending attachment tasks and removes app-owned attachment files.
- Service restoration must reconcile session state with the attachment record before resuming UI timers.

## Scope

First implementation includes:

- Compact add-action menu.
- Background audio recording with notification controls.
- Audio attachment playback and transcription status.
- Video capture and generic file attachment.
- Attachment display in existing diary cards/editor.
- Asynchronous transcript and diary knowledge extraction.
- Database migration and compatibility with existing image diaries.

Out of scope:

- A recording inbox or attachment library.
- Background video capture.
- Full OCR/parser support for every file format.
- Speaker diarization or collaborative diaries.
- Redesigning the existing diary feed.

## Testing And Verification

- Database migration tests for existing diaries and attachment cascade behavior.
- Repository tests proving new diary inserts return a nonzero ID and tasks reference it.
- Recorder state-machine tests for start, pause, resume, stop, failure, and service restoration.
- Task tests for transcription success/failure/retry and idempotent knowledge extraction.
- Compose tests for menu dismissal, permission states, recording controller, unchanged month/date/card hierarchy, and attachment overflow.
- Device verification on the oldest supported Android version and Android 14+ for screen-off/background recording, notification controls, process interruption, permission denial, and low-storage failure.
- Visual verification at compact and large font scales in dark mode, checking that floating controls do not cover diary content or bottom navigation.
