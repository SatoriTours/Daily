package com.dailysatori.service.backup

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.config.DatabaseConfig
import com.dailysatori.config.SettingKeys
import com.dailysatori.data.repository.IdeaTopicRepository
import com.dailysatori.data.repository.SettingRepository
import com.dailysatori.data.repository.MemoryRepository
import com.dailysatori.service.assistant.AssistantTurnRepository
import com.dailysatori.service.assistant.AssistantPrivacyRepository
import com.dailysatori.service.ideatopic.*
import com.dailysatori.service.diagnostics.*
import com.dailysatori.platform.FileManager
import com.dailysatori.service.security.DatabaseKey
import com.dailysatori.service.security.SecretFieldProcessor
import com.dailysatori.service.security.SecretValueCipher
import com.dailysatori.shared.db.DailySatoriDatabase
import java.io.File
import java.nio.file.Files
import java.util.Base64
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlin.test.*

class BackupRoundTripTest {
    @Test fun physical44RetirementSurvivesRebackupAndRestoreAfterDeletingSourceDevice()=runBlocking {
        var time=Instant.parse("2026-10-30T00:00:00Z").toEpochMilliseconds()
        val clock=object:Clock { override fun now()=Instant.fromEpochMilliseconds(time.also { time+=60_000 }) }
        withVerificationFixture(clock) { files,source,oldBackup ->
            withDatabase(files.getDatabasePath()) { d ->
                val db=DailySatoriDatabase(d)
                com.dailysatori.data.repository.DiaryRepository(db,d).update(23,"完整日记\u0000tail","保留标签",null,
                    """["diary_images/photo.jpg","diary_images/photo.jpg","https://example.test/remote.png"]""")
                SettingRepository(db).upsert("diary_tag_state_v1_023","unknown\u0000tail")
                com.dailysatori.service.migration.DiaryLegacyFieldsFixture.install(d)
                SettingRepository(db).upsert("schema_version","44")
                d.execute(null,"UPDATE diary SET tags='retired-csv' WHERE id=23",0)
            }
            val original=File(files.getDatabasePath()).readBytes()
            assertTrue(source.backupNow(),source.lastMessage.value)
            assertContentEquals(original,File(files.getDatabasePath()).readBytes())
            val backup=oldBackup.parentFile!!.listFiles()!!.single { it.name!=oldBackup.name }
            val sourceRoot=File(files.getDatabasePath()).parentFile!!
            check(!backup.toPath().startsWith(sourceRoot.toPath()))
            assertTrue(sourceRoot.deleteRecursively())
            val destinationRoot=Files.createTempDirectory("retired-history-destination").toFile()
            try {
                val destination=DiskFiles(destinationRoot)
                val restored=service(destination,backup.parentFile!!,DeviceCipher("retired-other-device"),"unused",configured=false)
                assertTrue(restored.restoreFile(backup.path,"correct horse battery"),restored.lastMessage.value)
                withDatabase(destination.getDatabasePath()) { d ->
                    val db=DailySatoriDatabase(d)
                    val row=com.dailysatori.data.repository.DiaryRepository(db,d).getById(23)!!
                    assertEquals("完整日记\u0000tail",row.content);assertEquals("保留标签",row.tags)
                    assertEquals(listOf("${destination.getAppDataDir()}/diary_images/photo.jpg",
                        "${destination.getAppDataDir()}/diary_images/photo.jpg","https://example.test/remote.png"),
                        com.dailysatori.service.diary.parseDiaryImagePaths(row.images))
                    assertEquals(listOf(0L,1L,2L),db.dailySatoriQueries.selectAttachmentsForDiary(23).executeAsList().filter { it.kind=="image" }.map { it.position })
                    assertEquals(DatabaseConfig.currentSchemaVersion.toString(),SettingRepository(db).get("schema_version"))
                    assertEquals("unknown\u0000tail",SettingRepository(db).get("diary_tag_state_v1_023"))
                    val fields=com.dailysatori.service.migration.DatabaseSchemaInventory.strings(d,"SELECT name FROM pragma_table_info('diary')")
                    assertFalse("images" in fields);assertFalse("tags" in fields)
                }
                assertContentEquals("voice bytes".toByteArray(),File(destination.getAppDataDir(),"diary/audio/23/voice.m4a").readBytes())
                assertContentEquals("photo bytes".toByteArray(),File(destination.getAppDataDir(),"diary_images/photo.jpg").readBytes())
            } finally { destinationRoot.deleteRecursively() }
        }
    }
    @Test fun assistantOwnershipDeletedBodiesAndRestoreHoldsSurviveEncryptedCrossDeviceRestore()=runBlocking {
        var time=Instant.parse("2026-10-29T00:00:00Z").toEpochMilliseconds()
        val clock=object:Clock { override fun now()=Instant.fromEpochMilliseconds(time.also { time+=60_000 }) }
        withVerificationFixture(clock) { files,source,oldBackup ->
            var deletedId=0L;var pendingId=0L
            withDatabase(files.getDatabasePath()) { driver ->
                com.dailysatori.service.migration.AssistantLegacyFixture.seed(driver)
                val db=DailySatoriDatabase(driver)
                val turns=AssistantTurnRepository(driver)
                val deleted=turns.accept("owned","private-client","已删原文",4,"UTC");deletedId=deleted.id
                AssistantPrivacyRepository(driver).deleteConversationMessage(deleted.id)
                pendingId=turns.accept("owned","pending-client","完整输入\u0000tail",5,"UTC").id
                val settings=SettingRepository(db);settings.upsert("schema_version","43")
                com.dailysatori.service.migration.DatabaseMigration(driver,settings).runMigrations()
                MemoryRepository(db).insert("content","diary",998,"人工","人工正文")
                driver.execute(null,"INSERT INTO memory_entry(type,title,content,content_origin,created_at,updated_at) VALUES('assistant','采纳','采纳正文','accepted',6,6)",0)
            }
            assertTrue(source.backupNow(),source.lastMessage.value)
            val backup=oldBackup.parentFile!!.listFiles()!!.single { it.name!=oldBackup.name }
            val destinationRoot=Files.createTempDirectory("assistant-history-destination").toFile()
            try {
                val destination=DiskFiles(destinationRoot)
                val restored=service(destination,backup.parentFile!!,DeviceCipher("other-device"),"unused",configured=false)
                assertTrue(restored.restoreFile(backup.path,"correct horse battery"),restored.lastMessage.value)
                withDatabase(destination.getDatabasePath()) { driver ->
                    val db=DailySatoriDatabase(driver);val q=db.dailySatoriQueries
                    assertNull(q.selectChatById(deletedId).executeAsOneOrNull())
                    assertNotNull(AssistantTurnRepository(driver).get(deletedId))
                    assertTrue(AssistantPrivacyRepository(driver).isExcluded("chat",deletedId.toString()))
                    assertEquals("完整输入\u0000tail",q.selectChatById(pendingId).executeAsOne().content)
                    assertTrue(AssistantTurnRepository(driver).get(pendingId)!!.requiresResume)
                    val tasks=com.dailysatori.data.repository.AsyncTaskRepository(db)
                    assertEquals("restore_requires_resume",tasks.getById(AssistantTurnRepository(driver).get(pendingId)!!.taskId)!!.last_error_code)
                    val rows=q.selectAllMemory().executeAsList()
                    assertEquals(setOf("legacy_unknown","manual","accepted"),rows.map { it.content_origin }.toSet())
                    assertTrue(rows.any { it.content=="保留正文\u0000tail" && it.evidence=="未知证据" && it.source_version=="历史指纹\u0000tail" })
                    assertEquals(listOf(71L,72L),q.selectChatBySession("missing-session").executeAsList().map { it.id })
                    assertTrue(tasks.runnableTasks(Long.MAX_VALUE,100).none { it.type=="assistant_turn" })
                }
            } finally { destinationRoot.deleteRecursively() }
        }
    }

