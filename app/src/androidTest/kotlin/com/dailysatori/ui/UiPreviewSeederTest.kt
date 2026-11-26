package com.dailysatori.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dailysatori.bookkeeping.LedgerKind
import com.dailysatori.core.lifearchive.EncryptedLifeArchiveRepository
import com.dailysatori.data.repository.BookRepository
import com.dailysatori.data.repository.BookViewpointRepository
import com.dailysatori.data.repository.BookkeepingRepository
import com.dailysatori.data.repository.DiaryMonthSummaryRepository
import com.dailysatori.data.repository.DiaryRepository
import com.dailysatori.data.repository.DiaryThoughtRepository
import com.dailysatori.data.repository.ReminderRepository
import com.dailysatori.data.repository.SettingRepository
import com.dailysatori.platform.DatabaseDriverFactory
import com.dailysatori.platform.PlatformContext
import com.dailysatori.service.diary.DiaryThought
import com.dailysatori.service.diary.DiaryThoughtArchive
import com.dailysatori.service.diary.DiaryThoughtEvidence
import com.dailysatori.service.lifearchive.LifeArchiveField
import com.dailysatori.service.lifearchive.LifeArchiveRecord
import com.dailysatori.service.lifearchive.LifeArchiveRepository
import com.dailysatori.service.opportunity.NewsOpportunity
import com.dailysatori.service.opportunity.OpportunityDraft
import com.dailysatori.service.opportunity.OpportunityPreview
import com.dailysatori.service.opportunity.ReadNewsArticle
import com.dailysatori.service.reminder.ReminderDraft
import com.dailysatori.service.reminder.ReminderProfileSnapshot
import com.dailysatori.service.security.SecretCipher
import com.dailysatori.shared.db.DailySatoriDatabase
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 仅在显式传入 `-e seed_ui_demo yes` 时运行的非破坏性测试数据注入器：
 * 保留设备已有数据，仅为当前为空的模块补充真实感中文样本以便真机视觉验收。
 */
@RunWith(AndroidJUnit4::class)
class UiPreviewSeederTest {
    private lateinit var db: DailySatoriDatabase
    private lateinit var diaryRepo: DiaryRepository
    private lateinit var monthSummaryRepo: DiaryMonthSummaryRepository
    private lateinit var bookRepo: BookRepository
    private lateinit var viewpointRepo: BookViewpointRepository
    private lateinit var reminderRepo: ReminderRepository
    private lateinit var bookkeepingRepo: BookkeepingRepository
    private lateinit var lifeArchiveRepo: LifeArchiveRepository

    @Test
    fun seedRealisticUiDemoData() = runBlocking {
        assumeTrue(
            "Pass -e seed_ui_demo yes to seed UI preview data",
            InstrumentationRegistry.getArguments().getString("seed_ui_demo") == "yes",
        )
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val platform = PlatformContext(context)
        val driver = DatabaseDriverFactory(platform).createDriver()
        try {
            db = DailySatoriDatabase(driver)
            diaryRepo = DiaryRepository(db, driver)
            monthSummaryRepo = DiaryMonthSummaryRepository(db)
            bookRepo = BookRepository(db)
            viewpointRepo = BookViewpointRepository(db)
            reminderRepo = ReminderRepository(db)
            bookkeepingRepo = BookkeepingRepository(db)
            lifeArchiveRepo = EncryptedLifeArchiveRepository(File(context.noBackupFilesDir, "life_archive"), SecretCipher(platform))

            val now = Clock.System.now().toEpochMilliseconds()
            val zone = TimeZone.currentSystemDefault()
            val today = Clock.System.todayIn(zone)
            val monthKey = "%04d-%02d".format(today.year, today.monthNumber)
            val q = db.dailySatoriQueries
            val oppQ = db.newsOpportunityQueries

            seedHeadlines(now, today.toString())
            seedOpportunities(oppQ, now)
            seedDiaries(now, monthKey)
            seedBooks()
            seedIdeaTopics(q, now)
            seedReminders(today)
            seedLedger(now)
            seedLifeArchive()
            seedAiChat(q, now)
            seedArticles(q, now)
            seedDiaryThoughts(q, now)
        } finally {
            driver.close()
        }
    }

