# 生活档案与待办导入实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. 按项目规则在当前工作区由当前代理执行，不使用 worktree 或子代理。

**Goal:** 实现已确认的生活档案界面、自然语言新增/修改和续费订阅待办的可重复导入。

**Architecture:** 共享模块负责模型、AI 协议和来源校验；Android 私有文件仓库负责加密存储。ViewModel 管理输入、请求与保存，用户确认后提交草稿，待办只读。

**Tech Stack:** Kotlin Multiplatform、Jetpack Compose、Koin、kotlinx.serialization、现有 AI 配置及 Android Keystore AES-GCM。

**Spec:** `docs/superpowers/specs/2026-10-03-life-archive-design.md`

## Global Constraints

- 当前工作区开发；保留既有 Config/BackupService/测试及未跟踪文件。
- 不新增数据库 Schema；档案和自定义分类位于 `noBackupFilesDir/life_archive/`，完整内容加密，不进入现有备份、记忆、MCP 或普通聊天。
- 云端直接处理本次原文/当前资料，不做脱敏；用户点击 AI 动作才上传，不记录原文或远端响应正文。
- 输入最多 20,000 字；响应最多 100,000 字；新增最多 20 条，每条最多 50 个动态字段；单条编辑只能返回一条。
- 待办每批最多 20 条且文本不超过 20,000 字；原待办只读，导入/同步需确认并校验来源版本。
- UI 使用现有主题和中英文字符串；搜索默认收起、类型可自定义、字段可手动修改。
- 仅代码级测试；最终受影响测试、compileDebugKotlin 和涉及资源的 assembleDebug 合并执行，不启动模拟器。

## Review Focus

- AI 响应包含代码块包装时解析合法 JSON；纯文本、截断、多结果保持失败，避免误保存。
- 编辑中用户改动/离开页面后，迟到响应不得覆盖输入或已保存资料。
- Keystore 丢失、损坏文件、失败写入应显示错误，不伪装为空列表或覆盖原文件。
- 已导入来源再次同步不重复创建，且不静默覆盖用户改过的字段或修改原待办。
- 分类删除/重命名不使资料失联；提醒日期与实际到期/扣款日期有明确区别。

## Task 1：资料模型与严格 AI 协议

**Files:** 创建 `shared/src/commonMain/kotlin/com/dailysatori/service/lifearchive/LifeArchiveModels.kt`、`LifeArchiveAiCodec.kt`，以及对应 `commonTest/.../lifearchive/LifeArchiveAiCodecTest.kt`。

**Interfaces:** `LifeArchiveField(name: String, value: String)`；`LifeArchiveCategory(id: String, name: String)`；`LifeArchiveRecord(id: String, title: String, categoryId: String, body: String, fields: List<LifeArchiveField>, createdAt: Long, updatedAt: Long, sourceReminderId: String? = null, sourceReminderVersion: Long? = null)`；`LifeArchiveAiCodec.decode(response: String, categories: List<LifeArchiveCategory>, editing: Boolean): List<LifeArchiveRecord>` 生成未保存草稿，本地控制 ID/时间/来源。

- [x] 写聚焦测试：动态字段/自定义分类、代码块、未知分类、重复字段、空/超限响应、单条编辑数量限制，本地元数据不接受模型伪造。
- [x] `./gradlew :shared:testDebugUnitTest --tests '*LifeArchiveAiCodecTest'` 确认 RED；实现模型/校验后同命令确认 GREEN。

## Task 2：加密仓库与分类管理

**Files:** 创建 `shared/.../service/lifearchive/LifeArchiveRepository.kt`、`app/src/main/kotlin/com/dailysatori/core/lifearchive/EncryptedLifeArchiveRepository.kt`；测试 `app/src/test/kotlin/com/dailysatori/core/lifearchive/EncryptedLifeArchiveRepositoryTest.kt`。

**Interfaces:** 仓库提供 `suspend fun records(): List<LifeArchiveRecord>`、`suspend fun categories(): List<LifeArchiveCategory>`、`suspend fun save(record: LifeArchiveRecord, expectedUpdatedAt: Long?): LifeArchiveRecord`、`suspend fun delete(id: String)`、`suspend fun addCategory(name: String): LifeArchiveCategory`、`suspend fun renameCategory(id: String, name: String)`、`suspend fun deleteCategory(id: String, replacementId: String)`。Android 实现注入目录和 `SecretValueCipher`，串行文件事务；版本值须单调递增，不能仅用可能相等的毫秒时钟判断冲突。

- [x] 写测试：完整落盘内容加密、解密异常显式失败、原子写入失败保留原文件、同版本写冲突、来源唯一、分类重命名及带资料迁移。
- [x] 聚焦 RED/GREEN：`:app:testDebugUnitTest --tests '*EncryptedLifeArchiveRepositoryTest'`；分类迁移采用完整加密清单的原子提交，避免多文件半成功。
- [x] 目录严格使用 `noBackupFilesDir`，不修改现有 BackupService；首次保存提示不可恢复/迁移限制。

