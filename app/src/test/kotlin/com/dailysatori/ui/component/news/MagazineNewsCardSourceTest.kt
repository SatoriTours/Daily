package com.dailysatori.ui.component.news

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MagazineNewsCardSourceTest {
    @Test
    fun magazineNewsCardUsesRequiredSharedListStructure() {
        val source = File("src/main/kotlin/com/dailysatori/ui/component/news/MagazineNewsCard.kt").readText()

        assertTrue(source.contains("fun MagazineNewsCard("))
        listOf("title", "summary", "meta", "coverUrl", "onClick", "modifier", "trailingActions").forEach {
            assertTrue(source.contains("$it:"), "Missing parameter fragment: $it")
        }
        assertTrue(source.contains("Modifier"))
        assertTrue(source.contains("@Composable RowScope.() -> Unit"))
        assertTrue(Regex("height\\s*\\(\\s*articleCardHeightDp\\.dp\\s*\\)").containsMatchIn(source))
        assertTrue(Regex("width\\s*\\(\\s*magazineNewsCoverWidthDp\\.dp\\s*\\)").containsMatchIn(source))
        assertTrue(Regex("maxLines\\s*=\\s*2").containsMatchIn(source))
        assertTrue(Regex("maxLines\\s*=\\s*articleCardSummaryMaxLines").containsMatchIn(source))
        assertTrue(Regex("Spacer[\\s\\S]*weight\\s*\\(\\s*1f\\s*\\)").containsMatchIn(source))
        assertTrue(source.contains("MaterialTheme.colorScheme"))
        assertTrue(source.contains("resolveMagazineNewsCoverPath"))
        assertFalse(source.contains("BorderStroke"))
        assertFalse(source.contains("Color("))
    }

    @Test
    fun coverPathResolverKeepsRemoteUrlUnchanged() {
        val path = "https://example.com/cover.jpg"

        assertEquals(path, resolveMagazineNewsCoverPath(path, "/tmp/daily-files"))
    }

    @Test
    fun coverPathResolverKeepsAbsoluteLocalPathUnchanged() {
        val path = "/storage/emulated/0/Pictures/cover.jpg"

        assertEquals(path, resolveMagazineNewsCoverPath(path, "/tmp/daily-files"))
    }

    @Test
    fun coverPathResolverResolvesRelativePathUnderDailySatori() {
        val filesDirPath = "/tmp/daily-files"
        val path = "images/cover.jpg"
        val expected = File(filesDirPath, "DailySatori/$path").absolutePath

        assertEquals(expected, resolveMagazineNewsCoverPath(path, filesDirPath))
    }

    @Test
    fun coverPathResolverReturnsNullForBlankPath() {
        assertNull(resolveMagazineNewsCoverPath(" ", "/tmp/daily-files"))
    }

    @Test
    fun magazineNewsCardSkipsTitleTextWhenTitleIsBlank() {
        val source = File("src/main/kotlin/com/dailysatori/ui/component/news/MagazineNewsCard.kt").readText()

        assertTrue(source.contains("title.isNotBlank()"))
    }

    @Test
    fun sharedAndLocalCardComposableFunctionsStayWithinProjectLengthLimit() {
        val magazineSource = File("src/main/kotlin/com/dailysatori/ui/component/news/MagazineNewsCard.kt").readText()
        val articleSource = File("src/main/kotlin/com/dailysatori/ui/component/card/ArticleCard.kt").readText()

        assertTrue(functionLineCount(magazineSource, "MagazineNewsCard") <= 50)
        assertTrue(functionLineCount(articleSource, "ArticleCard") <= 50)
    }

    private fun functionLineCount(source: String, functionName: String): Int {
        val start = source.lines().indexOfFirst { it.contains("fun $functionName(") }
        require(start >= 0) { "Missing function: $functionName" }

        var depth = 0
        var foundBody = false
        source.lines().drop(start).forEachIndexed { index, line ->
            depth += line.count { it == '{' }
            foundBody = foundBody || line.contains('{')
            depth -= line.count { it == '}' }
            if (foundBody && depth == 0) return index + 1
        }
        error("Unclosed function: $functionName")
    }
}
