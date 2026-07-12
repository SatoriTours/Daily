package com.dailysatori

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DiaryRecordingManifestTest {
    private val manifest = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
    }.newDocumentBuilder().parse(File("src/main/AndroidManifest.xml"))

    @Test
    fun declaresMicrophoneForegroundServicePermissions() {
        val permissions = manifest.getElementsByTagName("uses-permission")
            .asSequence()
            .map { it.getAttributeNS(ANDROID_NAMESPACE, "name") }
            .toSet()

        assertTrue("android.permission.RECORD_AUDIO" in permissions)
        assertTrue("android.permission.FOREGROUND_SERVICE" in permissions)
        assertTrue("android.permission.FOREGROUND_SERVICE_MICROPHONE" in permissions)
        assertTrue("android.permission.POST_NOTIFICATIONS" in permissions)
    }

    @Test
    fun recordingServiceIsPrivateAndUsesTheMicrophoneType() {
        val service = manifest.getElementsByTagName("service")
            .asSequence()
            .first { it.getAttributeNS(ANDROID_NAMESPACE, "name") == ".core.recording.DiaryRecordingService" }

        assertEquals("false", service.getAttributeNS(ANDROID_NAMESPACE, "exported"))
        assertEquals("microphone", service.getAttributeNS(ANDROID_NAMESPACE, "foregroundServiceType"))
    }

    private fun org.w3c.dom.NodeList.asSequence() = sequence {
        for (index in 0 until length) yield(item(index) as org.w3c.dom.Element)
    }

    private companion object {
        const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
    }
}
