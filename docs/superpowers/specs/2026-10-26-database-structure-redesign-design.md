# 数据库结构重构详细设计

日期：2026-10-26。状态：总体方向已确认；本文为详细设计，尚未实施。

## 1. 目标与边界

优先保护提醒、日记、完整助手对话、点子，以及它们的原始输入、附件、历史和关联。减少同一事实的重复维护，不以减少表数或压缩容量为首要目标。

- 维持 KMP + SQLDelight、单个 SQLCipher 数据库、现有设备密钥及密码加密备份机制。
- 保持现有功能、记录 ID、顺序、时间和已确认内容；不自动删除爬取内容。
- 不新增跨设备同步、通用实体框架、全库事件溯源、附件内容去重或新的 AI 行为。
- 迁移不调用 AI 或网络，不通过重新生成来“补回”用户数据。
- 本轮只设计，不修改 Schema、执行迁移、读取真实用户库、提交或发布。

基线：`main`、HEAD `3dd073a5`、`DatabaseConfig.currentSchemaVersion = 38`。工作区已有备份加固保持不动；此前相关 176 项测试通过，不是本设计已经实现的证据。

已核实的结构问题：

| 现状 | 风险/改进 |
|---|---|
| `diary.images` 与 `diary_attachment` 两种附件表示 | 统一关联，物理文件不搬迁 |
| `diary.tags` + 多个 setting JSON | 标签、来源、排除与撤销需跨表示一致，改为日记域内保存 |
| 草稿 confirmed/discarded/confirmation_state | 收敛生命周期，保留独立勾选状态 |
| 每条提醒完整 profile_json | 引用共享的不可变配置快照，不引用可变模板 |
| 点子来源与事件重复嵌入来源快照 | 独立快照，来源和事件引用 |
| 部分助手关联无外键 | 有选择地补关系约束及清理边界 |
| 缺 schema_version 直接视为新库、迁移末尾统一写目标版本 | 改为结构识别、逐版本验证，失败不推进 |

Android 新建及现有数据库打开路径均已启用外键；本次不是简单补一条 PRAGMA。备份/内存/测试连接也要核对配置，历史孤立记录须诊断。

## 2. 架构选择与数据归属

已选：**单个加密库、模块内规范化、逻辑分层**。暂不拆物理库，避免跨库事务与恢复一致性问题；不合并所有消息表，避免不同生命周期互相牵连。

| 层次 | 内容 | 规则 |
|---|---|---|
| 个人核心 | 原始输入、用户编辑、附件、完整消息、历史、人工确认内容 | 只有明确领域删除才能删除 |
| 持久执行 | 当前提醒周期、草稿生命周期、动作结果、幂等身份、恢复暂停、隐私排除 | 不是缓存，完成且无依赖才可处理 |
| 派生投影 | FTS、摘要、索引、明确标识为自动生成的记忆 | 可重建，不能覆盖人工内容 |
| 外部内容 | 远程同步、文章、下载缓存 | 本期不清理；存在用户关系时不能当纯缓存 |

以字段和来源分类，不整表粗暴分类。任务 payload 若仍含唯一原始输入，须先迁出原文才可清理任务。动作参数和上下文可能是历史意图，不因是 JSON 就判冗余。

主动收藏、批注、手工文章、外部收藏导入，以及捕获进点子的快照均受保护。既有文章三种正文、封面路径与 URL、外部收藏 provider/排序字段保留，遵守[现有字段语义](../../database-schema-semantics.md)。本期不另造收藏系统。

通用规则：

- 既有业务 ID 不变，不统一转换类型；新表引用原主键。
- UTC 时间戳沿用毫秒；提醒本地日期、时间、时区不按迁移机器时区重算。
- 状态使用枚举 CHECK，布尔限制 0/1；索引服务实际查询。
- 消息与事件按 `(created_at,id)` 稳定排序/分页，不因同毫秒而漏记录。
- 任务租约和重试计数由 async_task 单独拥有，不复制成另一套可写真相。
- JSON 用于版本化快照、事件及有限聚合，不代替应受约束的关系。

## 3. 日记：正文、附件与标签

