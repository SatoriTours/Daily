package com.dailysatori.ui.feature.aichat

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AiReferenceDetailSheetTextTest {
    @Test
    fun viewpointReferencePassesBookAuthorMetadata() {
        val source = File("src/main/kotlin/com/dailysatori/ui/feature/aichat/AiReferenceDetailSheet.kt").readText()
        val viewpointCardCall = source.substringAfter("state.viewpoint != null -> ViewpointCard(")
            .substringBefore("        )")

        assertTrue(viewpointCardCall.contains("bookTitle = state.book?.title.orEmpty()"))
        assertTrue(viewpointCardCall.contains("author = state.book?.author.orEmpty()"))
        assertTrue(viewpointCardCall.contains("page = 0"))
        assertTrue(viewpointCardCall.contains("total = 1"))
        assertTrue(viewpointCardCall.contains("showProgress = false"))
        assertFalse(viewpointCardCall.contains("reserveBottomSpace = true"))
    }
}
