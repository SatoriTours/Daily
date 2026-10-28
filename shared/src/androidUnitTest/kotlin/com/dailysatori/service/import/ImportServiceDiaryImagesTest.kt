package com.dailysatori.service.import

import android.content.ContextWrapper
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.data.repository.DiaryRepository
import com.dailysatori.platform.FileManager
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createTempDirectory
import kotlin.test.*

class ImportServiceDiaryImagesTest {
    @Test fun imageImportPreservesOriginalDiaryAndAttachmentTimesAndIds() = fixture { root,driver,db,files ->
        val zip=archive(root)
        val result=runBlocking { ImportService(db,driver,files).importFromZip(zip.path) }
        assertEquals(1,result.diaries)
        val diary=DiaryRepository(db,driver).getAllSync().single()
        assertEquals("原始历史正文",diary.content)
        assertEquals(1_600_000_000_000L,diary.created_at)
        assertEquals(1_600_000_001_000L,diary.updated_at)
        assertEquals("diary_images/old.png",diary.images)
        assertEquals("历史标签",diary.tags)
        assertEquals(listOf("1"),com.dailysatori.service.migration.DatabaseSchemaInventory.strings(driver,
            "SELECT COUNT(*) FROM diary_tag_history WHERE occurred_at IS NULL AND edit_revision=0"))
        assertNull(db.dailySatoriQueries.selectDiaryById(diary.id).executeAsOne().images)
        val attachment=db.dailySatoriQueries.selectAttachmentsForDiary(diary.id).executeAsOne()
        assertEquals(diary.id,attachment.diary_id)
        assertEquals(diary.created_at,attachment.created_at)
        assertEquals(diary.updated_at,attachment.updated_at)
    }

    @Test fun failureToInitializeImportedImagesRollsBackTheirDiaryToo() = fixture { root,driver,db,files ->
        driver.execute(null,"CREATE TRIGGER reject_image BEFORE INSERT ON diary_attachment BEGIN SELECT RAISE(ABORT,'injected'); END",0)
        assertFails { runBlocking { ImportService(db,driver,files).importFromZip(archive(root).path) } }
        assertEquals(0L,db.dailySatoriQueries.diaryRootCount().executeAsOne())
        assertTrue(db.dailySatoriQueries.selectAllDiaryAttachments().executeAsList().isEmpty())
    }

    private fun archive(root:File):File = File(root,"legacy.zip").also { zip ->
        ZipOutputStream(zip.outputStream()).use { out ->
            out.putNextEntry(ZipEntry("diaries.json"))
            out.write("""[{"id":123,"content":"原始历史正文","images":"old/old.png","tags":"历史标签","created_at":1600000000000,"updated_at":1600000001000}]""".toByteArray())
            out.closeEntry()
        }
    }

    private fun fixture(block:(File,JdbcSqliteDriver,DailySatoriDatabase,FileManager)->Unit) {
        val root=createTempDirectory().toFile()
        try {
            val unsafeClass=Class.forName("sun.misc.Unsafe")
            val unsafe=unsafeClass.getDeclaredField("theUnsafe").apply { isAccessible=true }.get(null)
            val context=unsafeClass.getMethod("allocateInstance",Class::class.java).invoke(unsafe,TestContext::class.java) as TestContext
            context.root=root
            val files=FileManager().apply { init(context) }
            JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
                DailySatoriDatabase.Schema.create(driver)
                block(root,driver,DailySatoriDatabase(driver),files)
            }
        } finally { root.deleteRecursively() }
    }

    private class TestContext:ContextWrapper(null) {
        lateinit var root:File
        override fun getFilesDir()=File(root,"files")
        override fun getCacheDir()=File(root,"cache")
    }
}
