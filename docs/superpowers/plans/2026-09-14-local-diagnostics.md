# 本地诊断日志与导出 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. 按项目规则由当前代理直接执行，不使用 worktree、不自动提交；不默认委派。

**Goal:** 提供持续、安全、可关联的本地诊断与最近 30 分钟/上次崩溃单文件导出。

**Architecture:** commonMain 定义事件、安全投影与关联规则；Android 负责单写入滚动存储、崩溃现场、退出信息与 SAF 导出。保留 Kermit，分别接入多网络栈和关键业务边界，不将任意已有自由文本复制进诊断文件。

**Tech Stack:** Kotlin Multiplatform、Kermit 2.0.5、Ktor 3.1.3、现有 LangChain4j/OkHttp、kotlinx.serialization、协程、Koin、Compose、SAF；minSdk 26。

**Spec:** [本地诊断日志与导出设计](../specs/2026-09-14-local-diagnostics-design.md)

## 实施状态（2026-09-14）

第一版已实施。下方 Task 清单保留为原始开发计划/回归参考，不要作为未完成工作重复执行；本节记录实际交付与设计细化。

- [x] 安全事件、异常投影、trace 与模型/工具安全标识。
- [x] 单写入滚动文件、健康计数、固定窗口快照和安全 Kermit/Android Log 桥接。
- [x] Ktor、LangChain 三种提供方、Coil 传输接入，WebView/下载/调试服务器的可观测事件。
- [x] AI/MCP 工具、公共任务、同步、导入、备份、文章处理、日记处理及迁移关键边界。
- [x] 独立紧急崩溃记录、隔夜恢复和 API 30+ 退出原因。
- [x] 设置页、单文件 SAF 保存、token 绑定的取消/过期回调处理、清理及中英文资源。
- [x] 集中审查、单元测试、编译和 Debug APK 构建。

实际边界：`DiagnosticKtorEngine.kt` 使用版本绑定的引擎 InternalAPI 传递 trace；`DiagnosticCrashArchive.kt` 独占现场整理；`DiagnosticExportSession.kt` 持有导出状态，代码级状态测试放在 `DiagnosticExporterTest.kt`，并非 Android ViewModel/UI 仪器测试。Coil 使用全局 ImageLoader，不需修改 SmartImage。ANR/native 不读取原始系统 trace；无完整正文开关。其余细化见 Spec 的“实施时的明确细化”。

最终验证命令均通过：

```bash
./gradlew :app:compileDebugKotlin :app:assembleDebug :app:testDebugUnitTest :shared:testDebugUnitTest --continue
git diff --check
```

真实设备文件提供者、配置变化/进程重建、系统退出信息、严重 OOM 和 release 混淆还原未设备验证；没有启动模拟器或安装 App。改动留在当前工作区，未提交。

## Global Constraints

- 第一版为常驻安全诊断，不实现正文采集开关。
- 默认导出窗口为点击时刻之前 30 分钟，普通导出不混入历史崩溃。
- 所有新落盘诊断先脱敏，不持久化凭据或私人正文。
- 不修改数据库 Schema，不引入监控 SaaS，不升级现有依赖。
- 当前工作区直接开发，保留已有修改，不创建 worktree，不自动提交。
- 日常只执行代码级测试与必要编译；纯文档修改不构建，涉及资源时合并执行打包验证。

容量/生命周期以 Spec 第 5 节为唯一来源，集中实现为策略常量。

## 执行前与验证节奏

- [ ] 读取 Spec、AGENTS.md；UI 工作补读 `docs/04-style-guide.md`、`docs/05-i18n-guide.md`、`docs/06-koin-viewmodel-guide.md`。
- [ ] `git status --short`，读取本任务将接触的既有差异，特别保留 Application、MainActivity、DiaryThoughtService 及相关测试中的用户工作。
- [ ] 每任务先写聚焦失败测试并确认 RED，再最小实现、同一测试 GREEN；不重复全量验证。
- [ ] SDK 信息先用版本匹配的本地源码/JAR；不足时按项目 Context7 规则单次查库、单次查文档。无证据不编造 API。

