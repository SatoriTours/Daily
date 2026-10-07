# 点子主题闭环 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. 默认按用户的 Paseo 配置分工，不使用逐任务创建代理／审查代理的循环。

**Goal:** 跑通日记／产品机会手动收录、来源追溯、主题合并、持续 AI 沟通和推进闭环。

**Architecture:** SQLDelight 保存主题、来源快照、追加式事件和多会话消息；IdeaTopicService 是唯一写入所有者。AI 网络适配与工作流分离，生成草稿通过版本校验后由用户确认。Compose 页面通过 Koin ViewModel 访问共享服务，不改造全局聊天或新闻分析存档。

**Tech Stack:** Kotlin Multiplatform、SQLDelight、Koin、StateFlow、Jetpack Compose、现有 AiService；不新增第三方依赖。

**Spec:** `docs/superpowers/specs/2026-10-08-idea-topic-loop-design.md`（用户已于本会话要求继续，进入计划阶段）。

## Global Constraints

- 手动选入；现有入口就是候选池；收藏不等同于主题收录，旧收藏不自动迁移。
- 第一版仅接日记和产品机会；保留来源快照，不伪造不存在的 AI 提炼或生成时间。
- 业务状态只有：待研究、研究中、推进中、已完成。合并／删除是独立生命周期。
- 合入已有主主题，保留原归属及合并链，不自动覆盖目标正式内容；第一版不撤销合并。
- 删除主主题及其合并组件，不删除原日记、新闻、机会、提醒；删除不可恢复。
- AI 不自动更新正式内容／状态，不自动合并／删除；过期草稿不可直接确认。
- 同一主题同时一个 AI 请求；网络不持有数据库事务；生成中禁止相关主题合并／删除。
- 新增页面用 `com.dailysatori.ui.theme.*`，不得硬编码颜色／间距／字体；中英文 YAML 与打包资源同步。
- Schema 修改同时递增实际最新版本并编写 DatabaseMigration；探索时为 32，不假定实施时仍是 32。
- 日常只做代码测试及必要编译；资源变化才合并 assembleDebug；不启动模拟器／App。
- AI 真实测试使用 `.local/ai-test.json`，目录 700、密钥文件 600；不打印 Token，不以跳过充当通过。
- 保留主工作区其他任务修改；不得把其 staged／untracked 文件纳入本任务提交。

## Review Focus

以下五类隐含输入补入对应任务的测试，不靠口头审查替代：
1. 相同时间戳的消息和事件：按 `(created_at,id)` 稳定排序，分页不丢／不重复（任务 1、3）。
2. 中文、emoji、空白标题及超长当前问题：可正常保存，空白拒绝，问题超预算明确报错而不静默截断（任务 1、3）。
3. 双击收录、并发确认同一草稿：最多一次有效写入，不产生重复主题／修订（任务 1、4）。
4. 删除后相同来源重新收录、恢复旧备份：没有旧合并指针／草稿复活，旧数据仍可用（任务 2、7）。
5. 摘要更新后迟到回复、后台切回主题或停流后重试：不串主题、不误更新正式稿、不丢用户消息（任务 3、4、5）。

## 执行与文件边界

任务 1→2→3→4 是共享层依赖链，任务 5 消费其接口，任务 6 接入现有入口，任务 7 验收。不并行修改 Schema、DI、导航或 i18n。

- 当前协调 Agent 负责方案、集成与交付；执行前读取 Paseo profiles 的 notes 和实际 provider/model/thinking/mode。
- 「日常工作」复用同一开发 Agent，负责任务 1–4、共享层接入、ViewModel、测试和集成。
- 「Gemini」负责任务 5–6 的 Compose 页面、入口交互和文案；共享层 API 和 ViewModel 状态先定稿，不允许其修改数据库／共享服务。
- 「方案设计，架构思考」负责最终验收；协调 Agent 若正在承担此角色直接验收，不重复创建同职责 Agent。
- 常规问题集中修复一次；仅遇到不能解决的复杂问题才使用「高消耗工作」。不每个任务派新审查 Agent。
- 执行裁定：项目 AGENTS.md 禁止 worktree，故在当前 `/home/jimxl/projects/Daily` 串行执行，不新建分支／workspace。任何开发代理同时只能有一名写入者。
- 已协调日记续写任务：对方确认不写主 checkout，仅在其独立执行目录开发；主目录当前无待保留未提交改动。日记分支暂用 schema 33，本任务仍从主目录实际版本递增；最终集成时双方重新协调迁移版本，不覆盖对方修改。
- 本计划只是待评审文档，不代表已经创建 workspace、派发任务或执行测试；未经授权不合并／推送／归档。

