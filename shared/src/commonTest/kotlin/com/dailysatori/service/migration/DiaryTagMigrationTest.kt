package com.dailysatori.service.migration

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.data.repository.SettingRepository
import com.dailysatori.service.diary.*
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlin.test.*

class DiaryTagMigrationTest {
    @Test fun preservesActiveOrderAndUnknownFingerprintHistoryWithoutTruncation() = fixture { driver,db ->
        val longName="很久以前保存的超过二十四个字符的完整私人自定义标签名称"
        db.dailySatoriQueries.insertDiary("body", null, 10, 20)
        driver.execute(null,"UPDATE diary SET tags=? WHERE id=1",1) { bindString(0,"$longName,读书") }
        val settings=SettingRepository(db)
        settings.upsert("diary_tag_state_v1_1",Json.encodeToString(DiaryTagState(
            automatic=listOf("读书"),suppressed=listOf("旅行"),contentFingerprint="old-fingerprint",revision=7)))
        settings.upsert("diary_tag_state_v1_not-an-id","keep unknown")
        settings.upsert("diary_tag_state_v1_01","keep noncanonical key")
        migrate(driver)
        assertEquals(listOf(longName,"读书"),strings(driver,"SELECT t.name FROM diary_tag_assignment a JOIN diary_tag_definition t ON t.id=a.tag_id WHERE a.state='active' ORDER BY a.position"))
        assertTrue(strings(driver,"SELECT tag_id FROM diary_tag_assignment WHERE state='suppressed'").isEmpty())
        assertEquals(listOf("old-fingerprint"),strings(driver,"SELECT source_fingerprint FROM diary_tag_history WHERE decision='suppressed'"))
        assertEquals(listOf("1"),strings(driver,"SELECT COUNT(*) FROM diary_tag_history WHERE decision='suppressed' AND source_revision IS NULL AND occurred_at IS NULL"))
        assertNull(settings.get("diary_tag_state_v1_1"))
        assertEquals("keep unknown",settings.get("diary_tag_state_v1_not-an-id"))
        assertEquals("keep noncanonical key",settings.get("diary_tag_state_v1_01"))
        val count=strings(driver,"SELECT COUNT(*) FROM diary_tag_history")
        migrate(driver)
        assertEquals(count,strings(driver,"SELECT COUNT(*) FROM diary_tag_history"))
        assertEquals(20L,db.dailySatoriQueries.selectDiaryById(1).executeAsOne().updated_at)
    }

    @Test fun malformedLegacyStateDoesNotPartiallyImportAnything() = fixture { driver,db ->
        db.dailySatoriQueries.insertDiary("body", null, 10, 20)
        driver.execute(null,"UPDATE diary SET tags='keep' WHERE id=1",0)
        SettingRepository(db).upsert("diary_tag_state_v1_1","{broken")
        assertFails { migrate(driver) }
        assertEquals("{broken",SettingRepository(db).get("diary_tag_state_v1_1"))
        assertEquals(listOf("keep"),strings(driver,"SELECT tags FROM diary WHERE id=1"))
    }

    @Test fun aliasCyclesAreRejectedInsteadOfGuessed() = fixture { driver,db ->
        SettingRepository(db).upsert("diary_tag_vocabulary_v1",Json.encodeToString(
            DiaryTagVocabulary(aliases=mapOf("one" to "two","two" to "one"))))
        assertFails { migrate(driver) }
        assertNotNull(SettingRepository(db).get("diary_tag_vocabulary_v1"))
    }

    @Test fun migratedMergeUndoIsPreservedButCannotOverwriteLaterTagEdits() {
        for(modify in listOf(false,true)) fixture { driver,db ->
            val q=db.dailySatoriQueries
            q.insertDiary("body", null, 10, 20)
            driver.execute(null,"UPDATE diary SET tags='焦虑' WHERE id=1",0)
            val fingerprint=diaryTagFingerprint("body")
            val before=DiaryTagVocabulary(listOf("压力"),emptyMap())
            val after=DiaryTagVocabulary(listOf("焦虑"),mapOf("压力" to "焦虑"))
            val old=DiaryTagState(contentFingerprint=fingerprint)
            val current=old.copy(revision=1)
            val payload=kotlinx.serialization.json.buildJsonObject {
                put("before",Json.encodeToJsonElement(before))
                put("after",Json.encodeToJsonElement(after))
                put("entries",kotlinx.serialization.json.buildJsonArray { add(kotlinx.serialization.json.buildJsonObject {
                    put("id",kotlinx.serialization.json.JsonPrimitive(1))
                    put("beforeTags",kotlinx.serialization.json.JsonPrimitive("压力"))
                    put("afterTags",kotlinx.serialization.json.JsonPrimitive("焦虑"))
                    put("beforeState",Json.encodeToJsonElement(old))
                    put("afterState",Json.encodeToJsonElement(current))
                }) })
            }.toString()
            val settings=SettingRepository(db)
            settings.upsert("diary_tag_state_v1_1",Json.encodeToString(current))
            settings.upsert("diary_tag_vocabulary_v1",Json.encodeToString(after))
            settings.upsert("diary_tag_merge_undo_v1",payload)
            migrate(driver)
            assertEquals(listOf(payload),strings(driver,"SELECT merge_undo_json FROM diary_tag_catalog_state"))
            val tags=com.dailysatori.data.repository.DiaryTagRepository(db,
                com.dailysatori.data.repository.DiaryThreadRepository(db,driver))
            if(modify) tags.edit(1,listOf("其他"))
            assertEquals(!modify,tags.undoMerge())
            assertEquals(listOf(if(modify) "其他" else "压力"),tags.tags(1))
        }
    }

    @Test fun readdedActiveTagAndEarlierSuppressionAreBothPreserved() = fixture { driver,db ->
        db.dailySatoriQueries.insertDiary("body", null, 10, 20)
        driver.execute(null,"UPDATE diary SET tags='读书' WHERE id=1",0)
        val raw=Json.encodeToString(DiaryTagState(suppressed=listOf("读书"),contentFingerprint=diaryTagFingerprint("body")))
        SettingRepository(db).upsert("diary_tag_state_v1_1",raw)
        migrate(driver)
        assertEquals(listOf("读书"),strings(driver,"SELECT tags FROM diary_record WHERE id=1"))
        assertEquals(listOf("active:manual"),strings(driver,"SELECT state||':'||origin FROM diary_tag_assignment"))
        assertEquals(listOf("active:manual","suppressed:automatic"),strings(driver,
            "SELECT decision||':'||origin FROM diary_tag_history ORDER BY decision"))
        assertEquals(listOf(diaryTagFingerprint("body"),diaryTagFingerprint("body")),strings(driver,
            "SELECT source_fingerprint FROM diary_tag_history ORDER BY decision"))
        assertNull(SettingRepository(db).get("diary_tag_state_v1_1"))
    }

    private fun migrate(driver:JdbcSqliteDriver) {
        DiaryTagMigration.migrate(driver)
        DiaryLegacyFieldsFixture.readProjection(driver)
    }
    private fun strings(driver:JdbcSqliteDriver,sql:String)=DatabaseSchemaInventory.strings(driver,sql)
    private fun fixture(block:(JdbcSqliteDriver,DailySatoriDatabase)->Unit) {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            DailySatoriDatabase.Schema.create(driver)
            DiaryLegacyFieldsFixture.install(driver)
            block(driver,DailySatoriDatabase(driver))
        }
    }
}
