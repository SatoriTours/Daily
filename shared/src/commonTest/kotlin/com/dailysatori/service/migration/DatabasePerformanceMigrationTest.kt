package com.dailysatori.service.migration

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.config.DatabaseConfig
import com.dailysatori.config.SettingKeys
import com.dailysatori.data.repository.ArticleRepository
import com.dailysatori.data.repository.AsyncTaskRepository
import com.dailysatori.data.repository.SettingRepository
import com.dailysatori.service.security.SecretValueCipher
import com.dailysatori.shared.db.DailySatoriDatabase
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DatabasePerformanceMigrationTest {
    @Test
    fun localArticleListsUseAnOrderedIndexInsteadOfSortingFullRows() = withDatabase { driver, db ->
        seedArticles(driver)
        assertEquals(listOf(2L, 1L), ArticleRepository(db).getLocalSync().map { it.id })
        assertLocalArticlePlans(driver)
    }

    @Test
    fun sourceSpecificTaskQueriesNarrowTheIndexByType() = withDatabase { driver, _ ->
        assertTypedTaskPlans(driver)
    }

    @Test
    fun version27UpgradePreservesArticlesAndTasksAndCanBeRepeated() = withDatabase { driver, db ->
        driver.execute(null, "DROP INDEX IF EXISTS idx_article_local_created", 0)
        driver.execute(null, "DROP INDEX IF EXISTS idx_async_task_type_status_run_after", 0)
        seedArticles(driver)
        val tasks = AsyncTaskRepository(db)
        val taskId = tasks.enqueue("external_favorite_sync", "{\"sourceId\":7}")
        val settings = SettingRepository(db)
        val migration = DatabaseMigration(driver, settings)
        repeat(2) {
            settings.upsert(SettingKeys.schemaVersion, "27")
            migration.runMigrations()
            assertLocalArticlePlans(driver)
            assertTypedTaskPlans(driver)
            assertEquals(listOf(2L, 1L), ArticleRepository(db).getLocalSync().map { it.id })
            assertEquals("remote body", ArticleRepository(db).getById(3L)?.original_markdown_content)
            assertEquals("{\"sourceId\":7}", tasks.getById(taskId)?.payload_json)
            assertEquals(taskId, tasks.runnableTasksByType("external_favorite_sync", Long.MAX_VALUE).single().id)
            assertEquals(DatabaseConfig.currentSchemaVersion.toString(), settings.get(SettingKeys.schemaVersion))
        }
    }

    private fun assertLocalArticlePlans(driver: JdbcSqliteDriver) {
        for (query in listOf("selectLocalArticles", "selectLocalArticlesPaginated", "selectArticlesByDateRange")) {
            val plan = queryPlan(driver, query)
            assertTrue(plan.any { it.contains("USING INDEX") }, "$query must use an ordered index: $plan")
            assertFalse(plan.any { it.contains("TEMP B-TREE") }, "$query must avoid sorting article bodies: $plan")
        }
    }

    private fun assertTypedTaskPlans(driver: JdbcSqliteDriver) {
        for (query in listOf("selectRunnableAsyncTasksByType", "selectNextAsyncTaskRunAfterByType")) {
            val plan = queryPlan(driver, query)
            assertTrue(plan.any { it.contains("type=?") }, "$query must restrict the index scan by type: $plan")
        }
    }

    private fun queryPlan(driver: JdbcSqliteDriver, name: String): List<String> {
        val schema = File("src/commonMain/sqldelight/com/dailysatori/shared/db/DailySatori.sq").readText()
        val sql = Regex("(?m)^$name:\\n([\\s\\S]*?);").find(schema)!!.groupValues[1]
            .replace("LIMIT ? OFFSET ?", "LIMIT 5 OFFSET 0")
            .replace("created_at >= ? AND created_at <= ?", "created_at >= 0 AND created_at <= 100")
            .replace(":task_type", "'external_favorite_sync'")
            .replace(":runnable_at", "10000").replace(":task_limit", "1").replace(":after_ms", "10000")
        return driver.executeQuery(null, "EXPLAIN QUERY PLAN $sql", { cursor ->
            val details = mutableListOf<String>()
            while (cursor.next().value) details += requireNotNull(cursor.getString(3))
            QueryResult.Value(details)
        }, 0).value
    }

    private fun seedArticles(driver: JdbcSqliteDriver) {
        driver.execute(null, "INSERT INTO article(id,title,source_type,created_at,updated_at) VALUES (1,'older','local',10,10),(2,'newer','local',20,20)", 0)
        driver.execute(null, "INSERT INTO article(id,title,original_markdown_content,source_type,created_at,updated_at) VALUES (3,'remote','remote body','remote_news',30,30)", 0)
    }

    private fun withDatabase(block: (JdbcSqliteDriver, DailySatoriDatabase) -> Unit) {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            DailySatoriDatabase.Schema.create(driver)
            block(driver, DailySatoriDatabase(driver))
        }
    }

    private object PlainCipher : SecretValueCipher {
        override fun encrypt(value: String): String = value
        override fun decrypt(value: String): String = value
        override fun isEncrypted(value: String): Boolean = false
    }
}
