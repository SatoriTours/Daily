# 日记多模态采集与个人知识库开发交接

更新时间：2026-07-12
当前分支：`main`
当前功能 HEAD：`80c6fd36 feat: add compact diary capture controls`

功能代号：`Diary Multimodal Knowledge`
中文名称：`日记多模态采集与个人知识库`

## 总目标

在不改变现有日记列表展示方式的前提下，为日记增加后台语音录音、语音转文字、视频拍摄、图片/文件附件和个人知识库入库能力。日记始终是核心对象，所有媒体和处理状态都归属于普通日记。

完整交付包含：

- 日记附件数据模型和安全文件生命周期。
- 后台/锁屏持续录音与系统通知控制。
- 语音转写和失败重试。
- 日记、文章、书籍统一进入个人知识库。
- 视频、图片和文件采集。
- 保持现有列表的紧凑深色 UI。
- 权限、异常恢复、真机和锁屏验证。

## 开发总览

| 阶段 | 状态 | 内容 |
|---|---|---|
| 设计与原型 | 已确认 | 深色模式、现有列表不变、小加号采集入口 |
| Task 1 | 已完成 | 真实日记 ID、附件表、迁移、文件清理 |
| Task 2 | 已完成 | 知识库提取绑定真实 diary ID |
| Task 3 | 已完成 | 后台录音、锁屏通知、Runtime cleanup |
| Task 4 | 已完成 | 语音转写与知识库异步任务 |
| Task 5 | 已完成 | 紧凑采集 UI 与现有 DiaryCard 附件行 |
| Task 6 | 未开始 | 麦克风/通知权限、视频和文件接入 |
| Task 7 | 未开始 | 集成、真机、后台与锁屏验收 |

## 新会话入口

新会话先读取：

1. `AGENTS.md`：token 节省与项目执行规则。
2. 本文件 `docs/DIARY_MULTIMODAL_KNOWLEDGE_HANDOFF.md`：当前唯一交接来源。
3. 仅在需要实现具体任务时，再读取：
   - `docs/superpowers/specs/2026-07-11-diary-capture-knowledge-design.md`
   - `docs/superpowers/plans/2026-07-11-diary-capture-knowledge.md`

不要重新扫描完整聊天历史，不要重新设计已确认的 UI，不要使用 worktree。用户要求子代理只使用 `gpt-5.6-sol`、reasoning `medium`；默认直接在当前会话开发，不做多轮代理审查。

## 已确认产品要求

- 开始语音录音时，立即创建一篇普通日记并取得真实非零日记 ID。
- 录音不是独立收件箱，不提供“全部 / 录音 / 待整理 / 已入库”等分类。
- 日记列表必须保持当前 App 样式：月份概览、日期分组和现有 `DiaryCard` 不改。
- 音频、视频、图片和文件作为普通日记内部附件展示。
- 录音停止后异步转文字；转写内容进入日记正文草稿。
- 日记和附件文本进入个人知识库，与文章、书籍统一检索。
- 转写或知识库处理失败不能删除日记或原始音频，必须可重试。
- App 切到后台、熄屏或锁屏后，录音必须继续。
- 后台录音使用 microphone foreground service。
- 通知栏和锁屏显示常见录音 App 样式：固定标题“语音日记录音中”，副文案显示状态和时长，不显示日记正文。
- 通知提供暂停/继续、停止和打开日记；持久化失败时提供重试/放弃。
- 通知为 ongoing、`CATEGORY_SERVICE`、`VISIBILITY_PUBLIC`。
- Android 13+ 锁屏/通知栏可见以前台入口取得 `POST_NOTIFICATIONS` 权限为前提。
- 后台不能新启动麦克风服务；必须由用户在 App 可见时点击开始，之后才可持续在后台录音。

## UI 约束

- 深色模式。
- 保留现有 `DiaryScreen`、`DiaryMonthHeader`、`DiaryDateHeader`、`DiaryCard`、照片墙、心情、标签、Markdown 正文和展开行为。
- 右下角维持 48dp 点击区域、36dp 视觉圆形加号。
- 加号展开紧凑菜单：语音日记、文字日记、拍摄、添加文件。
- 不显示常驻“写点什么”输入框。
- 录音中才显示顶部轻提示和底部紧凑录音控制条。
- 原型文件保留在 `docs/mockups/`，当前 9 个未跟踪原型属于用户/设计过程，不要删除或批量提交。

## 已完成开发

### Task 1：日记 ID 与附件数据层

已完成并审查通过：

- `diary_attachment` SQLDelight 表、索引和 V20 迁移。
- 新日记插入返回真实 ID，使用旧 Android SQLite 兼容的事务内 `last_insert_rowid()`。
- 附件仓库、状态更新、Koin 注册。
- Android SQLite foreign keys 开启和 cascade。
- 删除日记时安全清理 app-owned 附件文件。
- canonical path containment，拒绝 `..` 和同前缀目录逃逸。

关键范围提交：`5b5dc669..6fe71220`。

### Task 2：知识库 source ID

已完成并审查通过：

- 新日记知识提取使用持久化后的真实 ID，不再使用 `sourceId = 0`。
- 保存失败不提取；提取失败不伪装成日记保存失败。
- 保存仍由 `viewModelScope` 管理。
- 新增最小 `MemoryExtractor` 接口，`MemoryExtractService` 保持 final。
- schema V20 相关测试已同步。