    private fun seedHeadlines(now: Long, todayIso: String) {
        val q = db.dailySatoriQueries
        val content = """
            ## 今日要闻
            今日技术社区聚焦两件事：端侧模型推理显存开销进一步下降，以及个人知识工具重新回归本地优先与可控 AI 协作。

            ## 今日要点
            - 端侧推理框架推出混合精度打包，8B 模型内存占用再降 35%：开源社区发布面向移动 ARM 芯片的新量化格式，在中端手机上也能保持稳定首字延迟，离线总结与语音润色进入日常可用区间。[R1]
            - 「本地优先 + 可控 AI」成为个人效率工具新共识：多位独立产品作者复盘了过去一年的留存数据，用户更倾向把长期日记与私密想法保留在本地加密库中，再按需脱敏调用模型。[R2]
            - Android 新版收紧后台常驻配额，长时录音需显式前移通知：系统对静默后台连接执行更严格省电策略，语音转写与异步同步任务需要做好断点续作与任务状态透明化。[R3]
        """.trimIndent()
        val existing = q.selectUnifiedNewsSummaries().executeAsList().firstOrNull { it.window_key == "demo_daily" }
        if (existing != null) {
            q.updateUnifiedNewsSummary(
                window_start_ms = now - 86_400_000L,
                window_end_ms = now,
                title = "今日要闻 · $todayIso",
                content = content,
                status = "success",
                error_message = null,
                source_warnings = null,
                generated_at = now,
                updated_at = now,
                summary_date = existing.summary_date,
                window_key = "demo_daily",
            )
            return
        }
        q.insertUnifiedNewsSummary(
            summary_date = todayIso,
            window_key = "demo_daily",
            window_start_ms = now - 86_400_000L,
            window_end_ms = now,
            title = "今日要闻 · $todayIso",
            content = content,
            status = "success",
            error_message = null,
            source_warnings = null,
            generated_at = now,
            created_at = now,
            updated_at = now,
        )
        val summaryId = q.selectUnifiedNewsSummaries().executeAsList().firstOrNull { it.window_key == "demo_daily" }?.id ?: return
        q.insertUnifiedNewsSource(summaryId, "R1", "remote_article", null, "github.com", "https://github.com/local-ai/quant-kit", "quant-kit: Mobile 8B Quantization", "端侧量化压缩新方案", now)
        q.insertUnifiedNewsSource(summaryId, "R2", "remote_article", null, "v2ex.com", "https://v2ex.com/t/109283", "为什么大家重新回到本地优先笔记工具", "本地优先产品复盘", now)
        q.insertUnifiedNewsSource(summaryId, "R3", "remote_article", null, "developer.android.com", "https://developer.android.com/about/versions", "Android Foreground Service Best Practices", "后台任务与前台服务规范", now)
    }

    private fun seedAiChat(q: com.dailysatori.shared.db.DailySatoriQueries, now: Long) {
        val sessionId = "demo-session-editorial"
        q.ensureChatSession(sessionId, now - 600_000L)
        if (q.selectChatBySession(sessionId).executeAsList().isNotEmpty()) return
        q.insertChat(
            session_id = sessionId,
            role = "user",
            content = "帮我梳理一下我最近日记和《思考，快与慢》笔记里的共同主线。",
            search_results = null,
            steps = null,
            created_at = now - 540_000L,
        )
        q.insertChat(
            session_id = sessionId,
            role = "assistant",
            content = "你最近一周的记录里有两条很清晰的共鸣：\n\n1. **用结构对抗「眼见即为事实」**：你在日记里反复提到删减重复资讯源，这与卡尼曼「不凭残缺线索急于下判断」的观点一致。\n2. **克制的工具更能促成系统 2 慢思考**：把界面噪声和无关推送收起来之后，散步语音随记与长期点子更容易沉淀成可执行动作。\n\n建议把「语音随记自动提炼待办」列为本周最优先推进的点子主题。",
            search_results = null,
            steps = null,
            created_at = now - 480_000L,
        )
    }