## 共用类型与接口约定

以下模型统一放在 `shared/src/commonMain/kotlin/com/dailysatori/service/ideatopic/IdeaTopicModels.kt`，标识均为 String，时间均为 epochMillis Long；JSON 快照使用 kotlinx.serialization。

- `IdeaTopicStatus { PendingResearch, Researching, Advancing, Completed }`。
- `IdeaSourceKey(type: String, recordId: String)`：初始 type 为 `diary`／`news_opportunity`，相同 ID 不跨类型碰撞。
- `IdeaSourceSnapshot(key, originalTitle: String, originalContent: String, originalCreatedAt: Long?, originalRecordId: String?, originalUrl: String?, analysisId: String?, analysisContent: String?, analysisCreatedAt: Long?, analysisVersion: String?)`：新闻 originalRecordId 是本地文章 ID，diary 是日记 ID；analysis 字段日记初始为空。
- `IdeaTopicContent(title: String, description: String, provenanceSummary: String, conclusions: String, nextAction: String)`。
- `IdeaCaptureInput(source: IdeaSourceSnapshot, content: IdeaTopicContent, targetTopicId: String? = null)`。
- `IdeaCaptureResult(topicId: String, alreadyCaptured: Boolean)`。
- `IdeaTopicSummary(id: String, content: IdeaTopicContent, status: IdeaTopicStatus, latestProgress: String?, sourceCount: Long, updatedAt: Long)`。
- `IdeaTopicDetail(topic: IdeaTopicSummary, contextRevision: Long, sources: List<IdeaTopicSource>, events: List<IdeaTopicEvent>, sessions: List<IdeaTopicSession>)`；Source 带当前／原始归属及 snapshot；Event 带稳定 ID、kind、当前／原始归属、payload、createdAt；Session 带稳定 ID、当前／原始归属、title、summary、summaryThroughMessageId、summaryCoveredMessageIds（JSON 消息 ID 列表）、summaryStatus、createdAt、updatedAt。
- `IdeaTopicMessage(id: String, sessionId: String, role: String, content: String, status: String, error: String?, createdAt: Long)`，status 为 pending／complete／failed／interrupted。
- `IdeaDraftContent(content: IdeaTopicContent, referenceIds: List<String>)`；`IdeaTopicDraft(id: String, topicId: String, baseRevision: Long, proposal: IdeaDraftContent, state: String)`，state 为 pending／applied／discarded。
- `IdeaAiContext(topicId: String, sessionId: String?, revision: Long, systemPrompt: String, messages: List<IdeaTopicMessage>, userPrompt: String, allowedReferenceIds: Set<String>)`。
- `IdeaTopicException(code: IdeaTopicError)`；枚举覆盖 NotFound、InvalidInput、AlreadyMerged、MergeCycle、Busy、StaleDraft、InvalidAiResponse、AiNotConfigured、InputTooLong、StorageFailure。UI 根据错误码取 i18n，不展示原始异常／接口正文。

## Task 1: 持久化、迁移与手动收录

