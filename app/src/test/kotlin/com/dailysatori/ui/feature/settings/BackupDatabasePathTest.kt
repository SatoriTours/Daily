package com.dailysatori.ui.feature.settings

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BackupDatabasePathTest {
    @Test
    fun backupServiceUsesAndroidDatabasePathForDatabaseFile() {
        val source = File("../shared/src/commonMain/kotlin/com/dailysatori/service/backup/BackupService.kt").readText()

        assertTrue(source.contains("fileManager.getDatabasePath()"))
        assertFalse(source.contains("fileManager.getAppDataDir()}/${'$'}{DatabaseConfig.name}"))
        assertTrue(source.contains("return failBackup(\"数据库文件不存在，无法创建完整备份\")"))
        assertTrue(source.contains("deleteRecursive(tempDir)"))
        assertTrue(source.indexOf("deleteRecursive(tempDir)") < source.indexOf("fileManager.extractZip(tempZip, tempDir)"))
    }

    @Test
    fun fileManagerExposesDatabasePathAndRestartHooks() {
        val common = File("../shared/src/commonMain/kotlin/com/dailysatori/platform/FileManager.kt").readText()
        val android = File("../shared/src/androidMain/kotlin/com/dailysatori/platform/FileManager.android.kt").readText()

        assertTrue(common.contains("fun getDatabasePath(): String"))
        assertTrue(common.contains("fun restartApp()"))
        assertTrue(android.contains("appContext.getDatabasePath"))
        assertTrue(android.contains("exitProcess(0)"))
    }
}
