package com.dailysatori.service.parser

import kotlin.test.*

class ArticleHtmlMarkdownTest {
    @Test fun preservesHeadingsTablesCodeLinksAndImagesWithoutAi() {
        val markdown = articleHtmlToMarkdown("""
            <h2>正文</h2><p>说明 <strong>重点</strong> <a href="/help">链接</a></p>
            <ul><li>第一项</li><li>第二项</li></ul>
            <pre><code class="language-kotlin">val x = "&lt;tag&gt;"
println(x)</code></pre>
            <table><tr><th>名称</th><th>数值</th></tr><tr><td>A</td><td>1</td></tr></table>
            <img data-src="/photo.png" alt="图片"><script>private()</script>
        """.trimIndent(), "https://example.com/article")
        listOf("## 正文", "**重点**", "[链接](https://example.com/help)", "- 第一项", "- 第二项",
            "```kotlin\nval x = \"<tag>\"\nprintln(x)\n```", "| 名称 | 数值 |", "| --- | --- |",
            "![图片](https://example.com/photo.png)").forEach { assertTrue(markdown.contains(it), it) }
        assertFalse(markdown.contains("private()"))
    }

    @Test fun keepsLongTextAndNestedLists() {
        val markdown = articleHtmlToMarkdown("<ol><li>外层<ul><li>内层</li></ul></li></ol><p>${"正文".repeat(8_000)}末尾</p>", "https://example.com")
        assertTrue(markdown.contains("1. 外层\n  - 内层"))
        assertTrue(markdown.endsWith("末尾"))
    }
}