    @Test fun migratedIdeaReferencesOpaqueHistoryAndMessagesSurviveEncryptedCrossDeviceRestore()=runBlocking {
        var time=Instant.parse("2026-10-28T00:00:00Z").toEpochMilliseconds()
        val clock=object:Clock { override fun now()=Instant.fromEpochMilliseconds(time.also { time+=60_000 }) }
        withVerificationFixture(clock) { files,source,oldBackup ->
            withDatabase(files.getDatabasePath()) { driver ->
                com.dailysatori.service.migration.IdeaLegacyFixture.seed(driver)
                val db=DailySatoriDatabase(driver);val settings=SettingRepository(db)
                settings.upsert("schema_version","42")
                com.dailysatori.service.migration.DatabaseMigration(driver,settings).runMigrations()
                driver.execute(null,"""INSERT INTO idea_topic_event(id,topic_id,original_topic_id,kind,payload_json,created_at)
                    VALUES('confirmed-draft','legacy-idea','gone-owner','draft_applied',?,10)""",1) {
                    bindString(0,"""{"draftId":"old","before":{"title":"用户旧稿"},"after":{"title":"用户确认稿"},"unknown":true}""")
                }
                com.dailysatori.data.repository.IdeaTopicRepository(db).updateContent(
                    "legacy-idea",IdeaTopicContent(title="当前主题",conclusions="保留结论"),"content-edit",11)
            }
            assertTrue(source.backupNow(),source.lastMessage.value)
            val backup=oldBackup.parentFile!!.listFiles()!!.single { it.name!=oldBackup.name }
            val destinationRoot=Files.createTempDirectory("idea-history-destination").toFile()
            try {
                val destination=DiskFiles(destinationRoot)
                val restored=service(destination,backup.parentFile!!,DeviceCipher("other-device"),"unused",configured=false)
                assertTrue(restored.restoreFile(backup.path,"correct horse battery"),restored.lastMessage.value)
                withDatabase(destination.getDatabasePath()) { driver ->
                    val db=DailySatoriDatabase(driver);val q=db.dailySatoriQueries
                    assertEquals(com.dailysatori.service.migration.IdeaLegacyFixture.raw,
                        q.selectIdeaTopicSourceById("legacy-source").executeAsOne().snapshot_json)
                    val detail=com.dailysatori.data.repository.IdeaTopicRepository(db).getDetailSync("legacy-idea")!!
                    assertEquals("保留结论",detail.topic.content.conclusions)
                    val captured=Json.parseToJsonElement(detail.events.single { it.id=="legacy-captured" }.payload).toString()
                    assertTrue(captured.contains("retained"));assertTrue(captured.contains("unknown"))
                    assertEquals("unknown\u0000tail",detail.events.single { it.id=="legacy-opaque" }.payload)
                    assertTrue(detail.events.single { it.id=="confirmed-draft" }.payload.contains("用户确认稿"))
                    assertEquals("gone-owner",q.selectIdeaTopicSourceById("legacy-source").executeAsOne().original_topic_id)
                    assertEquals("完整回复\u0000tail",q.selectIdeaTopicMessagesBySession("legacy-session").executeAsOne().content)
                    assertEquals(1,q.selectIdeaEventSnapshots("legacy-captured").executeAsList().size)
                }
            } finally { destinationRoot.deleteRecursively() }
        }
    }

    @Test fun pendingReminderSchedulingAndImmutableUnknownPolicySurviveCrossDeviceRestore()=runBlocking {
        var time=Instant.parse("2026-10-27T00:00:00Z").toEpochMilliseconds()
        val clock=object:Clock { override fun now()=Instant.fromEpochMilliseconds(time.also { time+=60_000 }) }
        withVerificationFixture(clock) { files,source,oldBackup ->
            var taskId=0L; var batchId=""; var raw=""; var policyId=""
            val draft=com.dailysatori.service.reminder.ReminderDraft("untrusted-id","preserve reminder",
                kotlinx.datetime.LocalDate(2026,9,1),kotlinx.datetime.LocalDate(2027,9,1),kotlinx.datetime.LocalTime(10,0),
                timeZone=kotlinx.datetime.TimeZone.UTC)
            val profile=com.dailysatori.service.reminder.ReminderProfileSnapshot.gentle()
            withDatabase(files.getDatabasePath()) { driver ->
                val db=DailySatoriDatabase(driver); val q=db.dailySatoriQueries
                val batches=com.dailysatori.data.repository.ReminderAiBatchRepository(db)
                batchId=batches.enqueueOrReuse("original input",draft.timeZone,draft.startDate!!).id
                batches.markReady(batchId,listOf(com.dailysatori.service.reminder.ReminderAiBatchDraft(0,"source","AI original")))
                taskId=assertNotNull(batches.confirmDraft(batchId,0,0,draft,profile))
                raw=q.selectReminderById("$batchId:0").executeAsOne().profile_json.removeSuffix("}")+""","unknown":"keep\\u0000tail"}"""
                policyId=com.dailysatori.data.repository.ReminderPolicySnapshots(db).intern(raw)
                driver.execute(null,"UPDATE reminder SET policy_snapshot_id=? WHERE id=?",2) {
                    bindString(0,policyId);bindString(1,"$batchId:0")
                }
                assertTrue(com.dailysatori.data.repository.AsyncTaskRepository(db).claimForRun(taskId,"interrupted-owner",Long.MAX_VALUE))
            }
            assertTrue(source.backupNow(),source.lastMessage.value)
            val backup=oldBackup.parentFile!!.listFiles()!!.single { it.name!=oldBackup.name }
            val destinationRoot=Files.createTempDirectory("reminder-outbox-destination").toFile()
            try {
                val destination=DiskFiles(destinationRoot)
                val restored=service(destination,backup.parentFile!!,DeviceCipher("other-device"),"unused",configured=false)
                assertTrue(restored.restoreFile(backup.path,"correct horse battery"),restored.lastMessage.value)
                withDatabase(destination.getDatabasePath()) { driver ->
                    val db=DailySatoriDatabase(driver); val q=db.dailySatoriQueries
                    val batches=com.dailysatori.data.repository.ReminderAiBatchRepository(db)
                    val tasks=com.dailysatori.data.repository.AsyncTaskRepository(db)
                    val current=q.selectReminderById("$batchId:0").executeAsOne()
                    assertEquals(raw,current.profile_json); assertEquals(policyId,current.policy_snapshot_id)
                    assertEquals(taskId,batches.confirmDraft(batchId,0,0,draft,profile))
                    tasks.markRunningForRetryAfterProcessRestart(Clock.System.now().toEpochMilliseconds())
                    val scheduled=mutableSetOf<String>()
                    val handler=com.dailysatori.service.reminder.ReminderScheduleTaskHandler(batches,tasks) { scheduled+=it }
                    val runner=com.dailysatori.service.asynctask.AsyncTaskRunner(tasks,com.dailysatori.service.asynctask.AsyncTaskHandlerRegistry(listOf(handler)))
                    assertEquals(com.dailysatori.service.asynctask.AsyncTaskRunOutcome.Succeeded,runBlocking { runner.run(taskId) })
                    assertEquals(setOf("$batchId:0"),scheduled)
                    assertEquals("CONFIRMED",batches.getBatch(batchId)!!.drafts.single().confirmationState)
                    assertEquals(listOf("1"),com.dailysatori.service.migration.DatabaseSchemaInventory.strings(driver,
                        "SELECT COUNT(*) FROM reminder_event WHERE reminder_id='$batchId:0' AND event_type='confirmed'"))
                }
            } finally { destinationRoot.deleteRecursively() }
        }
    }

    @Test fun legacySuppressionReactivationHistoryAndMergeUndoSurviveEncryptedCrossDeviceRestore() = runBlocking {
        var time=Instant.parse("2026-10-26T00:00:00Z").toEpochMilliseconds()
        val clock=object:Clock { override fun now()=Instant.fromEpochMilliseconds(time.also { time+=60_000 }) }
        withVerificationFixture(clock) { files,source,oldBackup ->
            withDatabase(files.getDatabasePath()) { driver ->
                val db=DailySatoriDatabase(driver)
                val settings=SettingRepository(db)
                com.dailysatori.service.migration.DiaryLegacyFieldsFixture.install(driver)
                settings.upsert("schema_version","40")
                settings.upsert("diary_tag_state_v1_23",Json.encodeToString(com.dailysatori.service.diary.DiaryTagState(
                    suppressed=listOf("读书"),contentFingerprint=com.dailysatori.service.diary.diaryTagFingerprint("journal entry"),revision=4)))
                com.dailysatori.service.migration.DatabaseMigration(driver,settings).runMigrations()
                val tags=com.dailysatori.data.repository.DiaryTagRepository(db,
                    com.dailysatori.data.repository.DiaryThreadRepository(db,driver))
                assertEquals(listOf("读书"),tags.state(23).suppressed)
                com.dailysatori.data.repository.DiaryRepository(db,driver).update(23,"changed body",null,null,"diary_images/photo.jpg")
                assertTrue(tags.apply(assertNotNull(tags.prepare(23)),listOf("读书")))
                tags.merge("读书","阅读")
            }
            assertTrue(source.backupNow(),source.lastMessage.value)
            val backup=oldBackup.parentFile!!.listFiles()!!.single { it.name!=oldBackup.name }
            val destinationRoot=Files.createTempDirectory("tag-history-destination").toFile()
            try {
                val destination=DiskFiles(destinationRoot)
                val service=service(destination,backup.parentFile!!,DeviceCipher("other-device"),"unused",configured=false)
                assertTrue(service.restoreFile(backup.path,"correct horse battery"),service.lastMessage.value)
                withDatabase(destination.getDatabasePath()) { driver ->
                    val db=DailySatoriDatabase(driver)
                    val tags=com.dailysatori.data.repository.DiaryTagRepository(db,
                        com.dailysatori.data.repository.DiaryThreadRepository(db,driver))
                    assertEquals(listOf("阅读"),tags.tags(23))
                    assertTrue(tags.state(23).suppressed.isEmpty())
                    assertEquals(listOf("1"),com.dailysatori.service.migration.DatabaseSchemaInventory.strings(driver,
                        "SELECT COUNT(*) FROM diary_tag_history WHERE diary_id=23 AND decision='suppressed' AND occurred_at IS NULL AND source_fingerprint IS NOT NULL"))
                    assertTrue(tags.undoMerge())
                    assertEquals(listOf("读书"),tags.tags(23))
                    assertNull(SettingRepository(db).get("diary_tag_state_v1_23"))
                }
                assertTrue(File(destination.getCacheDir()).listFiles().orEmpty().isEmpty())
            } finally { destinationRoot.deleteRecursively() }
        }
    }

