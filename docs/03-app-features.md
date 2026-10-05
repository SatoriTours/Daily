# Daily Satori 应用功能说明

> **重要**：本文档是 AI 助手理解应用功能的核心参考。在修改代码前，请先阅读相关模块的说明，确保不会破坏现有功能。

## 应用定位

**Daily Satori** 是一款**本地优先的智能知识管理工具**，帮助用户：
- 快速收集和整理网页内容
- 记录日常思考和感悟
- 管理阅读书籍和书摘
- 通过 AI 助手与知识库交互

## 功能模块总览

| 模块 | 页面文件 | 主要功能 |
|------|----------|----------|
| 首页导航 | `ui/feature/home/HomeScreen.kt` | 底部导航：文章、日记、读书、AI、设置 |
| 文章管理 | `ui/feature/article/` | 文章列表、搜索、筛选、详情 |
| 日记模块 | `ui/feature/diary/` | 日记列表、编辑器 |
| 读书模块 | `ui/feature/book/` | 书籍管理、搜索、观点记录 |
| AI 聊天 | `ui/feature/aichat/` | 智能对话、记忆搜索 |
| AI 配置 | `ui/feature/aiconfig/` | AI 模型配置管理 |
| 设置 | `ui/feature/settings/` | 应用设置、备份还原 |

## 本地诊断与日志

设置中的「诊断与日志」支持导出最近 30 分钟日志、导出上次崩溃现场和清理本地诊断。

- 保存为单个 UTF-8 文本文件，通过系统保存界面选择 Downloads 或其他目录，不申请广泛存储权限、不自动上传。
- 记录脱敏的网络元数据、关键业务阶段、异常类型/栈帧和应用生命周期；不默认记录正文、凭据或任意自由文本。
- 普通日志最多约 30 MiB，按分钟/大小切片、保留不超过约 24 小时；崩溃现场最多 3 份、7 天，每份 10 MiB。
- Ktor、LangChain 和 Coil 有传输事件；WebView、系统下载及调试服务器只承诺可观测范围，具体覆盖/缺口写在报告内。
- 强杀、系统限制、容量清理可能造成缺失；ANR/native 只记录可获得的退出原因，不自动收集原始系统 trace。
- 清理不影响已导出的文件和旧任务中心日志。临时完整正文模式、自动 AI 分析和云端监控不在第一版范围。

## 文章模块

### 核心功能

- **一键收藏**：从其他应用分享链接到 Daily Satori
- **智能解析**：自动提取标题、正文、图片
- **广告过滤**：内置 ADBlock 规则
- **离线缓存**：全文和图片本地存储
- **Markdown 渲染**：优化排版体验
- **AI 解读**：生成文章摘要和要点

### 数据模型 (SQLDelight)

```sql
-- shared/.../sqldelight/DailySatori.sq
CREATE TABLE article (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    title TEXT,
    ai_title TEXT,
    content TEXT,
    ai_content TEXT,
    html_content TEXT,
    ai_markdown_content TEXT,
    url TEXT UNIQUE,
    is_favorite INTEGER DEFAULT 0,
    comment TEXT,
    status TEXT DEFAULT 'pending',
    cover_image TEXT,
    cover_image_url TEXT,
    pub_date INTEGER,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
);
```

## 日记模块

### 核心功能

- **时间线展示**：按日期分页显示
- **搜索功能**：全文搜索日记内容
- **标签筛选**：按标签过滤日记
- **我的思想**：日记页顶部入口，基于已保存的日记正文持续整理价值观、做事准则、思维方式和变化；每条附可点击的原文依据，并区分明确表达与 AI 归纳。支持保存自己的补充与修正、手动重试更新。
- **思想档案更新**：应用运行时观察日记变化，由持久后台任务分段读取全部正文，缓存未改变的提取结果；退到后台或锁屏不主动暂停，通知显示整理进度，系统中断后从已保存断点续作。修改、删除日记时移除失去依据的条目。使用默认 AI 配置，失败保留仍有依据的旧内容，应用重启后补做更新；档案与用户修正保存在本地键值存储。
- **用于 AI 对话**：思想档案页可开关（默认开启）。流式和普通回答均可参考最新且有日记依据的归纳，并显示可打开的原日记引用；最新用户修正单独提供，旧档案不会覆盖当前表达。日记变化导致档案过期时跳过旧归纳；统计查询保持原有 SQL 检索流程。

### 数据模型

```sql
CREATE TABLE diary (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    content TEXT NOT NULL,
    tags TEXT,
    mood TEXT,
    images TEXT,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
);
```

## 读书模块

### 核心功能

- **书籍搜索**：在线搜索书籍信息
- **书籍管理**：添加、查看书籍
- **观点记录**：记录阅读感悟，关联书籍

