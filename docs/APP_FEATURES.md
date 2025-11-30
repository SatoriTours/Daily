# 📱 Daily Satori 应用功能说明

> **重要**：本文档是 AI 助手理解应用功能的核心参考。在修改代码前，请先阅读相关模块的说明，确保不会破坏现有功能。

## 🎯 应用定位

**Daily Satori** 是一款**本地优先的智能知识管理工具**，帮助用户：
- 快速收集和整理网页内容
- 记录日常思考和感悟
- 管理阅读书籍和书摘
- 通过 AI 助手与知识库交互

## 📋 功能模块总览

| 模块 | 页面路径 | 主要功能 |
|------|----------|----------|
| 首页导航 | `pages/home/` | 底部导航：文章、日记、读书、设置 |
| 文章管理 | `pages/articles/` | 文章列表、搜索、筛选 |
| 文章详情 | `pages/article_detail/` | 阅读、分享、AI 解读 |
| 日记模块 | `pages/diary/` | 日记列表、编辑器 |
| 读书模块 | `pages/books/` | 书籍管理、观点记录 |
| AI 聊天 | `pages/ai_chat/` | 与知识库智能对话 |
| AI 配置 | `pages/ai_config/` | AI 模型配置管理 |
| 设置 | `pages/settings/` | 应用设置、主题切换 |
| 备份还原 | `pages/backup_restore/` | 数据备份与恢复 |
| 插件中心 | `pages/plugin_center/` | 自定义提示词插件 |

---

## 📰 文章模块

### 核心功能

#### 1. 文章收集
- **一键收藏**：从其他应用分享链接到 Daily Satori
- **智能解析**：自动提取标题、正文、图片
- **广告过滤**：内置 ADBlock 规则，纯净阅读
- **离线缓存**：全文和图片本地存储

#### 2. 文章列表 (`pages/articles/`)
- **分页加载**：滚动加载更多
- **全文搜索**：搜索标题、内容、摘要
- **多维筛选**：
  - 标签筛选
  - 收藏筛选
  - 日期范围筛选
- **排序方式**：按时间排序

#### 3. 文章详情 (`pages/article_detail/`)
- **Markdown 渲染**：优化排版体验
- **截图分享**：生成文章卡片图片
- **图片管理**：查看、保存文章图片
- **AI 解读**：生成文章摘要和要点
- **格式转换**：HTML 转 Markdown

### 数据模型

```dart
// 文章实体 (data/article/)
Article {
  int id;
  String title;         // 标题
  String? subTitle;     // 副标题（AI 生成）
  String url;           // 原始链接
  String? content;      // Markdown 内容
  String? htmlContent;  // HTML 原始内容
  String? aiMarkdown;   // AI 生成的 Markdown
  String? coverImage;   // 封面图片路径
  bool isFavorite;      // 是否收藏
  DateTime createdAt;   // 创建时间（UTC）
  DateTime updatedAt;   // 更新时间（UTC）

  // 关联
  ToMany<Tag> tags;     // 标签（多对多）
  ToMany<ArticleImage> images;  // 图片（一对多）
}
```

### 关键约束

- ✅ 文章删除时必须清理关联的图片文件
- ✅ 时间存储为 UTC，展示转本地时间
- ✅ 使用 `ArticleStateService` 管理列表状态
- ❌ 禁止直接操作 ObjectBox，必须通过仓储层

---

## 📓 日记模块

### 核心功能

#### 1. 日记列表 (`pages/diary/`)
- **时间线展示**：按日期分组显示
- **快速预览**：显示日记摘要
- **搜索功能**：全文搜索日记内容

#### 2. 日记编辑器 (`components/diary_editor/`)
- **富文本编辑**：Markdown 支持
- **自动保存**：防止内容丢失
- **模板预填**：支持预设内容模板
- **跨模块复用**：读书模块也使用此组件

### 数据模型

```dart
// 日记实体 (data/diary/)
Diary {
  int id;
  String? title;        // 标题（可选）
  String content;       // 内容
  DateTime createdAt;   // 创建时间（UTC）
  DateTime updatedAt;   // 更新时间（UTC）
}
```

