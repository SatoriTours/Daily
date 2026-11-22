package com.dailysatori.service.security

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import java.security.MessageDigest
import java.nio.ByteBuffer
import com.dailysatori.service.migration.DiaryImageMigration
import com.dailysatori.service.migration.DiaryTagMigration
import com.dailysatori.service.migration.OpportunityArchiveMigration
import com.dailysatori.service.opportunity.NewsOpportunityStore
import com.dailysatori.service.opportunity.OpportunityArchive
import com.dailysatori.shared.db.DailySatoriDatabase

/** Hashes typed, length-framed cell bytes; no private text is logged or serialized to disk. */
internal class MigrationContentSnapshot private constructor(
    private val tables: Map<String, TableDigest>,
    private val sequences: Map<String, Long>,
    private val userVersion: String,
    private val originalAttachmentMaxId: Long,
    private val expectedImages: List<DiaryImageMigration.ImageRow>,
    private val expectedTags:DiaryTagMigration.Plan?,
    private val remainingSettings:TableDigest?,
    private val expectedPolicies:com.dailysatori.service.migration.ReminderPolicyMigration.Plan?,
    private val expectedDrafts:com.dailysatori.service.migration.ReminderDraftMigration.Plan?,
    private val expectedIdeas:com.dailysatori.service.migration.IdeaSnapshotMigration.Plan?,
    private val expectedAssistant:com.dailysatori.service.migration.AssistantRelationsMigration.Plan?,
    private val retiredDiary:TableDigest?,
    private val expectedOpportunities: OpportunityArchive?,
) {
    fun requirePreserved(driver: SqlDriver, legacy: Boolean = false, migratedImages: Boolean = false, migratedTags:Boolean = false,migratedReminders:Boolean = false,migratedIdeas:Boolean = false,migratedAssistant:Boolean = false,migratedRetirement:Boolean = false, migratedOpportunities: Boolean = false) {
        val opportunityPlan = if (migratedOpportunities) checkNotNull(expectedOpportunities) else null
        opportunityPlan?.let {
            check(it == NewsOpportunityStore(DailySatoriDatabase(driver)).load()) { "迁移机会归档核对失败" }
        }
        val assistantPlan=expectedAssistant.takeIf { migratedAssistant }
        assistantPlan?.requireApplied(driver)
        val ideaPlan=expectedIdeas.takeIf { migratedIdeas }
        ideaPlan?.requireApplied(driver)
        val tagPlan=expectedTags.takeIf { migratedTags }
        tagPlan?.requireApplied(driver)
        if(migratedReminders) { expectedPolicies?.requireApplied(driver); expectedDrafts?.requireApplied(driver) }
        tables.filterKeys { (!legacy || it in personalTables) && (tagPlan==null || it !in DiaryTagMigration.columns) &&
            !(migratedReminders && expectedDrafts!=null && it in setOf("reminder_ai_draft","reminder_ai_draft_legacy")) &&
            !(migratedReminders && expectedPolicies!=null && it=="reminder_policy_snapshot") &&
            !(ideaPlan!=null && it in setOf("idea_topic_event","idea_source_snapshot","idea_event_snapshot")) &&
            !(assistantPlan!=null && it=="assistant_session") &&
            !(opportunityPlan!=null && it.startsWith("opportunity_")) }
            .forEach { (table, original) ->
                val expected=when {
                    table=="diary" && migratedRetirement -> checkNotNull(retiredDiary)
                    table=="setting" && tagPlan!=null -> checkNotNull(remainingSettings)
                    else -> original
                }
                check(expected == digest(driver, table, expected.columns,
                    originalAttachmentMaxId.takeIf { migratedImages && table == "diary_attachment" },
                    tagPlan?.consumed?.keys.orEmpty(),migratedReminders && expectedPolicies!=null,ideaPlan!=null)) { "迁移数据核对失败: $table" }
            }
        if(migratedImages) check(expectedImages == DiaryImageMigration.actualNewRows(driver,originalAttachmentMaxId)) {
            "迁移图片关联核对失败"
        }
        val found = sequences(driver)
        sequences.forEach { (table, sequence) ->
            check((found[table] ?: -1) >= sequence) { "迁移主键序列核对失败: $table" }
        }
        check(sqlStrings(driver, "PRAGMA user_version").single() == userVersion) { "迁移数据库标识核对失败" }
    }

    companion object {
        private val personalTables = setOf("diary", "diary_attachment", "reminder", "reminder_profile",
            "reminder_event", "reminder_policy_snapshot", "reminder_ai_batch", "reminder_ai_draft", "sms_reminder_source", "phone_message",
            "bookkeeping_entry", "chat_conversation", "assistant_session", "assistant_turn", "assistant_action",
            "assistant_reference", "assistant_memory_exclusion", "memory_entry", "idea_topic", "idea_topic_source",
            "idea_topic_event", "idea_topic_session", "idea_topic_message", "idea_source_snapshot", "idea_event_snapshot", "book", "book_viewpoint",
            "book_viewpoint_ai_session", "book_viewpoint_ai_message", "opportunity_item", "opportunity_source",
            "opportunity_membership", "opportunity_checkpoint", "opportunity_state")

        fun capture(driver: SqlDriver, expectedOpportunities: OpportunityArchive? = null): MigrationContentSnapshot {
            val tables = sqlStrings(driver, """SELECT name FROM sqlite_master WHERE type='table'
                AND name NOT LIKE 'sqlite_%' AND name <> 'android_metadata' ORDER BY name""")
                .filterNot { it.endsWith("_fts") || it.contains("_fts_") }
            val digests = tables.associateWith { table ->
                val columns = sqlStrings(driver, "SELECT name FROM pragma_table_info('${table.replace("'", "''")}')").sorted()
                digest(driver, table, columns)
            }
            val maxId = if ("diary_attachment" in tables) sqlStrings(driver,"SELECT COALESCE(MAX(id),0) FROM diary_attachment").single().toLong() else 0L
            val tagPlan=if(DiaryTagMigration.isApplied(driver)) null else DiaryTagMigration.plan(driver)
            val remaining=tagPlan?.let { digest(driver,"setting",digests.getValue("setting").columns,
                excludedSettings=it.consumed.keys) }
            return MigrationContentSnapshot(digests, sequences(driver), sqlStrings(driver, "PRAGMA user_version").single(),
                maxId, DiaryImageMigration.expectedRows(driver),tagPlan,remaining,
                com.dailysatori.service.migration.ReminderPolicyMigration.plan(driver),
                com.dailysatori.service.migration.ReminderDraftMigration.plan(driver),
                com.dailysatori.service.migration.IdeaSnapshotMigration.plan(driver),
                com.dailysatori.service.migration.AssistantRelationsMigration.plan(driver),
                digests["diary"]?.let { digest(driver,"diary",it.columns-setOf("tags","images")) }, expectedOpportunities)
        }

        private fun sequences(driver: SqlDriver): Map<String, Long> =
            driver.executeQuery(null, "SELECT name,seq FROM sqlite_sequence WHERE name <> 'setting'", { cursor ->
                val result = mutableMapOf<String, Long>()
                while (cursor.next().value) result[checkNotNull(cursor.getString(0))] = checkNotNull(cursor.getLong(1))
                QueryResult.Value(result)
            }, 0).value

        private fun digest(driver: SqlDriver, table: String, columns: List<String>, maximumId:Long? = null,
            excludedSettings:Set<String> = emptySet(),resolvePolicy:Boolean=false,resolveIdea:Boolean=false): TableDigest {
            val hash = MessageDigest.getInstance("SHA-256")
            val largeColumn = when (table) {
                "setting" -> "value"
                "opportunity_source" -> "payload_json"
                "opportunity_item" -> "analysis_json"
                else -> null
            }
            val projection = columns.joinToString {
                val field=when {
                    resolvePolicy && table=="reminder" && it=="profile_json" -> "(SELECT payload_json FROM reminder_policy_snapshot WHERE id=reminder.policy_snapshot_id)"
                    resolveIdea && table=="idea_topic_source" && it=="snapshot_json" -> "(SELECT payload_json FROM idea_source_snapshot WHERE id=idea_topic_source.snapshot_id)"
                    else -> quote(it)
                }
                val blob = "CAST($field AS BLOB)"
                val hex = "hex($blob)"
                val cell = if (it == largeColumn) "CASE WHEN length($blob)>32768 THEN '!'||length($blob) ELSE $hex END" else hex
                "typeof($field),$cell"
            }
            val excluded=excludedSettings.joinToString { "'"+it.replace("'","''")+"'" }
            val filter = if (table == "setting") " WHERE key IS NOT 'schema_version'"+
                if(excludedSettings.isEmpty()) "" else " AND (key IS NULL OR key NOT IN ($excluded))"
                else maximumId?.let { " WHERE rowid <= $it" }.orEmpty()
            val longValues = mutableListOf<Pair<Long, Long>>()
            val largeIndex = columns.indexOf(largeColumn) * 2 + 2
            val count = driver.executeQuery(null, "SELECT rowid, $projection FROM ${quote(table)}$filter ORDER BY rowid", { cursor ->
                var count = 0L
                while (cursor.next().value) {
                    for (index in 1..columns.size * 2) {
                        val value = checkNotNull(cursor.getString(index))
                        hash.frame(value)
                        if (index == largeIndex && value.startsWith('!')) {
                            longValues += checkNotNull(cursor.getLong(0)) to value.substring(1).toLong()
                        }
                    }
                    count++
                }
                QueryResult.Value(count)
            }, 0).value
            if (largeColumn != null) longValues.forEach { (rowId, size) ->
                hashLargeValue(driver, table, largeColumn, rowId, size, hash)
            }
            return TableDigest(columns, count, hash.digest().toList())
        }

        private fun hashLargeValue(driver: SqlDriver, table: String, column: String, rowId: Long, size: Long, hash: MessageDigest) {
            val sql = "SELECT hex(substr(CAST(${quote(column)} AS BLOB), ?, 32768)) FROM ${quote(table)} WHERE rowid = ?"
            var offset = 1L
            while (offset <= size) {
                val part = driver.executeQuery(null, sql, { cursor ->
                    check(cursor.next().value)
                    QueryResult.Value(checkNotNull(cursor.getString(0)))
                }, 2) { bindLong(0, offset); bindLong(1, rowId) }.value
                hash.frame(part)
                offset += 32_768
            }
        }

        private fun MessageDigest.frame(value: String) {
            val bytes = value.toByteArray(Charsets.UTF_8)
            update(ByteBuffer.allocate(8).putLong(bytes.size.toLong()).array())
            update(bytes)
        }

        private fun quote(value: String) = "\"${value.replace("\"", "\"\"")}\""
    }
    private data class TableDigest(val columns: List<String>, val count: Long, val digest: List<Byte>)
}
