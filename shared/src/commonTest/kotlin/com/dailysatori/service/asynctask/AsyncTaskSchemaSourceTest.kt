package com.dailysatori.service.asynctask

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class AsyncTaskSchemaSourceTest {
    @Test
    fun schemaDefinesAsyncTaskTablesAndQueries() {
        val schema = File("src/commonMain/sqldelight/com/dailysatori/shared/db/DailySatori.sq").readText()

        assertTrue(schema.contains("CREATE TABLE async_task_batch"))
        assertTrue(schema.contains("CREATE TABLE async_task"))
        assertTrue(schema.contains("checkpoint_json TEXT NOT NULL DEFAULT ''"))
        assertTrue(schema.contains("lease_until_ms INTEGER"))
        assertTrue(schema.contains("selectAsyncTasksForTaskCenter:"))
        assertTrue(schema.contains("selectRunnableAsyncTasks:"))
        assertTrue(schema.contains("insertAsyncTask:"))
        assertTrue(schema.contains("updateAsyncTaskProgress:"))
        assertTrue(schema.contains("finishAsyncTask:"))
    }

    @Test
    fun claimQueryOnlyClaimsRunnableActiveTasks() {
        val schema = File("src/commonMain/sqldelight/com/dailysatori/shared/db/DailySatori.sq").readText()
        val claimQuery = schema.substringAfter("claimAsyncTaskForRun:").substringBefore("updateAsyncTaskProgress:")

        assertTrue(claimQuery.contains("WHERE id = ?"))
        assertTrue(claimQuery.contains("status IN ('queued', 'retrying')"))
        assertTrue(claimQuery.contains("(run_after_ms IS NULL OR run_after_ms <= ?)"))
    }

    @Test
    fun repositoryExposesAtomicBatchEnqueueApi() {
        val source = File("src/commonMain/kotlin/com/dailysatori/data/repository/AsyncTaskRepository.kt").readText()

        assertTrue(source.contains("data class AsyncTaskEnqueueRequest"))
        assertTrue(source.contains("data class AsyncTaskBatchEnqueueResult"))
        assertTrue(source.contains("fun enqueueBatch("))
        assertTrue(source.contains("q.transactionWithResult"))
        assertTrue(source.contains("batchId = batchId"))
    }

    @Test
    fun migrationDefinesAsyncTaskVersion() {
        val config = File("src/commonMain/kotlin/com/dailysatori/config/Config.kt").readText()
        val migration = File("src/commonMain/kotlin/com/dailysatori/service/migration/DatabaseMigration.kt").readText()

        assertTrue(config.contains("currentSchemaVersion = 14L"))
        assertTrue(migration.contains("if (currentVersion < 14)"))
        assertTrue(migration.contains("migrateV13ToV14()"))
        assertTrue(migration.contains("CREATE TABLE IF NOT EXISTS async_task_batch"))
        assertTrue(migration.contains("CREATE TABLE IF NOT EXISTS async_task"))
    }
}