### 3.1 正文与附件

`diary` 保留 id/content/mood/parent_diary_id/created_at/updated_at，最终移除 images/tags。保留现有主日记/续写模型和 diary_thread_revision，不新建另一套 thread ID。

`diary_attachment` 保留既有 ID、日记归属、文件元数据、录音、转写及处理状态，增加：

| 字段 | 约束与含义 |
|---|---|
| position INTEGER NOT NULL | 同一日记稳定显示顺序，非负；索引 `(diary_id,position,id)` |
| remote_url TEXT NULL | 历史网络图片地址，与本地路径区别保存 |
| local_path | 无本地副本时允许为空；与 remote_url 至少一个非空 |

kind 支持 image/audio/file。未知旧 kind 不猜测替换，预检报告冲突。既有转写、处理状态不在本次附件规范化时顺便重构。

迁移：

1. 现有附件 ID、路径、转写和状态原样保留，按既有稳定顺序赋 position。
2. diary.images 的每个有效路径产生图片关联，保持图片原始顺序；图片组与原附件组分别保持当前界面的组内顺序。
3. 仅过滤现有备份代码明确识别的历史空值占位；缺失文件不是空值，关联与已有转写必须保留。
4. 网络图片映射 remote_url，不下载；本地文件不改名、不移动。跨设备重定位仍由恢复流程负责。
5. 只有证明两份表示是同一条双写关联且顺序一致才合并；否则保留两条，不能仅凭路径相同删除关联。
6. 迁移不回收文件。运行期删除关联后，文件清理须确认没有其他日记、文章或封面引用，不能依靠删关联就直接删共享文件。

### 3.2 标签

新增日记专属表，不复用文章标签表：

| 表 | 主要字段与约束 |
|---|---|
| diary_tag_definition | id PK、name UNIQUE NOT NULL；不截断旧名称 |
| diary_tag_alias | alias PK、tag_id FK；指向最终规范名，不允许循环 |
| diary_tag_assignment | **仅当前投影**，`(diary_id,tag_id) PK`；origin(manual/automatic)、state(active/suppressed)、position、source_revision |
| diary_tag_history | id PK、diary_id/tag_id FK、operation_id、decision(active/suppressed/removed)、origin、position、source_revision NULL、source_fingerprint NULL、edit_revision、occurred_at NULL；UNIQUE(operation_id,diary_id,tag_id) |
| diary_tag_processing | root_diary_id PK/FK、processed_revision、edit_revision；处理正文版本与标签修改版本不同 |
| diary_tag_catalog_state | 单行版本化聚合：format_version、待定名称、合并建议、最后一次合并撤销载荷 |

手工置顶转 manual；删除自动标签保存 suppressed。抑制只对相应正文版本有效；原文改变后按现有行为不再抑制新版本，但不丢失历史决定。

当前状态和历史分开：assignment 供展示、搜索和新 AI 写回使用，history 只保存决定及其来源作用域，不作为第二个可写当前状态，也不要求靠重放历史启动。标签编辑、自动写回、置顶、合并、撤销在同一事务中追加决定、修改投影并递增 edit_revision；该版本覆盖所有标签修改，不只是手工修改。迁移先写入已有决定作为历史基线，后续 removed 决定也记录，不能把旧历史 UPDATE 成新状态。

正文变化时，旧 suppressed 决定保留在 history，移出当前有效投影；同标签在新正文重新激活只新增决定和更新当前投影，不占用或覆盖旧版本历史。history 索引 `(diary_id,edit_revision,id)`，随日记备份；不由缓存清理删除，仅随明确的日记领域删除处理。

映射：diary.tags 提供激活标签及顺序；`diary_tag_state_v1_<id>.automatic` 提供来源，suppressed 提供抑制。旧指纹与当前串正文匹配时映射当前 revision；无法匹配的原始指纹保存在 history.source_fingerprint，source_revision 留空，不伪造版本或历史时间（未知 occurred_at 留空）。旧过期抑制仅进入历史，不写当前 suppressed；当前已显示的激活项仍保留，不因来源版本未知而删除。迁移记录用稳定 operation_id 防止重跑重复导入。

