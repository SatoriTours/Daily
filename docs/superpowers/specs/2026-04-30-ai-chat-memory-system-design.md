# AI 助手记忆系统 & 聊天 UI 优化设计

> Status: approved | Date: 2026-04-30

## 概述

优化 AI 助手页面，实现三层记忆系统（参考 OpenClaw 架构），改造聊天 UI 为 ChatGPT 风格。

### 目标

1. ChatGPT 风格聊天界面：圆角一体化输入框、Markdown 渲染回复
2. 三层记忆系统：核心记忆(core) + 内容摘要(content) + 对话记忆(chat)
3. 自动记忆提取：添加内容（日记/文章/读书笔记）时自动 AI 摘要
4. 记忆搜索：对话中自动注入 + 手动搜索面板
5. 对话持久化：聊天记录保存到数据库，支持历史浏览

---

## 数据库设计

### 新增表 `memory_entry`

```sql
CREATE TABLE memory_entry (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    type TEXT NOT NULL,           -- 'core' | 'content' | 'chat'
    source_type TEXT,             -- 'manual' | 'article' | 'diary' | 'book' | 'book_viewpoint' | 'chat'
    source_id INTEGER,            -- 关联源数据 ID
    title TEXT NOT NULL,
    content TEXT NOT NULL,        -- AI 生成摘要/核心事实
    tags TEXT,                    -- 逗号分隔
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
);
```

### FTS5 全文搜索 `memory_entry_fts`

```sql
CREATE VIRTUAL TABLE memory_entry_fts USING fts5(
    title,
    content,
    content=memory_entry,
    content_rowid=id
);
```

### 新增表 `chat_conversation`

```sql
CREATE TABLE chat_conversation (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    session_id TEXT NOT NULL,
    role TEXT NOT NULL,
    content TEXT NOT NULL,
    search_results TEXT,
    steps TEXT,
    created_at INTEGER NOT NULL
);
```

### SQLDelight 查询

提供以下命名查询：
- `selectMemoryByType`, `selectMemoryBySource`, `searchMemory`, `insertMemory`, `updateMemory`, `deleteMemory`, `deleteMemoryBySource`, `deleteAllMemoryByType`, `memoryCountByType`, `selectAllMemory`
- FTS5: `searchMemoryFts` (INSERT INTO memory_entry_fts 触发器)
- Chat: `selectChatBySession`, `insertChat`, `deleteChatBySession`, `selectChatSessions`, `deleteChatSession`

---

## 数据层

### MemoryRepository

```
shared/.../data/repository/MemoryRepository.kt
```
- 构造函数: `MemoryRepository(db: DailySatoriDatabase)`
- 遵循现有 Repository 模式（Flow + Sync 双版本）
- FTS5 搜索通过 `getAllSync()` + 内存过滤实现（SQLDelight 对 FTS5 支持有限，或使用 raw SQL）

### ChatConversationRepository

```
shared/.../data/repository/ChatConversationRepository.kt
```
- CRUD 操作
- `getBySession(sessionId): List<ChatConversation>`
- `getSessions(): List<String>` - 获取所有 session id 列表

---

## 服务层

### MemoryExtractService

```
shared/.../service/memory/MemoryExtractService.kt
```

- `extractAndSave(sourceType, sourceId, title, content)` — 调 AI 提取摘要 → 写入 memory_entry
- `deleteBySource(sourceType, sourceId)` — 删除对应记忆
- `rebuildAll(articleRepo, diaryRepo, viewpointRepo, onProgress)` — 遍历所有内容重建

### McpAgentService 扩展

- 新增依赖: `MemoryRepository`
- 新增工具: `search_memory`, `get_memory_source`
- 对话前自动检索: 用户 query → search_memory → 注入 system prompt context
- 对话后自动存档: 提取关键信息 → 写入 memory_entry(type='chat')
- 系统 prompt 增强: 添加记忆使用指南

### DI 变更

```kotlin
// SharedModule.kt
single { MemoryRepository(get()) }
single { ChatConversationRepository(get()) }
single { MemoryExtractService(get(), get(), get()) }
// McpAgentService 新增 MemoryRepository 参数
single { McpAgentService(get(), get(), get(), get(), get(), get(), get()) }
```

