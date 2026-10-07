# 日记续写与 AI 汇总 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. 若按项目分工交由子 Agent 实现，仍复用同一执行空间与上下文，不为每个任务创建新的实现/审查 Agent。

**Goal:** 为已有日记提供独立保存的续写过程，并在顶部自动生成与最新原文版本匹配的 AI 汇总。

**Architecture:** 续写复用 `diary` 记录，以可空 `parent_diary_id` 归属主日记，继续复用自身附件和编辑器。日记串 Repository 提供完整原文和单调版本，现有持久异步任务负责汇总；版本条件写入阻止过期结果覆盖。搜索、标签、思想和月度汇总共用原文来源，不把 AI 汇总当成原文。

**Tech Stack:** Kotlin Multiplatform、SQLDelight/SQLite、Kotlin coroutines/Flow、kotlinx.serialization、Koin、Jetpack Compose、WorkManager、现有 AiService。

**Spec:** `docs/superpowers/specs/2026-10-08-diary-continuation-design.md`

## Global Constraints

- 「回复」指用户自己的追加记录，不是 AI 聊天回复，也不是多人评论。只支持一层续写，不支持对某条续写再嵌套回复。
- 单篇、无续写的日记保留现有展示，不自动增加一笔汇总调用。
- 首版不新增逐次 AI 汇总版本浏览、续写单独编辑/删除、日记互相合并或按键级编辑历史。
- 主日记创建时间不变；续写更新主日记的更新时间，但不改写主日记原文。
- 数据库版本在实现时从实际当前版本递增，不能固定假设版本号。
- 生成失败、AI 未配置和调度失败不回滚已保存续写；最近成功汇总仍保留并标记过期。
- 原文只是待整理的数据，不执行原文中的提示词或指令。
- 样式沿用项目主题，不硬编码颜色、字体或间距；Repository 不直接从 Composable 调用。
- 日常不启动模拟器、不安装 App、不执行设备 UI 测试。纯规格文档阶段不构建。
- AI 真实测试使用被忽略的 `.local/ai-test.json`，不打印、提交或打包密钥。
- 保存/查询兼容旧 SQLite，沿用 `last_insert_rowid()`，不引入 `INSERT ... RETURNING`。
- 遵守现有 Paseo 分工；派发前读取实际 profiles。需要隔离时先说明并创建单个 Paseo 托管功能 workspace，所有步骤复用它；不手工创建 worktree。
- 原会话始终负责协调与验收；未授权合并/推送时不自行执行。

## Review Focus

1. 主日记被删除或续写归属非法：写入明确失败，后台结果不得复活记录（Task 1、4）。
2. 关闭录音编辑器、转写失败或随后自动生成标题：录音成果不丢失，汇总随真实正文变化更新，不能总结占位文案（Task 1、4、5）。
3. AI 返回空正文、错误 JSON、提示词诱导，或日记超长：可校验失败并保留原文；长输入后半段的纠正不被截掉（Task 4）。
4. 主日记很旧、续写在新月份且唯一关键词只存在于续写：时间线不重复，月度缓存正确失效，搜索/MCP 能找到该过程（Task 2、3）。
5. 同版本任务重试、旧任务失败和系统重启交错：最新状态不被旧任务改写，失败不形成无限自动重排循环（Task 4、6）。

---

## 文件与所有权

新文件按清晰职责增加，不拆分既有大文件中的无关代码：

- `shared/.../service/diary/DiaryThreadModels.kt`：日记串快照、汇总和轻量来源/概览模型。
- `shared/.../data/repository/DiaryThreadRepository.kt`：续写事务、原文快照、概览、版本条件写入。
- `shared/.../service/diary/DiaryThreadSummaryGenerator.kt`：AI 提示词、长输入分段、返回校验和版本绑定断点。
- `shared/.../service/diary/DiaryThreadSummaryCoordinator.kt`：持久任务处理、失效观察和启动恢复。
- `app/.../ui/feature/diary/DiaryThreadSheet.kt`：汇总与过程展示；新增展示状态辅助函数放同文件或可独立测试的 `DiaryThreadPresentation.kt`。
- 既有 Schema、Repository、消费者、ViewModel、DI、Application 和字符串资源按下列任务修改。

