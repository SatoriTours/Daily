package com.dailysatori.service.book

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class WeReadSkillServiceTest {
    @Test
    fun buildsGatewayBodyWithFlatParametersAndSkillVersion() {
        val body = buildWeReadGatewayBody(
            apiName = "/store/search",
            params = mapOf("keyword" to "三体", "scope" to 10, "count" to 5),
        ).toString()

        assertTrue(body.contains("\"api_name\":\"/store/search\""))
        assertTrue(body.contains("\"skill_version\":\"1.0.3\""))
        assertTrue(body.contains("\"keyword\":\"三体\""))
        assertTrue(body.contains("\"scope\":10"))
        assertTrue(body.contains("\"count\":5"))
        assertTrue(!body.contains("\"params\""))
    }

    @Test
    fun parsesSearchResultsFromWeReadV3Response() {
        val json = """
            {
              "errcode": 0,
              "results": [
                {
                  "title": "电子书",
                  "scope": 10,
                  "books": [
                    {
                      "readingCount": 153000,
                      "newRating": 92,
                      "newRatingCount": 12000,
                      "bookInfo": {
                        "bookId": "3300045871",
                        "title": "三体",
                        "author": "刘慈欣",
                        "cover": "https://res.weread.qq.com/cover.jpg",
                        "intro": "文化大革命如火如荼进行的同时，军方探寻外星文明的绝秘计划取得突破。",
                        "category": "科幻小说",
                        "isbn": "9787536692930"
                      }
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        val results = parseWeReadSearchResults(json)

        assertEquals(1, results.size)
        assertEquals("三体", results.first().title)
        assertEquals("刘慈欣", results.first().author)
        assertEquals("科幻小说", results.first().category)
        assertEquals("9787536692930", results.first().isbn)
        assertEquals("https://res.weread.qq.com/cover.jpg", results.first().coverUrl)
        assertEquals("微信读书 9.2 分，153000 人在读，12000 人评分", results.first().sourceSummary)
        assertEquals("weread://reading?bId=3300045871", results.first().sourceUrl)
    }

    @Test
    fun parsesRatingFieldsFromBookInfoWhenSearchItemOmitsThem() {
        val json = """
            {
              "errcode": 0,
              "results": [
                {
                  "books": [
                    {
                      "bookInfo": {
                        "bookId": "123",
                        "title": "置身事内",
                        "author": "兰小欢",
                        "newRating": 91,
                        "readingCount": 64000,
                        "newRatingCount": 8800
                      }
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        val result = parseWeReadSearchResults(json).first()

        assertEquals("微信读书 9.1 分，64000 人在读，8800 人评分", result.sourceSummary)
    }

    @Test
    fun throwsChineseErrorWhenGatewayReturnsErrcode() {
        val error = assertFailsWith<WeReadSkillException> {
            parseWeReadSearchResults("""{"errcode":1001,"errmsg":"invalid api key"}""")
        }

        assertEquals(WeReadSkillErrorType.RemoteFailure, error.type)
        assertEquals("invalid api key", error.message)
    }

    @Test
    fun parsesBookInfoChaptersAndReviewsIntoViewpoints() {
        val infoJson = """
            {
              "errcode": 0,
              "bookId": "3300045871",
              "title": "三体",
              "author": "刘慈欣",
              "intro": "人类文明第一次面对宇宙级不确定性，个体选择与集体命运被放到同一个坐标系里审视。",
              "category": "科幻小说",
              "newRating": 92,
              "newRatingCount": 12000
            }
        """.trimIndent()
        val chaptersJson = """
            {
              "errcode": 0,
              "chapters": [
                {"chapterUid": 1, "chapterIdx": 1, "title": "科学边界", "level": 1, "wordCount": 12000},
                {"chapterUid": 2, "chapterIdx": 2, "title": "三体问题", "level": 1, "wordCount": 18000},
                {"chapterUid": 3, "chapterIdx": 3, "title": "黑暗森林", "level": 1, "wordCount": 20000}
              ]
            }
        """.trimIndent()
        val reviewsJson = """
            {
              "errcode": 0,
              "reviews": [
                {"review":{"review":{"content":"这本书真正震撼人的地方，是把文明选择写成每个人都能感受到的压力。", "star": 100}}},
                {"review":{"review":{"content":"它让读者意识到技术乐观和生存恐惧会同时存在。", "star": 80}}}
              ]
            }
        """.trimIndent()

        val drafts = buildWeReadViewpointDrafts(
            info = parseWeReadBookInfo(infoJson),
            chapters = parseWeReadChapters(chaptersJson),
            reviews = parseWeReadReviews(reviewsJson),
        )

        assertEquals(10, drafts.size)
        assertTrue(drafts.first().title.contains("三体"))
        assertTrue(drafts.first().content.length >= 80)
        assertTrue(drafts.first().example.length >= 100)
        assertTrue(drafts.any { it.content.contains("科学边界") || it.content.contains("三体问题") })
        assertTrue(drafts.any { it.content.contains("文明选择") || it.example.contains("文明选择") })
    }

    @Test
    fun missingApiKeyUsesDedicatedErrorType() {
        val error = assertFailsWith<WeReadSkillException> { requireWeReadApiKey("   ") }

        assertEquals(WeReadSkillErrorType.MissingApiKey, error.type)
        assertEquals("请先在设置中配置微信读书 API Key", weReadUserMessage(error))
    }

    @Test
    fun plaintextStoredApiKeyIsReturnedAndReportedForUpgrade() {
        var upgraded = ""

        val key = resolveStoredWeReadApiKey(
            stored = " wrk-plain ",
            isEncrypted = { false },
            decrypt = { error("should not decrypt plaintext") },
            onPlaintext = { upgraded = it },
        )

        assertEquals("wrk-plain", key)
        assertEquals("wrk-plain", upgraded)
    }

    @Test
    fun corruptEncryptedApiKeyIsTreatedAsMissing() {
        val key = resolveStoredWeReadApiKey(
            stored = "enc:v1:broken",
            isEncrypted = { true },
            decrypt = { it },
        )

        assertEquals("", key)
        val error = assertFailsWith<WeReadSkillException> { requireWeReadApiKey(key) }
        assertEquals(WeReadSkillErrorType.MissingApiKey, error.type)
    }

    @Test
    fun parsesDetailedAiFallbackViewpointsOnly() {
        val json = """
            [
              {"title":"待上架书也要先界定真实问题。","content":"当一本书还没有完整目录和书评时，观点生成不能假装拥有微信读书材料，而应基于书名、作者、简介和分类先界定读者可能面对的真实问题。这样生成的内容虽然来自 AI，但仍然围绕已知元数据展开，避免把不存在的章节或读者评价写成事实。","example":"例如一位读者想添加一本待上架的供应链新书，微信读书只返回书名、作者和一句简介。系统没有编造目录，而是把简介里的产业协同作为主题，让 AI 生成一个具体场景：采购、仓储和销售团队因为预测口径不同导致缺货，再说明如何用统一指标协调下一步动作。"},
              {"title":"太短","content":"短","example":"短"}
            ]
        """.trimIndent()

        val drafts = parseAiFallbackViewpointJson(json)

        assertEquals(1, drafts.size)
        assertEquals("待上架书也要先界定真实问题。", drafts.first().title)
    }

    @Test
    fun rejectsAiFallbackViewpointsWithTerseTitle() {
        val json = """
            [
              {"title":"短","content":"当一本书还没有完整目录和书评时，观点生成不能假装拥有微信读书材料，而应基于书名、作者、简介和分类先界定读者可能面对的真实问题。这样生成的内容虽然来自 AI，但仍然围绕已知元数据展开，避免把不存在的章节或读者评价写成事实。","example":"例如一位读者想添加一本待上架的供应链新书，微信读书只返回书名、作者和一句简介。系统没有编造目录，而是把简介里的产业协同作为主题，让 AI 生成一个具体场景：采购、仓储和销售团队因为预测口径不同导致缺货，再说明如何用统一指标协调下一步动作。"}
            ]
        """.trimIndent()

        val drafts = parseAiFallbackViewpointJson(json)

        assertEquals(0, drafts.size)
    }

    @Test
    fun skipsMalformedAiFallbackViewpointFields() {
        val json = """
            [
              {"title":{"text":"标题不是字符串"},"content":["内容不是字符串"],"example":"例如一位读者想添加一本待上架的供应链新书，微信读书只返回书名、作者和一句简介。系统没有编造目录，而是把简介里的产业协同作为主题，让 AI 生成一个具体场景：采购、仓储和销售团队因为预测口径不同导致缺货，再说明如何用统一指标协调下一步动作。"},
              {"title":"待上架书也要先界定真实问题。","content":"当一本书还没有完整目录和书评时，观点生成不能假装拥有微信读书材料，而应基于书名、作者、简介和分类先界定读者可能面对的真实问题。这样生成的内容虽然来自 AI，但仍然围绕已知元数据展开，避免把不存在的章节或读者评价写成事实。","example":"例如一位读者想添加一本待上架的供应链新书，微信读书只返回书名、作者和一句简介。系统没有编造目录，而是把简介里的产业协同作为主题，让 AI 生成一个具体场景：采购、仓储和销售团队因为预测口径不同导致缺货，再说明如何用统一指标协调下一步动作。"}
            ]
        """.trimIndent()

        val drafts = parseAiFallbackViewpointJson(json)

        assertEquals(1, drafts.size)
        assertEquals("待上架书也要先界定真实问题。", drafts.first().title)
    }

    @Test
    fun buildsAiFallbackPromptWithDisclosureAndJsonContract() {
        val prompt = buildAiFallbackViewpointPrompt(
            book = BookSearchResult(
                title = "供应链架构师",
                author = "施云",
                category = "管理",
                introduction = "供应链是一套端到端的系统能力。",
            ),
            info = WeReadBookInfo(bookId = "123", title = "供应链架构师", author = "施云", intro = "端到端供应链。"),
            chapters = listOf(WeReadChapter(chapterUid = 1, chapterIdx = 1, title = "战略到运营")),
            reviews = emptyList(),
        )

        assertTrue(prompt.contains("只返回 JSON 数组"))
        assertTrue(prompt.contains("10 个对象"))
        assertTrue(prompt.contains("AI 生成"))
        assertTrue(prompt.contains("不能声称来自微信读书书评或原文"))
        assertTrue(prompt.contains("供应链架构师"))
    }

    @Test
    fun detectsSparseWeReadMaterialAsInsufficient() {
        val info = WeReadBookInfo(bookId = "123", title = "待上架新书", intro = "")
        val drafts = buildWeReadViewpointDrafts(info, chapters = emptyList(), reviews = emptyList())

        assertEquals(false, hasSufficientWeReadMaterial(info, emptyList(), emptyList(), drafts))
    }

    @Test
    fun detectsConcreteWeReadMaterialAsSufficient() {
        val info = WeReadBookInfo(
            bookId = "123",
            title = "三体",
            author = "刘慈欣",
            intro = "人类文明第一次面对宇宙级不确定性，个体选择与集体命运被放到同一个坐标系里审视。",
        )
        val chapters = listOf(WeReadChapter(chapterUid = 1, chapterIdx = 1, title = "科学边界"))
        val reviews = listOf(WeReadReview("这本书真正震撼人的地方，是把文明选择写成每个人都能感受到的压力。"))
        val drafts = buildWeReadViewpointDrafts(info, chapters, reviews)

        assertEquals(true, hasSufficientWeReadMaterial(info, chapters, reviews, drafts))
    }

    @Test
    fun missingAiConfigUsesDedicatedErrorMessage() {
        val error = assertFailsWith<WeReadSkillException> { requireAiFallbackConfig(null) }

        assertEquals(WeReadSkillErrorType.RemoteFailure, error.type)
        assertEquals("微信读书资料不足，请先配置默认 AI 模型后重试", error.message)
    }
}