```bash
# 各任务替换为下面列出的实际测试类；RED/GREEN 使用同一命令。
./gradlew :shared:testDebugUnitTest --tests 'com.dailysatori.service.diagnostics.DiagnosticRedactorTest'
./gradlew :app:testDebugUnitTest --tests 'com.dailysatori.core.diagnostics.DiagnosticStoreTest'
```

## 路径约定

以下只是文档简称，不是新增构建变量；同一 Files 列表中的简写文件名沿用该行完整目录。

```text
S   = shared/src/commonMain/kotlin/com/dailysatori
SA  = shared/src/androidMain/kotlin/com/dailysatori
ST  = shared/src/commonTest/kotlin/com/dailysatori
SAT = shared/src/androidUnitTest/kotlin/com/dailysatori
A   = app/src/main/kotlin/com/dailysatori
AT  = app/src/test/kotlin/com/dailysatori
```

## Task 1：事件、安全投影与 trace

**Files**
- Create: `S/service/diagnostics/DiagnosticEvent.kt`、`DiagnosticRedactor.kt`、`Diagnostics.kt`。
- Tests: `ST/service/diagnostics/DiagnosticRedactorTest.kt`、`DiagnosticTraceTest.kt`。

**Interfaces**
- `DiagnosticInput` 为入口数据，禁止直接序列化落盘。
- `SafeDiagnosticEvent` 为只有 redactor 可以构造的可序列化事件，字段按 Spec 第 3 节。
- `DiagnosticRedactor.sanitize(input: DiagnosticInput): SafeDiagnosticEvent` 实施允许名单、异常投影、字节限长。
- `DiagnosticSink.tryEmit(event: SafeDiagnosticEvent): Boolean` 非阻塞提交。
- `Diagnostics` 注入 sink、时钟、ID 生成器；`suspend fun <T> operation(name: String, taskId: Long? = null, block: suspend () -> T): T` 建立 trace，保持返回值/异常。
- 网络适配器显式传入 trace/request 标识，不默认将内部 trace 发往远端 HTTP 头。

- [ ] 编写参数化安全测试：Authorization 大小写变体、嵌套 token、签名 URL、路径秘密、OAuth code、Cookie、异常 message、日记/对话诱饵、未知字段与 UTF-8 限长。编码后断言所有诱饵原值不存在，仍保留状态码/路由/异常类型。
- [ ] 写 trace 测试：两次重试共享 trace、request 不同；并行操作不串号；CancellationException 原样上抛；false/null 由调用方显式记录失败，不自动推断。
- [ ] 运行上述聚焦测试确认 RED。
- [ ] 实现固定 eventCode/允许字段、URL 路由表示、有界异常结构、单调时钟、上下文传播；未知文本省略并标 contentOmitted。测试注入固定时钟/ID，不依赖真实时间。
- [ ] 运行同一测试 GREEN；安全投影不得读配置数据库或依赖 Android Context。

## Task 2：滚动存储、快照、健康与 Kermit

**Files**
- Create: `A/core/diagnostics/DiagnosticStore.kt`、`DiagnosticLogWriter.kt`、`DiagnosticRuntime.kt`。
- Modify: `A/DailySatoriApplication.kt`、`A/core/di/AppModule.kt`、`S/di/SharedModule.kt`。
- Tests: `AT/core/diagnostics/DiagnosticStoreTest.kt`、`DiagnosticLogWriterTest.kt`。

**Interfaces**
- `DiagnosticStore` 实现 DiagnosticSink，构造接收目录、策略、时钟及可注入文件故障点。
- `suspend fun snapshot(endTimeMs: Long): DiagnosticSnapshot` 返回私有不可变快照描述，包含固定窗口/水位、文件、覆盖与健康元数据，可显式释放。
- `suspend fun clear()` 与普通写入/快照串行。
- `DiagnosticLogWriter` 复用 redactor，不将任意 message 标为 safe。
- `DiagnosticRuntime` 持有本进程唯一 store/writer；Application 早期初始化，Koin 注册同一对象，不创建第二份。