先完成共享接口，再允许界面实现；共享和 app 文件的修改边界不交叉。协调角色负责集成与一次集中验收。只提交本功能文件，每次提交前检查 staged diff，提交代码前必须编译无错误。

当前语音日记自动标题已提交为 `56249b77`；实施基线重新从实际 Git 确认，保留该功能，不依赖之前“未提交改动”的过期判断。Schema 当前读到 32，但实现时重新确认。

## Task 1：续写持久化、原文版本及旧库迁移

**Files:**
- Create: `shared/src/commonMain/kotlin/com/dailysatori/service/diary/DiaryThreadModels.kt`
- Create: `shared/src/commonMain/kotlin/com/dailysatori/data/repository/DiaryThreadRepository.kt`
- Modify: `shared/src/commonMain/sqldelight/com/dailysatori/shared/db/DailySatori.sq`
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/config/Config.kt`
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/service/migration/DatabaseMigration.kt`
- Test: `shared/src/commonTest/kotlin/com/dailysatori/data/repository/DiaryThreadRepositoryTest.kt`
- Test: `shared/src/commonTest/kotlin/com/dailysatori/service/migration/DiaryThreadMigrationTest.kt`
- Update affected generated-`Diary` constructor uses in existing tests only as required for compilation.

**Interfaces:**
- `DiaryThreadSummary(text: String, sourceRevision: Long, summaryRevision: Long, status: String, errorMessage: String?, generatedAt: Long?)`。
- `DiaryThreadSnapshot(root: Diary, entries: List<Diary>, attachments: List<Diary_attachment>, revision: Long, pendingAttachmentCount: Long, summary: DiaryThreadSummary?)`；entries 首项为主原文，其余按 `(created_at, id)` 排序；attachments 包含整串附件，界面按 `diary_id` 归属展示。
- `DiaryThreadSource(rootId: Long, content: String, createdAt: Long, updatedAt: Long, revision: Long)`。
- `DiaryThreadOverview(rootId: Long, replyCount: Long, pendingAttachmentCount: Long, summary: DiaryThreadSummary?)`。
- `DiaryThreadRepository(db: DailySatoriDatabase, driver: SqlDriver)`。
- `rootId(recordId: Long): Long?`、`getSnapshot(rootId: Long): DiaryThreadSnapshot?`、`observeThread(rootId: Long): Flow<DiaryThreadSnapshot?>`。
- `suspend createReply(rootId: Long, content: String, mood: String? = null, images: String? = null): Long`；禁止嵌套，非法父记录抛出 `IllegalArgumentException`。
- `discardEmptyReply(replyId: Long): Boolean`；只删除无真实正文、无图片和附件的空续写。
- `getSource(rootId: Long): DiaryThreadSource?`、`observeSources(): Flow<List<DiaryThreadSource>>`、`observeOverviews(): Flow<List<DiaryThreadOverview>>`。
- `renderDiaryThreadContent(entries: List<Diary>): String` 位于模型文件；带记录 ID/时间边界，排除现有自动转写占位正文，不排除真实正文。
- `commitSummary(rootId: Long, revision: Long, text: String): Boolean`、`markSummaryState(rootId: Long, revision: Long, status: String, error: String? = null): Boolean`；均是同版本条件写入。
- `pendingSummaryRootIds(): List<Long>` 返回有续写且待生成的主 ID，不返回无变化的终态失败记录；显式重试由后续协调器管理。

- [ ] **Step 1：增加真实 SQLite 行为测试与旧库样本测试。** 沿用 `JdbcSqliteDriver.IN_MEMORY`、`Schema.create` 和 `runBlocking`，每个测试 finally 关闭 driver。

```kotlin
// retainsOriginalAndTwoReplies：
assertEquals(listOf(rootId, replyA, replyB), threads.getSnapshot(rootId)!!.entries.map { it.id })
assertEquals("原始想法", diaries.getById(rootId)!!.content)
// rejectsNestedReply：
assertFailsWith<IllegalArgumentException> { threads.createReply(replyA, "嵌套") }
// rejectsStaleSummaryAfterDirectSqlUpdate：旧版本是从写正文前的快照读取。
assertFalse(threads.commitSummary(rootId, oldRevision, "过期结果"))
```