    @Test
    fun assistantPrivacyAndTurnsSurviveArchiveRestoreWithoutAutomaticallyReplayingWork() = runBlocking {
        withVerificationFixture { files, service, _ ->
            var pendingId = 0L
            var completedId = 0L
            var childTaskId = 0L
            var historyTaskId = 0L
            var summaryTaskId = 0L
            withDatabase(files.getDatabasePath()) { driver ->
                val turns = com.dailysatori.service.assistant.AssistantTurnRepository(driver)
                val tasks = com.dailysatori.data.repository.AsyncTaskRepository(DailySatoriDatabase(driver))
                val pending = turns.accept("assistant", "pending", "保留但不自动执行", 123L, "Asia/Shanghai")
                pendingId = pending.id
                tasks.claimForRun(pending.taskId, "before-backup", Long.MAX_VALUE)
                val completed = turns.accept("assistant", "done", "保存 https://example.com/restored", 124L, "UTC")
                completedId = completed.id
                tasks.claimForRun(completed.taskId, "owner", Long.MAX_VALUE)
                val business = com.dailysatori.service.assistant.RepositoryAssistantBusiness(driver) { _, _ -> }
                val actions = com.dailysatori.service.assistant.AssistantActionService(driver, business)
                childTaskId = actions.submit(completed, "owner", listOf(com.dailysatori.service.assistant.AssistantActionRequest(
                    "article", "保存 [input_link_0]", urlRef = "input_link_0"))).single().taskId!!
                assertTrue(turns.complete(completed.id, "owner", "已完成回答"))
                tasks.finishSuccess(completed.taskId, "")
                com.dailysatori.service.assistant.AssistantPrivacyRepository(driver).exclude("diary", "23")
                tasks.updateProgress(pending.taskId, 0, 0, "", "saved-model-context-must-not-be-restored")
                historyTaskId = tasks.enqueue("assistant_history_backfill", """{"cursor":0,"upperBound":999}""", "assistant_history_backfill")
                summaryTaskId = tasks.enqueue("assistant_summary", """{"sessionId":"assistant","upperId":999}""", "assistant_summary:assistant:999")
            }
            assertTrue(service.backupNow(), service.lastMessage.value)
            val archive = service.listBackups().maxBy { it.timestamp }
            assertTrue(service.restore(archive.name, "correct horse battery"), service.lastMessage.value)
            withDatabase(files.getDatabasePath()) { driver ->
                val turns = com.dailysatori.service.assistant.AssistantTurnRepository(driver)
                val tasks = com.dailysatori.data.repository.AsyncTaskRepository(DailySatoriDatabase(driver))
                val pending = turns.get(pendingId)!!
                assertTrue(pending.requiresResume)
                assertEquals(123L, pending.receivedAtMs)
                assertEquals("Asia/Shanghai", pending.timeZoneId)
                assertEquals("failed", tasks.getById(pending.taskId)!!.status)
                assertEquals("restore_requires_resume", tasks.getById(pending.taskId)!!.last_error_code)
                assertEquals("", tasks.getById(pending.taskId)!!.checkpoint_json)
                val settings = SettingRepository(DailySatoriDatabase(driver))
                assertEquals("true", settings.get("assistant.history.restore_hold"))
                for (id in listOf(historyTaskId, summaryTaskId)) {
                    assertEquals("failed", tasks.getById(id)!!.status)
                    assertEquals("restore_requires_resume", tasks.getById(id)!!.last_error_code)
                    assertTrue(tasks.runnableTasks(Long.MAX_VALUE, 100).none { it.id == id })
                }
                settings.upsert("assistant.cloud_consent", "true")
                assertNull(com.dailysatori.service.assistant.AssistantHistoryBackfill(driver).enqueue())
                assertEquals("true", settings.get("assistant.history.restore_hold"))
                assertTrue(tasks.runnableTasks(Long.MAX_VALUE, 100).none { it.type == "assistant_turn" })
                assertNotNull(turns.get(completedId)!!.responseMessageId)
                assertTrue(turns.get(completedId)!!.requiresResume)
                assertEquals("failed", tasks.getById(childTaskId)!!.status)
                assertEquals("restore_requires_resume", tasks.getById(childTaskId)!!.last_error_code)
                assertTrue(tasks.runnableTasks(Long.MAX_VALUE, 100).none { it.id == childTaskId })
                val actions = com.dailysatori.service.assistant.AssistantActionService(driver,
                    com.dailysatori.service.assistant.RepositoryAssistantBusiness(driver) { _, _ -> })
                assertEquals(childTaskId, actions.list(completedId).single().taskId)
                val privacy = com.dailysatori.service.assistant.AssistantPrivacyRepository(driver)
                assertTrue(privacy.isExcluded("diary", "23"))
                // A stale indexer recreating derived memory cannot resurrect forgotten material.
                driver.execute(null, "INSERT INTO memory_entry(type,source_type,source_id,title,content,tags,created_at,updated_at) VALUES('diary','diary',23,'旧归纳','禁止复现','',1,1)", 0)
            }
            withDatabase(files.getDatabasePath()) { driver ->
                val gateway = com.dailysatori.service.assistant.data.AssistantDataGateway(driver)
                assertFalse(runBlocking { gateway.search("禁止复现") }.toString().contains("禁止复现"))
                assertTrue(com.dailysatori.service.assistant.AssistantTurnRepository(driver).resume(pendingId))
                val turns = com.dailysatori.service.assistant.AssistantTurnRepository(driver)
                val tasks = com.dailysatori.data.repository.AsyncTaskRepository(DailySatoriDatabase(driver))
                assertFalse(turns.get(pendingId)!!.requiresResume)
                assertEquals("retrying", tasks.getById(turns.get(pendingId)!!.taskId)!!.status)
            }
        }
    }

