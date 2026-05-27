package com.dailysatori.ui.feature.article

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class ArticleListLayoutTest {
    @Test
    fun articleListScreenStaysFocusedAfterUnifiedEmbedding() {
        val source = File("src/main/kotlin/com/dailysatori/ui/feature/article/ArticleListScreen.kt").readText()

        listOf(
            "ArticleListScreen",
            "ArticleListEffects",
            "ArticleListContent",
            "ArticleListBody",
            "ArticleAddDialog",
            "ArticleListTopBar",
            "ArticleListMenu",
        ).forEach { functionName ->
            assertTrue(source.functionLineCount(functionName) <= 50, "$functionName exceeds 50 lines")
        }
    }
}

private fun String.functionBody(functionName: String): String {
    val match = Regex("fun\\s+(?:[A-Za-z0-9_<>.]+\\.)?${Regex.escape(functionName)}\\s*\\(").find(this)
    val start = match?.range?.first ?: -1
    require(start >= 0) { "Missing function $functionName" }
    val bodyStart = indexOf('{', start)
    require(bodyStart >= 0) { "Missing body for $functionName" }
    val bodyEnd = matchingBraceIndex(bodyStart)
    return substring(bodyStart, bodyEnd + 1)
}

private fun String.functionLineCount(functionName: String): Int = functionBody(functionName).lineSequence().count() + 1

private fun String.matchingBraceIndex(openBraceIndex: Int): Int {
    var depth = 0
    for (index in openBraceIndex until length) {
        when (this[index]) {
            '{' -> depth++
            '}' -> {
                depth--
                if (depth == 0) return index
            }
        }
    }
    error("Missing matching brace")
}