补充同毫秒排序、正文不变的标签修改不递增版本、直接 SQL 转写/标题回写递增版本、主日记删除后提交返回 false、录音占位不进入原文、旧样本原文/图片/附件保留及重复运行迁移可用的断言。

- [ ] **Step 2：运行 RED。** `./gradlew :shared:testDebugUnitTest --tests '*DiaryThreadRepositoryTest' --tests '*DiaryThreadMigrationTest'`；预期缺少本任务接口或新结构而失败，不能把环境错误当 RED。
- [ ] **Step 3：实现上述接口与 Schema/迁移。** 增加 `parent_diary_id` 及索引、`diary_thread_summary`。以正文/图片/归属相关触发器维护主版本和更新时间；原文变化时主更新时间至少增加 1ms，避免同毫秒续写使月度缓存无法失效。附件增删/转写状态变化通知观察并让待转写状态正确刷新。不因汇总写入或纯时间戳更新递增版本。禁止通过普通更新接口重设父 ID。
- [ ] **Step 4：运行同一聚焦测试 GREEN，并执行 `./gradlew :app:compileDebugKotlin`。** 预期全部测试通过且编译成功；只修本任务引起的生成模型适配。
- [ ] **Step 5：检查 staged diff 后只提交本任务文件。** `feat: 保存日记续写与版本化原文`。

## Task 2：主时间线、全文搜索和同步 MCP 原文

**Files:**
- Modify: `shared/src/commonMain/sqldelight/com/dailysatori/shared/db/DailySatori.sq`
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/data/repository/DiaryRepository.kt`
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/service/mcp/McpToolRegistry.kt`
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/service/mcp/McpToolResultFormatter.kt`
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/di/SharedModule.kt`
- Test: `shared/src/commonTest/kotlin/com/dailysatori/data/repository/DiaryThreadSearchTest.kt`
- Test: `shared/src/commonTest/kotlin/com/dailysatori/service/mcp/DiaryThreadMcpTest.kt`

**Interfaces:** 消费 Task 1；既有 `getAll/getPaginated/count/getByDateRange/getAllSync/getLatestSync/search/searchSync` 返回类型不变，但只返回主日记。`getById` 仍返回独立记录。`diaryListToJson(diaries: List<Diary>, contentFor: (Diary) -> String = { it.content }): JsonArray` 增加可选内容读取，默认行为兼容原调用。

- [ ] **Step 1：编写测试。** `replyKeywordFindsRootInFlowAndSync`、`replyMatchesAreDeduplicated`、`rootCountAndPaginationExcludeReplies`、`mcpReturnsReplyEvidenceWithRootId`、`tagAliasSearchStillWorks`。

```kotlin
assertEquals(listOf(rootId), diaries.searchSync("只在续写里的关键字").map { it.id })
assertEquals(1L, diaries.count())
assertEquals("续写原文", diaries.getById(replyId)!!.content)
```

MCP 测试在主原文超过 500 字时仍验证结果片段包含续写命中词，不能只有主原文的前 500 字；结果 ID 仍是 rootId。

- [ ] **Step 2：运行 RED。** `./gradlew :shared:testDebugUnitTest --tests '*DiaryThreadSearchTest' --tests '*DiaryThreadMcpTest'`；预期列表重复或续写检索/证据断言失败。
- [ ] **Step 3：实现主记录过滤、FTS 命中归主去重及原文输出。** 保留子记录现有 FTS 索引；搜索主/子命中映射主 ID。MCP 非搜索读完整串后按既有上限展示，搜索读命中附近片段，不能先截主正文再搜。线程来源观察批量读并分组，不在列表逐卡片加载全部过程。
- [ ] **Step 4：运行同一测试 GREEN 和 `./gradlew :app:compileDebugKotlin`。** 同时确认旧篇数、分页和标签别名用例不回归。
- [ ] **Step 5：检查 staged diff 后提交。** `feat: 将日记续写纳入搜索和原文检索`。