    private fun seedArticles(q: com.dailysatori.shared.db.DailySatoriQueries, now: Long) {
        if (q.selectArticles().executeAsList().any { it.is_favorite == 1L }) return
        q.insertArticle(
            title = "Local-First Software: You Own Your Data, in spite of the Cloud",
            ai_title = "本地优先软件设计原则：在云端时代重新拿回个人数据主权",
            ai_content = "本文系统梳理了本地优先（Local-First）软件的七项核心特征：即时响应、离线可用、跨端同步与长期可读。作者强调，个人知识工具应当把 SQLite 等开放本地格式作为主存，云端只承担加密同步与按需算力角色。",
            ai_markdown_content = """
                ## 为什么本地优先重新成为主流

                在过去十年里，许多笔记与阅读工具把数据完全托管在云端服务器中。一旦网络不稳定或服务停止运营，用户积累数年的思考记录就面临难以导出的风险。

                ### 七项关键原则
                - **零等待响应**：所有读写直接命中本地数据库，打开即可开始阅读与记录。
                - **离线完整可用**：在地铁、航班或弱网环境下，查阅日记、书籍观点与历史文章不受任何影响。
                - **可控的 AI 协作**：先在本地完成筛选与沉淀，再按需将脱敏上下文送入模型，而非无条件上传全部隐私。

                > 真正陪伴十年的个人工具，应该像一本放在桌边的纸质笔记——即使断网，它依然完整属于你。
            """.trimIndent(),
            url = "https://www.inkandswitch.com/local-first/",
            is_favorite = 1L,
            comment = "值得反复重读的架构基准",
            status = "completed",
            cover_image = null,
            cover_image_url = null,
            pub_date = now - 86_400_000L,
            created_at = now - 43_200_000L,
            updated_at = now - 43_200_000L,
        )
        q.insertArticle(
            title = "The Quiet Craft of Editorial Typography on Small Screens",
            ai_title = "移动端社论排版美学：如何在小屏上营造安静的深读体验",
            ai_content = "探讨移动阅读应用如何通过暖纸底色、衬线标题与克制的留白节奏降低视觉疲劳，让长文与摘录不再像高噪信息流。",
            ai_markdown_content = """
                ## 少即是多：从信息流回到纸面

                高饱和强调色与密集卡片边框会在短时间内制造「信息丰富」的错觉，却会迅速耗尽读者的注意力。

                ### 三条实用细节
                1. 用低对比暖纸画布（`#F7F5F0`）与纯暖白纸面（`#FFFDF9`）建立自然层次，避免生硬投影。
                2. 标题采用衬线体建立阅读仪式感，正文与控件保持高可读无衬线体。
                3. 把次要元数据收敛为单行轻灰注脚，首屏尽早把空间还给真正要读的句子。
            """.trimIndent(),
            url = "https://editorial.design/quiet-mobile-reading",
            is_favorite = 1L,
            comment = null,
            status = "completed",
            cover_image = null,
            cover_image_url = null,
            pub_date = now - 172_800_000L,
            created_at = now - 86_400_000L,
            updated_at = now - 86_400_000L,
        )
    }

    private fun seedDiaryThoughts(q: com.dailysatori.shared.db.DailySatoriQueries, now: Long) {
        val repo = DiaryThoughtRepository(SettingRepository(db))
        val existing = repo.load()
        if (existing.thoughts.isNotEmpty() && !existing.fingerprint.startsWith("demo-editorial-thoughts-")) return
        val roots = q.selectDiaryRoots().executeAsList()
        val quietDiary = roots.firstOrNull { it.content.contains("安静感") } ?: roots.firstOrNull() ?: return
        val walkDiary = roots.firstOrNull { it.content.contains("河边散步") } ?: quietDiary
        val feedDiary = roots.firstOrNull { it.content.contains("精简订阅源") } ?: walkDiary
        repo.save(
            DiaryThoughtArchive(
                fingerprint = "demo-editorial-thoughts-v3",
                diaryCount = roots.size.coerceAtLeast(3),
                generatedAt = now - 1_800_000L,
                thoughts = listOf(
                    DiaryThought(
                        category = "价值观",
                        statement = "好工具的核心价值在于「安静感」：先去掉噪音与冗余导航，让值得读的句子第一时间浮现。",
                        basis = "明确表达",
                        evidence = listOf(
                            DiaryThoughtEvidence(quietDiary.id, "真正反复回看的从来不是高密度仪表盘，而是排版舒服、打开就能静下心读完的长段思考。"),
                        ),
                    ),
                    DiaryThought(
                        category = "做事准则",
                        statement = "在信息输入上保持克制，用每日精选要闻替代高频刷榜，把精力留给长期推进的少数主题。",
                        basis = "AI归纳",
                        evidence = listOf(
                            DiaryThoughtEvidence(feedDiary.id, "删掉重复推送的资讯流之后，每天早上十分钟看完今日要闻和产品机会就足够掌握关键变化"),
                            DiaryThoughtEvidence(walkDiary.id, "把无关推送关掉之后，很多卡了两周的细节一下就通顺了。"),
                        ),
                    ),
                    DiaryThought(
                        category = "思维方式",
                        statement = "坚持本地优先与可控 AI 边界：长期私密记录留在本地加密库，云端仅用于按需深度归纳。",
                        basis = "AI归纳",
                        evidence = listOf(
                            DiaryThoughtEvidence(walkDiary.id, "晚饭后沿着河边走了四十分钟，顺手理清了本地优先同步的边界。"),
                        ),
                    ),
                ),
            ),
        )
    }

