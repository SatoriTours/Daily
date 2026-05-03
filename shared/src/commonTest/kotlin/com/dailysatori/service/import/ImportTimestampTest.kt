package com.dailysatori.service.import

import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals

class ImportTimestampTest {
    @Test
    fun parsesFlutterLocalIsoDatetimeWithoutFallingBackToImportTime() {
        val imported = parseImportEpochMs("2026-01-02T03:04:05.006", TimeZone.UTC)

        assertEquals(1767323045006, imported)
    }

    @Test
    fun parsesUtcIsoDatetimeFromMigrationSpec() {
        val imported = parseImportEpochMs("2026-01-02T03:04:05.006Z")

        assertEquals(1767323045006, imported)
    }

    @Test
    fun parsesEpochMilliseconds() {
        val imported = parseImportEpochMs("1767323045006")

        assertEquals(1767323045006, imported)
    }
}
