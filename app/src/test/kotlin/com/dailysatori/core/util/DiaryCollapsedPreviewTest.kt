package com.dailysatori.core.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DiaryCollapsedPreviewTest {
    @Test
    fun markdownHeadingIsSeparatedFromBodyWithoutRepeatingIt() {
        val preview = diaryCollapsedPreview("# **把想到的事情先记下来**\n\n一句话也可以留下。\n\n#日常")

        assertEquals("把想到的事情先记下来", preview.title)
        assertEquals("一句话也可以留下。", preview.body)
    }

    @Test
    fun ordinaryFirstLineAndLaterHeadingsRemainInTheBody() {
        val preview = diaryCollapsedPreview("普通的第一行\n\n## 后面的标题\n一些正文")

        assertNull(preview.title)
        assertEquals("普通的第一行 后面的标题 一些正文", preview.body)
    }

    @Test
    fun headingOnlyDiaryHasNoEmptyBody() {
        val preview = diaryCollapsedPreview("# 今天也要好好生活 ###\n\n#生活")

        assertEquals("今天也要好好生活", preview.title)
        assertEquals("", preview.body)
    }

    @Test
    fun attachmentOnlyAndTagOnlyDiariesHaveNoInventedTitle() {
        listOf("", "\n#生活\n").forEach { content ->
            val preview = diaryCollapsedPreview(content)
            assertNull(preview.title)
            assertEquals("", preview.body)
        }
    }

    @Test
    fun quoteAndIndentedCodeAreNotMistakenForTitles() {
        listOf("> # 引用里的标题", "    # 代码里的标题").forEach { content ->
            assertNull(diaryCollapsedPreview(content).title)
        }
    }
}