---

## UI 设计

### 输入框改造（核心改动）

**ChatGPT 风格一体式输入框：**
- `Surface` 包裹：`shadowElevation=4.dp`, `shape=RoundedCornerShape(Radius.circular)`, 背景色 `surfaceContainerHighest`
- 内部 `Row(verticalAlignment=CenterVertically)`：
  - `BasicTextField`：无边框背景，`weight=1f`, `maxLines=6`，自适应高度，16sp 字号
  - `Placeholder` 文字 "问我任何问题..." 在输入为空时显示
  - `FilledIconButton`(小型 36dp 圆形) 发送按钮，内嵌在输入区右侧
- 处理中状态：输入框禁用，按钮显示为 loading 指示器
- 底部提示文案："基于你的知识库和记忆回答"（小字灰色）

### 消息气泡

- 保持左右对齐布局
- **Markdown 渲染**：AI 回复使用 `com.mikepenz:multiplatform-markdown-renderer`
- **搜索引用展示**：AI 回复下方显示匹配到的记忆来源标签
- **操作按钮**：每条 AI 消息下方显示复制按钮；失败消息显示重试按钮
- 用户消息：蓝色 `primary` 背景右对齐
- AI 消息：`surfaceContainer` 背景左对齐
- 错误消息：`errorContainer` 背景左对齐

### Top Bar

- 标题 "AI 助手"
- 操作按钮：
  - `🔍` 记忆搜索 IconButton → 打开 BottomSheet 记忆搜索面板
  - `🗑️` 新对话 IconButton → 清空当前对话，创建新 session

### 记忆搜索面板（ModalBottomSheet）

- 顶部搜索框 + 结果 LazyColumn
- 每条结果显示：类型标签（core/content/chat）+ 标题 + 摘要预览
- 点击查看完整内容
- 支持手动添加 core 类型记忆（标题 + 内容输入 + 保存按钮）
- 提供"重建全部记忆"按钮（确认后触发 rebuildAll）

### 对话持久化

- 进入页面时从 `chat_conversation` 加载最近 session 的消息
- 发送/接收后逐条写入数据库
- "新对话"清空 UI，保留旧对话在数据库中

---

## 自动提取集成点

在各个 ViewModel 的 save 操作成功后调用 `MemoryExtractService.extractAndSave()`：

| 内容类型 | 保存位置 | source_type | 提取内容 |
|----------|----------|-------------|----------|
| 日记 | DiaryEditViewModel.save() | diary | content |
| 文章收藏 | ArticleDetailViewModel.toggleFavorite() | article | ai_markdown_content 或 content |
| 读书观点 | BookViewpointEditViewModel.save() | book_viewpoint | content |
| 书籍 | BookEditViewModel.save() | book | introduction |

调用方式：`viewModelScope.launch { memoryExtractService.extractAndSave(...) }` 后台异步，不阻塞 UI。

---

## 实现任务

1. **数据库**: 新增 `memory_entry`, `memory_entry_fts`, `chat_conversation` 表及 SQLDelight 查询
2. **Repository**: 新建 `MemoryRepository`, `ChatConversationRepository`
3. **Service**: 新建 `MemoryExtractService`
4. **MCP Agent**: 扩展 `McpAgentService`（新增工具、自动检索、自动存档）
5. **UI 输入框**: 重写 `AiChatScreen` 底部输入区
6. **UI 消息**: Markdown 渲染 + 搜索引用 + 操作按钮
7. **UI 记忆面板**: BottomSheet 记忆搜索/管理面板
8. **对话持久化**: `AiChatViewModel` 集成 `ChatConversationRepository`
9. **自动提取集成**: 各 ViewModel save 后调用 extractAndSave
10. **DI**: 更新 SharedModule / ViewModelModule
11. **测试验证**: flutter analyze / build / 手动测试

---

## 约束

- 不引入新依赖（Markdown 渲染已有）
- 遵循现有 Repository/Service/DI 模式
- 保持代码风格一致（函数 ≤50 行，缩进 ≤3 层）
