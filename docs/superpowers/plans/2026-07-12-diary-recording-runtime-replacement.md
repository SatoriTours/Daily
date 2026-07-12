# Diary Recording Runtime Replacement Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace overlapping diary recording runtimes with a Manager-owned serialized handoff that preserves foreground-service timing and command order.

**Architecture:** `DiaryRecordingRuntimeManager` owns the current runtime/host and one pending host plus FIFO commands. `DiaryRecordingRuntime` atomically checks open state, attaches, and submits under one lifecycle lock; old `onClosed` creates the fresh runtime only after old recorder/mailbox teardown. Koin creates a new recorder per runtime while the existing singleton store remains the only state flow.

**Tech Stack:** Kotlin, Android Service, coroutines `Deferred`/`StateFlow`, JVM tests with `kotlinx-coroutines-test`, Gradle.

## Global Constraints

- Do not create a fresh runtime or recorder while the current runtime is closing.
- Do not attach a replacement host to a closing runtime.
- Enter placeholder microphone foreground immediately with copy `正在准备录音` during a closing-window Start.
- Replay pending commands in FIFO order only after identity-matching `onClosed`.
- Keep lock-screen notification visibility, title, actions, and manifest contracts unchanged.
- Retry persistence only while a host exists and teardown has not started.
- Use one final commit: `fix: serialize diary recording runtime replacement`.

---

### Task 1: Add Closing-Handoff RED Tests

**Files:**
- Modify: `app/src/test/kotlin/com/dailysatori/core/recording/DiaryRecordingRuntimeTest.kt`

**Interfaces:**
- Consumes: existing runtime, actor, fake host, fake persistence, and test dispatchers.
- Produces: executable expectations for `attachAndSubmit`, Manager `submit`, placeholder foreground, FIFO replay, and terminal close.

- [ ] **Step 1: Replace the overlapping-runtime regression with serialized handoff tests**

Add tests that hold the old recorder release with latches, call `attachAndSubmit` for a new Start, and assert the replacement host received `DiaryRecordingState.Starting` while runtime/recorder factory counts remain one. Queue Pause and Stop through Manager and release the old recorder; assert factory count becomes two only after old close, commands complete in queue order, and final state belongs to the new diary.

- [ ] **Step 2: Add attach/shutdown race and PersistenceFailed teardown tests**

Use a blocking host callback to hold atomic attach while another thread calls shutdown; verify attach+submit completes on the old open runtime before closing. For persistence failure, stop into `PersistenceFailed`, detach the only host, advance the shutdown delay, and assert executor shutdown plus one `onClosed`; verify Retry returns `Ignored` after teardown.

- [ ] **Step 3: Run focused tests and capture RED**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests '*DiaryRecordingRuntimeTest' --rerun-tasks
```

Expected: compilation failures for missing `attachAndSubmit`/Manager command API, followed by behavioral failures if an API shell is needed.

### Task 2: Implement Atomic Manager Handoff

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/core/recording/DiaryRecordingRuntimeManager.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/core/recording/DiaryRecordingRuntime.kt`

**Interfaces:**
- Consumes: `DiaryRecordingAndroidHost`, `DiaryRecordingCommand`, runtime `onClosed` callback.
- Produces: `attachAndSubmit(host, startId, command)`, `submit(command, startId)`, `detachHost(host)`, and runtime atomic open attachment/submission.

- [ ] **Step 1: Add runtime lifecycle atomicity**

Guard closing and open attach+submit with one runtime lifecycle lock. The atomic method returns `null` after teardown starts and otherwise returns the attachment plus Actor deferred from the same critical section.

- [ ] **Step 2: Make Manager own current and pending host state**

Replace external leases with Manager records for current runtime attachment. In the open path, reuse the same host attachment or atomically replace it and submit. In the closing path, call `host.enterForeground(DiaryRecordingState.Starting(...))`, retain the host, and append a `CompletableDeferred` pending command without creating a runtime.

- [ ] **Step 3: Replay only from identity-matching onClosed**

The old callback must return without mutation when `current !== closedRuntime`. Otherwise clear the old host record, create a fresh runtime only when a pending host exists, attach it, and submit queued commands in insertion order. Bridge each Actor deferred into its queued completion. If the pending host detached, complete queued work as `Ignored` and leave `current` null.

- [ ] **Step 4: Run runtime tests GREEN**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests '*DiaryRecordingRuntimeTest' --rerun-tasks
```

Expected: all runtime tests pass with no overlapping factory creation.

### Task 3: Close PersistenceFailed Teardown Completely

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/core/recording/DiaryRecordingActor.kt`
- Modify: `app/src/test/kotlin/com/dailysatori/core/recording/DiaryRecordingActorTest.kt`

