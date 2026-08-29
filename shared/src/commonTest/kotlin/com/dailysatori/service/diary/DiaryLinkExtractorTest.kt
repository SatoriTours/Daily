package com.dailysatori.service.diary

import com.dailysatori.service.parser.ExtractedContent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DiaryLinkExtractorTest {
    @Test
    fun ordinaryPageUsesReadableTitleAndContent() = runBlocking {
        val extractor = DefaultDiaryLinkContentExtractor(fetch = {
            ExtractedContent("文章标题", "正文内容", "<html/>", null)
        })

        val result = extractor.extract("https://example.com/post")

        assertEquals("文章标题", result.title)
        assertEquals("正文内容", result.text)
        assertEquals(DiaryAssistantExtraction.FULL_TEXT, result.extraction)
    }

    @Test
    fun ordinaryPageRejectsBlankMaterial() = runBlocking {
        val extractor = DefaultDiaryLinkContentExtractor(fetch = {
            ExtractedContent("标题", "  ", "<html/>", null)
        })

        assertFailsWith<DiaryAssistantExtractionException> {
            extractor.extract("https://example.com/blocked")
        }
        Unit
    }

    @Test
    fun ordinaryPageRejectsBlockedMaterial() = runBlocking {
        val extractor = DefaultDiaryLinkContentExtractor(fetch = {
            ExtractedContent("标题", "Access Denied", "<html/>", null)
        })

        assertFailsWith<DiaryAssistantExtractionException> {
            extractor.extract("https://example.com/blocked")
        }
        Unit
    }

    @Test
    fun ordinaryPageCapsMaterialAtTwentyThousandCharacters() = runBlocking {
        val extractor = DefaultDiaryLinkContentExtractor(fetch = {
            ExtractedContent("标题", "x".repeat(20_001), "<html/>", null)
        })

        assertEquals(20_000, extractor.extract("https://example.com/long").text.length)
    }

    @Test
    fun douyinUsesPublicJsonLdTitleAuthorAndCaption() {
        val result = parsePublicDouyinMaterial(
            "https://v.douyin.com/a/",
            ExtractedContent(
                title = "页面标题",
                content = "公开描述",
                htmlContent = """
                    <script type="application/ld+json">
                    {"@type":"VideoObject","name":"JSON-LD 标题","author":{"name":"创作者"},"description":"JSON-LD 描述","caption":"公开字幕"}
                    </script>
                """.trimIndent(),
                coverImageUrl = null,
            ),
        )

        assertEquals("JSON-LD 标题", result.title)
        assertEquals("创作者", result.author)
        assertEquals("公开字幕", result.text)
        assertEquals(DiaryAssistantExtraction.FULL_TEXT, result.extraction)
    }

    @Test
    fun douyinWithoutCaptionReturnsExplicitDegradedMaterial() {
        val result = parsePublicDouyinMaterial(
            "https://v.douyin.com/a/",
            ExtractedContent("作者的视频", "公开文案", """<meta property="og:description" content="公开文案">""", null),
        )

        assertEquals(DiaryAssistantExtraction.NO_SUBTITLES, result.extraction)
        assertTrue("未获取到视频字幕" in result.warnings)
    }

    @Test
    fun douyinUsesExplicitPublicCaptionFieldOverDescription() {
        val result = parsePublicDouyinMaterial(
            "https://v.douyin.com/a/",
            ExtractedContent(
                "页面标题",
                "解析器描述",
                """<meta property="og:description" content="公开描述"><script>{"caption":"页面公开字幕"}</script>""",
                null,
            ),
        )

        assertEquals("页面公开字幕", result.text)
        assertEquals(DiaryAssistantExtraction.FULL_TEXT, result.extraction)
    }

    @Test
    fun extractionPropagatesCancellation() = runBlocking {
        val extractor = DefaultDiaryLinkContentExtractor(fetch = {
            throw CancellationException("cancelled")
        })

        assertFailsWith<CancellationException> {
            extractor.extract("https://example.com/cancelled")
        }
        Unit
    }
}