processedFingerprint 只有匹配时才映射已处理，不能证明时保持待处理。历史只保护实际存在的决定，不从旧指纹臆造已经丢失的早期版本。

`diary_tag_vocabulary_v1` 的名称、别名、建议和待定名称全部迁入。`diary_tag_merge_undo_v1` 的 before/after 完整保存在版本化撤销载荷，撤销校验 edit_revision，不覆盖迁移后编辑。`diary_auto_tags` 仍是全局配置，保留在 setting。未识别 key 不按前缀删除。

损坏 JSON、别名循环，阻止候选升级，不静默回退默认值。旧保存逻辑会把重新添加标签前的抑制合并回来；可见激活项与抑制并存时，以 diary.tags 保留当前显示，抑制决定及原指纹完整保存在 history，不为同标签再插当前 suppressed 关联，不因此阻断启动。迁移保留当前界面语义，不套用只适用于新输入的清洗规则。

FTS 的 tags 只是投影。由数据库触发器在日记正文、标签关联及名称变化的同一事务维护 diary_fts，标签按 position 聚合。迁移后重建核对；FTS shadow 表不是待去重的原始数据。

## 4. 提醒：配置快照与草稿状态机

### 4.1 不可变配置快照

新增 `reminder_policy_snapshot(id TEXT PK,format_version INTEGER,payload_json TEXT,sha256 TEXT,created_at INTEGER)`；hash 建普通索引，不能仅凭 hash 判断相等。

- reminder_profile 继续是可编辑模板。
- reminder 最终以 policy_snapshot_id NOT NULL FK、ON DELETE RESTRICT 替代 profile_json。
- 旧 JSON 完整迁入，未知字段不得通过当前模型重编码丢失；只共享相同格式且完整载荷相等的快照，不强行语义规范化。
- 模板修改不改历史快照；引用中的快照不能删除。
- 提醒 ID、version、周期计数、提醒/关闭/完成时间、next_occurrence/deadline 原样保留。本期不另拆一张“可重建状态表”。

### 4.2 草稿与原子确认

reminder_ai_draft 保留原 ID、batch/source_index、source_text、draft_json、override_json、selected 与时间，以 state 替代 confirmed/discarded/confirmation_state。

selected 是独立勾选状态；draft_json 是 AI 原建议，override_json 是用户编辑，两者不能合并覆盖。

```text
PENDING ──确认──> SCHEDULE_PENDING ──调度成功──> CONFIRMED
   │                    │
   └──丢弃──> DISCARDED  └──调度失败──> SCHEDULING_FAILED
                                           │
                                           └──重试──> SCHEDULE_PENDING
```

- 保持 UNIQUE(batch_id,source_index)，非空 reminder_id 建唯一索引。
- PENDING/DISCARDED 无 reminder_id；其余必须已有 reminder_id；CONFIRMED 必须有 confirmed_at。
- 草稿指向提醒使用 RESTRICT。显式删除提醒由领域事务同时处理关联草稿，不能由任务清理代办。
- 仅未创建提醒的草稿可直接丢弃；失败后取消已创建提醒是明确领域操作，不能靠删草稿隐式删除。

确认事务：校验草稿版本 → 按既有稳定 ID `batchId:sourceIndex` 创建或读取同一提醒 → 保存配置快照与草稿关联 → 入队调度意图 → 提交。

复用 async_task 作为 outbox，新增 reminder_schedule 类型，唯一键包含提醒 ID 与定义 version。系统通知不能纳入 SQLite 事务：调度使用稳定平台标识、重复执行幂等；调用后进程中断可安全重试，不宣称外部 exactly-once。仅调度成功改 CONFIRMED；旧版本任务不能标记新定义成功，租约不复制到草稿。

旧记录映射：

| 一致的旧状态 | 新状态 |
|---|---|
| confirmed=1、confirmation_state=CONFIRMED，关联及时间有效、无丢弃冲突 | CONFIRMED |
| confirmed=0、SCHEDULING_FAILED，关联提醒存在、无丢弃冲突 | SCHEDULING_FAILED |
| confirmed=0、discarded=1，未创建提醒且无其他状态冲突 | DISCARDED |
| confirmed=0、discarded=0、PENDING，无关联提醒 | PENDING |