### 数据模型

```sql
CREATE TABLE book (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    title TEXT NOT NULL,
    author TEXT NOT NULL,
    category TEXT NOT NULL,
    cover_image TEXT NOT NULL,
    introduction TEXT NOT NULL,
    has_update INTEGER DEFAULT 0,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
);

CREATE TABLE book_viewpoint (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    book_id INTEGER NOT NULL REFERENCES book(id) ON DELETE CASCADE,
    title TEXT NOT NULL,
    content TEXT NOT NULL,
    example TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
);
```

## 生活档案 (`ui/feature/lifearchive/`)

- 「我的」页面提供独立入口；保存域名、订阅、支付等资料，支持自定义类型、长按重命名/删除并迁移资料。
- 搜索默认收起，点击图标展开，仅在本地搜索标题、类型、正文和动态字段；关闭时清空搜索。
- 文字输入由默认云端 AI 整理成动态字段；编辑可「再说一段话」让 AI 优化，保留未涉及信息，标记字段变化，确认后保存。字段名、值、分类及正文也可手动修改。
- 「从待办导入」分批分析续费、订阅类待办，展示草稿和来源；确认保存时检查来源/档案版本。同来源重复导入更新已有档案，原待办和提醒保持原样，失败批次可重试，已生成草稿保留。
- 原文和当前编辑资料会发送给所选 AI，无脱敏；请求不附带普通聊天、记忆或其他档案值，错误不回显服务端正文。
- 完整资料和类型清单通过 Android Keystore AES-GCM 加密，保存到 `noBackupFilesDir/life_archive/` 的独立原子快照；不进入数据库、现有备份、自动记忆或 MCP。首次保存说明卸载/清除数据/密钥丢失后的恢复限制。
- 未保存输入、草稿及来源描述仅驻留页面内存；离开取消 AI 请求，进程结束后需重新输入。
- AI 结构化返回兼容单个 JSON 代码块包装，同时严格校验类型、字段、数量及来源 ID；纯文本和截断 JSON 仍视为失败。待办解析复用同一包装处理。

## AI 功能模块

### 通知自动记账

设置 → 自动记账提供独立监听开关、系统通知使用权入口和来源应用选择，默认关闭。仅处理开启后选定来源的新通知，不读取通知历史或其他应用内部账单；微信和支付宝需匹配支付类通知标题。金额、币种、类型、商户、尾号和交易号在本机识别，完整账目和来源正文加密保存并参与现有密码加密备份，不上传 AI。

明确交易自动入账；缺少金额、多个金额、币种不明、未确认完成或疑似重复进入待确认列表，支持确认、编辑、忽略和删除。类型与完成状态只根据交易正文判断，商户名称、备注与还款日不作为判断依据；带符号金额不自动取绝对值，单独的 `$` 不猜测为美元。相同通知事件及同来源的可靠交易号用于防重，金额相同且时间接近仅提示疑似重复；人工确认结果及删除／忽略记录不会因同一交易的新通知被重建，明确冲突的账号尾号不会自动合并。删除／忽略保留加密身份记录，清除原文和商户。各币种按通知接收月份分别统计收入、支出和退款，转账、还款及待确认项不计入收支。系统限制、隐藏内容、强行停止应用或来源未发通知仍可能漏记。

iOS 尚未实现，面向 iOS 27 的后续接入方案见 [iOS 通知自动记账实施方案](./ios-notification-bookkeeping.md)。

### 短信生成待办

- 设置 → 短信待办提供独立的「监听新短信」和「允许 AI 分析脱敏短信」开关，默认关闭。开启监听后自动接收并处理新短信，无手动导入入口。更多菜单保留「同步待处理短信」，用于重试已接收的任务；不读取或回补手机历史短信。
- 本地筛选充值、缴费、续费、取件、预约等候选，跳过普通聊天、已完成交易和验证码。验证码不保存、不上传；原短信和发送者使用本机密钥加密保存，随现有加密备份机制恢复。
- AI 请求只接收脱敏正文：所有数字、账号、卡号、余额、金额、链接与邮箱先遮蔽。发送者不提供给 AI；含姓名、地址、身份凭证或不支持文字体系的短信仅在本地生成通用事项，不把私密正文写进待办。提供脱敏预览，远端错误不回显服务端正文。
- 识别到事项后自动添加，无逐条确认弹窗；应用内合并弱提示「已创建 N 个待办」，后台创建的数量在下次打开应用时提示。重复事项跳过，验证码和普通聊天不创建。短信卡片保留查看待办主操作，重新调度、屏蔽等收进菜单。
- 小时／分钟／天数期限以接收时刻在本地计算；支持明确的数字日期和时刻，不猜测「明天」的具体时间。无可靠期限或期限已过时仍添加待办，但暂不定时提醒，持续保留在待办列表；编辑补充未来截止时间后自动启用提醒。
- 创建时仅显示应用内数量提示，未完成时在截止前两小时提醒；临近截止才创建时提醒一次。截止时刻不随时区变化，安静时段仅静默通知，完成／截止后停止。支持编辑截止时间、查看原文、忽略、屏蔽发送者、失败重试及重新调度。
- 后台任务仅保存来源 ID，接收与排队、待办与来源关联均以事务保存。广播重发与任务重试不重复创建；关闭 AI 授权或屏蔽来源后，未完成任务不再请求 AI，已发出的请求无法撤回，返回结果不会自动创建待办。
- 无网络时先保留任务，AI 重试耗尽后使用本地规则添加通用事项。强行停止应用、撤销短信／通知权限或系统省电限制仍可能导致漏收或延迟，第一版不能自动补回漏收记录。

