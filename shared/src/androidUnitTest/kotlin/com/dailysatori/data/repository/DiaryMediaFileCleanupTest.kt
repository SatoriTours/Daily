package com.dailysatori.data.repository

import android.content.ContextWrapper
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.platform.FileManager
import com.dailysatori.shared.db.DailySatoriDatabase
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.*

class DiaryMediaFileCleanupTest {
    @Test fun sharedRelativeAndAbsoluteReferencesKeepTheFileUntilTheLastDiaryIsDeleted() = fixture { db,driver,files ->
        val file=File(files.getAppDataDir(),"diary_images/shared.png").apply { parentFile.mkdirs(); writeText("user bytes") }
        val diaries=DiaryRepository(db,driver,files)
        val a=runBlocking { diaries.create("a",images="diary_images/shared.png") }
        val b=runBlocking { diaries.create("b",images=file.path) }
        diaries.delete(a)
        assertEquals("user bytes",file.readText())
        diaries.delete(b)
        assertFalse(file.exists())
    }

    @Test fun removingTheOnlyRelativeAssociationDeletesOnlyThatFile() = fixture { db,driver,files ->
        val file=File(files.getAppDataDir(),"diary_images/only.png").apply { parentFile.mkdirs(); writeText("one") }
        val diaries=DiaryRepository(db,driver,files)
        val id=runBlocking { diaries.create("one",images="diary_images/only.png") }
        diaries.delete(id)
        assertFalse(file.exists())
    }

    @Test fun anAttachmentWithADirectoryPathCannotRecursivelyEraseUnreferencedUserFiles() = fixture { db,driver,files ->
        val directory=File(files.getAppDataDir(),"diary_images").apply { mkdirs() }
        val unrelated=File(directory,"keep.png").apply { writeText("keep user bytes") }
        val diaries=DiaryRepository(db,driver,files)
        val id=runBlocking { diaries.create("invalid historical path") }
        DiaryAttachmentRepository(db,driver,files).create(id,DiaryAttachmentDraft(DiaryAttachmentKind.file,directory.path))
        diaries.delete(id)
        assertEquals("keep user bytes",unrelated.readText())
    }

    @Test fun failedDeleteNeverUnlinksFilesAndArticleCoverPreventsCleanup() = fixture { db,driver,files ->
        val file=File(files.getAppDataDir(),"diary_images/keep.png").apply { parentFile.mkdirs(); writeText("keep") }
        val diaries=DiaryRepository(db,driver,files)
        val id=runBlocking { diaries.create("keep",images=file.path) }
        driver.execute(null,"CREATE TRIGGER reject_delete BEFORE DELETE ON diary BEGIN SELECT RAISE(ABORT,'injected'); END",0)
        assertFails { diaries.delete(id) }
        assertEquals("keep",file.readText())
        assertNotNull(diaries.getById(id))
        driver.execute(null,"DROP TRIGGER reject_delete",0)
        driver.execute(null,"INSERT INTO article(cover_image,created_at,updated_at) VALUES('diary_images/keep.png',1,1)",0)
        diaries.delete(id)
        assertEquals("keep",file.readText())
    }

    private fun fixture(block:(DailySatoriDatabase,JdbcSqliteDriver,FileManager)->Unit) {
        val root=createTempDirectory().toFile()
        try {
            val unsafeClass=Class.forName("sun.misc.Unsafe")
            val unsafe=unsafeClass.getDeclaredField("theUnsafe").apply { isAccessible=true }.get(null)
            val context=unsafeClass.getMethod("allocateInstance",Class::class.java).invoke(unsafe,TestContext::class.java) as TestContext
            context.root=root
            val files=FileManager().apply { init(context) }
            JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
                DailySatoriDatabase.Schema.create(driver)
                block(DailySatoriDatabase(driver),driver,files)
            }
        } finally { root.deleteRecursively() }
    }

    private class TestContext:ContextWrapper(null) {
        lateinit var root:File
        override fun getFilesDir()=File(root,"files")
    }
}