其他组合先查稳定 ID 的提醒及历史，不能唯一证明则报告冲突，不按标记优先级硬选。confirmed 但 reminder_id 为空也不伪造提醒。原始批次输入、失败记录和用户覆盖不删除。

批次解析生命周期保留；是否全部处理由子草稿聚合，执行进度/重试展示读取 async_task。删 batch 的重复执行计数字段前先切换调用点。

## 5. 点子：来源快照引用，保留内容历史

保留 idea_topic 当前内容、状态、context_revision，以及来源、事件、会话和消息的全部 ID 与顺序。

| 新结构 | 设计 |
|---|---|
| idea_source_snapshot | id PK、format_version、payload_json、sha256、created_at；完整、不可变 |
| idea_topic_source.snapshot_id | FK RESTRICT，最终替代 snapshot_json；原 source ID、捕获时间、原归属不变 |
| idea_topic_event.payload_version | 原载荷 v1，新引用载荷 v2；不无版本改变 JSON 含义 |
| idea_event_snapshot | `(event_id,role,ordinal) PK`、snapshot_id FK；保存 captured_source/merged_duplicate 有序引用 |

迁移 captured.snapshot 与 merged.duplicateSourceSnapshots 到快照表，其余载荷及未知字段保留。v2 读取器联合引用还原内容；未知事件类型保持 v1 原载荷，不猜测解析。先建引用、逐事件重建核对，再移除已证明等价的嵌入副本。

hash 仅作查找预筛，相同格式且完整载荷相等才复用；不同来源 key/内容不能合并。before/after、合并前双方内容、进展及已确认草稿是历史，不因当前内容相似而去重。原文章/日记删除不删除快照；任一来源或事件引用时禁止回收。

original_topic_id 是历史归属，不机械加级联删除外键。当前 topic_id/session_id 补适当约束；合并先迁移关联，保留合并指向，禁止循环。删除整个合并组件仍沿用现有明确领域操作，不能变成缓存级联清理。

## 6. 助手：消息、执行、记忆与隐私

- 不重命名 chat_conversation，不合并点子/读书消息表。
- chat_conversation.session_id 指向 assistant_session，**不是 Web 登录的 session 表**。
- 旧消息缺 assistant_session 时，按既有非空 session ID 补身份行，created_at 取最早消息时间；不生成摘要/消息。
- assistant_turn.id 仍是原用户消息 ID；补 FK，并校验 role=user、session 一致。response_message_id 校验同会话 assistant 消息。
- assistant_action/reference 的 turn_id 补 FK。跨行角色/会话关系由事务写入及验证器保证，普通 FK 不覆盖这些条件。
- 顺序与分页使用 `(created_at,id)`；同文不同 ID 仍是两条消息。
- 任务清理默认 RESTRICT，不能级联删除 turn、消息、动作结果或幂等凭证。本期不为方便删任务而清空历史 task_id。

先核查现有隐私删除：用户原消息已明确删除但执行历史仍存的，不能补回正文“修复”。若强 FK 无法保持既有隐私语义，该条关系保留逻辑校验，暂不强加 FK。

memory_entry 增加 content_origin：automatic/manual/accepted/legacy_unknown。旧来源无法证明时默认 legacy_unknown，不整表判缓存；后台仅更新版本匹配的 automatic，不覆盖另三类。

assistant_memory_exclusion 独立于来源生命周期，清理、恢复和重建均保留。既有摘要、证据、search_results、steps、action_plan_json/context_json 本期保留；后者可能是审计快照。索引/FTS 可重建，但须检查来源版本和隐私排除。

恢复继续暂停未完成动作与历史补全任务。结构升级不能清除 requires_resume/restore_hold 或自动重放副作用。

## 7. 迁移与启动：唯一负责人、单一提交点

### 7.1 打开入口与候选安装

沿用 DatabaseBootstrap 初始化锁。升级完成前不开放业务读写、Worker 或提醒调度；不能只依赖 Application 中晚于 DI 的一处 runMigrations，须覆盖独立 Worker、备份恢复副本及其他打开路径。

