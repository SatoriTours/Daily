package com.dailysatori.service.book

import kotlinx.coroutines.runBlocking
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
        assertTrue(drafts.first().content.length >= 40)
        assertTrue(drafts.first().example.length >= 120)
        assertTrue(drafts.any { it.content.contains("科学边界") || it.content.contains("三体问题") })
        assertTrue(drafts.any { it.content.contains("条件、矛盾和后果") || it.example.contains("局势混乱") })
    }

    @Test
    fun weReadViewpointsExtractBookArgumentsInsteadOfReviews() {
        val drafts = buildWeReadViewpointDrafts(
            info = WeReadBookInfo(
                bookId = "mao-1",
                title = "毛泽东选集",
                author = "毛泽东",
                intro = "围绕中国革命的基本问题，分析阶级、群众、实践、矛盾和统一战线等关键主题。",
            ),
            chapters = listOf(
                WeReadChapter(chapterUid = 1, chapterIdx = 1, title = "实践论"),
                WeReadChapter(chapterUid = 2, chapterIdx = 2, title = "矛盾论"),
            ),
            reviews = listOf(WeReadReview("不要把这本书写成读后感，而要提炼书中的论点。")),
        )

        val text = drafts.joinToString("\n") { "${it.title}\n${it.content}\n${it.example}" }
        val reviewTerms = listOf("书评", "读者", "读完", "评价一本书", "读后感", "金句")

        assertEquals(10, drafts.size)
        assertTrue(drafts.any { it.content.contains("实践论") || it.content.contains("矛盾论") })
        assertTrue(reviewTerms.none { it in text }, text)
    }

    @Test
    fun weReadViewpointsUseShortSummaryAndBookContextStory() {
        val drafts = buildWeReadViewpointDrafts(
            info = WeReadBookInfo(
                bookId = "mao-1",
                title = "毛泽东选集",
                author = "毛泽东",
                intro = "围绕中国革命的基本问题，分析阶级、群众、实践、矛盾和统一战线等关键主题。",
            ),
            chapters = listOf(
                WeReadChapter(chapterUid = 1, chapterIdx = 1, title = "实践论"),
                WeReadChapter(chapterUid = 2, chapterIdx = 2, title = "矛盾论"),
            ),
            reviews = emptyList(),
        )

        assertEquals(10, drafts.size)
        assertTrue(drafts.all { it.content.length in 40..95 })
        assertTrue(drafts.all { it.example.length >= 120 })
        assertTrue(drafts.any { it.example.contains("实践论") || it.example.contains("矛盾论") })
        assertTrue(drafts.any { it.example.contains("先") && it.example.contains("再") })
    }

    @Test
    fun missingApiKeyUsesDedicatedErrorType() {
        val error = assertFailsWith<WeReadSkillException> { requireWeReadApiKey("   ") }

        assertEquals(WeReadSkillErrorType.MissingApiKey, error.type)
        assertEquals("请先在 Skills 中配置微信读书 Token", weReadUserMessage(error))
    }

    @Test
    fun preferEnabledWeReadSkillTokenOverLegacySetting() {
        val token = resolveWeReadTokenFromSkillOrLegacy(
            skillToken = " skill-token ",
            skillEnabled = true,
            legacyStored = " legacy-token ",
            isEncrypted = { false },
            decrypt = { it },
            onLegacyPlaintext = {},
        )

        assertEquals("skill-token", token)
    }

    @Test
    fun disabledWeReadSkillIsTreatedAsMissingToken() {
        val token = resolveWeReadTokenFromSkillOrLegacy(
            skillToken = "skill-token",
            skillEnabled = false,
            legacyStored = "legacy-token",
            isEncrypted = { false },
            decrypt = { it },
            onLegacyPlaintext = {},
        )

        assertEquals("", token)
    }

    @Test
    fun legacyWeReadTokenStillWorksWhenSkillRowIsMissing() {
        var upgraded = ""
        val token = resolveWeReadTokenFromSkillOrLegacy(
            skillToken = null,
            skillEnabled = false,
            legacyStored = " legacy-token ",
            isEncrypted = { false },
            decrypt = { it },
            onLegacyPlaintext = { upgraded = it },
        )

        assertEquals("legacy-token", token)
        assertEquals("legacy-token", upgraded)
    }

    @Test
    fun serviceReadsOnlyBuiltInWeReadSkillRow() {
        val source = java.io.File("src/commonMain/kotlin/com/dailysatori/service/book/WeReadSkillService.kt").readText()

        assertTrue(source.contains("skillConfigRepository.getBuiltInByTemplateId(BuiltInSkillTemplates.weRead)"))
        assertTrue(!source.contains("skillConfigRepository.getByTemplateId(BuiltInSkillTemplates.weRead)"))
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
              {"title":"待上架书也要先界定真实问题。","content":"资料不足时，观点只能围绕书名、作者和简介界定问题，不能假装拥有原文或不存在的章节。","example":"例如一本供应链新书还未正式上架，系统只知道书名、作者和一句简介。案例里，一家制造企业先发现采购、仓储和销售各自使用不同预测口径，会议一度互相推责；负责人再把简介中的端到端协同作为线索，追问订单、库存和交付为何脱节，最后用统一指标重新安排补货节奏，避免把缺货责任简单推给某个部门。"},
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
              {"title":"短","content":"资料不足时，观点只能围绕书名、作者和简介界定问题，不能假装拥有原文或不存在的章节。","example":"例如一本供应链新书还未正式上架，系统只知道书名、作者和一句简介。案例里，一家制造企业先发现采购、仓储和销售各自使用不同预测口径，会议一度互相推责；负责人再把简介中的端到端协同作为线索，追问订单、库存和交付为何脱节，最后用统一指标重新安排补货节奏，避免把缺货责任简单推给某个部门。"}
            ]
        """.trimIndent()

        val drafts = parseAiFallbackViewpointJson(json)

        assertEquals(0, drafts.size)
    }

    @Test
    fun skipsMalformedAiFallbackViewpointFields() {
        val json = """
            [
              {"title":{"text":"标题不是字符串"},"content":["内容不是字符串"],"example":"例如一本供应链新书还未正式上架，系统只知道书名、作者和一句简介。案例里，一家制造企业先发现采购、仓储和销售各自使用不同预测口径，会议一度互相推责；负责人再把简介中的端到端协同作为线索，追问订单、库存和交付为何脱节，最后用统一指标重新安排补货节奏，避免把缺货责任简单推给某个部门。"},
              {"title":"待上架书也要先界定真实问题。","content":"资料不足时，观点只能围绕书名、作者和简介界定问题，不能假装拥有原文或不存在的章节。","example":"例如一本供应链新书还未正式上架，系统只知道书名、作者和一句简介。案例里，一家制造企业先发现采购、仓储和销售各自使用不同预测口径，会议一度互相推责；负责人再把简介中的端到端协同作为线索，追问订单、库存和交付为何脱节，最后用统一指标重新安排补货节奏，避免把缺货责任简单推给某个部门。"}
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
        assertTrue(prompt.contains("书中核心观点"))
        assertTrue(prompt.contains("不要写书评"))
        assertTrue(prompt.contains("不要写读后感"))
        assertTrue(prompt.contains("观点总结控制在 40 到 90 个中文字符"))
        assertTrue(prompt.contains("书中情境"))
        assertTrue(prompt.contains("像一个小故事"))
        assertTrue(prompt.contains("供应链架构师"))
    }

    @Test
    fun detectsSparseWeReadMaterialAsInsufficient() {
        val info = WeReadBookInfo(bookId = "123", title = "待上架新书", intro = "")
        val drafts = buildWeReadViewpointDrafts(info, chapters = emptyList(), reviews = emptyList())

        assertEquals(false, hasSufficientWeReadMaterial(info, emptyList(), emptyList(), drafts))
    }

    @Test
    fun detectsIntroOnlyWeReadMaterialAsInsufficient() {
        val info = WeReadBookInfo(
            bookId = "123",
            title = "待上架新书",
            intro = "这是一段足够长的图书简介，用来描述主题、背景、问题意识和读者可能获得的启发，但它仍然只是简介。",
        )
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

        assertEquals(WeReadSkillErrorType.MissingAiFallbackConfig, error.type)
        assertEquals("微信读书资料不足，请先配置默认 AI 模型后重试", weReadUserMessage(error))
    }

    @Test
    fun aiFallbackFailureUsesDedicatedErrorMessage() {
        val error = WeReadSkillException(
            WeReadSkillErrorType.AiFallbackFailure,
            "AI 观点生成失败，请稍后重试",
        )

        assertEquals("AI 观点生成失败，请稍后重试", weReadUserMessage(error))
    }

    @Test
    fun sparseWeReadMaterialFailsWithAiFallbackFailureWhenAiDraftsAreTooFew() = runBlocking {
        val book = BookSearchResult(title = "待上架新书", author = "作者", introduction = "一句简介")
        val info = WeReadBookInfo(bookId = "123", title = "待上架新书", intro = "")
        val generator = object : BookAiFallbackGenerator {
            override suspend fun generate(
                book: BookSearchResult,
                info: WeReadBookInfo,
                chapters: List<WeReadChapter>,
                reviews: List<WeReadReview>,
            ): List<BookViewpointDraft> = emptyList()
        }

        val error = assertFailsWith<WeReadSkillException> {
            selectWeReadOrAiViewpoints(book, info, emptyList(), emptyList(), generator)
        }

        assertEquals(WeReadSkillErrorType.AiFallbackFailure, error.type)
        assertEquals("AI 观点生成失败，请稍后重试", weReadUserMessage(error))
    }

    @Test
    fun sparseWeReadMaterialUsesAiFallbackGenerator() = runBlocking {
        val book = BookSearchResult(title = "待上架新书", author = "作者", introduction = "一句简介")
        val info = WeReadBookInfo(bookId = "123", title = "待上架新书", intro = "")
        val aiDrafts = List(10) { index ->
            BookViewpointDraft(
                title = "AI 生成观点 ${index + 1} 要先界定问题。",
                content = "当微信读书材料不足时，系统应清楚承认观点来自 AI，并基于有限元数据建立判断边界，避免把不存在的目录、原文或书评写成事实，从而让读者知道这些观点是辅助理解而不是资料摘录。",
                example = "例如一位读者添加一本待上架新书时，系统只有书名和一句简介。AI 没有编造章节，而是围绕简介中的核心问题生成场景：团队先确认信息缺口，再把观点用于提出阅读问题，等正式资料补齐后再回到原书内容验证。",
            )
        }
        val generator = object : BookAiFallbackGenerator {
            override suspend fun generate(
                book: BookSearchResult,
                info: WeReadBookInfo,
                chapters: List<WeReadChapter>,
                reviews: List<WeReadReview>,
            ): List<BookViewpointDraft> = aiDrafts
        }

        val result = selectWeReadOrAiViewpoints(book, info, emptyList(), emptyList(), generator)

        assertEquals(BookViewpointSource.AiFallback, result.source)
        assertEquals(aiDrafts, result.drafts)
    }
}