- [ ] 临时目录测试 24 小时/30 MiB、1 MiB 轮转、512 队列满、16 KiB 限长、坏尾行恢复及跨重启接续。
- [ ] 可控执行器测试 T/水位屏障、快照与继续写入/轮转/清理，验证 T 后事件排除、快照固定不被轮转修改。
- [ ] 注入磁盘满/权限错误，断言提交不抛存储异常、健康状态可用、恢复后补记缺口；未知 Kermit 自由文本不落盘。
- [ ] 跑 RED，实现单写入者与独立控制命令通道，普通队列满不能让快照/清理永久饿死。逐行读取、有界缓冲，禁止任务日志式整文件重写。
- [ ] 使用 `noBackupFilesDir/diagnostics/`；核查自建备份选择列表确保排除；初始化在 Koin/迁移之前，保留现有启动业务顺序。
- [ ] 跑同一测试 GREEN。

## Task 3：HTTP、AI 与平台网络来源

**Files**
- Create: `S/service/diagnostics/DiagnosticHttpObserver.kt`、`SA/service/diagnostics/DiagnosticNetworkAdapters.kt`。
- Modify: `SA/di/HttpClientFactory.android.kt`、`S/di/HttpClientFactory.kt`、`SA/service/ai/LangChainAiClient.android.kt`、`S/service/ai/AiService.kt`。
- Modify: `SA/platform/WebViewLoader.android.kt`、`A/ui/component/media/SmartImage.kt`、`A/core/service/AppUpgradeService.kt`、`A/core/service/WebServerService.kt`、`A/DailySatoriApplication.kt`。
- Tests: `ST/service/diagnostics/DiagnosticHttpObserverTest.kt`、`SAT/service/diagnostics/DiagnosticNetworkAdaptersTest.kt`。
- Extend tests: `ST/service/ai/AiServiceStreamingTest.kt`、`SAT/service/ai/LangChainAiClientAndroidTest.kt`。

**Interfaces**
- DiagnosticHttpObserver 消费 Task 1 Diagnostics，把请求开始、headers、终态转为固定安全事件。
- Task 2 的单例通过 DI/初始化参数传给客户端工厂；expect/actual/调用点同步修改，测试允许空 sink。
- 每个 adapter 声明 Spec 第 7 节 coverage，报告使用实际注册结果，不维护虚假的“全支持”常量。

- [ ] 核查当前 Ktor/OkHttp body 生命周期、LangChain builder/decorator、Gemini 公开接入、Coil 3 网络工厂/单例 API；以版本匹配证据选接入点并写代码注释，不另建调研报告。
- [ ] 写测试：200/401/500、连接失败、超时、取消、重试、重定向、headers 后流中断、未消费响应关闭；每次可见请求恰好一个终态，HTTP 成功独立于业务结果。
- [ ] 扩展流式测试：首段耗时/最终结果有记录，但 prompt/生成文本/逐 token 无记录，消费者读到的原字节不变。
- [ ] 跑 RED，接入 metadata-only；不开 Ktor BODY/ALL 或 SDK 原始日志，不预读一次性流、不因日志新增请求/重试。
- [ ] OpenAI/Anthropic 接入经验证的传输扩展；Gemini 无公开接口则 lifecycle-only 并说明限制，不用反射/升级绕过。跨 SDK 线程显式传递不可变关联对象。
- [ ] Coil 接入专属可观测客户端并保留缓存；WebView 补可见回调；DownloadManager 记录提交/结果；WebServer 只记安全路由/状态。未知大小/状态标 unknown，不填 0 冒充测量。
- [ ] 跑同一测试 GREEN；逐来源核对实际 coverage。

## Task 4：业务结果与旧日志安全边界

