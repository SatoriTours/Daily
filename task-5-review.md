# Task 5 Review — Non-blocking reminder AI submission and confirmation

## Spec verdict: FAIL

### Critical

1. **A batch can be durably created without any durable runnable task, leaving it permanently `PARSING`.**
   `ReminderViewModel.submitReminderAiBatch()` creates/reuses the batch, then enqueues the async-task row, then asks WorkManager to schedule it in three independent operations. A process death after the first operation, or an `AsyncTaskRepository.enqueue`/`AsyncTaskScheduler.enqueue` failure, leaves the active batch with no runnable task. Process recovery only discovers async-task rows, not orphan `PARSING` batches. Subsequent duplicate taps may repair it, but an ordinary one-time submission never reaches parsing or a terminal notification. This breaks durable submission and the required atomic batch/task dedupe. Make create/reuse plus task creation a single database transaction (and attach `task_id` there); additionally reconcile active batches lacking a live task during recovery. Treat WorkManager scheduling failure as a recoverable scheduling state and arrange a durable retry/wake-up instead of presenting it as parse failure.

2. **Confirmation treats reminder scheduling failure as success.**
   `ReminderAiBatchViewModel.confirmSelected()` ignores `coordinator.recompute()` failures with `runCatching`, then marks the item/batch confirmed. The formal reminder row can therefore be stored but never receive its platform schedule, with no UI error or subsequent retry path. Task 5 is required to reuse the existing scheduler, and this is precisely the failure condition the existing synchronous batch flow represents as `SCHEDULING_FAILED`. Preserve that outcome, do not mark the draft/batch confirmed until it has been scheduled (or persist a recoverable unscheduled state), and display an actionable error.

### Important

1. **Draft edits, selections, and removals are not persisted and are lost on process recreation.**
   The database only stores AI-produced `draft_json` and `confirmed`; `ReminderAiBatchViewModel` keeps all UI edits, checkbox state, per-item removal, and save status in `_state`. Recreating the ViewModel rebuilds the preview from the original JSON and selects every valid unconfirmed draft. A user who deselected/edited a draft can therefore return to materially different confirmation choices. Persist draft overrides and explicit selection/discard state, then restore them before rendering; validate restored data with the same confirmation validation.

2. **Formal reminder creation and batch confirmation are not transactional.**
   Each selected reminder is created in its own repository transaction, and `markConfirmed()` is called only later in a separate transaction. A kill between them leaves formal reminders created while every draft remains unconfirmed; simultaneous notification opens can also race `get(id)`/`createConfirmed`, producing a constraint failure in one UI. The deterministic reminder ID usually prevents a duplicate row, but does not give an atomic, recoverable confirmation result. Perform idempotent formal-reminder insert plus per-draft confirmation in one database transaction (or introduce a durable per-draft `SCHEDULED/CONFIRMED` state that is atomically claimed) and make repeat opens converge cleanly.

3. **Failed manual retry mutates the original failed batch instead of creating a new task/batch linked to it.**
   `restartFailed()` resets `PARSE_FAILED` in place, clears its notification marker, and `retryBatch()` submits a new task for the same batch ID. The design requires keeping the failed record for diagnosis and creating a new task linked to the old request. In-place reset also makes old failure notification taps show a processing/retried batch rather than its historical terminal result. Keep the old batch immutable and create a successor batch/task with an explicit source/parent ID.

4. **The required behavioral UI-flow cases are not tested.**
   `ReminderAiAsyncFlowTest` only reads source files and checks string presence. It does not exercise a ViewModel, repositories, WorkManager scheduling boundary, process recreation, confirmation validation/editing/selection, scheduler failures, retry semantics, notification-marker behavior, or duplicate confirmation. Replace it with fake-backed production-path tests covering the Task 5 acceptance cases; source-string checks cannot detect the defects above.

## Code-quality verdict: FAIL

The route and durable batch observer are a sound direction, and AI is no longer called by the editor ViewModel. However, Task 5 duplicates confirmation behavior rather than reusing the existing guarded batch-save flow, then weakens its scheduling-failure handling. Its mutable UI-only confirmation state also makes persistence semantics unclear and fragile.

### Minor

- The confirmation screen observes only stored profiles, while the editor composes built-in and custom profiles. This can make the profile selector inconsistent with the rest of reminder editing; use the same profile projection if built-in profiles are intended to be selectable here.
