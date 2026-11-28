package com.dailysatori.service.assistant

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dailysatori.data.repository.*
import com.dailysatori.service.ai.*
import com.dailysatori.service.assistant.data.AssistantDataGateway
import com.dailysatori.service.assistant.data.AssistantNewsDigest
import kotlinx.datetime.toLocalDateTime
import com.dailysatori.shared.db.DailySatoriDatabase
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assume.assumeTrue
import kotlin.test.*

class KoogAssistantLiveTest {
    @Test
    fun configuredStreamingAndIncrementalSummaryUseExistingProviderAndPurposes() = runBlocking<Unit> {
        assumeTrue("Set DAILY_AI_LIVE_TEST=1 for a small real assistant test", System.getenv("DAILY_AI_LIVE_TEST") == "1")
        val file = File("../.local/ai-test.json")
        require(file.isFile) { "缺少 .local/ai-test.json，真实流式与归纳验证未完成" }
        val values = runCatching { Json.parseToJsonElement(file.readText()).jsonObject }.getOrElse { error("AI 测试配置格式无效") }
        fun required(key: String) = values[key]?.jsonPrimitive?.content?.trim()?.takeIf(String::isNotBlank) ?: error("AI 测试配置缺少 $key")
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            DailySatoriDatabase.Schema.create(driver)
            val db = DailySatoriDatabase(driver)
            val repo = AIConfigRepository(db)
            repo.insert(required("provider"), required("apiAddress"), required("apiToken"), required("modelName"), 1)
            SettingRepository(db).upsert(PersistentAssistantConversation.CONSENT_KEY, "true")
            val responseTypes = mutableListOf<String>()
            HttpClient(OkHttp) {
                install(HttpTimeout) { requestTimeoutMillis = 120_000; connectTimeoutMillis = 15_000 }
                engine { config { addInterceptor { chain -> chain.proceed(chain.request()).also { responseTypes += it.header("Content-Type").orEmpty() } } } }
            }.use { client ->
                val ai = AiService(client)
                val configs = AiConfigService(repo)
                val partials = mutableListOf<String>()
                val chats = ChatConversationRepository(db)
                val assistant = KoogReadOnlyAssistant(ai, configs, AssistantDataGateway(driver), AssistantPrivacyRepository(driver), chats,
                    onPartial = { partials += it })
                val answer = assistant.answer("stream-live", "请用120到180个中文字、三句自然话解释：蓝杉计划的代号是青石，先整理读书记录，再逐步完善。只用给出的内容，不调用工具。")
                assertTrue(answer.contains("青石"))
                assertTrue(partials.filter { it.isNotEmpty() }.distinct().size >= 2, "真实回复未产生多个安全增量")
                assertEquals(answer, partials.last())
                assertTrue(responseTypes.first().contains("text/event-stream"), "配置的实际入口未返回SSE，不能将JSON回退算作真实流式通过")
                val original = "蓝杉项目用于整理读书记录。".repeat(30) + "项目的唯一验收暗号是苔蓝97。"
                val source = chats.acceptUserInput("summary-live", original)
                repeat(12) { chats.acceptUserInput("summary-live", "近期普通对话$it") }
                val extraction = com.dailysatori.service.memory.MemoryExtractService(ai, configs, MemoryRepository(db))
                var calls = 0
                val summarizer = AssistantConversationSummarizer(driver, segmentSize = 300) { previous, text ->
                    check(++calls <= 3) { "真实归纳调用超过基础验证预算" }
                    extraction.summarizeConversation(previous, text)
                }
                val tasks = AsyncTaskRepository(db)
                val taskId = AssistantHistoryBackfill(driver).enqueueSummary("summary-live", Long.MAX_VALUE)!!
                val runner = com.dailysatori.service.asynctask.AsyncTaskRunner(tasks,
                    com.dailysatori.service.asynctask.AsyncTaskHandlerRegistry(listOf(AssistantSummaryTaskHandler(summarizer))))
                var completed = false
                for (step in 0..4) {
                    if (runner.run(taskId) == com.dailysatori.service.asynctask.AsyncTaskRunOutcome.Succeeded) { completed = true; break }
                    assertEquals("", tasks.getById(taskId)!!.last_error_code, "真实归纳失败，不自动重复付费调用")
                }
                assertTrue(completed)
                assertEquals(2, calls, "应分两片归纳，而不是一次整篇请求")
                assertTrue(AssistantMemoryService(driver).summary("summary-live", Long.MAX_VALUE).contains("苔蓝97"), "滚动归纳遗漏真实尾部证据")
                val coverage = Json.parseToJsonElement(AssistantStore(driver).text("SELECT summary_sources_json FROM assistant_session WHERE id='summary-live'")!!).jsonArray.single().jsonObject
                assertEquals(source, coverage["id"]!!.jsonPrimitive.long)
                assertEquals(original.length, coverage["offset"]!!.jsonPrimitive.int)
            }
        }
    }

    @Test
    fun configuredModelRetrievesCreatesBusinessActionsAndRemembersSourcedPreference() = runBlocking<Unit> {
        assumeTrue("Set DAILY_AI_LIVE_TEST=1 for a small real assistant test", System.getenv("DAILY_AI_LIVE_TEST") == "1")
        val file = File("../.local/ai-test.json")
        require(file.isFile) { "缺少 .local/ai-test.json，助手真实验证未完成" }
        val values = runCatching { Json.parseToJsonElement(file.readText()).jsonObject }
            .getOrElse { error("AI 测试配置格式无效，不输出配置内容") }
        fun required(key: String) = values[key]?.jsonPrimitive?.content?.trim()?.takeIf(String::isNotBlank)
            ?: error("AI 测试配置缺少 $key")
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            DailySatoriDatabase.Schema.create(driver)
            val db = DailySatoriDatabase(driver)
            val repo = AIConfigRepository(db)
            repo.insert(required("provider"), required("apiAddress"), required("apiToken"), required("modelName"), 1)
            driver.execute(null, "INSERT INTO diary(content,created_at,updated_at) VALUES ('紫杉计划的讨论时间是19:20，地点是北门。',1,1)", 0)
            val chats = ChatConversationRepository(db)
            chats.insert("assistant-live", "user", "我们把紫杉计划简称为那个计划", createdAt = 1)
            chats.insert("assistant-live", "assistant", "明白，那个计划指紫杉计划", createdAt = 2)
            HttpClient(OkHttp) { install(HttpTimeout) { requestTimeoutMillis = 120_000; connectTimeoutMillis = 15_000 } }.use { client ->
                val memory = AssistantMemoryService(driver)
                val actions = AssistantActionService(driver, RepositoryAssistantBusiness(driver) { _, _ -> })
                SettingRepository(db).upsert(PersistentAssistantConversation.CONSENT_KEY, "true")
                val runtime = KoogAssistantRuntime(driver, AiService(client), AiConfigService(repo), actions, memory,
                    com.dailysatori.service.mcp.RemoteMcpClient(client))
                val turns = AssistantTurnRepository(driver)
                val tasks = AsyncTaskRepository(db)
                val turn = turns.accept("assistant-live", "live-client", "查找那个计划的日记记录：讨论安排在几点？只依据本地记录回答。\n同时保存 https://example.com/assistant-live ，在2030年1月2日下午15:00提醒我读这篇文章。\n记住我喜欢徒步。请使用工具真实完成这些请求，并区分链接已接收和文章整理完成。",
                    kotlinx.datetime.Clock.System.now().toEpochMilliseconds(), "Asia/Shanghai")
                val handler = AssistantTurnTaskHandler(turns, tasks, runtime)
                val extraction = com.dailysatori.service.memory.MemoryExtractService(AiService(client),AiConfigService(repo),com.dailysatori.data.repository.MemoryRepository(db))
                val indexHandler = AssistantIndexTaskHandler(memory,AssistantMemoryIndexer(driver,memory,extraction::extractPersonalCandidates))
                val runner = com.dailysatori.service.asynctask.AsyncTaskRunner(tasks,
                    com.dailysatori.service.asynctask.AsyncTaskHandlerRegistry(listOf(handler,indexHandler)))
                assertEquals(com.dailysatori.service.asynctask.AsyncTaskRunOutcome.Succeeded, runner.run(turn.taskId))
                val answer = chats.getById(requireNotNull(turns.get(turn.id)?.responseMessageId))!!.content
                assertEquals(4, chats.getBySession("assistant-live").size)
                assertTrue(answer.contains("19:20") || answer.contains("19：20") || answer.contains("十九点二十"),
                    "助手没有返回记录中的准确讨论时间")
                assertEquals(2, actions.list(turn.id).size, "真实模型未完成两项业务动作："+com.dailysatori.service.assistant.data.AssistantDataGateway(driver).redact(answer).take(1000))
                val reminder = db.dailySatoriQueries.selectAllReminders().executeAsOne()
                assertEquals("2030-01-02", reminder.start_date)
                assertEquals("15:00", reminder.first_reminder_time)
                assertTrue(memory.list().any { it.active && it.content.contains("徒步") }, "真实模型未建立有来源的偏好记忆")
                val indexTask = tasks.runnableTasks(kotlinx.datetime.Clock.System.now().toEpochMilliseconds(),Long.MAX_VALUE).single { it.type=="assistant_index" }
                assertEquals(com.dailysatori.service.asynctask.AsyncTaskRunOutcome.Succeeded,runner.run(indexTask.id))
                assertTrue(memory.pendingSegments(turn.id).isEmpty(),"经济后台模型尚未完成全部分段提取")
            }
        }
    }
    @Test
    fun todayNewsQuestionIsAnsweredFromOneLocalBriefingRead() = runBlocking<Unit> {
        assumeTrue("Set DAILY_AI_LIVE_TEST=1 for a small real assistant test", System.getenv("DAILY_AI_LIVE_TEST") == "1")
        val file = File("../.local/ai-test.json")
        require(file.isFile) { "缺少 .local/ai-test.json，真实新闻路由验证未完成" }
        val values = runCatching { Json.parseToJsonElement(file.readText()).jsonObject }.getOrElse { error("AI 测试配置格式无效") }
        fun required(key: String) = values[key]?.jsonPrimitive?.content?.trim()?.takeIf(String::isNotBlank) ?: error("AI 测试配置缺少 $key")
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            DailySatoriDatabase.Schema.create(driver)
            val db = DailySatoriDatabase(driver)
            AIConfigRepository(db).insert(required("provider"), required("apiAddress"), required("apiToken"), required("modelName"), 1)
            val today = kotlinx.datetime.Clock.System.now()
                .toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault()).date.toString()
            // Synthetic public briefing only: no device data and no upstream fetch is involved.
            driver.execute(null, "INSERT INTO unified_news_summary(summary_date,window_key,window_start_ms,window_end_ms,title,content,status," +
                "error_message,source_warnings,generated_at,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)", 12) {
                bindString(0, today); bindString(1, "1330"); bindLong(2, 1_000); bindLong(3, 2_000)
                bindString(4, "午后要闻"); bindString(5, "今日重点：合成哨兵青禾0317，只用于验证这条内容不会未经授权进入云端请求。")
                bindString(6, "success"); bindString(7, null); bindString(8, null)
                bindLong(9, 2_000); bindLong(10, 2_000); bindLong(11, 2_000)
            }
            // Real built-in hot-board channel identity (platform:id); the feed URL is user-editable, so this
            // source is still not proven public and must not be released.
            driver.execute(null, "INSERT INTO unified_news_source(summary_id,ref_key,source_type,source_id,source_filename,source_url,title,summary,source_time) " +
                "VALUES ((SELECT id FROM unified_news_summary WHERE summary_date=? AND window_key='1330'),'H1','hot_news',NULL,'zhihu:1','https://www.zhihu.com/question/1','榜单标题','榜单摘要',?)", 2) {
                bindString(0, today); bindLong(1, 2_000)
            }
            HttpClient(OkHttp) {
                install(HttpTimeout) { requestTimeoutMillis = 120_000; connectTimeoutMillis = 15_000 }
            }.use { client ->
                val calls = mutableListOf<String>()
                val releases = mutableListOf<String>()
                val gateway = AssistantDataGateway(driver)
                val tools = koogDataTools(gateway, AssistantNewsDigest(driver, today, kotlinx.datetime.TimeZone.currentSystemDefault(), gateway::redact),
                    onResult = { name, args, result ->
                        calls += "$name:$args"
                        if (name == "read_news_digest") releases += result
                    })
                val partials = mutableListOf<String>()
                val assistant = KoogReadOnlyAssistant(AiService(client), AiConfigService(AIConfigRepository(db)), gateway,
                    AssistantPrivacyRepository(driver), ChatConversationRepository(db), tools = tools,
                    onPartial = { partials += it })
                val answer = assistant.answer("news-live", "今天有什么重点的新闻吗？")
                // Real streaming: visible prefixes must arrive and the last one must be the delivered answer.
                assertTrue(partials.filter { it.isNotEmpty() }.distinct().size >= 2, "真实回复未产生多个可见增量")
                assertEquals(answer, partials.last())
                val released = assertNotNull(releases.firstOrNull(), "没有走新闻读取")
                assertTrue(released.contains("\"state\":\"restricted\""), "未证明公开的来源被当成可发送：" + gateway.redact(released).take(300))
                assertFalse(released.contains("青禾0317"), "受限简报的正文进入了云端请求")
                assertFalse(answer.contains("青禾0317"), "受限简报的正文出现在回答里：" + gateway.redact(answer).take(300))
                assertTrue(calls.any { it.startsWith("read_news_digest:") }, "没有走一次新闻读取，实际工具：$calls")
                assertFalse(calls.any { it.startsWith("query_data:") && it.contains("article") },
                    "仍在遍历文章库：$calls")
                assertFalse(answer.contains("MCP") || answer.contains("token"))
            }
        }
    }
}