先恢复进行中的文件事务，再处理待恢复备份、确保加密，最后对当前库进行 Schema 升级。加密、备份恢复、Schema 安装使用独立目录并串行。若出现无法确定先后的多个 ready/journal，停止并诊断，不能猜测优先级，更不能让旧 Schema 候选覆盖刚恢复的数据。

破坏性改造在**加密候选副本**进行：

1. 锁内确认无其他写入者，检查实际结构与版本；checkpoint 并用一致性快照生成候选，不随手复制活动 WAL 库。
2. 沿用原密钥，不生成便携明文 key JSON，不顺带轮换密钥或修改附件文件。
3. 在候选上逐版本事务迁移、验证。失败后可继续安全的错误检查，但不执行依赖失败结果的破坏性步骤。
4. 关闭候选连接、处理 sidecar、flush；复用已测的 DatabaseInstallTransaction 文件替换能力，Schema 安装使用独立目录及原包装密钥。
5. 首次重新打开和结构验证属于安装提交条件；现有事务需补提交前验证与故障测试，不能先 committed 再发现新库不可用。
6. 提交前不开放业务；替换中断按日志回滚。提交后开放写入，不能因清理失败自动回退，丢掉用户已经写入的新数据。
7. 终态清理失败不重放替换，下次锁内继续清理。敏感候选和回滚库仅放 App 私有目录，不复制到开发机证据目录；日志不含正文或密钥。

预检候选、表重建、WAL 和回滚所需空间。空间不足、密钥不可读、原库损坏、未解释的孤立记录或冲突均停止升级，原库保留，不建空库顶替。保留独立诊断/导出入口，但不能让新业务查询误读旧结构。

### 7.2 版本推进

- 新建库验证目标结构后才写 schema_version。
- 缺版本先识别表/列；只有可唯一识别的旧结构才确定起点，不把无版本直接当新库。
- 每版本的数据修改与版本号同一事务提交。步骤有 try/catch、结构化日志；不在总函数末尾无条件写目标版本。
- 重试幂等，已有表/列核查结构；不以 IF NOT EXISTS 掩盖错误结构。
- 保持连接外键启用。表重建按 SQLite 约束处理，不整段关外键后漏掉核查。
- 日志只记错误码、阶段、记录 ID 与数量，不记录正文、转写或凭据。

### 7.3 可验收切片与版本预留

以当前 38 为基线预留，实施时基线改变则顺延。每片都包括新建/升级、调用点、备份恢复与验证，不只交 DDL。

| 片 | 版本 | 范围 |
|---|---|---|
| A | 39 | 结构识别、候选安装与提交门禁、数据核对基座 |
| B | 40 | 日记图片/附件统一，排序、删除、备份入口切换 |
| C | 41 | 日记标签/来源状态，搜索与撤销 |
| D | 42 | 提醒配置快照、草稿状态机、原子确认与调度意图 |
| E | 43 | 点子快照引用、版本化事件与合并历史 |
| F | 44 | 助手关联、分页、来源、隐私及任务清理边界 |
| G | 45 | 经验证的旧字段/旧业务 key 收尾，全迁移路径回归 |

中间字段可暂时可空以回填；切换后只有新结构可写，不长期双写。旧字段在收尾前仅为退役兼容表示，不再用于业务决策；不承诺旧 APK 能读取新 schema，较新版本库必须阻止旧版误写。G 通过后删除已退役字段，不无限保存重复来源。Schema 版本不是 App 发布版本，本设计不发版。

## 8. 备份恢复与模型读取边界