## Task 3：标签、思想、月度汇总及知识提取兼容

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/data/repository/DiaryTagRepository.kt`
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/service/diary/DiaryTagModels.kt`
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/service/diary/DiaryTagCoordinator.kt`
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/service/diary/DiaryThoughtService.kt`
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/service/diary/DiaryThoughtChatContext.kt`
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/service/diary/DiaryMonthSummaryService.kt`
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/service/diary/DiaryKnowledgeCoordinator.kt`
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/di/SharedModule.kt`
- Test: `shared/src/commonTest/kotlin/com/dailysatori/service/diary/DiaryThreadConsumersTest.kt`
- Extend: `shared/src/androidUnitTest/kotlin/com/dailysatori/service/diary/DiaryThoughtServiceTest.kt`

**Interfaces:** 消费 `getSource/observeSources/rootId/getSnapshot`。`DiaryTagSnapshot` 增加末尾默认参数 `threadRevision: Long? = null`，旧测试构造兼容；prepare/apply 中完整原文和版本一致才可写主标签。既有消费者外部调用接口保留，通过构造注入线程 Repository。

- [ ] **Step 1：增加测试。** `tagsSeeReplyButKeepManualPolicy`、`tagResultBeforeReplyIsRejected`、`thoughtRefreshSeesReplyAndKeepsRootReference`、`thoughtChatRejectsArchiveBeforeReply`、`laterMonthReplyInvalidatesOriginalMonth`、`knowledgeTaskForReplyWritesRootSourceOnce`。

```kotlin
assertTrue(threads.getSource(rootId)!!.content.contains("我现在改主意了"))
assertFalse(tags.apply(snapshotBeforeReply, listOf("旧标签")))
assertEquals(rootId, threads.rootId(replyId))
```

实际断言 fake AI 收到续写原文而非汇总，知识来源仅 rootId，不额外创建 replyId 的重复记忆；两次相同转写任务保持既有去重行为。

- [ ] **Step 2：运行 RED。** `./gradlew :shared:testDebugUnitTest --tests '*DiaryThreadConsumersTest' --tests '*DiaryThoughtServiceTest'`；预期只读取主正文导致相关行为失败。
- [ ] **Step 3：将消费者切到统一原文。** 标签状态/词库增量索引只针对主记录，但 fingerprint 和过期校验针对串。思想观察与执行侧/聊天有效性判断必须读同一来源。月度汇总保持主创建月份归属，用主更新时间失效缓存；知识按主来源聚合，附件状态仍按真实附件 ID 写入。不把 AI 汇总再送去提取。
- [ ] **Step 4：运行同一测试 GREEN 和 `./gradlew :app:compileDebugKotlin`。** 本任务只验证 fake AI 路径；改动相关真实 AI 用例统一在 Task 6 执行，避免重复消耗。
- [ ] **Step 5：检查 staged diff 后提交。** `feat: 让日记分析读取完整续写过程`。

## Task 4：AI 汇总、长文本断点和持久恢复

**Files:**
- Create: `shared/src/commonMain/kotlin/com/dailysatori/service/diary/DiaryThreadSummaryGenerator.kt`
- Create: `shared/src/commonMain/kotlin/com/dailysatori/service/diary/DiaryThreadSummaryCoordinator.kt`
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/service/asynctask/AsyncTaskModels.kt`
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/di/SharedModule.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/core/di/AppModule.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/DailySatoriApplication.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/core/worker/AsyncTaskWorker.kt`（仅新增类型需按现有规则进入长任务通知/超时处理时）
- Test: `shared/src/commonTest/kotlin/com/dailysatori/service/diary/DiaryThreadSummaryTest.kt`
- Test: `shared/src/commonTest/kotlin/com/dailysatori/service/diary/DiaryThreadSummaryCoordinatorTest.kt`

**Interfaces:**
- `DiaryThreadSummaryGenerator(ai: AiService)`；`suspend generate(snapshot: DiaryThreadSnapshot, checkpointJson: String = "", saveCheckpoint: suspend (String) -> Unit = {}): String`。
- `@Serializable DiaryThreadSummaryCheckpoint(rootId: Long, revision: Long, completedChunks: List<String>)`；中间文本含来源/时间边界。
- `DiaryThreadSummaryCoordinator(threads: DiaryThreadRepository, tasks: AsyncTaskRepository, generator: DiaryThreadSummaryGenerator, configured: () -> Boolean) : AsyncTaskHandler`。
- `override val type = AsyncTaskType.diary_thread_summarize.name`，展示名「日记续写汇总」。
- `enqueue(rootId: Long, force: Boolean = false): Long?`；`recoverPending(): List<Long>`；`start(scope: CoroutineScope, schedule: (Long) -> Unit)`；执行签名沿用 `AsyncTaskHandler.execute`。
- 唯一键 `diary-thread-summary:$rootId:$revision`。自动恢复只补 pending/过期任务；终态失败必须显式重试或新原文版本才能再入队，避免观察自身状态循环重试。

