# Diary Recording Runtime Replacement Design

## Goal

Serialize process-scoped diary recording runtime replacement so an old runtime fully closes before a fresh runtime, recorder, mailbox, or recorder executor is created.

## Manager State

`DiaryRecordingRuntimeManager` is the only process coordination entry point. Under one lock it owns:

- the current runtime and its attached Android host identity;
- a pending replacement host while the current runtime is closing;
- a FIFO queue of pending commands and their deferred completions.

The runtime exposes one atomic open-runtime operation that checks teardown state, attaches the host, and submits the command under the same lifecycle lock. A concurrent shutdown therefore either follows an already submitted command or wins before attachment; it cannot split the check from attachment.

## Handoff

For an open current runtime, `attachAndSubmit(host, startId, command)` atomically reuses or replaces the host attachment and submits the command. Normal commands use Manager `submit` and the Manager-owned current attachment.

For a closing runtime, `attachAndSubmit` does not create a runtime and does not attach to the closing host router. It immediately asks the new Android host to enter foreground with a content-free `Starting` notification whose status is `正在准备录音`, then stores that host and queues Start. Pause, Resume, and Stop received in this window join the same FIFO queue.

Only the identity-matching old `onClosed` callback may clear that old current slot. If a pending host still exists, the callback creates one fresh runtime with a fresh recorder and executor, attaches the host, and replays pending commands in FIFO order. The old actor has already published its final state, closed its recorder boundary and mailbox, and invoked `onClosed`, so it cannot overwrite fresh runtime state.

If the pending host detaches before handoff, Manager drops the pending commands as ignored and does not create a hostless runtime.

## Persistence Failure

Explicit persistence Retry is accepted only while the runtime has not entered teardown and an Android host remains attached. Shutdown from `PersistenceFailed` closes the complete terminal boundary, including mailbox and recorder executor, and invokes `onClosed`; a detached service cannot retain a retryable process zombie.

## Dependency Ownership

The Koin singleton remains the Manager and the shared recording store remains the sole public `StateFlow`. `AndroidDiaryRecorder` moves inside the runtime factory so each runtime owns a distinct recorder. Strict serialized creation prevents old and new stores writers or recorder operations from overlapping.

## Tests

Executable JVM tests first cover:

1. closing-window Start enters placeholder foreground before old close and starts only on a fresh runtime after close;
2. old Idle cannot overwrite fresh Recording;
3. shutdown racing attach cannot split the open check from attach and submit;
4. pending Pause and Stop are replayed in FIFO order;
5. detached `PersistenceFailed` shutdown closes mailbox/executor, calls `onClosed`, and rejects Retry.

Existing notification and manifest source contracts continue to protect lock-screen visibility, public ongoing service notification behavior, actions, and microphone foreground-service declarations.
