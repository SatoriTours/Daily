package com.dailysatori.core.service

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UpdateChannelTest {
    private val installed = InstalledBuild(100, UpdateChannel.COMMIT, 27)
    private fun release(code: Int = 101, channel: UpdateChannel = UpdateChannel.COMMIT, schema: Long = 27) = AppRelease(
        "5.1.63-commit.101", "", ReleaseAsset("app.apk", "https://github.com/SatoriTours/Daily/releases/download/commit-101/app.apk"),
        code, channel, schema, "a".repeat(64),
    )

    @Test
    fun newerCommitBuildIsOfferedRegardlessOfBaseVersionName() {
        val target = release()
        assertEquals(target, evaluateChannelUpdate(installed, target, UpdateChannel.COMMIT).release)
    }

    @Test
    fun sameBuildInSameChannelDoesNotRepeatUpdate() {
        assertNull(evaluateChannelUpdate(installed, release(100), UpdateChannel.COMMIT).release)
    }

    @Test
    fun sameCommitCanSwitchBetweenChannels() {
        val target = release(100, UpdateChannel.STABLE)
        assertEquals(target, evaluateChannelUpdate(installed, target, UpdateChannel.STABLE).release)
    }

    @Test
    fun olderStableBuildWaitsWithoutOfferingADowngrade() {
        val result = evaluateChannelUpdate(installed, release(99, UpdateChannel.STABLE), UpdateChannel.STABLE)
        assertNull(result.release)
        assertTrue(result.message.contains("等待"))
    }

    @Test
    fun olderDatabaseSchemaIsBlockedEvenWithHigherVersionCode() {
        val result = evaluateChannelUpdate(installed, release(schema = 26), UpdateChannel.COMMIT)
        assertNull(result.release)
        assertTrue(result.message.contains("数据"))
    }

    @Test
    fun wrongChannelOrMissingMetadataCannotBeInstalled() {
        assertNull(evaluateChannelUpdate(installed, release(channel = UpdateChannel.STABLE), UpdateChannel.COMMIT).release)
        assertNull(evaluateChannelUpdate(installed, release().copy(versionCode = null), UpdateChannel.COMMIT).release)
        assertNull(evaluateChannelUpdate(installed, release().copy(sha256 = null), UpdateChannel.COMMIT).release)
    }
}