- [ ] **Step 1：增加 fake AI/真实 SQLite 组合测试。** `summaryUsesAllEntriesAndChronology`、`noReplyDoesNotCallAi`、`failurePreservesLastSummary`、`oldSuccessAndOldFailureCannotOverwriteNewRevision`、`deletionDuringRequestDoesNotRecreateRoot`、`enqueueFailureRecoveredOnStart`、`sameRevisionDeduplicatesAndExplicitRetryWorks`、`checkpointFromOldRevisionIsIgnored`、`pendingTranscriptIsNotSummarizedAsFact`。

```kotlin
assertNull(coordinator.enqueue(singleEntryRoot))
assertEquals("上次成功汇总", threads.getSnapshot(rootId)!!.summary!!.text)
assertFalse(threads.markSummaryState(rootId, oldRevision, "failed", "旧请求失败"))
```

格式测试覆盖空 summary、非 JSON、`{"summary": 3}`；诱导文本在数据输入中保留，但系统提示明确拒绝执行。长输入测试验证末尾纠正进入分段/合成输入，单条超长记录也完整拆分，断点恢复不重复已完成分段。

- [ ] **Step 2：运行 RED。** `./gradlew :shared:testDebugUnitTest --tests '*DiaryThreadSummaryTest' --tests '*DiaryThreadSummaryCoordinatorTest'`。
- [ ] **Step 3：实现生成器。** 校验返回 `{"summary":"非空正文"}`；使用真实原文，每段最多 12,000 字符，按记录边界优先分段，超长单条继续拆分并带来源 ID，不截尾。中间要点合成必要时递归分组以保证单次输入上限；输出仍保留后续纠正和未决状态。断点绑定 rootId、revision，并检查 completedChunks 与当前分块对应。
- [ ] **Step 4：实现协调器和注入/启动观察。** 同版本任务在既有任务仓库去重，执行前读快照，执行后条件写入。过期任务快速退出并补最新版本；当前请求异常不删除旧汇总，取消异常继续抛出。观察原文/附件状态变化而非只观察 summary 状态；启动补建缺失任务，唤醒失败不清除 pending 标记。调度成功之前的配置缺失显示可重试状态；配置完成后可显式重试，不新增配置轮询。
- [ ] **Step 5：运行同一测试 GREEN 和 `./gradlew :app:compileDebugKotlin`。** 检查任务注册、显示名和 startup 恢复一致。
- [ ] **Step 6：检查 staged diff 后提交。** `feat: 后台生成并恢复日记续写汇总`。

## Task 5：过程界面、续写编辑器和录音归属

**Files:**
- Create: `app/src/main/kotlin/com/dailysatori/ui/feature/diary/DiaryThreadSheet.kt`
- Create: `app/src/main/kotlin/com/dailysatori/ui/feature/diary/DiaryThreadPresentation.kt`（仅可测试展示状态/文案选择）
- Modify: `app/src/main/kotlin/com/dailysatori/ui/component/card/DiaryCard.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/diary/DiaryScreen.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/diary/DiaryEditorSheet.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/diary/DiaryViewModel.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/core/di/ViewModelModule.kt`
- Modify: `app/src/main/res/values/strings.xml`、`app/src/main/res/values-en/strings.xml`
- Test: `app/src/test/kotlin/com/dailysatori/ui/feature/diary/DiaryThreadPresentationTest.kt`
- Test: `app/src/test/kotlin/com/dailysatori/ui/feature/diary/DiaryContinuationEditorTest.kt`
- Extend existing editor、feed、capture、recording 和 source ID 回归测试。