**Interfaces:**
- Consumes: actor shutdown state, host attachment visibility, terminal boundary close.
- Produces: terminal `PersistenceFailed` shutdown and host-gated Retry.

- [ ] **Step 1: Add Actor RED assertions**

Add a host-presence switch to the actor fake. Assert Retry is ignored when no host or after Shutdown, and assert Shutdown from `PersistenceFailed` invokes `onClosed` rather than retaining an open mailbox.

- [ ] **Step 2: Implement host-gated Retry and terminal close**

Expose `hasAttachedHost()` on `DiaryRecordingActorHost`. `retryPersistence` returns `Ignored` when `shutdownRequested` or no host is attached. `shutdown` calls `closeTerminalBoundary()` when state is `PersistenceFailed`, and post-shutdown acceptance no longer permits Retry or Stop.

- [ ] **Step 3: Run actor and runtime tests GREEN**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests '*DiaryRecordingActorTest' --tests '*DiaryRecordingRuntimeTest' --rerun-tasks
```

Expected: all actor/runtime tests pass.

### Task 4: Move Service and DI to Manager Atomic APIs

**Files:**
- Modify: `app/src/main/kotlin/com/dailysatori/core/recording/DiaryRecordingService.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/core/di/ViewModelModule.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/test/kotlin/com/dailysatori/core/recording/DiaryRecordingServiceContractTest.kt`

**Interfaces:**
- Consumes: Manager atomic APIs and Android host callback contract.
- Produces: Service without lease/acquire sequencing and a fresh `AndroidDiaryRecorder` per runtime factory call.

- [ ] **Step 1: Update source-contract tests RED**

Assert Service contains `attachAndSubmit(androidHost, startId, ...)`, Manager-only `submit`, and identity detach, while no `DiaryRecordingRuntimeLease`, `attachHost`, or lease-based submit remains. Assert DI constructs `AndroidDiaryRecorder(androidContext)` inside the Manager runtime factory and has no `single<DiaryRecorder>` binding. Assert preparing notification copy exists.

- [ ] **Step 2: Refactor Service host ownership**

Keep one `androidHost` object on the Service. `onCreate` initializes notification only. ACTION_START calls Manager `attachAndSubmit`; Pause/Resume/Stop/Retry call Manager `submit`; `onDestroy` calls `detachHost(androidHost)` before closing Android resources.

- [ ] **Step 3: Change runtime factory recorder ownership**

Remove the recorder singleton binding. Instantiate `AndroidDiaryRecorder` inside each `DiaryRecordingRuntimeManager` factory invocation so every runtime receives an independent recorder paired with its independent executor.

- [ ] **Step 4: Preserve placeholder and lock-screen copy contract**

Set the Starting status resource to `正在准备录音`; retain public visibility, ongoing/category service flags, active title, status/elapsed content, Pause/Resume, Stop/Discard, and Open Diary actions.

- [ ] **Step 5: Run focused Service/manifest tests GREEN**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests '*DiaryRecording*Test' --rerun-tasks
```

Expected: all recording behavior and source-contract tests pass.

### Task 5: Verify, Report, and Commit

**Files:**
- Modify: `.superpowers/sdd/task-3-report.md`
- Include: design, plan, production, and test files from Tasks 1-4.

**Interfaces:**
- Consumes: completed implementation and test evidence.
- Produces: final report and requested commit.

- [ ] **Step 1: Run focused, full, compile, and diff checks**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests '*DiaryRecording*Test' --rerun-tasks
./gradlew :app:testDebugUnitTest :shared:testDebugUnitTest --rerun-tasks
./gradlew :app:compileDebugKotlin :shared:compileDebugKotlinAndroid --rerun-tasks
git diff --check
```

Expected: all Gradle tasks and diff check pass.

- [ ] **Step 2: Append RED/GREEN evidence and concerns**

Add a final Task3 report section containing failing-test evidence, implementation summary, exact verification commands/results, and remaining device-only risks.

- [ ] **Step 3: Review the final diff and commit only task files**

Run:

```bash
git diff --stat
git diff --check
git add .superpowers/sdd/task-3-report.md docs/superpowers/specs/2026-07-12-diary-recording-runtime-replacement-design.md docs/superpowers/plans/2026-07-12-diary-recording-runtime-replacement.md app/src/main/kotlin/com/dailysatori/core/di/ViewModelModule.kt app/src/main/kotlin/com/dailysatori/core/recording app/src/main/res/values/strings.xml app/src/test/kotlin/com/dailysatori/core/recording
git commit -m "fix: serialize diary recording runtime replacement"
```

Expected: one new commit with the requested subject; unrelated untracked mockups remain uncommitted.
