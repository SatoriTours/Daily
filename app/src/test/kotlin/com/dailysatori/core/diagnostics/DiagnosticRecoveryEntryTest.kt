package com.dailysatori.core.diagnostics

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.w3c.dom.Element

class DiagnosticRecoveryEntryTest {
    @Test
    fun launcherCanOpenBeforeTheBusinessProcessInitializes() {
        val manifest = File("src/main/AndroidManifest.xml").takeIf { it.isFile }
            ?: File("app/src/main/AndroidManifest.xml")
        val document = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder().parse(manifest)
        val application = document.getElementsByTagName("application").item(0) as Element
        val activities = document.getElementsByTagName("activity")
        val launchers = (0 until activities.length).map { activities.item(it) as Element }.filter { activity ->
            val filters = activity.getElementsByTagName("intent-filter")
            (0 until filters.length).any { index ->
                val filter = filters.item(index) as Element
                filter.hasNamedChild("action", "android.intent.action.MAIN") &&
                    filter.hasNamedChild("category", "android.intent.category.LAUNCHER")
            }
        }
        assertEquals(1, launchers.size)
        val process = launchers.single().androidAttribute("process")
        assertTrue(process.isNotBlank(), "Recovery entry must not start in the default business process")
        assertNotEquals(application.androidAttribute("process"), process)
    }

    private fun Element.androidAttribute(name: String) =
        getAttributeNS("http://schemas.android.com/apk/res/android", name)

    private fun Element.hasNamedChild(tag: String, name: String): Boolean {
        val children = getElementsByTagName(tag)
        return (0 until children.length).any { (children.item(it) as Element).androidAttribute("name") == name }
    }
}