**Interfaces:**
- 消费前述快照/概览和协调器。`DiaryViewModel.openThread(rootId: Long)`、`closeThread()`、`retryThreadSummary(rootId: Long)`。
- `suspend saveReplyAndGetId(rootId: Long, content: String, mood: String? = null, images: String? = null, existingReplyId: Long? = null, polishedTranscripts: Map<Long, DiaryPolishedTranscript>? = null): Long?`；existingReplyId 仅指当前编辑器提前创建的录音续写，并验证所属 rootId。
- `suspend prepareReplyRecording(rootId: Long): Pair<Long, Long>?` 返回 `(replyId, attachmentId)`，继续调用现有 recording controller。
- `DiaryThreadSheet(snapshot: DiaryThreadSnapshot, onDismiss: () -> Unit, onContinue: () -> Unit, onEditOriginal: () -> Unit, onRetrySummary: () -> Unit)`；播放附件沿用现有组件，数据使用 snapshot.attachments 按记录归组；必要回调直接沿用既有附件组件接口，不从 Composable 访问数据层。
- `DiaryCard` 增加默认参数 `threadOverview: DiaryThreadOverview? = null`、`onContinue: () -> Unit = {}`、`onOpenThread: () -> Unit = {}`，不改变旧调用必填项。
- `DiaryEditorSheet` 增加默认 `continuationRootId: Long? = null`，只改变标题/续写模式及隐藏子标签，原正文状态始终来自独立记录，不取汇总。

- [ ] **Step 1：增加代码级行为测试。** `staleSummaryIsClearlyMarked`、`failedSummaryKeepsTextAndRetry`、`replyEditorDoesNotLoadSummary`、`typedDraftCancelDoesNotCreateReply`、`recordingUsesReplyId`、`dismissDuringTranscriptionKeepsRecording`、`replySaveDoesNotOverwriteRoot`、`emptyReplyCleanupDoesNotDeleteAudio`。

```kotlin
assertEquals("原始日记", diaries.getById(rootId)!!.content)
assertEquals(replyId, attachments.getById(attachmentId)!!.diary_id)
assertFalse(threads.discardEmptyReply(replyWithAudioId))
```

以现有 JVM 测试方式测状态/回调路由；需要 Android 生命周期的视觉行为不伪称已执行。断言原日记「编辑」和「继续写」分别路由，主标签不会被续写编辑器空值覆盖。

- [ ] **Step 2：运行 RED。** `./gradlew :app:testDebugUnitTest --tests '*DiaryThreadPresentationTest' --tests '*DiaryContinuationEditorTest'`。
- [ ] **Step 3：实现 ViewModel 状态及独立保存/录音流程。** 仅选中串观察完整过程，列表读轻量概览。保留附件转写、AI 整理意见、自动标题和关闭后后台运行。保存成功先返回 UI，再调度汇总/主标签与原文消费者；有录音成果时不静默取消持久记录。
- [ ] **Step 4：实现主题化界面及双语资源。** 卡片显示续写数及入口；过程面板顶部标明「AI 汇总」，下方按时间显示每段原文与附件。旧汇总必须标记待更新；待转写附件显示真实状态。无续写卡片保留原有表现。
- [ ] **Step 5：运行同一测试 GREEN。** 同次 `./gradlew :app:compileDebugKotlin :app:assembleDebug :app:testDebugUnitTest --tests '*DiaryThreadPresentationTest' --tests '*DiaryContinuationEditorTest'`；资源改动需打包，不安装 App。
- [ ] **Step 6：检查 staged diff 后提交。** `feat: 展示日记续写过程与 AI 汇总`。