    @Test
    fun backupFileNameUsesOnlyCurrentDeviceLocalDateAndTime() = runBlocking {
        val previous = java.util.TimeZone.getDefault()
        var time = Instant.parse("2026-05-21T23:05:00Z")
        val clock = object : Clock { override fun now() = time }
        try {
            listOf("Asia/Shanghai" to "2026-05-22-07-05-00",
                "Asia/Kolkata" to "2026-05-22-04-35-00",
                "America/New_York" to "2026-05-21-19-05-00",
                "UTC" to "2026-05-21-23-05-00").forEach { (zone, expected) ->
                java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone(zone))
                withVerificationFixture(clock) { _, service, _ ->
                    assertTrue(service.backupNow(), service.lastMessage.value)
                    assertEquals("Backup completed: daily_satori_backup_$expected.zip.enc", service.lastMessage.value)
                    val verification = service.verifyLatestBackup("correct horse battery")
                    assertEquals(BackupVerificationStatus.PASSED, verification.status)
                    assertEquals(time.toString(), verification.backupTime)
                }
            }
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("America/New_York"))
            withVerificationFixture(clock) { _, service, _ ->
                time = Instant.parse("2026-12-21T23:05:00Z")
                assertTrue(service.backupNow(), service.lastMessage.value)
                assertEquals("Backup completed: daily_satori_backup_2026-12-21-18-05-00.zip.enc", service.lastMessage.value)
            }
        } finally { java.util.TimeZone.setDefault(previous) }
    }

    @Test
    fun missingImagesAllowBackupVerificationAndRestoreWithoutDroppingReferences() = runBlocking {
        withVerificationFixture { files, service, _ ->
            withDatabase(files.getDatabasePath()) { driver ->
                com.dailysatori.data.repository.DiaryImages(DailySatoriDatabase(driver)).replace(23,"diary_images/photo.jpg,diary_images/missing.jpg")
                driver.execute(null, "INSERT INTO article(id,cover_image,created_at,updated_at) VALUES(71,'images/20241025_140752-2408',1,1)", 0)
                driver.execute(null, "INSERT INTO diary_attachment(diary_id,kind,local_path,position,created_at,updated_at) VALUES(23,'image','diary/images/missing-without-extension',(SELECT MAX(position)+1 FROM diary_attachment WHERE diary_id=23),1,1)", 0)
            }
            val source = File(files.getDatabasePath()).readBytes()
            assertTrue(service.backupNow(), service.lastMessage.value)
            val log = File(files.getAppDataDir(), "$BackupAttachmentLogDirectory/missing-attachments.log").readText()
            assertTrue(log.contains("expectedPath=diary_images/missing.jpg"))
            assertTrue(log.contains("expectedPath=images/20241025_140752-2408"))
            assertTrue(log.contains("expectedPath=diary/images/missing-without-extension"))
            assertEquals(BackupVerificationStatus.PASSED, service.verifyLatestBackup("correct horse battery").status)
            assertContentEquals(source, File(files.getDatabasePath()).readBytes())

            val latestBackup = service.listBackups().maxBy { it.timestamp }
            assertTrue(service.restore(latestBackup.name, "correct horse battery"), service.lastMessage.value)
            withDatabase(files.getDatabasePath()) { driver ->
                val queries = DailySatoriDatabase(driver).dailySatoriQueries
                assertEquals("journal entry", queries.selectAllDiaries().executeAsOne().content)
                assertEquals(listOf("diary_images/photo.jpg","diary_images/missing.jpg","diary/images/missing-without-extension"),
                    queries.selectAllDiaryAttachments().executeAsList().filter { it.kind=="image" }
                        .map { it.local_path.removePrefix(files.getAppDataDir()+"/") })
                assertEquals("images/20241025_140752-2408", queries.selectArticleById(71).executeAsOne().cover_image)
                assertTrue(queries.selectAllDiaryAttachments().executeAsList().any {
                    it.kind == "image" && it.local_path == "${files.getAppDataDir()}/diary/images/missing-without-extension"
                })
            }
            assertEquals("photo bytes", File(files.getAppDataDir(), "diary_images/photo.jpg").readText())
            assertEquals("voice bytes", File(files.getAppDataDir(), "diary/audio/23/voice.m4a").readText())
        }
    }

    @Test
    fun failedBackupReportsMissingAttachmentCountsAndPreservesDatabaseAndPreviousBackup() = runBlocking {
        withVerificationFixture { files, service, backup ->
            File(files.getAppDataDir(), "diary/audio/23/voice.m4a").delete()
            val database = File(files.getDatabasePath()).readBytes()
            val previousBackup = backup.readBytes()
            val events = mutableListOf<SafeDiagnosticEvent>()
            val previousDiagnostics = DiagnosticLog.diagnostics
            try {
                DiagnosticLog.diagnostics = Diagnostics(DiagnosticSink { events.add(it) })
                assertFalse(service.backupNow())
            } finally { DiagnosticLog.diagnostics = previousDiagnostics }

            assertContentEquals(database, File(files.getDatabasePath()).readBytes())
            assertContentEquals(previousBackup, backup.readBytes())
            assertEquals(1, backup.parentFile!!.listFiles()!!.size)
            assertTrue(File(files.getCacheDir()).listFiles().orEmpty().isEmpty())
            val missing = events.single { it.attributes["backupAttachmentSource"] == "diary_attachment.local_path" }
            assertEquals("1", missing.attributes["count"])
            assertFalse(Json.encodeToString(events).contains("voice.m4a"))
            val reference = service.missingAttachments.value.single()
            assertEquals(BackupAttachmentSource.DIARY_ATTACHMENT, reference.source)
            assertEquals(23L, reference.recordId)
            assertEquals("diary/audio/23/voice.m4a", reference.relativePath)
            assertEquals("${files.getAppDataDir()}/diary/audio/23/voice.m4a", reference.originalPath)

            files.writeFile(reference.originalPath, "recovered voice".toByteArray())
            assertTrue(service.backupNow(), service.lastMessage.value)
            assertTrue(service.missingAttachments.value.isEmpty())
        }
    }

    @Test
    fun freshBackupWithLegacyNullImagePlaceholdersCanBeCreatedAndVerifiedWithoutChangingLiveData() = runBlocking {
        withVerificationFixture { files, service, _ ->
            withDatabase(files.getDatabasePath()) { driver ->
                driver.execute(null, "INSERT INTO article(id,cover_image,created_at,updated_at) VALUES(1,'null',1,1)", 0)
                driver.execute(null, "INSERT INTO image(path,article_id,created_at,updated_at) VALUES('images/null',1,1,1)", 0)
                com.dailysatori.service.migration.DiaryLegacyFieldsFixture.install(driver)
                SettingRepository(DailySatoriDatabase(driver)).upsert("schema_version","39")
                driver.execute(null, "UPDATE diary SET images = 'diary_images/null,diary_images/photo.jpg' WHERE id = 23", 0)
            }
            val source = File(files.getDatabasePath()).readBytes()
            val voice = File(files.getAppDataDir(), "diary/audio/23/voice.m4a").readBytes()

            assertTrue(service.backupNow(), service.lastMessage.value)
            val result = service.verifyLatestBackup("correct horse battery")

            assertEquals(BackupVerificationStatus.PASSED, result.status)
            assertEquals(1L, result.summary["diary"])
            assertEquals(1L, result.summary["audio"])
            assertContentEquals(source, File(files.getDatabasePath()).readBytes())
            assertContentEquals(voice, File(files.getAppDataDir(), "diary/audio/23/voice.m4a").readBytes())
            assertFalse(files.pending.exists())
            assertTrue(File(files.getCacheDir()).listFiles().orEmpty().isEmpty())
        }
    }

    @Test
    fun verificationReadsPublishedEncryptedBackupAndPreservesAllSourceBytes() = runBlocking {
        withVerificationFixture { files, service, backup ->
            val database = File(files.getDatabasePath()).readBytes()
            val voice = File(files.getAppDataDir(), "diary/audio/23/voice.m4a").readBytes()
            val originalArchive = backup.readBytes()
            val result = service.verifyLatestBackup("correct horse battery")
            assertEquals(BackupVerificationStatus.PASSED, result.status)
            assertEquals(1L, result.summary["diary"])
            assertEquals(1L, result.summary["bookkeeping_entry"])
            assertEquals(1L, result.summary["images"])
            assertEquals(1L, result.summary["audio"])
            assertEquals(1L, result.summary["life_archive"])
            assertContentEquals(database, File(files.getDatabasePath()).readBytes())
            assertContentEquals(voice, File(files.getAppDataDir(), "diary/audio/23/voice.m4a").readBytes())
            assertContentEquals(originalArchive, backup.readBytes())
            assertFalse(files.pending.exists())
            assertNull(files.restoreResult)
            assertTrue(File(files.getCacheDir()).listFiles().orEmpty().isEmpty())
        }
    }

    @Test
    fun orphanedReminderEventsAreRejectedBeforeRestoreChangesAnyLiveData() = runBlocking {
        withVerificationFixture { files, service, backup ->
            rewriteBackup(backup) { content ->
                withDatabase(content.resolve(DatabaseConfig.name).path) { driver ->
                    driver.execute(null, "PRAGMA foreign_keys=OFF", 0)
                    driver.execute(null, """INSERT INTO reminder_event(reminder_id,event_type,actual_at)
                        VALUES('nonexistent-reminder','completed',123)""", 0)
                }
            }
            assertRejectedRestorePreservesFiles(files, service, backup)
        }
    }

    @Test
    fun portableDescriptorDamageIsRejectedEvenWithAConsistentManifest() = runBlocking {
        for (mode in listOf("missing", "malformed", "oversized")) {
            withVerificationFixture { files, service, backup ->
                rewriteBackup(backup) { content ->
                    val key = content.resolve(DatabaseKeyBackupName)
                    when (mode) {
                        "missing" -> key.delete()
                        "malformed" -> key.writeText("{}")
                        else -> key.writeText("x".repeat(1025))
                    }
                }
                assertRejectedRestorePreservesFiles(files, service, backup)
            }
        }
    }

    @Test
    fun duplicateManifestEntriesAndUnregisteredFilesCannotBeRestored() = runBlocking {
        for (mode in listOf("duplicate", "unregistered")) {
            withVerificationFixture { files, service, backup ->
                rewriteBackup(backup, updateManifest = false) { content ->
                    if (mode == "unregistered") content.resolve("extra-secret.txt").writeText("not registered")
                    else {
                        val path = content.resolve(BackupManifestName)
                        val manifest = Json.decodeFromString<BackupManifest>(path.readText())
                        path.writeText(Json.encodeToString(manifest.copy(files = manifest.files + manifest.files.first())))
                    }
                }
                assertRejectedRestorePreservesFiles(files, service, backup)
            }
        }
    }

    private suspend fun assertRejectedRestorePreservesFiles(files: DiskFiles, service: BackupService, backup: File) {
        val database = File(files.getDatabasePath()).readBytes()
        val media = File(files.getAppDataDir()).walkTopDown().filter { it.isFile }.associate { it.path to it.readBytes() }
        val archive = backup.readBytes()
        assertFalse(service.restore(backup.name, "correct horse battery"))
        assertContentEquals(database, File(files.getDatabasePath()).readBytes())
        media.forEach { (path, bytes) -> assertContentEquals(bytes, File(path).readBytes()) }
        assertContentEquals(archive, backup.readBytes())
        assertFalse(files.pending.exists())
        assertNull(files.restoreResult)
        assertFalse(service.isBackingUp.value)
        assertTrue(File(files.getCacheDir()).listFiles().orEmpty().isEmpty())
    }

    @Test
    fun verificationRejectsTruncatedPublishedCiphertext() = runBlocking {
        withVerificationFixture { files, service, backup ->
            backup.writeBytes(backup.readBytes().dropLast(20).toByteArray())
            assertVerificationFailure(files, service, BackupVerificationStage.DECRYPTING)
        }
    }

    @Test
    fun verificationRejectsAuthenticatedButInvalidZip() = runBlocking {
        withVerificationFixture { files, service, backup ->
            val invalid = checkNotNull(backup.parentFile).resolve("not-a-zip").apply { writeText("invalid zip") }
            FileManager().encryptFile(invalid.path, backup.path, "correct horse battery")
            invalid.delete()
            assertVerificationFailure(files, service, BackupVerificationStage.EXTRACTING)
        }
    }

    @Test
    fun verificationRejectsModifiedPayloadWithoutMatchingManifest() = runBlocking {
        withVerificationFixture { files, service, backup ->
            rewriteBackup(backup, updateManifest = false) { it.resolve("diary_images/photo.jpg").writeText("changed") }
            assertVerificationFailure(files, service, BackupVerificationStage.FILES)
        }
    }

    @Test
    fun verificationRejectsCorruptDatabaseEvenWhenFileHashMatches() = runBlocking {
        withVerificationFixture { files, service, backup ->
            rewriteBackup(backup) { it.resolve(DatabaseConfig.name).writeText("not sqlite") }
            assertVerificationFailure(files, service, BackupVerificationStage.DATABASE)
        }
    }

    @Test
    fun verificationAcceptsMissingReferencedAudioWithAConsistentManifest() = runBlocking {
        withVerificationFixture { files, service, backup ->
            rewriteBackup(backup) { it.resolve("diary/audio/23/voice.m4a").delete() }
            assertEquals(BackupVerificationStatus.PASSED, service.verifyLatestBackup("correct horse battery").status)
            assertFalse(files.pending.exists())
        }
    }

    @Test
    fun legacyBackupWithMissingAudioRemainsUsableWithoutChangingSourceOrBackup() = runBlocking {
        withVerificationFixture { files, service, backup ->
            rewriteBackup(backup, updateManifest = false) { content ->
                content.resolve(BackupManifestName).delete()
                content.resolve(DatabaseKeyBackupName).delete()
                content.resolve("diary/audio/23/voice.m4a").delete()
            }
            val source = File(files.getDatabasePath()).readBytes()
            val originalArchive = backup.readBytes()
            val result = service.verifyLatestBackup("correct horse battery")
            assertEquals(BackupVerificationStatus.LIMITED, result.status)
            assertEquals(BackupVerificationStage.COMPLETE, result.stage)
            assertNull(result.issue)
            assertFalse(result.diagnosticLog.contains("voice.m4a"))
            assertContentEquals(source, File(files.getDatabasePath()).readBytes())
            assertContentEquals(originalArchive, backup.readBytes())
            assertFalse(files.pending.exists())
            assertNull(files.restoreResult)
            assertTrue(File(files.getCacheDir()).listFiles().orEmpty().isEmpty())
        }
    }

    @Test
    fun incompleteLiveSchemaCannotPublishABackupOrReplacePreviousArchives() = runBlocking {
        withVerificationFixture { files, service, backup ->
            val previous = backup.readBytes()
            withDatabase(files.getDatabasePath()) { it.execute(null, "DROP TABLE assistant_reference", 0) }
            val original = File(files.getDatabasePath()).readBytes()
            assertFalse(service.backupNow())
            assertContentEquals(previous, backup.readBytes())
            assertContentEquals(original, File(files.getDatabasePath()).readBytes())
            assertEquals(listOf(backup.name), backup.parentFile?.listFiles().orEmpty().map { it.name })
            assertFalse(service.isBackingUp.value)
            assertTrue(File(files.getCacheDir()).listFiles().orEmpty().isEmpty())
        }
    }

    @Test
    fun verificationRejectsDatabaseMissingCurrentApplicationTables() = runBlocking {
        withVerificationFixture { files, service, backup ->
            rewriteBackup(backup) { content -> withDatabase(content.resolve(DatabaseConfig.name).path) {
                it.execute(null, "DROP TABLE idea_topic_message", 0)
            } }
            assertVerificationFailure(files, service, BackupVerificationStage.PREPARING)
        }
    }

    @Test
    fun restorationRejectsMissingApplicationTablesWithoutChangingActiveData() = runBlocking {
        withVerificationFixture { files, service, backup ->
            val original = File(files.getDatabasePath()).readBytes()
            rewriteBackup(backup) { content ->
                withDatabase(File(content, DatabaseConfig.name).path) { driver -> driver.execute(null, "DROP TABLE phone_message", 0) }
            }
            assertFalse(service.restoreFile(backup.path, "correct horse battery"))
            assertContentEquals(original, File(files.getDatabasePath()).readBytes())
            assertFalse(files.pending.exists())
        }
    }

    @Test
    fun verificationRejectsMissingRequiredDatabaseColumns() = runBlocking {
        withVerificationFixture { files, service, backup ->
            rewriteBackup(backup) { content -> withDatabase(content.resolve(DatabaseConfig.name).path) {
                it.execute(null, "ALTER TABLE diary RENAME COLUMN mood TO lost_mood", 0)
            } }
            assertVerificationFailure(files, service, BackupVerificationStage.PREPARING)
        }
    }

    @Test
    fun verificationRejectsFutureDatabaseVersions() = runBlocking {
        withVerificationFixture { files, service, backup ->
            rewriteBackup(backup) { content -> withDatabase(content.resolve(DatabaseConfig.name).path) {
                it.execute(null, "UPDATE setting SET value = '9999' WHERE key = 'schema_version'", 0)
            } }
            assertVerificationFailure(files, service, BackupVerificationStage.DATABASE)
        }
    }

    @Test
    fun legacyVerificationMigratesOnlyTheTemporaryCopyAndPreservesOldData() = runBlocking {
        withVerificationFixture { files, service, backup ->
            rewriteBackup(backup, updateManifest = false) { content ->
                content.resolve(BackupManifestName).delete()
                content.resolve(DatabaseKeyBackupName).delete()
                content.resolve(LifeArchiveBackupName).delete()
                withDatabase(content.resolve(DatabaseConfig.name).path) {
                    com.dailysatori.service.migration.IdeaLegacyFixture.install(it)
                    it.execute(null, "UPDATE setting SET value = '32' WHERE key = 'schema_version'", 0)
                    listOf("idea_topic_message", "idea_topic_session", "idea_topic_event", "idea_topic_source", "idea_topic").forEach { table ->
                        it.execute(null, "DROP TABLE $table", 0)
                    }
                }
            }
            val publishedBytes = backup.readBytes()
            val source = File(files.getDatabasePath()).readBytes()
            val result = service.verifyLatestBackup("correct horse battery")
            assertEquals(BackupVerificationStatus.LIMITED, result.status)
            assertEquals(1L, result.summary["diary"])
            assertEquals(1L, result.summary["bookkeeping_entry"])
            assertContentEquals(source, File(files.getDatabasePath()).readBytes())
            assertContentEquals(publishedBytes, backup.readBytes())
            assertFalse(files.pending.exists())
            assertTrue(File(files.getCacheDir()).listFiles().orEmpty().isEmpty())
        }
    }

    @Test
    fun verificationRejectsMalformedLifeArchiveEvenWithAConsistentManifest() = runBlocking {
        withVerificationFixture { files, service, backup ->
            rewriteBackup(backup) { it.resolve(LifeArchiveBackupName).writeText("not json") }
            assertVerificationFailure(files, service, BackupVerificationStage.LIFE_ARCHIVE)
        }
    }

    private suspend fun assertVerificationFailure(files: DiskFiles, service: BackupService, stage: BackupVerificationStage) {
        val source = File(files.getDatabasePath()).readBytes()
        val result = service.verifyLatestBackup("correct horse battery")
        assertEquals(BackupVerificationStatus.FAILED, result.status)
        assertEquals(stage, result.stage)
        assertContentEquals(source, File(files.getDatabasePath()).readBytes())
        assertFalse(files.pending.exists())
        assertNull(files.restoreResult)
        assertTrue(File(files.getCacheDir()).listFiles().orEmpty().isEmpty())
    }

    private suspend fun withVerificationFixture(clock: Clock = Clock.System, block: suspend (DiskFiles, BackupService, File) -> Unit) {
        val root = Files.createTempDirectory("verify-round-trip").toFile()
        try {
            val files = DiskFiles(root.resolve("source"))
            val cipher = DeviceCipher("source")
            seedDatabase(files, cipher)
            files.writeFile("${files.getAppDataDir()}/diary/audio/23/voice.m4a", "voice bytes".toByteArray())
            files.writeFile("${files.getAppDataDir()}/diary_images/photo.jpg", "photo bytes".toByteArray())
            val backups = root.resolve("backups").apply { mkdirs() }
            val service = service(files, backups, cipher, "{\"categories\":[],\"records\":[{\"title\":\"policy\"}]}", clock = clock)
            assertTrue(service.backupNow(), service.lastMessage.value)
            block(files, service, backups.listFiles()!!.single())
        } finally { root.deleteRecursively() }
    }

    private fun rewriteBackup(backup: File, updateManifest: Boolean = true, change: (File) -> Unit) {
        val temporary = Files.createTempDirectory("rewrite-backup").toFile()
        try {
            val manager = FileManager()
            val zip = temporary.resolve("backup.zip")
            val content = temporary.resolve("content")
            manager.decryptFile(backup.path, zip.path, "correct horse battery")
            manager.extractZip(zip.path, content.path)
            change(content)
            if (updateManifest) {
                val manifestFile = content.resolve(BackupManifestName)
                val manifest = Json.decodeFromString<BackupManifest>(manifestFile.readText())
                val entries = manager.listFilesRecursively(content.path).filter { it != manifestFile.path }.map {
                    BackupManifestFile(it.removePrefix("${content.path}/"), manager.fileSize(it), manager.sha256(it))
                }
                manifestFile.writeText(Json.encodeToString(manifest.copy(files = entries)))
            }
            manager.createZip(content.path, zip.path, manager.listFilesRecursively(content.path))
            manager.encryptFile(zip.path, backup.path, "correct horse battery")
        } finally { temporary.deleteRecursively() }
    }

    @Test
    fun encryptedArchiveRestoresDatabaseVoicePhotosAndSecretsToAnotherDevice() = runBlocking { roundTrip(false) }

    @Test
    fun newDeviceRestoresSelectedFileWithoutConfiguringDirectoryOrPassword() = runBlocking { roundTrip(true) }

    private suspend fun roundTrip(directFile: Boolean) {
        val root = Files.createTempDirectory("backup-round-trip").toFile()
        try {
            val backups = File(root, "backups").apply { mkdirs() }
            val sourceFiles = DiskFiles(File(root, "source"))
            val sourceCipher = DeviceCipher("source")
            seedDatabase(sourceFiles, sourceCipher)
            sourceFiles.writeFile("${sourceFiles.getAppDataDir()}/diary/audio/23/voice.m4a", "voice bytes".toByteArray())
            sourceFiles.writeFile("${sourceFiles.getAppDataDir()}/diary_images/photo.jpg", "photo bytes".toByteArray())
            val archive = "{\"categories\":[{\"id\":\"insurance\",\"name\":\"保险\"}],\"records\":[{\"title\":\"policy\"}]}"
            val source = service(sourceFiles, backups, sourceCipher, archive)
            seedPersonalRecords(sourceFiles)
            assertTrue(source.backupNow(), source.lastMessage.value)
            val name = backups.listFiles()!!.single().name
            assertFalse(backups.resolve(name).readBytes().decodeToString().contains("private-token"))

            val destinationFiles = DiskFiles(File(root, "destination"))
            val destinationCipher = DeviceCipher("destination")
            val destination = service(destinationFiles, backups, destinationCipher, "unused", configured = !directFile)
            destinationFiles.writeFile(destinationFiles.getDatabasePath(), "original data".toByteArray())
            if (directFile) {
                assertFalse(destination.restoreFile(backups.resolve(name).path, "wrong password"))
                assertEquals("original data", File(destinationFiles.getDatabasePath()).readText())
                assertTrue(destination.restoreFile(backups.resolve(name).path, "correct horse battery"), destination.lastMessage.value)
                assertEquals("恢复完成，请选择自动备份目录", destinationFiles.restoreResult)
            } else assertTrue(destination.restore(name, "correct horse battery"), destination.lastMessage.value)
            assertEquals("voice bytes", File(destinationFiles.getAppDataDir(), "diary/audio/23/voice.m4a").readText())
            assertEquals("photo bytes", File(destinationFiles.getAppDataDir(), "diary_images/photo.jpg").readText())
            assertEquals(archive, destinationCipher.decrypt(File(destinationFiles.life, "archive.json.enc").readText()))
            withDatabase(destinationFiles.getDatabasePath()) { driver ->
                val queries = DailySatoriDatabase(driver).dailySatoriQueries
                assertEquals("journal entry", queries.selectAllDiaries().executeAsList().single { it.id == 23L }.content)
                assertEquals("${destinationFiles.getAppDataDir()}/diary/audio/23/voice.m4a",
                    queries.selectAllDiaryAttachments().executeAsList().single { it.kind=="audio" }.local_path)
                assertEquals("private-token", destinationCipher.decrypt(queries.selectSettingByKey("weread_api_key").executeAsOne().value_!!))
                assertEquals("{\"amount\":12345,\"note\":\"original ledger\"}",
                    destinationCipher.decrypt(queries.selectBookkeepingEntries().executeAsOne().encrypted_payload))
                assertEquals(if (directFile) "" else backups.path, queries.selectSettingByKey("backup_directory").executeAsOne().value_)
                assertIdeaTopicData(DailySatoriDatabase(driver))
                assertPersonalRecords(driver)
            }
            assertEquals("secured:correct horse battery", destinationFiles.password.readText())
            assertTrue(File(destinationFiles.getCacheDir()).listFiles().orEmpty().isEmpty())
        } finally { root.deleteRecursively() }
    }

    private fun seedPersonalRecords(files: DiskFiles) = withDatabase(files.getDatabasePath()) { driver ->
        val q = DailySatoriDatabase(driver).dailySatoriQueries
        q.insertReminder("personal-reminder", "每月缴费，不能丢", "保留备注", "ACTIVE",
            "2026-05-01", "2027-05-01", "09:30", "every_day", "monthly", "Asia/Shanghai",
            com.dailysatori.data.repository.ReminderPolicySnapshots(DailySatoriDatabase(driver)).intern("""{"name":"original profile"}"""), 7, "2026-05-21", 2, 123, 124, null, 999999, 100, 200)
        q.setReminderDeadline(888888, "personal-reminder")
        driver.execute(null, """INSERT INTO reminder_event(reminder_id,event_type,scheduled_at,actual_at,metadata_json)
            VALUES('personal-reminder','dismissed',123,124,'{"cycle":"2026-05"}')""", 0)
        driver.execute(null, """INSERT INTO diary(id,content,mood,created_at,updated_at,parent_diary_id)
            VALUES(24,'续写原文：不是摘要','calm',2,3,23)""", 0)
        driver.execute(null, """UPDATE diary_attachment SET transcript='录音原始转写',transcript_status='ready',
            duration_ms=65432,display_name='我的录音.m4a' WHERE diary_id=23 AND kind='audio'""", 0)
        com.dailysatori.data.repository.DiaryTagStore(DailySatoriDatabase(driver)).initializeImported(24,listOf("工作","点子"))
        q.ensureChatSession("personal-chat",10)
        q.insertChat("personal-chat", "user", "我输入的原文，保留换行\n以及表情 🌱", null, null, 10)
        q.insertChat("personal-chat", "assistant", "完整助手回复", "source evidence", "tool steps", 11)
    }

    private fun assertPersonalRecords(driver: JdbcSqliteDriver) {
        val q = DailySatoriDatabase(driver).dailySatoriQueries
        val reminder = q.selectReminderById("personal-reminder").executeAsOne()
        assertEquals("每月缴费，不能丢", reminder.content)
        assertEquals("保留备注", reminder.notes)
        assertEquals("monthly", reminder.recurrence_rule)
        assertEquals("Asia/Shanghai", reminder.time_zone_id)
        assertEquals(7L, reminder.version)
        assertEquals(2L, reminder.dismissal_count)
        assertEquals(999999L, reminder.next_occurrence_at)
        assertEquals(888888L, reminder.deadline_at)
        assertEquals("""{"name":"original profile"}""", reminder.profile_json)
        val child = q.selectAllDiaries().executeAsList().single { it.id == 24L }
        assertEquals(23L, child.parent_diary_id)
        assertEquals("续写原文：不是摘要", child.content)
        assertEquals("工作,点子", child.tags)
        val attachment = q.selectAllDiaryAttachments().executeAsList().single { it.kind=="audio" }
        assertEquals("录音原始转写", attachment.transcript)
        assertEquals(65432L, attachment.duration_ms)
        val messages = q.selectChatBySession("personal-chat").executeAsList()
        assertEquals(listOf("user", "assistant"), messages.map { it.role })
        assertEquals("我输入的原文，保留换行\n以及表情 🌱", messages.first().content)
        assertEquals("完整助手回复", messages.last().content)
        assertEquals("source evidence", messages.last().search_results)
        assertEquals("tool steps", messages.last().steps)
        driver.executeQuery(null, "SELECT metadata_json FROM reminder_event WHERE reminder_id='personal-reminder'",
            { cursor -> assertTrue(cursor.next().value); assertEquals("""{"cycle":"2026-05"}""", cursor.getString(0))
                app.cash.sqldelight.db.QueryResult.Value(Unit) }, 0)
    }

    private fun seedDatabase(files: DiskFiles, cipher: SecretValueCipher) = withDatabase(files.getDatabasePath()) { driver ->
        DailySatoriDatabase.Schema.create(driver)
        driver.execute(null, "INSERT INTO diary(id,content,created_at,updated_at) VALUES(23,'journal entry',1,1)", 0)
        com.dailysatori.data.repository.DiaryImages(DailySatoriDatabase(driver)).replace(23,"diary_images/photo.jpg")
        driver.execute(null, "INSERT INTO diary_attachment(diary_id,kind,local_path,created_at,updated_at) VALUES(23,'audio',?,1,1)", 1) {
            bindString(0, "${files.getAppDataDir()}/diary/audio/23/voice.m4a")
        }
        driver.execute(null, "INSERT INTO setting(key,value,created_at,updated_at) VALUES('weread_api_key',?,1,1)", 1) {
            bindString(0, cipher.encrypt("private-token"))
        }
        driver.execute(null, "INSERT INTO setting(key,value,created_at,updated_at) VALUES('schema_version',?,1,1)", 1) {
            bindString(0, DatabaseConfig.currentSchemaVersion.toString())
        }
        driver.execute(null, "INSERT INTO bookkeeping_entry(id,encrypted_payload,created_at,updated_at) VALUES('ledger-1',?,1,1)", 1) {
            bindString(0, cipher.encrypt("{\"amount\":12345,\"note\":\"original ledger\"}"))
        }
        seedIdeaTopicData(DailySatoriDatabase(driver))
    }

    private fun seedIdeaTopicData(database: DailySatoriDatabase) {
        val q = database.dailySatoriQueries
        q.insertIdeaTopic("topic-main", "Main product", "Main description", "Main origins", "Main conclusions", "Main next action",
            "advancing", null, 7, 1, 10)
        q.insertIdeaTopic("topic-merged", "Merged product", "Merged description", "Merged origins", "Merged conclusions", "Merged next action",
            "researching", null, 2, 2, 11)
        val original = IdeaSourceSnapshot(IdeaSourceKey("diary", "23"), "Diary source", "first snapshot", originalCreatedAt = 1, originalRecordId = "23")
        val duplicate = original.copy(originalTitle = "Earlier capture", originalContent = "duplicate snapshot must survive")
        val opportunity = IdeaSourceSnapshot(IdeaSourceKey("news_opportunity", "opportunity-9"), "News source", "news snapshot",
            originalUrl = "https://example.test/news", analysisId = "analysis-9", analysisContent = "analysis snapshot", analysisVersion = "v2")
        q.insertIdeaTopicSource("source-main", "topic-main", "topic-main", "diary", "23", com.dailysatori.data.repository.IdeaSnapshots(database).intern(Json.encodeToString(original),createdAt=3), 3, 3, 3)
        q.insertIdeaTopicSource("source-duplicate", "topic-merged", "topic-merged", "diary", "23", com.dailysatori.data.repository.IdeaSnapshots(database).intern(Json.encodeToString(duplicate),createdAt=4), 4, 4, 4)
        q.insertIdeaTopicSource("source-news", "topic-merged", "topic-merged", "news_opportunity", "opportunity-9", com.dailysatori.data.repository.IdeaSnapshots(database).intern(Json.encodeToString(opportunity),createdAt=5), 5, 5, 5)
        q.insertIdeaTopicEvent("progress-1", "topic-merged", "topic-merged", "progress", "{\"text\":\"prototype completed\"}", 6)
        q.insertIdeaTopicSession("session-main", "topic-main", "topic-main", "First conversation", "Retained summary",
            "main-reply", "[\"main-user\",\"main-reply\"]", "ready", 7, 10)
        q.insertIdeaTopicSession("session-child", "topic-merged", "topic-merged", "Second conversation", "Partial summary",
            "child-user", "[\"child-user\"]", "pending", 8, 11)
        q.insertIdeaTopicMessage("main-user", "session-main", "user", "main question", "complete", null, 8)
        q.insertIdeaTopicMessage("main-reply", "session-main", "assistant", "main answer", "complete", null, 9)
        q.insertIdeaTopicMessage("child-user", "session-child", "user", "child question", "complete", null, 10)
        q.insertIdeaTopicMessage("child-reply", "session-child", "assistant", "unfinished streamed answer", "pending", null, 11)
        val repository = IdeaTopicRepository(database)
        repository.merge("topic-merged", "topic-main", "merge-1", 20)
        repository.insertDraft("draft-1", "topic-main", "topic-merged", 8,
            IdeaTopicContent("Unapplied title", "Unapplied description", "Draft origins", "Draft conclusions", "Draft next action"),
            listOf("source-news", "progress-1"), "session-child", 21)
    }

    private fun assertIdeaTopicData(database: DailySatoriDatabase) {
        val q = database.dailySatoriQueries
        val main = q.selectMainIdeaTopics().executeAsOne()
        assertEquals("topic-main", main.id)
        assertEquals("Main product", main.title)
        assertEquals("Main description", main.description)
        assertEquals("Main origins", main.provenance_summary)
        assertEquals("Main conclusions", main.conclusions)
        assertEquals("Main next action", main.next_action)
        assertEquals("advancing", main.status)
        assertEquals(8L, main.context_revision)
        assertEquals(2L, main.source_count)
        assertEquals("{\"text\":\"prototype completed\"}", main.latest_progress)
        val merged = q.selectIdeaTopicById("topic-merged").executeAsOne()
        assertEquals("topic-main", merged.merged_into_topic_id)
        assertEquals("Merged product", merged.title)
        assertEquals("Merged description", merged.description)
        assertEquals("researching", merged.status)
        assertEquals(listOf("topic-merged"), q.selectIdeaTopicIdsMergedInto("topic-main").executeAsList())
        assertIdeaTopicSourcesAndEvents(database)
        assertIdeaTopicConversations(database)
        val draft = IdeaTopicRepository(database).draftsByTopicSync("topic-main").single()
        assertEquals("draft-1", draft.id)
        assertEquals("pending", draft.state)
        assertEquals(8L, draft.baseRevision)
        val draftPayload = Json.decodeFromString<IdeaDraftEventPayload>(q.selectIdeaTopicEventById("draft-1").executeAsOne().payload_json)
        assertEquals("session-child", draftPayload.originSessionId)
        assertEquals("Unapplied title", draft.proposal.content.title)
        assertEquals("Unapplied description", draft.proposal.content.description)
        assertEquals("Draft conclusions", draft.proposal.content.conclusions)
        assertEquals("Draft next action", draft.proposal.content.nextAction)
        assertEquals(listOf("source-news", "progress-1"), draft.proposal.referenceIds)
    }

    private fun assertIdeaTopicSourcesAndEvents(database: DailySatoriDatabase) {
        val q = database.dailySatoriQueries
        val sources = q.selectIdeaTopicSourcesByTopic("topic-main").executeAsList()
        assertEquals(listOf("source-main", "source-news"), sources.map { it.id })
        assertEquals("first snapshot", Json.decodeFromString<IdeaSourceSnapshot>(sources.first().snapshot_json).originalContent)
        val news = sources.last()
        assertEquals("topic-merged", news.original_topic_id)
        val snapshot = Json.decodeFromString<IdeaSourceSnapshot>(news.snapshot_json)
        assertEquals("news snapshot", snapshot.originalContent)
        assertEquals("analysis snapshot", snapshot.analysisContent)
        assertEquals("analysis-9", snapshot.analysisId)
        assertEquals("v2", snapshot.analysisVersion)
        assertEquals("https://example.test/news", snapshot.originalUrl)
        val progress = q.selectIdeaTopicEventById("progress-1").executeAsOne()
        assertEquals("topic-main", progress.topic_id)
        assertEquals("topic-merged", progress.original_topic_id)
        assertEquals("prototype completed", Json.decodeFromString<IdeaProgressEventPayload>(progress.payload_json).text)
        val mergeRow=q.selectIdeaTopicEventById("merge-1").executeAsOne()
        assertEquals(2L,mergeRow.payload_version)
        assertEquals(1,q.selectIdeaEventSnapshots("merge-1").executeAsList().size)
        val merge = Json.decodeFromString<IdeaMergedEventPayload>(com.dailysatori.data.repository.IdeaTopicRepository(database)
            .getDetailSync("topic-main")!!.events.single { it.id=="merge-1" }.payload)
        assertEquals("topic-merged", merge.fromTopicId)
        assertEquals("topic-main", merge.intoTopicId)
        assertEquals("Merged conclusions", merge.fromContent.conclusions)
        val duplicate = merge.duplicateSourceSnapshots.single()
        assertEquals(IdeaSourceKey("diary", "23"), duplicate.key)
        assertEquals("Earlier capture", duplicate.originalTitle)
        assertEquals("duplicate snapshot must survive", duplicate.originalContent)
    }

    private fun assertIdeaTopicConversations(database: DailySatoriDatabase) {
        val q = database.dailySatoriQueries
        assertEquals(setOf("session-main", "session-child"), q.selectIdeaTopicSessionsByTopic("topic-main").executeAsList().map { it.id }.toSet())
        val session = q.selectIdeaTopicSessionById("session-main").executeAsOne()
        assertEquals("Retained summary", session.summary)
        assertEquals("main-reply", session.summary_through_message_id)
        assertEquals("[\"main-user\",\"main-reply\"]", session.summary_covered_message_ids)
        assertEquals("ready", session.summary_status)
        val child = q.selectIdeaTopicSessionById("session-child").executeAsOne()
        assertEquals("topic-main", child.topic_id)
        assertEquals("topic-merged", child.original_topic_id)
        assertEquals("Second conversation", child.title)
        assertEquals("Partial summary", child.summary)
        assertEquals("pending", child.summary_status)
        assertEquals(listOf("main question", "main answer"), q.selectIdeaTopicMessagesBySession("session-main").executeAsList().map { it.content })
        val messages = q.selectIdeaTopicMessagesBySession("session-child").executeAsList()
        assertEquals(listOf("child-user", "child-reply"), messages.map { it.id })
        assertEquals(listOf("child question", "unfinished streamed answer"), messages.map { it.content })
        assertEquals(listOf("user", "assistant"), messages.map { it.role })
        assertEquals(listOf("complete", "pending"), messages.map { it.status })
        assertEquals(listOf("child-reply"), q.selectIdeaTopicMessagesByStatus("pending").executeAsList().map { it.id })
    }

    private fun service(files: DiskFiles, backups: File, cipher: SecretValueCipher, archive: String,
        configured: Boolean = true, clock: Clock = Clock.System): BackupService {
        val values = if (configured) mutableMapOf(SettingKeys.backupDir to backups.path) else mutableMapOf()
        val settings = object : BackupSettings {
            override fun get(key: String) = values[key]
            override fun upsert(key: String, value: String) { values[key] = value }
        }
        val passwords = object : BackupPasswords {
            override fun get() = if (configured) "correct horse battery" else null
            override fun encryptedPassword(password: String) = "secured:$password".toByteArray()
        }
        // JDBC exercises archive/storage semantics only; native encryption has separate host probes.
        val secrets = object : BackupDatabaseAccess {
            override fun exportSnapshot(databasePath: String): DatabaseKey {
                files.createDatabaseSnapshot(databasePath)
                withDatabase(databasePath) { SecretFieldProcessor(it, cipher).decryptLegacyFields() }
                return DatabaseKey.fromHex("ab".repeat(32))
            }
            override fun prepareRestoredDatabase(databasePath: String, key: DatabaseKey?): DatabaseKey {
                withDatabase(databasePath) { BackupDatabaseData(it).prepareSecrets(cipher) }
                return key ?: DatabaseKey.fromHex("cd".repeat(32))
            }
            override fun wrapRestoredKey(key: DatabaseKey) = cipher.encrypt(key.portableJson()).toByteArray()
            override fun checkBackupDatabase(databasePath: String, key: DatabaseKey?) = withDatabase(databasePath) {
                BackupDatabaseData(it).checkBackupDatabase()
            }
            override fun backupDatabaseSummary(databasePath: String, key: DatabaseKey): Map<String, Long> {
                val reference = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
                return try {
                    DailySatoriDatabase.Schema.create(reference)
                    withDatabase(databasePath) { BackupDatabaseData(it).verifiedSummary(BackupDatabaseData(reference).schemaColumns()) }
                } finally { reference.close() }
            }
            override fun attachmentReferences(databasePath: String, appDataDir: String, key: DatabaseKey) = withDatabase(databasePath) {
                BackupDatabaseData(it).attachmentReferences(appDataDir)
            }
            override fun relocateRestoredData(databasePath: String, appDataDir: String, backupDirectory: String, key: DatabaseKey) =
                withDatabase(databasePath) { BackupDatabaseData(it).prepareRestore(appDataDir, backupDirectory) }
        }
        val life = object : LifeArchiveBackup {
            override suspend fun exportSnapshot() = archive
            override suspend fun prepareRestore(snapshot: String) = cipher.encrypt(snapshot).toByteArray()
        }
        return BackupService(files, settings, passwords, secrets, clock = clock, lifeArchive = life)
    }

    private class DeviceCipher(private val device: String) : SecretValueCipher {
        override fun encrypt(value: String) = "enc:v1:$device:" + Base64.getEncoder().encodeToString(value.toByteArray())
        override fun decrypt(value: String) = if (value.startsWith("enc:v1:$device:"))
            String(Base64.getDecoder().decode(value.removePrefix("enc:v1:$device:"))) else value
        override fun isEncrypted(value: String) = value.startsWith("enc:v1:")
    }

    private class DiskFiles(private val root: File) : BackupFiles {
        private val manager = FileManager()
        val life = root.resolve("life_archive")
        val password = root.resolve("backup_password.sec")
        var restoreResult: String? = null
        val pending get() = root.resolve("pending")
        private val installer get() = BackupRestoreTransaction(pending, File(getAppDataDir()), File(getDatabasePath()), life, password,
            databaseKey = root.resolve("database_key.sec"))
        init { root.mkdirs() }
        override fun getAppDataDir() = root.resolve("DailySatori").apply { mkdirs() }.path
        override fun getDatabasePath() = root.resolve(DatabaseConfig.name).path
        override fun getImagesDir() = "${getAppDataDir()}/images"
        override fun getDiaryImagesDir() = "${getAppDataDir()}/diary_images"
        override fun getCacheDir() = root.resolve("cache").apply { mkdirs() }.path
        override fun createDatabaseSnapshot(destination: String) = withDatabase(getDatabasePath()) {
            createSqliteBackupSnapshot(it, getDatabasePath(), destination, manager::copyFile)
        }
        override fun listFilesRecursively(path: String) = manager.listFilesRecursively(path)
        override fun readFile(path: String) = manager.readFile(path)
        override fun writeFile(path: String, bytes: ByteArray) = manager.writeFile(path, bytes)
        override fun fileSize(path: String) = manager.fileSize(path)
        override fun sha256(path: String) = manager.sha256(path)
        override fun stageRestore(directory: String) = installer.stage(File(directory))
        override fun deleteFile(path: String) = manager.deleteFile(path)
        override fun exists(path: String) = manager.exists(path)
        override fun listFiles(path: String) = manager.listFiles(path)
        override fun copyFile(src: String, dest: String) = manager.copyFile(src, dest)
        override fun moveFile(src: String, dest: String) = manager.moveFile(src, dest)
        override fun readFileFromUri(uri: String, destPath: String): Boolean {
            manager.copyFile(uri, destPath)
            return true
        }
        override fun createDirectory(path: String) = manager.createDirectory(path)
        override fun extractZip(zipPath: String, destDir: String) = manager.extractZip(zipPath, destDir)
        override fun createZip(sourceDir: String, zipPath: String, files: List<String>) = manager.createZip(sourceDir, zipPath, files)
        override fun encryptFile(inputPath: String, outputPath: String, password: String) = manager.encryptFile(inputPath, outputPath, password)
        override fun decryptFile(inputPath: String, outputPath: String, password: String) = manager.decryptFile(inputPath, outputPath, password)
        override fun listBackupFilesInDirectory(uri: String) = File(uri).listFiles().orEmpty().map { it.name }.filter { it.endsWith(".enc") }
        override fun writeFileToDirectory(uri: String, name: String, sourcePath: String): String {
            manager.copyFile(sourcePath, File(uri, name).path)
            return name
        }
        override fun readFileFromDirectory(uri: String, name: String, destPath: String): Boolean {
            manager.copyFile(File(uri, name).path, destPath)
            return true
        }
        override fun deleteFileFromDirectory(uri: String, name: String) = File(uri, name).delete()
        override fun restartApp() { restoreResult = installer.applyPending() }
    }
}

private fun <T> withDatabase(path: String, block: (JdbcSqliteDriver) -> T): T {
    val driver = JdbcSqliteDriver("jdbc:sqlite:$path")
    return try { block(driver) } finally { driver.close() }
}
