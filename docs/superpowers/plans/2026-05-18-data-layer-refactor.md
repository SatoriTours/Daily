# Data Layer Refactor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Standardize repository timestamp and boolean handling, improve SQL count/search queries, and add a dedicated migration-safety stage before any schema changes ship.

**Architecture:** Refactor repository helpers before changing query behavior. Keep SQLDelight schema changes separate from Kotlin repository cleanup, and run migration-safety verification as its own sub-stage whenever `DailySatori.sq` changes.

**Tech Stack:** Kotlin Multiplatform, SQLDelight, SQLite migrations, Gradle, Android debug build.

---

## File Structure

- `shared/src/commonMain/kotlin/com/dailysatori/data/repository/`: repository wrappers that should share timestamp and boolean conversion helpers.
- `shared/src/commonMain/kotlin/com/dailysatori/data/repository/RepositoryValueHelpers.kt`: create for internal timestamp and boolean conversion helpers used by repositories.
- `shared/src/commonMain/sqldelight/com/dailysatori/shared/db/DailySatori.sq`: SQLDelight schema and count/search queries.
- `shared/src/commonMain/kotlin/com/dailysatori/config/Config.kt`: schema version source of truth when schema changes require migrations.
- `shared/src/commonMain/kotlin/com/dailysatori/service/migration/DatabaseMigration.kt`: migration runner and versioned migration methods.
- `shared/src/commonTest/`: add or extend tests for repository helper conversion and SQL query behavior where existing test infrastructure supports it.

## Guardrails

- Do not change database schema and repository cleanup in the same commit unless the migration-safety sub-stage is included in that commit.
- Do not modify `DailySatori.sq` without also reviewing `Config.kt` and `DatabaseMigration.kt`.
- Keep timestamp units unchanged; document whether each helper handles epoch milliseconds or seconds before replacing call sites.
- Keep boolean storage compatible with existing rows; preserve current `0`/`1` semantics.
- Prefer query improvements that preserve result ordering and pagination behavior unless a task explicitly changes those expectations.

## Task 1: Standardize Repository Timestamp And Boolean Helpers

**Files:**
- Create: `shared/src/commonMain/kotlin/com/dailysatori/data/repository/RepositoryValueHelpers.kt`
- Modify as needed: repository files under `shared/src/commonMain/kotlin/com/dailysatori/data/repository/`
- Test: add or extend shared tests for helper conversion.

- [ ] **Step 1: Inventory repeated conversions**

Run:

```bash
grep -R "currentTimeMillis\|toLong()\|== 1L\|!= 0L\|if (.*) 1L else 0L\|if (.*) 1 else 0" shared/src/commonMain/kotlin/com/dailysatori/data/repository --include='*.kt'
```

Expected: repeated timestamp creation and boolean-to-SQL conversion call sites are visible before helper extraction.

- [ ] **Step 2: Add focused helper tests**

Cover `true` to SQL truthy value, `false` to SQL falsy value, SQL truthy value to `true`, SQL zero to `false`, timestamp passthrough, and nullable timestamp fallback behavior that matches current repository code.

- [ ] **Step 3: Create repository helper file**

Create internal helpers with narrow names, such as `Boolean.toSqlLongFlag()`, `Long.toKotlinBooleanFlag()`, and `currentEpochMillis()`, only for patterns that appear in multiple repositories.

- [ ] **Step 4: Replace repeated repository conversions**

Update repositories incrementally, one file at a time. After each file, check that generated SQLDelight parameter types still match and no public repository method signatures changed.

- [ ] **Step 5: Verify repository helper cleanup**

Run:

```bash
./gradlew :shared:allTests --no-configuration-cache
./gradlew :app:compileDebugKotlin --no-configuration-cache
```

Expected: tests and compile finish with `BUILD SUCCESSFUL`.

## Task 2: Improve SQL Count Queries

**Files:**
- Modify: `shared/src/commonMain/sqldelight/com/dailysatori/shared/db/DailySatori.sq`
- Modify as needed: repository files under `shared/src/commonMain/kotlin/com/dailysatori/data/repository/`
- Test: add or extend SQL/repository tests for count behavior.

- [ ] **Step 1: Inventory count query patterns**

Run:

```bash
grep -n "COUNT\|count\|Count" shared/src/commonMain/sqldelight/com/dailysatori/shared/db/DailySatori.sq
```

Expected: count queries and callers that may duplicate list-query filters are identified.

- [ ] **Step 2: Add tests for current count expectations**

Cover empty-table count, filtered count matching filtered list size, deleted/archived visibility where applicable, and source-specific or tag-specific counts where applicable.

- [ ] **Step 3: Update count queries**

Improve count SQL so filters match the corresponding list/search queries exactly. Prefer named SQLDelight queries over Kotlin-side counting when the count can be computed by SQLite.