关键范围提交：`6fe71220..e9fe5032`。

### Task 3：后台录音核心

主体已实现并提交：

- `DiaryRecordingService` microphone foreground service。
- Manifest 权限：`RECORD_AUDIO`、`FOREGROUND_SERVICE`、`FOREGROUND_SERVICE_MICROPHONE`、`POST_NOTIFICATIONS`。
- `MediaRecorder` API 26+ 适配与专用单线程执行边界。
- 进程级 `DiaryRecordingRuntimeManager`、Runtime 和 Actor，串行处理 Start/Pause/Resume/Stop/Retry/Shutdown。
- Service replacement 的 1000ms generation-safe 接替窗口。
- closing 窗口使用“正在准备录音”前台占位通知，旧 Runtime 完整关闭后才创建新 Runtime，避免 recorder 重叠。
- session UUID token 绑定输出文件，拒绝 stale/zero-byte/错误路径文件。
- attachment diary/type 归属校验。
- persistence 1s/2s/4s 重试、Retry/Discard。
- 锁屏公开 ongoing 通知和录音操作。

主要提交（按时间）：

- `4c715278 feat: record voice diaries in foreground service`
- `57e1385d fix: harden diary recording lifecycle`
- `06454919 fix: complete diary recording failure recovery`
- `852032d4 refactor: serialize diary recording sessions`
- `84316e0f fix: make diary recording runtime process scoped`
- `0d7a2d49 fix: close diary recording runtime lifecycle gaps`
- `c873a53b fix: finalize diary recording service teardown`
- `12607b1a fix: rebuild closing diary recording runtime`
- `8d74dab3 fix: serialize diary recording runtime replacement`
- `a133d027 fix: reject diary recording without foreground host`

最后一次已提交验证结果：

- recording 聚焦测试：57 passed。
- App 全量：743 passed。
- Shared 全量：434 passed。
- App/shared compile：passed。

真机尚未验证：API 26/30/31/34+、OEM `MediaRecorder`、Service recreation、锁屏通知、熄屏持续录音和 `stopSelfResult(startId)` 时序。

## Task 3 与 Task 4 收尾

- Task 3 cleanup 提交：`b8f0717e fix: retain rejected recording cleanup ownership`。
- Task 4 提交：`b71cd889 feat: transcribe and index diary captures`。
- 录音完成后按 `diary-transcribe:<attachmentId>` 唯一键排队，并立即交给 WorkManager。
- OpenAI-compatible multipart `/audio/transcriptions` 复用默认 AI base/token，speech model 默认 `whisper-1`。
- 转写状态按 queued/processing/completed/failed 持久化；失败不删除音频。
- 仅精确替换自动正文 `这篇日记正在转写…`，用户已编辑正文则追加 `## 语音转写`。
- 转写与日记正文在同一数据库事务更新；完成后按真实 diary ID 链式排队知识提取。
- 知识任务合并当前正文和已完成附件转写，按 `diary-knowledge:<diaryId>:<updatedAt>` 幂等。
- Shared/App 全量测试及 APK 构建通过；已安装到 `192.168.2.120:37749` 并冷启动，无 Koin/WorkManager 崩溃。

## 后续任务

### Task 5：保持现有列表的采集 UI（已完成）

- 小加号紧凑菜单。
- 语音入口创建日记/附件后启动 Runtime。
- 顶部录音提示、底部紧凑控制条。
- 附件行放在现有 `DiaryCard` 正文和 footer 之间。
- 不重做列表。
- 提交：`80c6fd36 feat: add compact diary capture controls`。
- Shared/App 全量测试与 APK 构建通过；已安装并冷启动。
- 设备 ROM 禁止 ADB 注入点击，菜单和录音控制仍需手动视觉验收。

### Task 6：权限、视频与文件

- Android 13+ `POST_NOTIFICATIONS` 运行时权限 UX。
- 麦克风权限；拒绝时不得启动录音。
- 视频拍摄、系统文件选择、app-owned copy、500MB 上限。
- 取消选择不创建空日记。

### Task 7：最终集成与设备验证

- 自动测试和编译。
- 安装真机。
- 验证前台开始后：切后台、熄屏、锁屏持续录音。
- 验证锁屏/通知栏提示和操作。
- 验证权限拒绝、低存储、进程中断、转写重试、删除附件文件。

## Token 节省执行方式

- 遵守 `AGENTS.md`。
- 当前会话直接开发，不使用子代理。
- reasoning 使用 medium。
- 只读精确行段，不重复读取完整 diff/计划。
- 每个微修复只跑聚焦测试；阶段结束才跑全量。
- Task 3 收尾后不再额外审查循环，直接进入 Task 4。
- 更新只报告改动、验证和真实阻塞。

## 新会话恢复指令

切换会话后直接发送：

```text
继续开发“日记多模态采集与个人知识库”。先读取 AGENTS.md 和
docs/DIARY_MULTIMODAL_KNOWLEDGE_HANDOFF.md，按交接文件从当前未提交的
Task 3 cleanup 开始。直接在当前工作区开发，不使用子代理，reasoning medium，
优先节省 token。
```