### 1. AI 聊天 (`ui/feature/aichat/`)

**功能描述：**
- **智能对话**：与知识库进行自然语言交互
- **MCP Agent**：AI 自动调用工具搜索日记/文章/书籍
- **记忆系统**：三层记忆（核心偏好、内容摘要、对话历史）
- **Markdown 渲染**：AI 回复支持格式渲染
- **记忆搜索**：独立记忆搜索面板，支持全文搜索和重建

**交互流程：**
1. 用户输入问题
2. AI 自动检索相关记忆作为上下文
3. AI 调用工具搜索知识库
4. AI 生成结构化答案（Markdown 格式）
5. 显示搜索引用来源

**文件结构：**
```
ui/feature/aichat/
├── AiChatScreen.kt     # 聊天界面 + 记忆搜索面板
└── AiChatViewModel.kt  # 状态管理 + 对话持久化

shared/.../service/mcp/
└── McpAgentService.kt  # MCP Agent 核心（工具定义、执行、结果汇总）

shared/.../service/memory/
└── MemoryExtractService.kt  # 记忆提取服务（AI 摘要 + 全量重建）
```

### 2. AI 配置 (`ui/feature/aiconfig/`)

**功能描述：**
- 模型管理：添加、编辑、删除 AI 模型配置
- 多 Provider 支持：OpenAI、DeepSeek、Anthropic 等兼容 API
- 默认配置：标记一个配置为默认使用

### 3. 记忆系统

**架构：**
```
memory_entry 表 (SQLDelight)
├── type='core'     # 核心偏好/事实（手动添加或自动提取）
├── type='content'  # 内容摘要（从日记/文章/读书笔记提取）
└── type='chat'     # 对话记忆（从聊天中提取关键信息）
```

- **自动提取**：添加日记/收藏文章时自动调用 AI 提取摘要
- **手动搜索**：记忆搜索面板支持全文检索
- **全量重建**：一键从所有现有内容重新生成记忆
- **对话注入**：每次 AI 对话自动检索相关记忆作为上下文

## 服务架构

```
shared/.../service/
├── ai/
│   ├── AiService.kt         # AI HTTP 客户端 (OpenAI / Anthropic)
│   └── AiConfigService.kt   # AI 配置管理
├── mcp/
│   └── McpAgentService.kt   # MCP Agent 核心
├── memory/
│   └── MemoryExtractService.kt  # 记忆提取
├── migration/
│   └── DatabaseMigration.kt # 数据库迁移
├── backup/
│   └── BackupService.kt     # 备份服务
├── book/
│   └── BookSearchService.kt # 书籍搜索
├── parser/
│   └── WebpageParserService.kt  # 网页解析
├── setting/
│   └── SettingService.kt    # 设置管理
└── weekly/
    └── WeeklySummaryService.kt  # 周报生成
```

## 数据层结构

```
shared/.../sqldelight/
└── DailySatori.sq           # 所有表定义 + SQLDelight 查询

shared/.../data/repository/
├── ArticleRepository.kt
├── DiaryRepository.kt
├── BookRepository.kt
├── BookViewpointRepository.kt
├── MemoryRepository.kt
├── ChatConversationRepository.kt
├── AIConfigRepository.kt
├── TagRepository.kt
├── SettingRepository.kt
├── WeeklySummaryRepository.kt
└── SessionRepository.kt
```

## 修改代码前检查

- [ ] 阅读了相关模块的功能说明
- [ ] 理解了数据模型和关联关系
- [ ] 确认修改不会破坏现有功能
- [ ] 数据库变更编写了迁移脚本
- [ ] 编译通过：`./gradlew :app:compileDebugKotlin`