    private fun seedOpportunities(oppQ: com.dailysatori.shared.db.NewsOpportunityQueries, now: Long) {
        if (oppQ.selectOpportunityItems().executeAsList().isNotEmpty()) return
        val json = Json { encodeDefaults = true }
        val items = listOf(
            NewsOpportunity(
                id = "demo-opp-1",
                article = ReadNewsArticle("demo:voice-notes", "语音笔记从转写到行动项的断层观察", "正文", source = "Hacker News", url = "https://news.ycombinator.com/item?id=42001", readAt = now),
                title = "语音随记到「可执行待办」的零摩擦闭环",
                category = "语音与效率",
                fact = "社区调研显示，70% 以上的散步/通勤语音录音只停留在长段转写稿，用户很少回看二次整理。",
                relevance = "个人知识工具用户：希望说一段话后自动沉淀成可读日记与可执行待办。\n与关注方向相关；推断：结合现有离线录音与润色链路，保存后直接提供轻量待办/点子提炼卡可显著提升复用率。",
                action = "在润色预览下方增加一键提炼卡片。",
                caveat = "需避免自动打扰不需要待办的随想。",
                quote = "Most voice memos become write-only archives.",
                createdAt = now - 3_600_000L,
                preview = OpportunityPreview(
                    title = "语音随记到「可执行待办」的零摩擦闭环",
                    reason = "多数语音录音只停留在转写稿，事后不整理就沉底。",
                    opportunity = "在现有语音日记润色后提供「提炼待办 / 收为点子」一键动作，零打断完成闭环。",
                ),
            ),
            NewsOpportunity(
                id = "demo-opp-2",
                article = ReadNewsArticle("demo:cross-hotlist", "多平台科技热榜跨站去重与降噪实践", "正文", source = "Linux DO", url = "https://linux.do/t/topic/88201", readAt = now),
                title = "跨平台热榜同题聚合与「只看核心三条」模式",
                category = "信息降噪",
                fact = "同一条开源项目或行业事件常在 GitHub、HN、V2EX 同日刷屏，读者重复点开耗时严重。",
                relevance = "每日高频阅读者：更在意事实本身与跨站讨论视角，而非重复刷榜。\n与关注方向相关；推断：利用现有多平台热榜抓取结果，合并同 URL/同主题的中文摘要并折叠多站排名。",
                action = "在热榜详情顶部聚合多平台排名。",
                caveat = "需保留原讨论链接入口。",
                quote = "Readers want synthesis, not five tabs of the same launch.",
                createdAt = now - 7_200_000L,
                preview = OpportunityPreview(
                    title = "跨平台热榜同题聚合与「只看核心三条」模式",
                    reason = "同条热点在多个科技社区反复出现，信息噪音高。",
                    opportunity = "用统一中文事实摘要替代多端重复阅读，顶部折叠呈现各平台排名。",
                ),
            ),
        )
        items.forEach { item ->
            val sourceJson = json.encodeToString(item.article)
            val sourceId = "demo-src-${item.id}"
            oppQ.insertOpportunitySource(sourceId, sourceJson)
            val draft = OpportunityDraft(item.title, item.category, item.fact, item.relevance, item.action, item.caveat, item.quote, item.relevanceScore, item.actionabilityScore, item.preview)
            oppQ.insertOpportunityItem(item.id, item.article.key, sourceId, item.createdAt, false, false, null, null, json.encodeToString(draft))
        }
    }