**Files:**
- Create: `shared/src/commonMain/kotlin/com/dailysatori/service/ideatopic/IdeaTopicModels.kt`
- Create: `shared/src/commonMain/kotlin/com/dailysatori/data/repository/IdeaTopicRepository.kt`
- Create: `shared/src/commonMain/kotlin/com/dailysatori/service/ideatopic/IdeaTopicService.kt`
- Modify: `shared/src/commonMain/sqldelight/com/dailysatori/shared/db/DailySatori.sq`
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/config/Config.kt`
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/service/migration/DatabaseMigration.kt`
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/di/SharedModule.kt`
- Test: `shared/src/androidUnitTest/kotlin/com/dailysatori/service/ideatopic/IdeaTopicPersistenceTest.kt`
- Test: `shared/src/androidUnitTest/kotlin/com/dailysatori/service/ideatopic/IdeaTopicMigrationTest.kt`

**Interfaces:**
- Repository: `observeSummaries(): Flow<List<IdeaTopicSummary>>`, `observeDetail(topicId: String): Flow<IdeaTopicDetail?>`, `getDetailSync(topicId: String): IdeaTopicDetail?`, `findBySourceSync(key: IdeaSourceKey): String?`。内部事务写方法仅 TopicService 使用，不暴露给 UI。
- Service: `suspend capture(input: IdeaCaptureInput): IdeaCaptureResult`；读 API 同 Repository 的 observe／get／find 签名，由 Service 转发并解析主主题。
- 构造 Service 注入 repository，`now: () -> Long`、`newId: () -> String` 提供生产默认值和测试确定值。

- [ ] **Step 1 — 写失败测试。** 测试 diaryCaptureIsManualAndIdempotent、opportunityCapturePreservesRawAndAnalysis、attachToExistingDoesNotOverwriteContent、blankTitleRejected、equalTimestampsHaveStableOrder、oldDatabasePreservesExistingRows。建立本包可复用 `IdeaTopicTestFixture.kt`，用 JdbcSqliteDriver 创建／关闭新库，时钟和 ID 可控；迁移测试建真实旧结构样本，不用新 Schema 冒充旧库。

```kotlin
val first = service.capture(input)
assertEquals(first.topicId, service.capture(input).topicId)
assertTrue(service.capture(input).alreadyCaptured)
assertEquals(1, service.getDetailSync(first.topicId)!!.sources.size)
assertEquals(IdeaTopicStatus.PendingResearch, service.getDetailSync(first.topicId)!!.topic.status)
```

- [ ] **Step 2 — RED。** `./gradlew :shared:testDebugUnitTest --tests '*IdeaTopicPersistenceTest' --tests '*IdeaTopicMigrationTest'`；确认因缺失新 API／表失败，不是环境故障。
- [ ] **Step 3 — 实现。** 五张表按规格创建，消息／事件索引用 `(created_at,id)`；source 唯一键 `(topic_id,source_type,source_record_id)`。快照不外键级联到 diary/article。新增 Schema 迁移以实际最新版本 +1，迁移幂等；共享 DI 注册唯一 Service。capture 在串行写锁和单事务中完成校验、反查、建主题／附来源、写收录事件。标题 trim 后拒绝空白，正文与 emoji 保持原文。
- [ ] **Step 4 — GREEN 与提交。** 同一聚焦命令通过，再运行 `./gradlew :app:compileDebugKotlin`；仅暂存本任务文件、检查 staged diff 后提交 `feat: 增加点子主题持久化与手动收录`。

## Task 2: 推进、修订、合并和删除

**Files:**
- Modify: 上一任务的 IdeaTopicRepository.kt、IdeaTopicService.kt、IdeaTopicModels.kt 与 DailySatori.sq 中查询（不额外改变表结构）。
- Test: `shared/src/androidUnitTest/kotlin/com/dailysatori/service/ideatopic/IdeaTopicLifecycleTest.kt`

**Interfaces:**
- Service: `suspend updateContent(topicId: String, content: IdeaTopicContent): Unit`、`suspend setStatus(topicId: String, status: IdeaTopicStatus): Unit`、`suspend appendProgress(topicId: String, text: String): Unit`。
- Service: `suspend merge(fromTopicId: String, intoTopicId: String): String` 返回最终目标；`suspend delete(topicId: String): Unit`；`resolveTopicId(topicId: String): String?`。

- [ ] **Step 1 — 写失败测试。** 覆盖修订前后快照、状态再开启、进展追加；A→B→C 保留原归属／目标正式内容；重复来源不同快照保留；自合并／环路拒绝；失败事务回滚；删除整组件、原日记／机会／提醒不变、同源可重新选入；双击／并发收录幂等。故障注入在事件写入阶段抛异常，检查无部分移动。
- [ ] **Step 2 — RED。** `./gradlew :shared:testDebugUnitTest --tests '*IdeaTopicLifecycleTest'`；预期新操作缺失或断言失败。
- [ ] **Step 3 — 实现。** 所有变更经同一 Service；解析目标链并检测循环，迁移来源／事件／会话的当前归属，原始归属不变。来源重复时先把两边分析／原文快照存入合并事件，再保留一条来源关联；不得丢消息。删除事务遍历整个指针组件并清除下属记录；手动变更更新 revision、updatedAt。相同状态重复设置不制造事件。
- [ ] **Step 4 — GREEN 与提交。** 聚焦测试及 app compile 通过，提交 `feat: 支持点子推进合并与完整历史追溯`；保持原始候选存档不变。

## Task 3: 主题多会话、AI 聊天与会话摘要

**Files:**
- Create: `shared/src/commonMain/kotlin/com/dailysatori/service/ideatopic/IdeaTopicAiService.kt`（现有 AiService 的网络适配及提示词）
- Create: `shared/src/commonMain/kotlin/com/dailysatori/service/ideatopic/IdeaTopicAiContext.kt`（有界上下文构建）
- Create: `shared/src/commonMain/kotlin/com/dailysatori/service/ideatopic/IdeaTopicAiWorkflow.kt`（请求生命周期协调，写入仍委托 TopicService）
- Modify: IdeaTopicRepository.kt、IdeaTopicService.kt、IdeaTopicModels.kt、SharedModule.kt。
- Test: `shared/src/androidUnitTest/kotlin/com/dailysatori/service/ideatopic/IdeaTopicConversationTest.kt`
- Test: `shared/src/commonTest/kotlin/com/dailysatori/service/ideatopic/IdeaTopicAiContextTest.kt`

**Interfaces:**
- `IdeaTopicAiPort` 在 IdeaTopicAiService.kt 声明：`suspend reply(context: IdeaAiContext, onChunk: suspend (String) -> Unit): String`、`suspend summarize(context: IdeaAiContext): String`、`suspend propose(context: IdeaAiContext): IdeaDraftContent`（任务 4 完成解析）。生产实现注入 AiService、AIConfigRepository、AiConversationSessionStore；测试用可控 fake。
- Service: `suspend createSession(topicId: String, title: String): String`、`observeMessages(sessionId: String): Flow<List<IdeaTopicMessage>>`（最近 30 条）、`getMessagesSync(sessionId: String): List<IdeaTopicMessage>`（摘要／测试读取）、`getMessagesBeforeSync(sessionId: String, beforeCreatedAt: Long, beforeId: String, limit: Int = 30): List<IdeaTopicMessage>`、`suspend recoverInterruptedRequests(): Unit`。分页用 `(created_at,id)` 游标，不用仅时间戳或 offset。
- Workflow: `suspend send(sessionId: String, text: String): Unit`、`suspend summarize(sessionId: String): Unit`、`suspend cancel(topicId: String): Unit`。
- Context builder: `buildIdeaAiContext(detail: IdeaTopicDetail, sessionId: String?, currentMessages: List<IdeaTopicMessage>, userPrompt: String, maxCharacters: Int = 32000): IdeaAiContext`。预算包括系统提示／序列化输入；当前问题单独超过 8000 字符直接 InputTooLong，历史可截断并标记，不能截当前问题。

- [ ] **Step 1 — 写失败测试。** 多会话消息完整保存、超过 30 条的同时间消息分页无丢失／重复、合并后继续旧会话用主主题上下文、摘要覆盖消息 ID、继续沟通摘要过期、与全局聊天隔离；fake 挂起请求测试第二请求 Busy、生成中合并／删除拒绝、取消后重试、重启中断保留用户消息。上下文测试检查预算、当前问题完整、超限明确拒绝、无关来源不注入、来源指令被标识为数据。
- [ ] **Step 2 — RED。** `./gradlew :shared:testDebugUnitTest --tests '*IdeaTopicConversationTest' --tests '*IdeaTopicAiContextTest'`。
- [ ] **Step 3 — 实现。** Service 内按最终主题登记请求 token、请求 Job 和 revision，先保存用户消息／pending 回复，再取请求上下文；锁只保护短写事务。Workflow 通过 Service 的 internal begin／complete／fail／cancel 方法操作请求，禁止直写 Repository。端口用 `withAiRequestSession(sessionStore.getOrCreate("idea-topic:$sessionId"))`；未配置、空回复、错误和取消分开处理，不把错误文字当答案。启动恢复只中断遗留 pending，不影响当前进程活动请求。摘要保存实际覆盖消息 ID 列表及末尾 ID，超过预算只总结输入中实际保留的消息，页面标明部分覆盖，不能用末尾 ID 假装覆盖被截掉的历史；未完成回复不计入。不用全局聊天 Repository。
- [ ] **Step 4 — GREEN 与提交。** 聚焦测试及 app compile 通过，提交 `feat: 增加点子主题持续沟通与摘要`。

## Task 4: AI 更新稿、引用校验与确认修订

**Files:**
- Modify: IdeaTopicAiService.kt、IdeaTopicAiWorkflow.kt、IdeaTopicAiContext.kt、IdeaTopicService.kt、IdeaTopicRepository.kt。
- Test: `shared/src/androidUnitTest/kotlin/com/dailysatori/service/ideatopic/IdeaTopicDraftTest.kt`

**Interfaces:**
- Workflow: `suspend propose(topicId: String): IdeaTopicDraft`。
- Service: `suspend applyDraft(draftId: String, editedContent: IdeaTopicContent): Unit`、`suspend discardDraft(draftId: String): Unit`、`observeDrafts(topicId: String): Flow<List<IdeaTopicDraft>>`。
- AI structured JSON 精确字段：title、description、provenanceSummary、conclusions、nextAction、referenceIds；不包含可执行状态／删除／合并字段。关联 ID 必须属于 context.allowedReferenceIds。

- [ ] **Step 1 — 写失败测试。** 提案不改正式稿；编辑后确认才改变；丢弃不影响内容；未知引用／无效 JSON／空标题拒绝；人工修改／新消息／摘要更新后旧草稿 StaleDraft；双击确认只产生一次修订；取消后的迟到结果不写入；被合并／删除主题不能被旧草稿复活。
- [ ] **Step 2 — RED。** `./gradlew :shared:testDebugUnitTest --tests '*IdeaTopicDraftTest'`。
- [ ] **Step 3 — 实现。** 结构化解析和引用验证；草稿作为事件 payload 持久化，保存 baseRevision。生成期间人工编辑允许但使草稿过期；所有请求结束回写核对 token／归属，确认事务核对草稿 state 与 revision 后写前后快照、正式内容、applied 状态。第二次确认已 applied 草稿幂等返回，不多写事件。草稿保存本身不使 baseRevision 失效，正式内容／消息／来源／摘要更新才递增语义上下文版本。
- [ ] **Step 4 — GREEN 与提交。** 聚焦测试及 app compile 通过，提交 `feat: 支持 AI 完善点子草稿与人工确认`。

## Task 5: 主题列表、详情与沟通界面

**Files:**
- Create: `app/src/main/kotlin/com/dailysatori/ui/feature/ideatopic/IdeaTopicViewModels.kt`（列表／详情／会话 StateFlow，常规开发负责）
- Create: 同目录 `IdeaTopicListScreen.kt`、`IdeaTopicDetailScreen.kt`、`IdeaTopicSessionScreen.kt`、`IdeaTopicCaptureSheet.kt`（Gemini 负责）
- Modify: `app/src/main/kotlin/com/dailysatori/core/di/ViewModelModule.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/core/navigation/Routes.kt`、`NavHost.kt`
- Modify: `shared/src/commonMain/resources/i18n/zh.yaml`、`en.yaml`，同步 `app/src/main/assets/i18n/zh.yaml`、`en.yaml`。
- Test: `app/src/test/kotlin/com/dailysatori/ui/feature/ideatopic/IdeaTopicViewModelTest.kt`
- Test: `app/src/test/kotlin/com/dailysatori/ui/feature/ideatopic/IdeaTopicPresentationTest.kt`

**Interfaces:**
- Routes: `IdeaTopicListRoute`、`IdeaTopicDetailRoute(id: String)`、`IdeaTopicSessionRoute(topicId: String, sessionId: String)`，均 @Serializable。
- `IdeaTopicListViewModel(service: IdeaTopicService)`：`state: StateFlow<IdeaTopicListState>`、`setQuery(String)`、`setStatusFilter(IdeaTopicStatus?)`。
- `IdeaTopicDetailViewModel(topicId: String, service: IdeaTopicService, workflow: IdeaTopicAiWorkflow)`：state 包含 detail／drafts／busy／error；actions 对应任务 2、4，不额外发明写接口。
- `IdeaTopicSessionViewModel(sessionId: String, service: IdeaTopicService, workflow: IdeaTopicAiWorkflow)`：state 包含 messages／summary／busy／error；send(String)、summarize()、cancel()、loadOlder()。最新 30 条 Flow 与旧页按消息 ID 去重合并，按 `(createdAt,id)` 排序。
- Screens: `IdeaTopicListScreen(onBack: () -> Unit, onOpen: (String) -> Unit)`；`IdeaTopicDetailScreen(id: String, onBack: () -> Unit, onSession: (String, String) -> Unit, onSource: (IdeaSourceSnapshot) -> Unit)`；`IdeaTopicSessionScreen(topicId: String, sessionId: String, onBack: () -> Unit)`。
- CaptureSheet: `IdeaTopicCaptureSheet(source: IdeaSourceSnapshot, initialContent: IdeaTopicContent, onCaptured: (String) -> Unit, onDismiss: () -> Unit)`；使用独立 Koin `IdeaTopicCaptureViewModel(service)`，不复用详情 ViewModel 或直读 Repository。

- [ ] **Step 1 — 写失败测试。** 列表排序／状态／标题搜索；主主题旧 ID 解析／删除不可用；加载错误不伪装空列表；生成中禁用合并／删除；草稿明确为待确认并提供变化预览；相同时间稳定排序；离开后重入恢复消息不重新请求；来源不可用显示快照与原文不可用提示。用 coroutine test dispatcher 做状态测试，不启动 UI 自动化。
- [ ] **Step 2 — RED。** `./gradlew :app:testDebugUnitTest --tests '*IdeaTopicViewModelTest' --tests '*IdeaTopicPresentationTest'`。
- [ ] **Step 3 — 实现。** 先完成 ViewModel 状态与动作，再交付 Gemini 页面。详情三个区域实现编辑、追加进展、状态、合并选择、删除确认、草稿预览、历次沟通和来源快照。会话页支持新建／继续、流式回复、停止／重试、总结及完整详情；初始页面加载不请求 AI。删除提示明确“会删除合入主题及沟通记录，原日记和新闻不受影响”。所有文案使用 `idea_topic.*` i18n 键。
- [ ] **Step 4 — GREEN 与提交。** 聚焦测试通过后合并一次 `./gradlew :app:compileDebugKotlin :app:assembleDebug`，提交 `feat: 增加点子主题列表详情与沟通界面`。

## Task 6: 两个入口收录、主页入口与反向关联

**Files:**
- Create: `shared/src/commonMain/kotlin/com/dailysatori/service/ideatopic/IdeaTopicSourceAdapters.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/component/card/DiaryCard.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/diary/DiaryScreen.kt`、`DiaryEditorSheet.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/myspace/NewsOpportunityCard.kt`、`NewsOpportunityScreens.kt`、`MySpaceScreen.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/home/HomeScreen.kt`、导航 NavHost.kt，必要的 ViewModel 状态及上任务四份 YAML。
- Test: `shared/src/commonTest/kotlin/com/dailysatori/service/ideatopic/IdeaTopicSourceAdaptersTest.kt`
- Test: `app/src/test/kotlin/com/dailysatori/ui/feature/ideatopic/IdeaTopicEntryTest.kt`

**Interfaces:**
- `diaryIdeaCaptureInput(diary: Diary): IdeaCaptureInput`、`opportunityIdeaCaptureInput(opportunity: NewsOpportunity): IdeaCaptureInput`，纯适配函数，不调用 AI／Repository。
- DiaryCard 新增可空回调 `onCaptureIdea: (() -> Unit)? = null`，兼容旧调用；编辑页通过保存成功拿到稳定 diary.id 后才触发。
- MySpaceScreen／HomeScreen 增加 `onIdeaTopics: () -> Unit = {}` 并由 NavHost 导航。入口已收录状态从 TopicService 的来源反查更新；打开合并后的主主题，不读取 saved 代替。

- [ ] **Step 1 — 写失败测试。** 日记标签不变成 analysisContent；新闻原文和机会提炼分开；收藏／忽略和旧提醒不变；未保存日记不得收录临时 ID；重复点击显示已有主题；合并后反查到主主题；删除后可重新收录；默认回调保持旧 DiaryCard 调用兼容。
- [ ] **Step 2 — RED。** `./gradlew :shared:testDebugUnitTest --tests '*IdeaTopicSourceAdaptersTest' :app:testDebugUnitTest --tests '*IdeaTopicEntryTest'`。
- [ ] **Step 3 — 实现。** 常规开发完成适配／状态和导航回调；Gemini 增加“收为点子／已收录”与 CaptureSheet 交互。日记保存继续用现有保存通路，不改转录／标题生成逻辑；保存失败不得选入。主页／我的空间增一个轻量主题入口，不新增顶层 Tab，不移除原候选页面、收藏或提醒。
- [ ] **Step 4 — GREEN 与提交。** 同一聚焦命令及 compile／assemble 合并检查通过，提交 `feat: 接通日记与新闻机会的点子主题闭环`。

## Task 7: 备份往返、真实 AI 基础测试与集中验收

**Files:**
- Modify: `shared/src/androidUnitTest/kotlin/com/dailysatori/service/backup/BackupRoundTripTest.kt`
- Create: `shared/src/androidUnitTest/kotlin/com/dailysatori/service/ideatopic/IdeaTopicAiLiveTest.kt`
- Modify: 前述主题测试及本计划复选项，修复仅限本功能；不创建额外报告文件。

**Interfaces:** 使用任务 1–6 API；真实 AI 测试复用已有 NewsOpportunityAiLiveTest 的本地配置读取／PlainCipher／环境开关模式，不打印配置；日志产物仅写忽略的 build 目录。

- [ ] **Step 1 — 写失败测试。** 备份播种含两次合并、重复来源快照、多会话、进展、已完成和 pending 草稿；恢复到另一临时目录后逐项断言。旧备份迁移测试保留旧数据且得到空主题列表。网络 fake 覆盖配置缺失、空回复、未知引用和取消／迟到响应。
- [ ] **Step 2 — RED。** `./gradlew :shared:testDebugUnitTest --tests '*BackupRoundTripTest' --tests '*IdeaTopicMigrationTest'`；先确认新增断言或故障暴露，不为了 RED 修改无关业务。已有快照机制若已满足全部新增断言，如实记录“现有行为已通过”，不制造失败。
- [ ] **Step 3 — 实现最小补齐。** 备份原则上无需新增存档；只修正实际遗漏。真实测试至多一条场景顺序执行 reply、summarize、propose 三次基本请求，验证回复非空、会话摘要非空、草稿结构／引用合法以及正式主题未被自动覆盖。
- [ ] **Step 4 — GREEN 与真实验证。** 聚焦备份／迁移测试通过；核对 `.local/` 权限且不输出密钥后运行 `DAILY_AI_LIVE_TEST=1 ./gradlew :shared:testDebugUnitTest --tests '*IdeaTopicAiLiveTest' --rerun`。配置缺失、额度或网络故障标为真实验证未完成，不把 skip 记成通过。
- [ ] **Step 5 — 一次集中验收。** 「方案设计，架构思考」核对规格第 8 节逐项覆盖，集中修复 Important／Critical；必要的最终完整调用仅一次：`./gradlew :shared:testDebugUnitTest :app:testDebugUnitTest :app:compileDebugKotlin :app:assembleDebug`。不与覆盖相同的其他 Gradle 调用同时运行；不使用无理由 rerun-tasks。真实请求只在上一步显式执行，完整单测中默认跳过。
- [ ] **Step 6 — 提交与交付。** 最终检查 staged diff 仅含本功能，编译成功后提交 `test: 验证点子主题迁移备份与 AI 闭环`。向原会话报告实际提交、运行测试与未验证风险；未做设备／UI 验证必须明确。合并、推送和 workspace 归档遵从用户授权与安全检查，不因代码验收通过自行执行。

## 计划自检与评审状态

- 规格覆盖：来源／快照／选入→任务 1、6；推进／合并／删除→任务 2；对话／摘要→任务 3、5；草稿／确认／并发→任务 3、4；页面／i18n→任务 5、6；迁移／备份／真实接口→任务 1、7。
- 类型与接口使用本计划共用约定；不以全局聊天作为主题对话归属，不双写 saved 代表选入。
- 五项 Review Focus 已各自加入拥有该行为的任务测试；预算数值为本计划的实现约定，用户评审可调整。
- 本计划写成后仅做文档检查，不运行开发测试；以上复选项尚未执行。
- 用户已确认实施计划；2026-10-08 开始执行。主目录基线为 80bf1520，schema=32；规格、计划与独立日记任务的文档提交均保留。执行方式采用上文的既定 Paseo 分工。
- 原协调 Agent：3929957d-e6ec-4737-8370-68bd70fd6716；原 local workspace：wks_0fb0b3aaf0585dc4，不归档。
- 共享层开发 Agent：aa518599-3828-43ba-82e6-fc834041bd2f，使用「日常工作」实际配置 pi / opencode-go/deepseek-v4.1-flash / high，未指定 profile 中不存在的 mode。已提交 a09e65b5、9689ccd2、a5bf3b76、ac0bc58a、fdefaa22、05533b3a、b30ceb4c；当前 idle，停止写入。schema=33；已实现任务 1–4、来源适配和 ViewModel，尚待集中复核，不能等同完整功能验收。
- 共享阶段测试记录 build/idea-topic/final-verification.txt：57 项聚焦单测通过，日常调用中的真实测试 1 项跳过；开发 Agent 另报告显式 DAILY_AI_LIVE_TEST=1 真实基础场景 1 项通过，产物 .local/idea-topic-live-results.json。编译通过为开发阶段报告，最终界面集成后仍须必要验收。
- 界面开发 Agent：72056a74-3737-4871-b9af-43001ef96306，使用「Gemini」实际配置 pi / antigravity/gemini-3.8-flash / high；已完成任务 5–6，提交 9d78aaee，当前 idle。开发报告主题 app 聚焦测试、compileDebugKotlin、assembleDebug 通过；未做 UI／设备验证。未修改备份任务文件。
- 任务 1–6：实现已完成。任务 7 集中审查由协调 Agent 本身承担「方案设计，架构思考」，不重复派审查代理。收录弹窗残留状态、动态合并、响应式 busy、摘要取消／重启恢复、长内容预算、当前会话摘要及引用边界已集中修复；app 的 3 项与 shared 的 5 项回归均观察 RED→GREEN，日志 acceptance-app-red.log、acceptance-shared-red.log、acceptance-green.log。
- 同次审查的首次摘要入口、日记查看主题导航、保存失败不关闭编辑器、正式更新草稿和错误文案国际化也已修复。请求 Job 与 token 统一归 TopicService 所有，取消等待原 Job 收尾，网络不持有事务；不新增第二状态机。
- 最终完整检查暴露新增 ViewModel 测试未清理订阅，以及 settings_design 测试越界解析后来新增 YAML 节；已修正测试生命周期和节边界。日记收录保存改为原 DiaryViewModel.saveDiary 的可选 onSaved 回调，由 ViewModel 生命周期执行；两项真实数据库回调测试确认保存成功才交付快照、失败不回调。
- 备份测试由现有备份协调 Agent f1710cd6-967c-44c7-8522-5b4cb069fd04 拥有并补点子往返种子，本 Agent 未改其文件。对方回报备份 shared 75／app 40、BackupRoundTripTest 13 项通过；本 Agent 完整 shared 验证也覆盖了该类。加密跨设备还原验证来源／分析快照、真实合并的重复快照、主主题／指针／事件、多会话摘要／消息与 pending 草稿；恢复快照保留 pending，首次构造 TopicService 再标为中断。
- 主题真实 AI：首次因模型把 conclusions 返回数组并引用非白名单记录 ID 而失败；保持严格解析，补齐字符串字段示例和有界引用白名单，随后 1 个场景（聊天／摘要／草稿三次请求）无 skip 通过。证据 build/idea-topic/acceptance-live-evidence.json。
- 最终验证：shared 1189 项，0 failure、9 项可选真实测试 skip；受影响 app 聚焦 57 项，0 failure／skip；compileDebugKotlin 与 assembleDebug 均通过。App 全量 1276 项仍有 1 项 NewsRecommendationContextTest 的 UncaughtExceptionsBeforeTest：栈来源既有 ArticlesViewModelFailureTest 临时改 article 表时的 getDailyCounts 异常，非主题测试失败，不扩大到文章模块修复，也不记为全量通过。证据 acceptance-final-counts.json、acceptance-app-focused-evidence.json、acceptance-app-final.log。
- 未做模拟器／UI 验证，未合并续写／AI 分流分支，未推送。代码级主题闭环验证完成，但 App 全量无故障门槛尚受上述既有测试污染影响。
- 日记续写兼容协调：对方回报隔离分支提交 92c17206、436ca691、63a61829、8482f5eb，schema 暂为 33，未合并／推送。未来 DiaryThreadRepository.rootId(recordId: Long): Long? 可归一化来源 ID，getSnapshot/getSource 提供线程快照／有边界原文。本任务不提前导入未合并 API，继续单条日记捕获和独立来源适配；最终串行集成时再接根 ID、全文和重新编号迁移。信息已转发开发 Agent，不构成对方代码已经在主目录验证的声明。