**Files**
- Modify: `S/service/asynctask/AsyncTaskRunner.kt`、`S/service/externalfavorites/FavoriteSyncService.kt`、`S/service/parser/WebpageParserService.kt`。
- Modify: `S/service/import/ImportService.kt`、`S/service/backup/BackupService.kt`、`S/service/diary/DiaryThoughtService.kt`、`S/service/migration/DatabaseMigration.kt`。
- Modify: `A/core/task/AsyncTaskLogStore.kt`、`A/MainActivity.kt`、`A/core/recording/DiaryRecordingService.kt`、`A/core/service/I18nInitializer.kt`、`A/ui/feature/article/ArticlesViewModel.kt`。
- Tests: `ST/service/diagnostics/DiagnosticBusinessFlowTest.kt`。
- Extend: `ST/service/asynctask/AsyncTaskRunnerTest.kt`、`AT/core/task/AsyncTaskLogStoreTest.kt`、`SAT/service/diary/DiaryThoughtServiceTest.kt`。

**Interfaces**
- 业务边界使用 Diagnostics.operation 和固定事件，不另造 trace。
- 旧 HTTP writer 使用 redactor 安全摘要，保留 AsyncTaskLogger/FavoriteSyncHttpLogger 接口和任务中心读取能力。

- [ ] 写链路测试：200 后解析失败、false/null 失败、重试最终成功、取消不额外重试；断言 trace/task 与最终结果正确。
- [ ] 旧 writer 加 header/body/URL 凭据诱饵，断言新任务文件/诊断文件均无诱饵；旧文件不进入新报告。
- [ ] 跑 RED，补公共 runner/所列服务入口和终态，解析/写库等关键阶段使用固定事件，不逐函数打点、不记 SQL/私人内容。
- [ ] 所列 Android Log 关键异常/状态改走安全事件或桥接；不企图拦截所有第三方 Android Log。任务文件不整份镜像，以新结构化事件补足。
- [ ] 跑同一测试 GREEN，核查业务返回值、重试条件、取消与数据库语义未变。

## Task 5：崩溃现场与历史退出

**Files**
- Create: `A/core/diagnostics/DiagnosticCrashHandler.kt`、`DiagnosticExitInfoReader.kt`。
- Modify: `A/core/diagnostics/DiagnosticRuntime.kt`、`DiagnosticStore.kt`、`A/DailySatoriApplication.kt`。
- Tests: `AT/core/diagnostics/DiagnosticCrashHandlerTest.kt`、`DiagnosticExitInfoReaderTest.kt`。

**Interfaces**
- DiagnosticCrashHandler 包装原 Thread.UncaughtExceptionHandler，只依赖安全异常投影与独立紧急文件，不依赖 actor。
- DiagnosticExitInfoReader 通过可替换平台读取器输出规范化事件；API 30 以下 unsupported。
- runtime 的 `suspend fun recoverPreviousExit()` 在普通清理前整理现场，store 暴露最新现场描述供 Task 6 消费。

- [ ] 假 handler 测试写入成功、失败和递归保护后原 handler 恰好调用一次，不执行真实退出。
- [ ] 测试隔夜先固化后清理、10 MiB/总 30 MiB/3 份/7 天、恢复幂等、损坏现场、磁盘错误说明。
- [ ] 测试 API 26/30+、不可读/重复退出记录、ANR/native/用户退出不误分类；原始 trace 不落盘。
- [ ] 跑 RED，实现独立紧急写入和有限异常遍历；handler 不 drain、不 runBlocking、不改变正常退出语义。
- [ ] 跑同一测试 GREEN；报告注明初始化前、强杀及 OOM 局限。

## Task 6：报告、设置与保存状态机

**Files**
- Create: `A/core/diagnostics/DiagnosticExporter.kt`。
- Create: `A/ui/feature/settings/diagnostics/DiagnosticSettingsScreen.kt`、`DiagnosticSettingsViewModel.kt`。
- Modify: `A/ui/feature/settings/SettingsScreen.kt`、`A/core/di/ViewModelModule.kt`。
- Resources: `shared/src/commonMain/resources/i18n/zh.yaml`、`en.yaml`；`app/src/main/assets/i18n/zh.yaml`、`en.yaml`。
- Tests: `AT/core/diagnostics/DiagnosticExporterTest.kt`、`AT/ui/feature/settings/diagnostics/DiagnosticSettingsViewModelTest.kt`。

