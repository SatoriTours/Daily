package com.dailysatori.ui.theme

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CompactTitleSpacingTest {
    @Test
    fun appTopBarUsesCompactHeightToken() {
        val source = readProjectFile("app/src/main/kotlin/com/dailysatori/ui/component/appbar/AppTopBar.kt")

        assertTrue(source.contains("import androidx.compose.foundation.layout.height"))
        assertTrue(source.contains("import androidx.compose.ui.Modifier"))
        assertTrue(source.contains("import com.dailysatori.ui.theme.Height"))
        assertTrue(source.contains("modifier = Modifier.height(Height.appBar)"))
        assertTrue(source.contains("windowInsets = TopAppBarDefaults.windowInsets"))
        assertFalse(source.contains("height(64.dp)"))
    }
}

private fun readProjectFile(path: String): String =
    (java.io.File(path).takeIf { it.exists() }
        ?: java.io.File(path.removePrefix("app/")))
        .readText()