    private fun seedDiaries(now: Long, monthKey: String) {
        val q = db.dailySatoriQueries
        if (q.selectDiaryRoots().executeAsList().isNotEmpty()) return
        val dayMs = 86_400_000L
        // Entry 1: Thread root + 2 replies + AI thread summary
        q.insertDiary(
            content = "# 重新思考知识工具的「安静感」\n早上翻了一遍过去半年的记录，发现真正反复回看的从来不是高密度仪表盘，而是排版舒服、打开就能静下心读完的长段思考。好工具应该像一本顺手的纸质笔记本。",
            mood = "平静",
            created_at = now - 2 * 3_600_000L,
            updated_at = now - 2 * 3_600_000L,
        )
        val rootId = q.selectDiaryRoots().executeAsList().first().id
        q.insertDiaryReply("下午把顶栏冗余的页名去掉后，整个首屏一下子透气了很多，阅读节奏顺畅了不少。", "专注", now - 3_600_000L, now - 3_600_000L, rootId)
        q.insertDiaryReply("决定把「素纸墨青」沉淀成统一规范，后续所有页面按同一套纸面层次演进。", "舒展", now - 1_800_000L, now - 1_800_000L, rootId)
        q.upsertDiaryThreadSummary(
            diary_id = rootId,
            source_revision = 3L,
            summary_revision = 3L,
            summary = "围绕「知识工具的安静感」做了三次推进：从复盘过往记录得出纸质笔记本般的阅读体验，到去掉冗余顶栏释放首屏空间，最终确立「素纸墨青」为全应用视觉规范。",
            status = "ready",
            error_message = "",
            generated_at = now - 1_200_000L,
            updated_at = now - 1_200_000L,
        )
        // Entry 2: Reflective entry with tags & mood
        diaryRepo.insert(
            content = "# 河边散步后的架构随想\n晚饭后沿着河边走了四十分钟，顺手理清了本地优先同步的边界。把无关推送关掉之后，很多卡了两周的细节一下就通顺了。",
            tags = "产品思考,随想,设计",
            mood = "舒展",
        )
        // Entry 3: Pull-quote short diary
        q.insertDiary(
            content = "> 克制不是减少能力，而是让真正值得读的句子、和当下要做的决定，第一时间浮现出来。",
            mood = null,
            created_at = now - 2 * dayMs,
            updated_at = now - 2 * dayMs,
        )
        // Entry 4: Previous month entry (to verify month separation)
        q.insertDiary(
            content = "# 精简订阅源的第一周\n删掉重复推送的资讯流之后，每天早上十分钟看完今日要闻和产品机会就足够掌握关键变化，心里踏实很多。",
            mood = "轻快",
            created_at = now - 32 * dayMs,
            updated_at = now - 32 * dayMs,
        )
        monthSummaryRepo.upsert(
            monthKey = monthKey,
            summary = "这个月的核心主线是「收敛与专注」：把新闻阅读、语音随记和长期想法重新理顺成一套更安静、低打扰的日常节奏，并确立了素纸墨青的统一阅读美学。",
            diaryCount = 3L,
            latestDiaryUpdatedAt = now,
            status = "ready",
            errorMessage = null,
            generatedAt = now,
        )
    }

    private suspend fun seedBooks() {
        if (bookRepo.getAll().first().isNotEmpty()) return
        val bookId = bookRepo.insertAndReturnId(
            title = "思考，快与慢",
            author = "丹尼尔·卡尼曼",
            category = "心理学 · 决策",
            coverImage = "",
            introduction = "诺贝尔经济学奖得主卡尼曼系统梳理人类大脑系统 1（直觉快思考）与系统 2（理性慢思考）的运作机制与常见认知偏差。",
        )
        viewpointRepo.insert(
            bookId = bookId,
            title = "眼见即为事实（WYSIATI）：大脑擅长用残缺信息编织自洽故事",
            content = "系统 1 并不关心信息是否完整，只要手头现有的几条线索能拼出一个逻辑顺滑的故事，它就会立刻产生高度自信的判断。\n\n因此，**衡量一个结论是否可靠，不能看故事讲得有多顺，而要看哪些关键证据尚未出现。**",
            example = "在评估一个新产品想法时，仅凭两三条社区好评就容易高估需求强度；有意识地列出「最可能失败的三个前提」，才能迫使系统 2 介入校验。",
        )
        viewpointRepo.insert(
            bookId = bookId,
            title = "事前验尸法：在决策落地前先假设项目已经失败",
            content = "在团队即将拍板前，设想一年后该项目彻底失败，请每人独立写下失败原因。这种逆向设问能有效打破群体盲从，让被压抑的真实风险浮出水面。",
            example = "发版或大改版前花 10 分钟写下「用户最可能弃用的 3 个原因」，往往能提前避开体验硬伤。",
        )
    }