**Interfaces**
- DiagnosticExporter 消费快照、coverage、最新现场，向 OutputStream 流式写 Spec 第 8 节报告。
- 状态：`Idle → Preparing → AwaitingDestination → Saving → Saved/Failed`；取消回 Idle，拒绝重复动作。
- Compose 管理 CreateDocument launcher；ViewModel 管理私有快照 token/IO，仅当前 token 消费 URI 回调；取消、过期回调、页面离开释放快照。
- 清理仅非 Saving 时执行，取消待选目的地 token、调用 store.clear、重置状态，不碰已另存/旧任务文件。

- [ ] 模拟时钟测试：选择器停留 20 分钟仍为原 T 窗口；精确包含 T-30分钟/T，不混历史崩溃。
- [ ] 测试 JSONL 可解析、UTF-8、空窗口、事实统计、覆盖/缺口、构建信息、截断和未完成请求提示。
- [ ] 失败 OutputStream 测试 open/write/close 失败均非成功；取消/重复点击/过期回调/清理冲突不泄漏快照。
- [ ] 跑 RED，流式生成报告；SettingsPage 切换、Koin ViewModel、主题常量和 i18n 沿用项目模式。

```kotlin
val createDocument = rememberLauncherForActivityResult(
    ActivityResultContracts.CreateDocument("text/plain"),
) { uri -> viewModel.onDestinationChosen(uri) }
// 消费 ViewModel 的一次性选目的地事件后 launch(fileName)，不在重组中重复 launch。
```

- [ ] 实现 `DiagnosticSettingsViewModel.onDestinationChosen(uri: Uri?)`：null 取消清理；有效 URI 在 IO 打开/写入/use/close 后才成功；失败尽力删除本次新建残缺目标，删除不支持时说明残留。SAF URI 不转 File(path)。
- [ ] 同步四份中英文资源，不加存储权限、FileProvider 分享或未实现正文模式按钮。
- [ ] 跑同一测试 GREEN。

## Task 7：集中审查与最终验证

**Files**
- Extend: `AT/core/diagnostics/DiagnosticExporterTest.kt`，增加临时目录集成场景。
- Docs: `docs/02-testing.md`、`docs/03-app-features.md`，仅追加本功能，保留既有差异。

- [ ] 代码级集成：操作/失败请求/崩溃 → 模拟隔夜重启 → 导出现场；验证安全、关联、现场保留、普通导出不混历史崩溃。
- [ ] 压力/故障注入：超过容量、持续采集并发导出、坏尾行、磁盘失败、队列满；断言有界、缺口可见，不用依赖本机速度的脆弱耗时阈值。
- [ ] 当前代理一次集中审查：Spec 覆盖、写前安全、流式透明、终态去重、单写入所有权、handler 转交、用户修改保留。集中修复 Important/Critical，仅重跑受影响检查。
- [ ] 文档记入口、保存范围、平台限制；不写“所有 HTTP 全覆盖”或“崩溃绝不丢日志”。
- [ ] 第一版涉及资源，结束时合并执行一次必要完整验证：

```bash
./gradlew :app:compileDebugKotlin :app:assembleDebug \
  :app:testDebugUnitTest :shared:testDebugUnitTest
git diff --check
```

- [ ] 不启动模拟器；实际 SAF、不同系统退出记录、真实强杀/OOM、release 混淆还原列为未设备验证。仅发布前/明确要求时验证并关闭模拟器。
- [ ] 交付路径、实际命令/结果与风险。用户要求提交时才检查 staged diff、提交本任务文件；代码编译失败不得提交。

## 计划自检

| Spec | 对应任务 |
|---|---|
| 事件/安全/trace | 1、4 |
| 有界持久化/时间窗口/快照/健康 | 2、6 |
| 多网络栈/流式/覆盖声明 | 3 |
| 旧任务日志新输出安全 | 4 |
| 崩溃/隔夜恢复/退出原因 | 5 |
| 单文件/保存/清理/i18n | 6 |
| 故障注入/编译打包/交付 | 7 |

SDK 接入假设由 Task 3 明确检查，并有不可接入分支；不将文档设计当作已通过的运行验证。
