package com.dailysatori.service.parser

import kotlin.test.Test
import kotlin.test.assertEquals

class ArticleReadingMarkdownTest {
    @Test fun adjacentIdenticalMultiParagraphCyclesCollapseWithoutTruncatingTheTail() {
        val paragraphs = "量产白色微型 LED，无需使用稀土材料。\n\n芯片尺寸可小于 100 微米。"
        val tail = "公告没有披露生产成本。"
        assertEquals("$paragraphs\n\n$tail",
            tidyArticleTranslation(List(5) { paragraphs }.joinToString("\n\n") + "\n\n$tail"))
    }

    @Test fun leadingTitleIsHiddenOnlyWhenItMatchesTheVisibleTitleOrAnAdjacentLoopProvesItIsAccidental() {
        val body = "一段关键事实。\n\n另一段完整的正文。"
        assertEquals(body, tidyArticleTranslation("# 同一标题\n\n$body", "同一 标题"))
        val changed = "$body # 补充标题\n\n一段关键事实。\n\n不同的补充事实。"
        assertEquals("# 补充标题\n\n$changed", tidyArticleTranslation("# 补充标题\n\n$changed", "较长的文章名称"))
    }

    @Test fun chaptersAndStructuredMarkdownKeepTheirContent() {
        val pair = "必须每次保留的段落。\n\n另一段相同事实。"
        val code = "```markdown\n$pair\n\n$pair\n\n句子。 # 代码不是标题\n```"
        val structure = "[说明](https://example.org/notes)\n\n| 字段 | 值 |\n| --- | --- |\n\n- 项目"
        val text = "## 第一章\n\n$pair\n\n## 第二章\n\n$pair\n\n$code\n\n$structure\n\n$structure"
        assertEquals(text, tidyArticleTranslation(text))
        val continuation = pair.prependIndent("  ")
        val list = "- 需要缩进的项目\n\n$continuation\n\n$continuation"
        assertEquals(list, tidyArticleTranslation(list))
    }

    @Test fun knownLegacyBriefShowsOnlyTheFactsAndLosesNoSentence() {
        val facts = "公司已量产微型 LED。芯片尺寸低于 100 微米。"
        val legacy = "2026年11月22日\n\n**新闻事实：** $facts\n\n**为什么重要（分析）：** 旧分析。\n\n" +
            "**工作／技术决策（分析）：** 旧建议。\n\n来源：[原文](<https://example.org>)"
        assertEquals(facts, articleReaderSummary(legacy))
        val publisher = "发布者说明\n\n**新闻事实：** $facts\n\n另一段作者解释。"
        assertEquals(publisher, articleReaderSummary(publisher))
        assertEquals(facts, articleReaderSummary(facts))
    }
}
