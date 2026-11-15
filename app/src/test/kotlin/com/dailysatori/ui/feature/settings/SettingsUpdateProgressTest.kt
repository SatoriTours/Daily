package com.dailysatori.ui.feature.settings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SettingsUpdateProgressTest {
    @Test
    fun knownTotalProducesDeterminateFractionAndRealBytes() {
        val snapshot = updateDownloadSnapshot(downloadedBytes = 25, totalBytes = 100)
        assertEquals(25, snapshot.downloadedBytes)
        assertEquals(100, snapshot.totalBytes)
        assertEquals(0.25f, snapshot.fraction)
    }

    @Test
    fun zeroDownloadedWithKnownTotalIsZeroPercentNotIndeterminate() {
        val snapshot = updateDownloadSnapshot(downloadedBytes = 0, totalBytes = 100)
        assertEquals(0f, snapshot.fraction)
        assertEquals(0, snapshot.downloadedBytes)
    }

    @Test
    fun unknownOrInvalidTotalKeepsBytesButNoFakeFraction() {
        listOf(-1L, 0L).forEach { total ->
            val snapshot = updateDownloadSnapshot(downloadedBytes = 25, totalBytes = total)
            assertEquals(25, snapshot.downloadedBytes)
            assertNull(snapshot.totalBytes)
            assertNull(snapshot.fraction)
        }
    }

    @Test
    fun negativeBytesClampButOverrunBytesStayVisibleWithoutFakePercent() {
        val negative = updateDownloadSnapshot(downloadedBytes = -5, totalBytes = 100)
        assertEquals(0, negative.downloadedBytes)
        assertEquals(0f, negative.fraction)
        val over = updateDownloadSnapshot(downloadedBytes = 150, totalBytes = 100)
        assertEquals(150, over.downloadedBytes)
        assertNull(over.totalBytes)
        assertNull(over.fraction)
    }

    @Test
    fun formatsDownloadedAndTotalByteCounts() {
        assertEquals("0 B", formatDownloadBytes(0))
        assertEquals("1023 B", formatDownloadBytes(1023))
        assertEquals("1.0 KB", formatDownloadBytes(1024))
        assertEquals("1.0 MB", formatDownloadBytes(1024L * 1024))
        assertEquals("20.1 MB", formatDownloadBytes(21_100_205))
    }

    @Test
    fun completedDirectDownloadUsesFilePathForInstall() {
        assertEquals("/tmp/app.apk", installReadyFilePathAfterDownload("/tmp/app.apk"))
        assertEquals(null, installReadyFilePathAfterDownload("   "))
    }
}
