package com.dailysatori.service.backup

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BackupServiceTest {
    @Test
    fun backupFileNameIncludesPasswordHint() {
        val name = backupFileName("2026-05-04-10-30-00", "correct horse battery")

        assertEquals("daily_satori_backup_2026-05-04-10-30-00_hint_ery.zip.enc", name)
    }

    @Test
    fun passwordHintIsParsedFromBackupFileName() {
        val hint = backupPasswordHint("daily_satori_backup_2026-05-04-10-30-00_hint_abc.zip.enc")

        assertEquals("abc", hint)
    }

    @Test
    fun passwordHintReturnsNullForOldNames() {
        val hint = backupPasswordHint("daily_satori_backup_2026-05-04-10-30-00.zip.enc")

        assertNull(hint)
    }
}
