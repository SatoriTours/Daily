# Task 1 Report: Persist Real Diary IDs And Attachments

## Status

DONE_WITH_CONCERNS

## RED

Command:

```sh
./gradlew :shared:testDebugUnitTest --tests '*DiaryAttachmentSchemaTest' --tests '*DiaryRepositoryInsertTest'
```

Result: failed as expected before production implementation. `DiaryAttachmentSchemaTest` failed because the attachment schema and migration did not exist. `DiaryRepositoryInsertTest` failed because the returning diary insert API did not exist. Three tests ran and three failed.

## GREEN

Focused command:

```sh
./gradlew :shared:testDebugUnitTest --tests '*DiaryAttachmentSchemaTest' --tests '*DiaryRepositoryInsertTest'
```

Result: passed after implementation. Three focused tests completed successfully.

Shared command:

```sh
./gradlew :shared:testDebugUnitTest
```

Result: passed. Gradle reported `BUILD SUCCESSFUL` with 18 actionable tasks.

## Modified Files

- `shared/src/commonMain/sqldelight/com/dailysatori/shared/db/DailySatori.sq`
- `shared/src/commonMain/kotlin/com/dailysatori/config/Config.kt`
- `shared/src/commonMain/kotlin/com/dailysatori/service/migration/DatabaseMigration.kt`
- `shared/src/commonMain/kotlin/com/dailysatori/data/repository/DiaryRepository.kt`
- `shared/src/commonMain/kotlin/com/dailysatori/data/repository/DiaryAttachmentRepository.kt`
- `shared/src/commonTest/kotlin/com/dailysatori/data/repository/DiaryAttachmentSchemaTest.kt`
- `shared/src/commonTest/kotlin/com/dailysatori/data/repository/DiaryRepositoryInsertTest.kt`
- `shared/src/commonTest/kotlin/com/dailysatori/data/repository/McpServerRepositoryPresetApiTest.kt`
- `shared/src/commonTest/kotlin/com/dailysatori/data/repository/RemoteArticleSyncSchemaTest.kt`
- `shared/src/commonTest/kotlin/com/dailysatori/service/asynctask/AsyncTaskSchemaSourceTest.kt`
- `shared/src/commonTest/kotlin/com/dailysatori/service/skill/SkillSchemaSourceTest.kt`

## Commit

`1d5820d1 feat: persist diary attachments and inserted ids`

## Remaining Concern

The task brief requested a `lastInsertRowId` SQLDelight query backed by `SELECT last_insert_rowid();`. SQLDelight 2.1.0's SQLite 3.35 dialect rejects that function during interface generation. The implementation uses `INSERT ... RETURNING id` instead, which returns the inserted row's ID atomically and compiles with the configured dialect. A later SQLDelight dialect upgrade or a raw-driver query could restore the exact requested query form if that compatibility contract is mandatory.

## Review Follow-up: Legacy SQLite Insert Compatibility

### RED

Command:

```sh
./gradlew :shared:testDebugUnitTest --tests '*DiaryAttachmentSchemaTest' --tests '*DiaryRepositoryInsertTest'
```

Result: failed as expected before the fix. Four focused assertions failed: canonical diary/attachment SQL still contained `RETURNING`, the diary create path did not query `last_insert_rowid()`, V20 caught migration failures, and attachment deletion used the unsafe app-data string prefix check.

### GREEN

Focused command:

```sh
./gradlew :shared:testDebugUnitTest --tests '*DiaryAttachmentSchemaTest' --tests '*DiaryRepositoryInsertTest' --tests '*FileManagerPathSafetyTest'
```

Result: passed. Coverage includes a real in-memory SQLite `DiaryRepository.create` call that returns a nonzero ID and reads its row back; the migration schema helper propagates a simulated failure; and canonical file containment rejects both sibling-prefix and `..` escape paths.

Shared command:

```sh
./gradlew :shared:testDebugUnitTest
```

Result: passed with `BUILD SUCCESSFUL` (18 actionable tasks).

### Fixes

- Removed all canonical `INSERT ... RETURNING` queries and changed diary/attachment ID retrieval to `INSERT` plus same-driver `SELECT last_insert_rowid()` inside a SQLDelight transaction. This is connection-local and does not use `MAX(id)`.
- Reduced the configured SQLDelight dialect from SQLite 3.35 to 3.24. Existing pre-Task-1 UPSERT syntax requires 3.24 for interface generation; the new diary capture insert and V20 migration SQL use constructs available on API 26 SQLite.
- Removed V20's exception swallowing so an attachment schema failure prevents `schema_version` from advancing and can retry on next startup.
- Replaced attachment deletion's prefix check with Android canonical-path, segment-boundary containment through `FileManager.isAppDataPath`.

## Review Follow-up: Diary Attachment DI Registration

### RED

Command:

```sh
./gradlew :shared:testDebugUnitTest --tests '*DiaryAttachmentRepositoryDiTest'
```

Result: failed as expected. The new source contract test failed at `DiaryAttachmentRepositoryDiTest.kt:12` because `SharedModule.kt` did not import or register `DiaryAttachmentRepository`.

### GREEN

Focused command:

```sh
./gradlew :shared:testDebugUnitTest --tests '*DiaryAttachmentRepositoryDiTest' --tests '*DiaryAttachmentSchemaTest' --tests '*DiaryRepositoryInsertTest'
```

Result: passed with `BUILD SUCCESSFUL` and all three focused test classes selected.

Shared command:

```sh
./gradlew :shared:testDebugUnitTest
```

Result: passed with `BUILD SUCCESSFUL` (18 actionable tasks).

### Fixes

- Restored `gradle/libs.versions.toml` to the pre-Task-1 `sqlite-3-35-dialect` dependency without changing existing UPSERT queries.
- Added `DiaryAttachmentRepository` to `SharedModule` with `get()` injection for `DailySatoriDatabase`, `SqlDriver`, and `FileManager`.
- Added `shared/src/commonTest/kotlin/com/dailysatori/di/DiaryAttachmentRepositoryDiTest.kt` as the source contract covering the registration and constructor arity.