### 关键约束

- ✅ `DiaryEditor` 组件供读书模块复用
- ✅ 支持预填模板内容
- ✅ 使用 `DiaryStateService` 管理状态

---

## 📚 读书模块

### 核心功能

#### 1. 书籍管理 (`pages/books/`)
- **书籍搜索**：集成 OpenLibrary API
- **中文支持**：自动转拼音搜索
- **封面获取**：自动下载高清封面
- **AI 翻译**：英文信息翻译为中文

#### 2. 书籍观点/感悟
- **观点记录**：记录阅读感悟
- **关联书籍**：观点与书籍绑定
- **时间线展示**：按时间显示感悟

### 数据模型

```dart
// 书籍实体 (data/book/)
Book {
  int id;
  String title;         // 书名
  String? author;       // 作者
  String? publisher;    // 出版社
  String? coverImage;   // 封面路径
  String? description;  // 简介
  String? isbn;         // ISBN
  DateTime? publishDate;// 出版日期
  DateTime createdAt;   // 创建时间

  // 关联
  ToMany<BookViewpoint> viewpoints;  // 观点列表
}

// 书籍观点 (data/book_viewpoint/)
BookViewpoint {
  int id;
  String content;       // 观点内容
  DateTime createdAt;   // 创建时间

  // 关联
  ToOne<Book> book;     // 所属书籍
}
```

### ⚠️ 强制约束（重要！）

- ✅ **必须始终显示"添加感悟"悬浮按钮（FAB）**
- ✅ 位置：右下角 `FloatingActionButtonLocation.endFloat`
- ✅ 图标：`Icons.edit_note`
- ✅ tooltip: `'tooltip.add_viewpoint'.t`
- ✅ 点击行为：预填模板 + 打开 `DiaryEditor`
- ❌ **禁止在无观点时隐藏 FAB**
- ❌ **禁止移除或修改 FAB 的行为**

```dart
// FAB 必须的实现模式
FloatingActionButton(
  onPressed: _openDiaryEditor,
  tooltip: 'tooltip.add_viewpoint'.t,
  child: const Icon(Icons.edit_note),
)
```

---

## 🤖 AI 功能模块

### 1. AI 聊天 (`pages/ai_chat/`)

#### 功能描述
- **智能对话**：与知识库进行自然语言交互
- **意图识别**：自动识别搜索意图（文章/日记/书籍）
- **语义搜索**：基于语义理解检索相关内容
- **结果总结**：AI 自动总结搜索结果

#### 交互流程
1. 用户输入问题
2. AI 分析意图并扩展关键词
3. 搜索本地知识库
4. AI 生成结构化答案
5. 显示可折叠的搜索结果卡片

### 2. AI 配置 (`pages/ai_config/`)

#### 功能描述
- **模型管理**：添加、编辑、删除 AI 模型配置
- **多模型支持**：支持配置多个 AI 服务
- **配置测试**：验证 API 连接

#### 配置项
```yaml
# AI 模型配置
- name: 模型名称
  apiKey: API密钥
  baseUrl: API地址
  model: 模型标识
```

### 3. 插件系统 (`pages/plugin_center/`)

#### 功能描述
- **自定义提示词**：用户可创建提示词模板
- **AI 能力扩展**：扩展 AI 的处理能力
- **模板管理**：编辑、删除、排序

### AI 服务架构

```
services/ai_service/
├── ai_agent_service.dart     # AI Agent 核心服务
├── ai_service.dart           # AI 基础服务
└── ai_prompt_service.dart    # 提示词管理

services/
├── ai_config_service.dart    # AI 配置管理
└── plugin_service.dart       # 插件服务
```

### 关键约束

- ✅ AI 功能是可选的，未配置时应用正常使用
- ✅ 提示词统一在 `assets/configs/ai_prompts.yaml` 管理
- ✅ 敏感信息（API Key）不输出到日志
- ✅ AI 调用失败时提供友好的错误提示

---

## ⚙️ 设置与备份