    private fun seedIdeaTopics(q: com.dailysatori.shared.db.DailySatoriQueries, now: Long) {
        if (q.selectMainIdeaTopics().executeAsList().isNotEmpty()) return
        q.insertIdeaTopic(
            id = "demo-idea-1",
            title = "语音随记自动提炼待办与点子闭环",
            description = "散步录音转写润色后，低打扰识别出其中的待办事项与产品灵感，一键转入提醒或点子主题。",
            provenance_summary = "来自 Hacker News 产品机会与 11 月日记随想",
            conclusions = "先在润色确认页下方以轻卡片呈现，不自动写入。",
            next_action = "梳理离线语音润色后的结构化输出格式",
            status = "researching",
            merged_into_topic_id = null,
            context_revision = 1L,
            created_at = now - 86_400_000L,
            updated_at = now - 3_600_000L,
        )
        q.insertIdeaTopic(
            id = "demo-idea-2",
            title = "Daily Satori 素纸墨青阅读体验升级",
            description = "统一全应用纸墨色板、衬线标题节奏与方案 A 紧凑上下文顶栏，减少仪表盘噪音。",
            provenance_summary = "来自日常复盘日记",
            conclusions = "四大主屏与共享设置底座已落地验证。",
            next_action = "补齐详情页与业务子页微调",
            status = "advancing",
            merged_into_topic_id = null,
            context_revision = 2L,
            created_at = now - 172_800_000L,
            updated_at = now - 600_000L,
        )
    }

    private fun seedReminders(today: kotlinx.datetime.LocalDate) {
        if (db.dailySatoriQueries.selectAllReminders().executeAsList().isNotEmpty()) return
        val profile = ReminderProfileSnapshot.standard()
        reminderRepo.createConfirmedOnce(
            ReminderDraft(
                id = "demo-rem-1",
                content = "复查本周阅读摘要与待整理点子",
                startDate = today,
                endDate = today,
                firstReminderTime = LocalTime(20, 30),
            ),
            profile,
        )
        val tomorrow = today.plus(1, DateTimeUnit.DAY)
        reminderRepo.createConfirmedOnce(
            ReminderDraft(
                id = "demo-rem-2",
                content = "域名 dailysatori.app 年度续费核对",
                startDate = tomorrow,
                endDate = tomorrow,
                firstReminderTime = LocalTime(10, 0),
            ),
            profile,
        )
    }

    private fun seedLedger(now: Long) {
        if (bookkeepingRepo.snapshot().entries.isNotEmpty()) return
        bookkeepingRepo.addManual("demo", "evt-1", "手冲咖啡与全麦面包", now - 3_600_000L, "38.00", "CNY", LedgerKind.EXPENSE, "蓝瓶咖啡")
        bookkeepingRepo.addManual("demo", "evt-2", "技术图书《思考，快与慢》精装版", now - 86_400_000L, "69.50", "CNY", LedgerKind.EXPENSE, "京东图书")
        bookkeepingRepo.addManual("demo", "evt-3", "云端 API 额度充值", now - 172_800_000L, "100.00", "CNY", LedgerKind.EXPENSE, "DeepSeek")
    }

    private suspend fun seedLifeArchive() {
        if (lifeArchiveRepo.records().isNotEmpty()) return
        val categories = lifeArchiveRepo.categories()
        val catId = categories.firstOrNull()?.id ?: return
        lifeArchiveRepo.save(
            LifeArchiveRecord(
                id = "demo-la-1",
                title = "dailysatori.app 主域名",
                categoryId = catId,
                body = "Cloudflare Registrar 自动续费已开启，DNS 托管在同一账号。",
                fields = listOf(
                    LifeArchiveField("注册商", "Cloudflare"),
                    LifeArchiveField("到期日", "2027-04-18"),
                ),
            ),
            expectedUpdatedAt = null,
        )
        lifeArchiveRepo.save(
            LifeArchiveRecord(
                id = "demo-la-2",
                title = "AI 深度思考模型订阅",
                categoryId = categories.getOrNull(1)?.id ?: catId,
                body = "用于日常深度总结、思想归纳与读书观点梳理。",
                fields = listOf(
                    LifeArchiveField("计费周期", "按月自动扣费"),
                    LifeArchiveField("备注", "仅按需脱敏调用"),
                ),
            ),
            expectedUpdatedAt = null,
        )
    }
}
