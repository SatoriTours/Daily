package com.dailysatori.core.recording

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DiaryPlaybackLoudnessTest {
    @Test
    fun enablesBoundedGainOnceForTheSamePlaybackSession() {
        val sessions = mutableListOf<Int>()
        var releases = 0
        val loudness = DiaryPlaybackLoudness { session, gain ->
            sessions += session
            assertTrue(gain in 1..1800, "Speech gain must be positive and bounded")
            val release: () -> Unit = { releases++ }
            release
        }

        loudness.attach(42)
        loudness.attach(42)
        assertEquals(listOf(42), sessions)
        loudness.release()
        loudness.release()
        assertEquals(1, releases)
    }

    @Test
    fun changingSessionsReleasesTheOldEffectBeforeCreatingTheNewOne() {
        val events = mutableListOf<String>()
        val loudness = DiaryPlaybackLoudness { session, _ ->
            events += "create:$session"
            val release: () -> Unit = { events += "release:$session" }
            release
        }

        loudness.attach(11)
        loudness.attach(22)
        loudness.release()
        assertEquals(listOf("create:11", "release:11", "create:22", "release:22"), events)
    }

    @Test
    fun unavailableEffectDoesNotThrowAndCanBeRetried() {
        var attempts = 0
        var releases = 0
        val loudness = DiaryPlaybackLoudness { _, _ ->
            attempts++
            if (attempts == 1) throw UnsupportedOperationException("Effect unavailable")
            val release: () -> Unit = { releases++ }
            release
        }

        loudness.attach(42)
        loudness.attach(42)
        loudness.release()
        assertEquals(2, attempts)
        assertEquals(1, releases)
    }

    @Test
    fun invalidSessionsReleaseTheActiveEffectWithoutAttachingToTheGlobalMix() {
        val sessions = mutableListOf<Int>()
        var releases = 0
        val loudness = DiaryPlaybackLoudness { session, _ ->
            sessions += session
            val release: () -> Unit = { releases++ }
            release
        }

        loudness.attach(42)
        loudness.attach(0)
        loudness.attach(-1)
        assertEquals(listOf(42), sessions)
        assertEquals(1, releases)
    }

    @Test
    fun failedReleaseIsNotRepeatedAndDoesNotPreventTheNextPlayback() {
        var creations = 0
        var releases = 0
        val loudness = DiaryPlaybackLoudness { _, _ ->
            creations++
            val release: () -> Unit = {
                releases++
                throw IllegalStateException("Effect already released by the platform")
            }
            release
        }

        loudness.attach(42)
        loudness.release()
        loudness.release()
        loudness.attach(42)
        loudness.release()
        assertEquals(2, creations)
        assertEquals(2, releases)
    }
}
