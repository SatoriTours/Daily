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

## AI 功能模块

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
