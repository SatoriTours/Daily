package com.dailysatori.core.task

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AsyncTaskLogStoreHttpTest {
    @Test
    fun httpLinesHaveTheirOwnCapAndSurviveTaskDeletion() {
        val root = File.createTempFile("task-http", "dir").also { it.delete(); it.mkdirs() }
        try {
            val store = AsyncTaskLogStore(root, maxBytesPerTask = 1024, maxHttpBytesPerTask = 128)
            store.appendHttp(41L, "first line")
            assertEquals("first line\n", store.readHttp(41L))

            repeat(40) { store.appendHttp(41L, "line " + it + " " + "x".repeat(10)) }
            assertTrue(store.readHttp(41L).toByteArray().size <= 128, "the http file keeps its own cap")
            assertTrue(store.readHttp(41L).contains("line 39"), "the newest lines survive the cap")
            assertEquals("", store.readHttp(999L), "an unknown task has no capture")

            store.delete(listOf(41L))
            assertFalse(File(root, "task-41.http.log").exists(), "task deletion removes the capture file")
            assertEquals("", store.readHttp(41L))
        } finally {
            root.deleteRecursively()
        }
    }
}

