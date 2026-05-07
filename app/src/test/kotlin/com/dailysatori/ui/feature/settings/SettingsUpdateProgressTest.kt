package com.dailysatori.ui.feature.settings

import kotlin.test.Test
import kotlin.test.assertEquals

class SettingsUpdateProgressTest {
    @Test
    fun calculatesDownloadProgressWhenTotalSizeIsKnown() {
        assertEquals(0.25f, updateDownloadProgress(downloadedBytes = 25, totalBytes = 100))
        assertEquals(1f, updateDownloadProgress(downloadedBytes = 150, totalBytes = 100))
    }

    @Test
    fun keepsProgressIndeterminateWhenTotalSizeIsUnknown() {
        assertEquals(null, updateDownloadProgress(downloadedBytes = 25, totalBytes = -1))
        assertEquals(null, updateDownloadProgress(downloadedBytes = 25, totalBytes = 0))
    }

    @Test
    fun formatsUpdateDownloadProgressText() {
        assertEquals("下载中 25%", updateDownloadProgressText(0.25f))
        assertEquals("正在准备下载...", updateDownloadProgressText(null))
    }
}
