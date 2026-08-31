# Task 3 report

## RED / GREEN

- RED: `ReminderBatchUiStateTest` failed to compile because the batch state, status enum, and save function did not exist.
- GREEN: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest --tests '*ReminderBatchUiStateTest' --tests '*ReminderAiParseStateTest' --tests '*ReminderRouteStateTest'` passed.

## Evidence

- Partial failure leaves the failed item selected with `FAILED`; successful items retain their created reminder ID and become `SAVED`.
- A second `saveBatch` call skips `SAVED` items, proving idempotent UI snapshots.
- ViewModel snapshots selected IDs/items, saves sequentially through `createConfirmed` plus `recompute`, and ignores stale batch updates after a new interpretation.

## Ruling and risk

- Per coordinator ruling, `ReminderAiParseState.draft` and `requiresConfirmation` are read-only compatibility getters derived from its single batch; no parallel single-draft state exists. Task4 will remove their old UI use.
- Device installation was not run. A process interruption after repository creation but before the in-memory state update relies on the repository's fixed reminder ID to reject duplicate creation on retry.

## Recovery review

- Parse requests carry a monotonic token; prompt changes invalidate the token and stop the stale loading indicator, so late successes and failures cannot overwrite newer input.
- Batch saves atomically claim selected retryable items within one `_state.update`; the claim marks items `SAVING` before the coroutine starts, preventing a second tap from creating duplicates.
- Every save claim captures the parse state's monotonic batch generation. Late completion from an earlier interpretation is rejected even if a replacement batch reuses the same `batchId`.
- `CancellationException` is rethrown from parse and save paths; cancellation does not mark items failed or continue the sequential batch.
- RED/GREEN: the new stale-indicator regression failed before the state transition change and now passes with the focused batch/parse/route tests and `:app:compileDebugKotlin`.

## Final review remediation: linearized batch creation

### RED / GREEN

- RED: `./gradlew :app:testDebugUnitTest --tests '*ReminderBatchUiStateTest'` failed in `compileDebugUnitTestKotlin` because `ReminderBatchSaveGate` and `ReminderBatchOperationKey` did not exist.
- GREEN: the focused batch/parse tests pass after adding the production gate and its latch-controlled concurrency coverage.

### Remediation

- `ReminderBatchSaveGate` owns the active batch/generation key under one lock. `invalidate()` and `createIfCurrent()` use that same lock, so reset/new interpretation either invalidates before creation (skip) or follows a creation that has formally started.
- The ViewModel invalidates before prompt changes, resets, and new interpretations; accepted batch results activate their key. Repository creation only occurs inside `createIfCurrent`; cancellation remains rethrown.
- The gate tests exercise the real production class: invalidation-first performs zero creates; creation-first performs one and the invalidated old key cannot create again.

## Review remediation round 4: single-owner state/gate transitions

### RED / GREEN

- RED: `./gradlew :app:testDebugUnitTest --tests '*ReminderBatchUiStateTest'` failed in `compileDebugUnitTestKotlin` because the production `ReminderBatchStateTransitions` and `ReminderBatchSaveGate.serialized` APIs did not exist.
- GREEN: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest --tests '*ReminderBatchUiStateTest' --tests '*ReminderAiParseStateTest' --tests '*ReminderRouteStateTest'` passed.

### Remediation

- `ReminderBatchStateTransitions` now exclusively publishes prompt/reset/submit/completion, batch edits, save claims, and save results under `ReminderBatchSaveGate.serialized`; every publication uses an explicit `MutableStateFlow.compareAndSet` loop.
- Gate invalidation/activation happens only after or alongside the matching state publication in the same lock, never inside a retryable Flow update lambda. Remote interpretation and notification scheduling remain outside the lock; synchronous repository creation retains verification-to-create coverage under that lock.
- The production-transition regression forces reset to own the gate while a completion attempts to publish, then proves the completion is stale and the old batch/generation key cannot create.

## Final review remediation round 5: active claim and save-launch ownership

### RED / GREEN

- RED: the focused batch test failed to compile because the production `ReminderBatchSaveLaunchController` did not exist; the new parse-in-flight regression also exposed that `claimSelectedBatch` did not require the gate's active batch/generation key.
- GREEN: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest --tests '*ReminderBatchUiStateTest' --tests '*ReminderAiParseStateTest' --tests '*ReminderRouteStateTest'` passed.

### Remediation

- `claimSelectedBatch` now checks the exact active batch/generation key inside the gate's serialized section before publishing `SAVING`. Starting or failing a replacement parse leaves the visible old items unchanged and unclaimable.
- `ReminderBatchSaveLaunchController` atomically rejects a launch while its current job is active, before invoking the claim callback. It no longer cancels an in-progress batch to start a retry; prompt changes, resets, and new submissions retain explicit cancellation ownership.
- The production regressions prove parse-in-flight save clicks cannot claim the old batch, and a retryable failed item cannot be reclaimed while a later slow item is active; the slow item completes before the retry becomes launchable.