## Task 6：整串删除、备份恢复与最终集中验收

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/dailysatori/data/repository/DiaryRepository.kt`
- Modify: `app/src/main/kotlin/com/dailysatori/ui/feature/diary/DiaryViewModel.kt`
- Modify if required by round-trip test: `shared/src/commonMain/kotlin/com/dailysatori/service/backup/BackupDatabaseData.kt`
- Extend: `shared/src/commonTest/kotlin/com/dailysatori/data/repository/DiaryRepositoryDeleteTest.kt`
- Create: `shared/src/androidUnitTest/kotlin/com/dailysatori/service/backup/DiaryThreadBackupTest.kt`
- Create: `shared/src/androidUnitTest/kotlin/com/dailysatori/service/diary/DiaryThreadSummaryAiLiveTest.kt`
- Extend relevant existing live cases: `DiaryTagAiLiveTest.kt`、`DiaryThoughtServiceTest.kt` 的本功能来源用例（思想真实链路若需另文件，新增 `DiaryThreadThoughtAiLiveTest.kt`）。
- Modify: `docs/03-app-features.md`。

**Interfaces:** 主删除接口不改返回类型；删除前收集所有 record ID、图片/音频路径和整理历史键。录音属于主/任一续写时均进入既有停止保护；任务原文归属通过 rootId 解析，删除后结果提交均无效。备份路径枚举涵盖子 `diary.images` 与子附件 `local_path`，不新增附件目录协议。

- [ ] **Step 1：增加失败测试。** `deleteRootCleansReplyAttachmentsAndPolishHistory`、`deleteRootStopsActiveReplyRecording`、`backupRoundTripKeepsThreadSourcesAndSummary`、`restoredPendingSummaryCanRecover`。备份样本含两次续写、图片、音频、整理意见和待更新汇总；断言恢复后路径与原文可读、外键检查通过。
- [ ] **Step 2：运行 RED。** `./gradlew :shared:testDebugUnitTest --tests '*DiaryRepositoryDeleteTest' --tests '*DiaryThreadBackupTest'`；app 停录音行为用受影响 JVM 测试验证，不启动设备。
- [ ] **Step 3：集中实现清理及恢复缺口。** 收集后事务删除并清理应用拥有的文件；取消相关主/子任务，不依赖取消保证正确性，版本/存在性验证仍负责兜底。保留其他日记附件和历史。
- [ ] **Step 4：准备本功能真实 AI 测试。** 汇总至少两组：事实纠正、观点变化且尚无结论；长输入 fake 测试已覆盖，真实调用选少量基础样例。另运行受影响标签与思想原文链路小样例，不以汇总验证替代。配置缺失/网络/额度错误报告未完成，不记通过；输出文件仅写忽略的 build 目录，不含配置。
- [ ] **Step 5：执行一次最终完整代码验证。** `./gradlew :shared:testDebugUnitTest :app:testDebugUnitTest :app:compileDebugKotlin`；资源/打包未再变化时复用 Task 5 的 assemble 结果，否则同次增加 `:app:assembleDebug`。不加 `--rerun-tasks`。
- [ ] **Step 6：执行少量真实接口验证。** `DAILY_AI_LIVE_TEST=1 ./gradlew :shared:testDebugUnitTest --tests '*DiaryThreadSummaryAiLiveTest' --tests '*DiaryTagAiLiveTest' --tests '*DiaryThreadThoughtAiLiveTest' --rerun`；思想专属用例如未新增则改为实际实现的对应测试类，不使用不存在类制造假通过。
- [ ] **Step 7：协调角色集中审查一次。** 对照规格逐项检查结果、任务提交、旧数据样本、串删除、录音归属和五项 Review Focus；一次汇总 Important/Critical，当前实现者集中修复，随后只重跑受影响验证。不为 Minor 建议无限往返。
- [ ] **Step 8：更新功能说明并检查 staged diff 后提交。** `feat: 验证日记续写备份与整串清理`。最终交付列实际测试、提交和未执行的设备界面验证；未授权不合并/推送，也不归档执行空间。

## 执行交接

本计划完成后由用户审阅确认，再开始产品代码实现。遵循已给定的 Paseo 分工：协调角色继续负责设计/集成/验收，共享实现使用「日常工作」，前端使用「Gemini」；任务依赖顺序为 1 → 2 → 3 → 4 → 5 → 6，Task 5 的界面实现可在共享接口稳定后提前开始，但不与共享修改争用文件。

派发前读取实际 profiles、当前 Git 状态与 workspace；只有需要隔离时新建一个「【自动】日记续写与汇总」执行空间，分支 `feature/diary-continuation`，基线取实际已确认主分支。记录归属信息并在原会话告知，不为审查或修复重新建空间。若工具/profile 不可用，报告阻塞或明确改用当前会话直接执行，不冒称已调度。
