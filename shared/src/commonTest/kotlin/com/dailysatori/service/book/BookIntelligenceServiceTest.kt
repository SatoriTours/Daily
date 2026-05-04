package com.dailysatori.service.book

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BookIntelligenceServiceTest {
    @Test
    fun parsesBookCandidatesFromJsonArray() {
        val json = """
            [
              {"title":"穷查理宝典","author":"查理·芒格","category":"投资","introduction":"芒格思想合集","isbn":"","coverUrl":"https://img.example/a.jpg","sourceSummary":"来自公开书评"},
              {"title":"","author":"无效"}
            ]
        """.trimIndent()

        val results = parseBookCandidateJson(json)

        assertEquals(1, results.size)
        assertEquals("穷查理宝典", results.first().title)
        assertEquals("查理·芒格", results.first().author)
        assertEquals("投资", results.first().category)
        assertEquals("来自公开书评", results.first().sourceSummary)
    }

    @Test
    fun parsesBookCandidatesFromFencedJson() {
        val json = """
            ```json
            [{"title":"原则","author":"Ray Dalio","introduction":"原则说明"}]
            ```
        """.trimIndent()

        val results = parseBookCandidateJson(json)

        assertEquals(1, results.size)
        assertEquals("原则", results.first().title)
    }

    @Test
    fun parsesValidViewpointDraftsOnly() {
        val json = """
            [
              {"title":"复利来自长期主义","content":"长期主义不是简单等待，而是把时间投入到能持续复用的能力、关系和判断框架上。它要求人在短期波动里保持节奏，通过复盘和迭代让每一次投入都成为下一次行动的基础，而不是每次都从零开始寻找方向。","example":"例如一名产品经理每周固定访谈三个真实用户，不急着追逐每个热点需求，而是把用户反复提到的卡点整理成模式。半年后，他发现真正影响留存的是新手第一次完成任务的速度，于是推动团队重做引导流程，产品数据才开始稳定改善。"},
              {"title":"缺少解释","content":"","example":"无效"},
              {"title":"缺少案例","content":"缺字段"},
              "not-an-object"
            ]
        """.trimIndent()

        val drafts = parseBookViewpointJson(json)

        assertEquals(1, drafts.size)
        assertEquals("复利来自长期主义，必须转化为可执行的判断。", drafts.first().title)
        assertEquals("长期主义不是简单等待，而是把时间投入到能持续复用的能力、关系和判断框架上。它要求人在短期波动里保持节奏，通过复盘和迭代让每一次投入都成为下一次行动的基础，而不是每次都从零开始寻找方向。", drafts.first().content)
        assertEquals("例如一名产品经理每周固定访谈三个真实用户，不急着追逐每个热点需求，而是把用户反复提到的卡点整理成模式。半年后，他发现真正影响留存的是新手第一次完成任务的速度，于是推动团队重做引导流程，产品数据才开始稳定改善。", drafts.first().example)
    }

    @Test
    fun rejectsTerseViewpointDraftsFromAi() {
        val json = """
            [
              {"title":"供应链网络与物流节点的优化","content":"优化节点。","example":"某企业优化仓库。"}
            ]
        """.trimIndent()

        assertEquals(emptyList(), parseBookViewpointJson(json))
    }

    @Test
    fun completesNounPhraseViewpointTitles() {
        val json = """
            [
              {"title":"供应链网络与物流节点的优化","content":"供应链网络不是点状仓库的简单堆叠，而是订单、库存、运输和服务承诺共同形成的履约系统。企业需要同时看节点位置、库存分布、干线与末端协同，才能降低局部优化造成的整体浪费。","example":"例如一家家电企业只在华东建大仓，北方客户每次都要跨区调拨，旺季配送慢还容易缺货。后来它按销量和服务半径重设华北前置仓，把高频 SKU 提前放到近端节点，同时把低频件保留在中心仓，配送时效和库存周转一起改善。"}
            ]
        """.trimIndent()

        val drafts = parseBookViewpointJson(json)

        assertEquals("供应链网络与物流节点的优化，必须转化为可执行的判断。", drafts.first().title)
    }

    @Test
    fun viewpointPromptRequiresTenStructuredCards() {
        val prompt = buildBookViewpointPrompt(
            title = "穷查理宝典",
            author = "查理·芒格",
            introduction = "投资与人生智慧",
            sourceNotes = "公开资料摘要",
        )

        assertTrue(prompt.contains("10"))
        assertTrue(prompt.contains("title"))
        assertTrue(prompt.contains("content"))
        assertTrue(prompt.contains("example"))
        assertTrue(prompt.contains("只返回 JSON 数组"))
        assertTrue(prompt.contains("title 是完整观点句"))
        assertTrue(prompt.contains("content 至少 80 个中文字符"))
        assertTrue(prompt.contains("example 至少 100 个中文字符"))
    }

    @Test
    fun fallbackViewpointsProvideTenCardsWhenAiCannotGenerate() {
        val drafts = fallbackBookViewpoints(
            BookSearchResult(
                title = "供应链架构师",
                author = "施云",
                category = "管理",
                introduction = "供应链是一套端到端的系统能力。",
                sourceSummary = "从战略到运营都需要协同。",
            ),
        )

        assertEquals(10, drafts.size)
        assertTrue(drafts.first().title.contains("供应链架构师"))
        assertTrue(drafts.first().content.contains("供应链是一套端到端的系统能力"))
        assertTrue(drafts.first().title.endsWith("。"))
        assertTrue(drafts.first().content.length >= 80)
        assertTrue(drafts.first().example.length >= 100)
        assertTrue(drafts.first().example.contains("例如"))
        assertFalse(drafts.drop(1).all { it.example.contains("供应链") || it.example.contains("物流") || it.example.contains("仓") })
    }

    @Test
    fun androidDoesNotUseLocalCommandMcpAsCallableSource() {
        assertFalse(isAndroidCallableMcpSource("local"))
        assertFalse(isAndroidCallableMcpSource("stdio"))
        assertTrue(isAndroidCallableMcpSource("remote"))
        assertTrue(isAndroidCallableMcpSource("http"))
    }

    @Test
    fun localizesChineseBookSearchQuery() {
        assertEquals("孔子 中文书籍 中文资料", localizedBookSearchQuery("孔子"))
        assertEquals("Ray Dalio", localizedBookSearchQuery("Ray Dalio"))
    }

    @Test
    fun parsesDoubanSuggestResultWithAuthorCoverAndUrl() {
        val json = """
            [
              {"title":"供应链架构师","url":"https:\/\/book.douban.com\/subject\/26995807\/","pic":"https://img9.doubanio.com\/view\/subject\/s\/public\/s33514286.jpg","author_name":"施云","year":"2016","type":"b","id":"26995807"}
            ]
        """.trimIndent()

        val results = parseDoubanSuggestResults(json)

        assertEquals(1, results.size)
        assertEquals("供应链架构师", results.first().title)
        assertEquals("施云", results.first().author)
        assertEquals("https://img9.doubanio.com/view/subject/s/public/s33514286.jpg", results.first().coverUrl)
        assertEquals("https://book.douban.com/subject/26995807/", results.first().sourceUrl)
    }

    @Test
    fun stripsAiLocalizationTermsBeforeExternalBookSearch() {
        assertEquals("供应链架构师", externalBookSearchQuery("供应链架构师 中文书籍 中文资料"))
        assertEquals("Ray Dalio", externalBookSearchQuery("Ray Dalio"))
    }

    @Test
    fun sourceResultsRankBeforeAiForExactChineseBookTitle() {
        val ranked = rankBookCandidates(
            query = "供应链架构师",
            sourceResults = listOf(BookSearchResult(title = "供应链架构师", author = "施云", coverUrl = "cover", sourceUrl = "url")),
            aiResults = listOf(BookSearchResult(title = "供应链架构师：从战略到运营", author = "刘宝红")),
        )

        assertEquals("施云", ranked.first().author)
        assertEquals("cover", ranked.first().coverUrl)
        assertEquals("url", ranked.first().sourceUrl)
    }

    @Test
    fun parsesDoubanSubjectDetailsForIntroAndLargeCover() {
        val html = """
            <meta property="og:description" content="供应链是一个复杂的系统，供应链的变革九死一生。" />
            <meta property="og:image" content="https://img9.doubanio.com/view/subject/l/public/s33514286.jpg" />
            <meta property="book:isbn" content="9787504760463" />
        """.trimIndent()

        val details = parseDoubanSubjectDetails(html)

        assertEquals("供应链是一个复杂的系统，供应链的变革九死一生。", details.introduction)
        assertEquals("https://img9.doubanio.com/view/subject/l/public/s33514286.jpg", details.coverUrl)
        assertEquals("9787504760463", details.isbn)
    }

    @Test
    fun doubanSubjectDetailFetchUsesShortTimeout() {
        assertTrue(doubanSubjectDetailTimeoutMs() <= 3_000L)
    }
}