## Task 3：AI 新增/优化服务

**Files:** 创建 `shared/.../service/lifearchive/LifeArchiveAiService.kt`；按需为 `shared/.../service/ai/AiService.kt` 添加本功能专用的安全错误边界；测试 `shared/src/androidUnitTest/.../lifearchive/LifeArchiveAiRequestTest.kt`。

**Interfaces:** `suspend fun organize(text: String, categories: List<LifeArchiveCategory>, fieldNames: List<String>): List<LifeArchiveRecord>`；`suspend fun optimize(record: LifeArchiveRecord, instruction: String, categories: List<LifeArchiveCategory>): LifeArchiveRecord`。复用默认 AI 配置，优化保留原 ID/创建时间/来源；不调用记忆和工具。

- [x] 使用 MockEngine 写 RED 测试：请求范围、编辑携带当前草稿和说明、无其他资料值、错误正文不进入返回错误/日志、取消传播。
- [x] 实现新增/编辑提示词，规则为不编造、明确纠正优先、保留未涉及字段、结构校验成功后才返回草稿；同测试 GREEN。

## Task 4：确认过的界面与编辑状态

执行裁定：先完成 Task 5 的共享导入服务，再一次性接入 Task 4 页面与状态，避免为未完成的导入功能编写临时占位接口。

**Files:** 创建 `app/.../ui/feature/lifearchive/LifeArchiveScreen.kt`、`LifeArchiveViewModel.kt`；修改 `core/di/ViewModelModule.kt`、仓库注册所在 DI 模块、`core/navigation/Routes.kt`/`NavHost.kt`、`ui/feature/home/HomeScreen.kt`/`myspace/MySpaceScreen.kt` 和中英文 `strings.xml`；测试 `app/.../lifearchive/LifeArchiveViewModelTest.kt`。

**Interfaces:** `LifeArchiveRoute` 指向列表，编辑页面在模块内由记录 ID/新增状态控制；ViewModel 的 StateFlow 包含记录、分类、可见搜索/搜索词、当前草稿、请求前草稿、会话 ID、忙状态和固定错误。

- [x] 用假服务/仓库写 RED：搜索展开/关闭、自定义类型、迟到响应丢弃、重复发送禁止、优化取消恢复本次之前草稿、保存冲突。
- [x] 接入现有「我的」入口，按效果图实现列表、文字输入+整理结果、动态字段编辑+「再说一段话」；修改字段视觉标记由前后草稿比较生成。
- [x] 只有确认保存才调用仓库；手动改动使旧会话失效；同聚焦状态测试 GREEN。

## Task 5：只读待办 AI 分析与幂等同步

**Files:** 创建 `shared/.../service/lifearchive/LifeArchiveReminderImportService.kt` 和对应测试；扩展 Task 3 服务协议、Task 4 ViewModel/页面，复用 `ReminderRepository.observeAll().first()` 取得快照。

**Interfaces:** `suspend fun analyze(reminders: List<Reminder>, existing: List<LifeArchiveRecord>, categories: List<LifeArchiveCategory>): List<LifeArchiveRecord>`；导入草稿只允许当前批次的 `sourceReminderId`，本地写入来源版本。以来源 ID 匹配已有档案，未变化的来源跳过，再次分析生成待确认更新；已有档案值只用于该来源的更新。

- [x] 写 RED：AI 识别续费/订阅并跳过普通待办、批量边界、伪造/重复来源拒绝、同一来源重复导入不新增、来源变更保存拦截、手动字段保留、原待办无写调用。
- [x] 添加「从待办导入」，展示草稿及来源，分批失败可重试，保存前重新读取来源版本和档案版本；同测试 GREEN。
- [x] 日期字段区分提醒日期与来源明确说出的扣款/到期日期；状态不作为订阅仍有效的推断依据。

## Task 6：集中审查、文档和最终验证

**Files:** 更新 `docs/03-app-features.md`；只审查本任务代码与上述测试，集中修复 Critical/Important。

- [x] 核对主动上传、保存确认、日志隔离、来源真实性、编辑冲突、分类一致性、备份排除及原待办只读。
- [x] 一次运行 `./gradlew :shared:testDebugUnitTest :app:testDebugUnitTest :app:compileDebugKotlin :app:assembleDebug`，必要时对受影响失败项聚焦复跑；不启动模拟器、不真实上传用户资料。
- [x] 汇报实际验证、未进行的 UI/真实模型测试及本地档案不可恢复限制；不自动发布、安装或提交用户其他文件。

## 独立的待办 JSON 故障修复

这项修复针对现有解析器，不依赖生活档案计划，可先完成：用假 AI 返回合法 JSON 的代码块等常见包装复现失败，只移除格式包装，保留严格字段/来源验证。增加单条/批量回归及后台任务回归，验证编译；尚未取得实际失败响应时，不声称已确认用户那次失败的唯一根因。