### 1. 设置页面 (`pages/settings/`)

#### 功能列表
- **主题切换**：亮色/暗色/跟随系统
- **语言设置**：中文/英文
- **Web 服务**：局域网访问配置
- **存储管理**：清理缓存、查看占用
- **关于应用**：版本信息、检查更新

### 2. 备份还原 (`pages/backup_restore/`)

#### 功能列表
- **本地备份**：导出数据到文件
- **数据还原**：从备份文件恢复
- **图片路径恢复**：自动修复图片路径

#### 关键约束

- ✅ 备份包含：文章、日记、书籍、设置
- ✅ 备份包含图片文件
- ✅ 恢复后自动修复图片路径
- ✅ 使用 `FileService.i.resolveLocalMediaPath`

---

## 🔧 核心服务说明

### 状态服务

| 服务 | 职责 |
|------|------|
| `AppStateService` | 应用全局状态 |
| `ArticleStateService` | 文章列表状态、搜索、筛选 |
| `DiaryStateService` | 日记列表状态 |

### 数据服务

| 服务 | 职责 |
|------|------|
| `ObjectboxService` | 数据库管理 |
| `FileService` | 文件存储管理 |
| `BackupService` | 备份还原 |

### 功能服务

| 服务 | 职责 |
|------|------|
| `WebContentService` | 网页内容解析 |
| `AdblockService` | 广告过滤 |
| `BookService` | 书籍搜索 |
| `AIConfigService` | AI 配置管理 |
| `I18nService` | 国际化 |

### 系统服务

| 服务 | 职责 |
|------|------|
| `LoggerService` | 日志记录 |
| `HttpService` | 网络请求 |
| `ClipboardMonitorService` | 剪贴板监控 |
| `AppUpgradeService` | 应用更新 |

---

## 📂 数据层结构

```
lib/app/data/
├── article/          # 文章
│   ├── article_model.dart
│   └── article_repository.dart
├── diary/            # 日记
│   ├── diary_model.dart
│   └── diary_repository.dart
├── book/             # 书籍
│   ├── book_model.dart
│   └── book_repository.dart
├── book_viewpoint/   # 书籍观点
│   ├── book_viewpoint_model.dart
│   └── book_viewpoint_repository.dart
├── tag/              # 标签
│   ├── tag_model.dart
│   └── tag_repository.dart
├── image/            # 图片
│   ├── article_image_model.dart
│   └── article_image_repository.dart
├── setting/          # 设置
│   ├── setting_model.dart
│   └── setting_repository.dart
└── ai_config/        # AI配置
    ├── ai_config_model.dart
    └── ai_config_repository.dart
```

---

## 🚨 修改代码前的检查清单

### 通用检查

- [ ] 阅读了相关模块的功能说明
- [ ] 理解了数据模型和关联关系
- [ ] 了解了模块的约束条件
- [ ] 确认修改不会破坏现有功能

### 特定模块检查

#### 文章模块
- [ ] 删除文章时清理了关联图片
- [ ] 使用 `ArticleStateService` 更新状态
- [ ] 时间处理正确（UTC 存储，本地展示）

#### 读书模块
- [ ] FAB 始终显示（无论有无观点）
- [ ] FAB 点击打开 `DiaryEditor`
- [ ] 观点与书籍正确关联

#### AI 模块
- [ ] API Key 不输出到日志
- [ ] 失败时提供友好提示
- [ ] 未配置时不影响其他功能

#### 备份还原
- [ ] 图片路径正确恢复
- [ ] 关联数据完整性保持

---

## 📝 版本更新记录

### v3.6.60 (2025-11-24)
- ✨ 新增 AI 智能助手
- ✨ 智能意图识别
- ✨ 可折叠搜索结果卡片

### v3.6.53 (2025-11-10)
- ✨ 智能书籍搜索
- ✨ 多语言国际化框架
- 🔧 书籍搜索架构重构

---

**⚠️ 重要提示**：修改代码前，请确保阅读并理解本文档中相关模块的说明。如有疑问，请先确认再动手。