- 默认仍完整备份，不顺带改成只备份核心，不剔除外部内容。
- 旧备份在恢复副本完成迁移和目标业务校验后才 stage；失败不触碰活动库。
- BackupDatabaseData.verifiedSummary 当前按最新内存 Schema 校验；合法旧备份须先识别源版本并迁移，不能直接用最新列集拒绝。
- 同步更新 BackupAttachmentSource、路径重定位、缺失报告、摘要和 round-trip fixtures；新结构不依赖 diary.images，但旧结构读取/迁移仍支持。
- 保留此前 foreign_key_check、写出读回 SHA-256、临时清理和成功时间/retention 门禁。旧库孤立记录只诊断，不自动删除以通过校验。
- 新表/字段同步更新 AssistantSchemaPolicy 清单，默认 LOCAL_ONLY/DENIED。尤其新配置快照不能绕过旧 profile_json 的禁止策略；新增结构不等于扩大模型权限。
- 未知 payload、setting key、来源和加密字段默认保留，不默认可清理或可对模型读取。

## 9. 验收合同

迁移前后按显式旧/新模型映射核对，不能只比较表行数：

| 数据 | 必须核对 |
|---|---|
| 日记 | 每 ID 的正文、心情、时间、父子关系、图片/附件顺序、转写、标签来源/排除/撤销 |
| 提醒 | 原始输入/编辑、ID、时区/规则、完整配置、当前周期及计数、历史、确认身份 |
| 助手 | 会话/消息 ID、原文/角色/顺序、动作意图/结果、幂等、隐私、恢复暂停 |
| 点子 | 当前字段/状态、完整来源快照、捕获/原归属、事件重建、合并、会话/消息 |
| 文件 | 已有文件字节与多重引用；明确原有缺失项，迁移不新增缺失 |
| 其他模块 | 未改造数据逐表保持，不借机删改历史字段 |

核对在本地完成；持久报告仅留阶段/ID/计数/核对结果。摘要采用确定性带字段边界编码，区分 NULL/空字符串；测试对关键原文及配置逐字段比较，不把 hash 当绝对证明。

必需代码级场景：

1. v38 和相关历史结构升级，新建/升级结构等价，包含索引、触发器及 FTS。
2. 缺/错版本、缺表列、损坏 JSON、冲突、孤立：拒绝且原库不变，不假报成功。
3. 长正文、未知字段、同毫秒多消息、同文不同 ID、多来源合并、来源已删除。
4. 缺附件、多日记共享文件、图片/附件双表示、网络图片、图片顺序、录音转写。
5. 标签抑制/置顶/别名/撤销；新编辑后旧撤销和旧 AI 结果不能覆盖。覆盖“旧版本抑制 → 修改正文 → 同标签重新激活 → 备份恢复”，当前标签可见且旧抑制/原指纹仍在历史；未知时间不伪造，迁移重跑不重复导入。
6. 确认双击/并发、调度失败重试、创建后中断、外部成功后回写前中断：仅一个提醒且可恢复。
7. 候选、逐版本事务、安装每次移动、提交前验证的中断；重复启动，成功后新写入不被旧日志回滚。
8. 各切片旧备份 → 新结构 → 再备份 → 删除源设备目录 → 仅备份恢复，使用真实文件及主机 SQLCipher。
9. 恢复动作仍暂停；缓存/任务清理不删原文、快照、人工记忆或隐私排除。
10. Schema 权限清单完整、默认不暴露；相关 repository/迁移/备份回归及 App 编译通过。

每个功能片先最小 RED/GREEN，再组合相关回归与 compileDebugKotlin。文稿不运行构建；本设计不改 AI prompt/API，如实施触及则额外验证对应真实接口。

设备覆盖升级、真实旧库、Keystore、SAF、强制终止与断电持久化另需设备验证；日常不擅自启动模拟器或安装 App。设计检查与 JVM 测试不能宣称这些已通过。

## 10. 留给实施者的选择与前置核查

- SQLDelight 查询组织、Kotlin 类名、批量上限按项目模式决定，不新增通用迁移框架。
- snapshot 去重批次和空间预算依据测量调整，但不足就停止、不删原库。
- 实施前复现核查：助手隐私删除后的关联、独立 Worker 打开入口、旧备份迁移调用顺序。安全默认是保留/限制/拒绝，不猜测启用强约束。
- 无法无损映射的产品歧义一次汇总确认，不自动选择一份数据覆盖另一份。

尚未检查真实旧库及数据分布，不能预估容量缩减或承诺零故障。完成详细设计不等于执行了迁移。