- [ ] **Step 4: Update repository callers**

Use improved generated count queries from repositories. Keep return types and public repository method names stable unless existing names are incorrect and all call sites are updated in this task.

- [ ] **Step 5: Verify count query behavior**

Run:

```bash
./gradlew :shared:generateCommonMainDailySatoriInterface --no-configuration-cache
./gradlew :shared:allTests --no-configuration-cache
./gradlew :app:compileDebugKotlin --no-configuration-cache
```

Expected: SQLDelight generation, tests, and compile finish with `BUILD SUCCESSFUL`.

## Task 3: Improve SQL Search Queries

**Files:**
- Modify: `shared/src/commonMain/sqldelight/com/dailysatori/shared/db/DailySatori.sq`
- Modify as needed: repository files under `shared/src/commonMain/kotlin/com/dailysatori/data/repository/`
- Test: add or extend SQL/repository tests for search behavior.

- [ ] **Step 1: Inventory search query patterns**

Run:

```bash
grep -n "LIKE\|MATCH\|search\|Search\|query" shared/src/commonMain/sqldelight/com/dailysatori/shared/db/DailySatori.sq
```

Expected: search filters, ordering, pagination, and duplicated Kotlin-side filtering are identified.

- [ ] **Step 2: Add tests for current search expectations**

Cover blank query handling, case-insensitive matching if current behavior supports it, title/content/source matching, pagination boundaries, and stable ordering.

- [ ] **Step 3: Update search SQL**

Move repeated Kotlin filtering into SQL only when the SQL query can preserve current matching semantics. Normalize wildcard binding in repositories instead of string-concatenating SQL.

- [ ] **Step 4: Update repository callers**

Route search methods through improved SQLDelight queries. Keep public repository APIs stable and preserve ordering used by UI screens.

- [ ] **Step 5: Verify search query behavior**

Run:

```bash
./gradlew :shared:generateCommonMainDailySatoriInterface --no-configuration-cache
./gradlew :shared:allTests --no-configuration-cache
./gradlew :app:compileDebugKotlin --no-configuration-cache
```

Expected: SQLDelight generation, tests, and compile finish with `BUILD SUCCESSFUL`.

## Task 4: Migration-Safety Sub-Stage

**Files:**
- Review: `shared/src/commonMain/sqldelight/com/dailysatori/shared/db/DailySatori.sq`
- Review or modify: `shared/src/commonMain/kotlin/com/dailysatori/config/Config.kt`
- Review or modify: `shared/src/commonMain/kotlin/com/dailysatori/service/migration/DatabaseMigration.kt`
- Test: add migration or repository compatibility tests where existing infrastructure supports them.

- [ ] **Step 1: Determine whether schema changed**

Run:

```bash
git diff -- shared/src/commonMain/sqldelight/com/dailysatori/shared/db/DailySatori.sq
```

Expected: if tables, columns, indexes, or query result shapes changed, migration review is required before merging.

- [ ] **Step 2: Apply schema version rule when needed**

If persisted schema changed, increment `currentSchemaVersion` in `Config.kt`. If only named queries changed and the underlying schema is unchanged, document that no version bump is needed in the commit message.

- [ ] **Step 3: Add versioned migration when needed**

If persisted schema changed, add `if (currentVersion < N) migrateV(N-1)ToV(N)()` in `DatabaseMigration.runMigrations()` and implement a private migration method using `CREATE TABLE IF NOT EXISTS`, `CREATE INDEX IF NOT EXISTS`, or `ALTER TABLE ... ADD COLUMN` as appropriate.

- [ ] **Step 4: Make migration failure handling consistent**

Wrap each migration operation in `try/catch`, log failures through the existing migration logger, and do not interrupt later safe migration steps because one idempotent operation already exists.

- [ ] **Step 5: Verify migration safety**

Run:

```bash
./gradlew :shared:generateCommonMainDailySatoriInterface --no-configuration-cache
./gradlew :app:compileDebugKotlin --no-configuration-cache
JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:installDebug
adb shell am start -n com.dailysatori/.MainActivity
```

Expected: SQLDelight generation and compile finish with `BUILD SUCCESSFUL`; upgraded app starts without database migration crash.

## Verification

- [ ] **Run after each data-layer task**

Run:

```bash
./gradlew :shared:generateCommonMainDailySatoriInterface --no-configuration-cache
./gradlew :shared:allTests --no-configuration-cache
./gradlew :app:compileDebugKotlin --no-configuration-cache
```

Expected: all commands finish with `BUILD SUCCESSFUL`.

- [ ] **Run after any persisted schema change**

Run:

```bash
JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:installDebug
adb shell am start -n com.dailysatori/.MainActivity
```

Expected: app launches on an existing install and a fresh install without database crashes.

- [ ] **Check patch hygiene**

Run:

```bash
git diff --check
```

Expected: no whitespace errors.
