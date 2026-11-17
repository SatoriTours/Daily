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
        assertTrue(source.contains("private val magazineNewsCoverSize = 88.dp"))
        assertTrue(source.contains("Modifier.size(magazineNewsCoverSize)"))
        assertTrue(Regex("maxLines\\s*=\\s*3").containsMatchIn(source))
        assertTrue(Regex("maxLines\\s*=\\s*articleCardSummaryMaxLines").containsMatchIn(source))
        assertFalse(source.contains("articleCardHeightDp"), "卡片高度由内容决定，不再固定旧卡片高度")
        assertFalse(Regex("height\\s*\\(\\s*magazineNewsCoverSize").containsMatchIn(source))
        assertTrue(source.contains("Modifier.weight(1f)"), "无封面时摘要文字铺满可用宽度")
        assertTrue(source.contains("RoundedCornerShape(Radius.m)"), "封面使用主题小圆角")
        assertTrue(source.contains("MaterialTheme.colorScheme"))
        assertTrue(source.contains("resolveMagazineNewsCoverPath"))
        assertTrue(source.contains("if (resolvedPath == null || loadFailed) return"), "无图或加载失败时不出现占位块")
        assertFalse(source.contains("ic_menu_gallery"))
        assertFalse(source.contains("ic_menu_report_image"))
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
