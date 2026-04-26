# ObjectBox 数据导出与 KMP 导入设计

## 概述

实现从 Flutter 版 Daily Satori 的 ObjectBox 数据库导出数据为可移植 JSON 格式，并在 KMP/Android 版本中导入到 SQLDelight 数据库。同时清理 android 分支的 `.gitignore`。

## 导出格式

### ZIP 结构

```
daily_satori_export_<timestamp>.zip
├── manifest.json
├── articles.json
├── diaries.json
├── books.json
├── book_viewpoints.json
├── tags.json
├── article_tags.json
├── images.json
├── screenshots.json
├── settings.json
├── ai_configs.json
├── weekly_summaries.json
├── sessions.json
├── images/
│   ├── 20260426_123456-7890.jpg
│   └── ...
└── diary_images/
    ├── 20260426_123456-1234.jpg
    └── ...
```

### manifest.json

```json
{
  "version": 1,
  "app_version": "2.x.x",
  "export_time": "2026-04-26T12:00:00.000Z",
  "platform": "flutter",
  "counts": {
    "articles": 100,
    "diaries": 50,
    "books": 20,
    "book_viewpoints": 30,
    "tags": 15,
    "images": 200,
    "screenshots": 10,
    "settings": 10,
    "ai_configs": 3,
    "weekly_summaries": 5,
    "sessions": 2
  }
}
```

### 实体 JSON 字段映射

所有 datetime 字段用 ISO 8601 字符串（KMP 端用 `kotlinx.datetime` 解析）。布尔字段用 `true/false`。

#### articles.json

```json
[
  {
    "id": 1,
    "title": "文章标题",
    "ai_title": "AI 摘要标题",
    "content": "原文内容",
    "ai_content": "AI 摘要",
    "html_content": "<p>HTML 内容</p>",
    "ai_markdown_content": "# Markdown",
    "url": "https://example.com",
    "is_favorite": false,
    "comment": "用户评论",
    "status": "completed",
    "cover_image": "images/20260426_123456-7890.jpg",
    "cover_image_url": "https://example.com/cover.jpg",
    "pub_date": "2026-04-26T00:00:00.000Z",
    "created_at": "2026-01-01T00:00:00.000Z",
    "updated_at": "2026-01-01T00:00:00.000Z"
  }
]
```

#### diaries.json

```json
[
  {
    "id": 1,
    "content": "日记内容",
    "tags": "标签1,标签2",
    "mood": "happy",
    "images": "diary_images/img1.jpg,diary_images/img2.jpg",
    "created_at": "2026-01-01T00:00:00.000Z",
    "updated_at": "2026-01-01T00:00:00.000Z"
  }
]
```

#### books.json

```json
[
  {
    "id": 1,
    "title": "书名",
    "author": "作者",
    "category": "分类",
    "cover_image": "images/book_cover.jpg",
    "introduction": "简介",
    "has_update": false,
    "created_at": "2026-01-01T00:00:00.000Z",
    "updated_at": "2026-01-01T00:00:00.000Z"
  }
]
```

#### book_viewpoints.json

```json
[
  {
    "id": 1,
    "book_id": 1,
    "title": "观点标题",
    "content": "观点内容",
    "example": "示例",
    "created_at": "2026-01-01T00:00:00.000Z",
    "updated_at": "2026-01-01T00:00:00.000Z"
  }
]
```

#### tags.json

```json
[
  {
    "id": 1,
    "name": "技术",
    "icon": "💻",
    "created_at": "2026-01-01T00:00:00.000Z",
    "updated_at": "2026-01-01T00:00:00.000Z"
  }
]
```

#### article_tags.json

```json
[
  { "article_id": 1, "tag_id": 1 },
  { "article_id": 1, "tag_id": 2 }
]
```

#### images.json

```json
[
  {
    "id": 1,
    "url": "https://example.com/image.jpg",
    "path": "images/20260426_123456-7890.jpg",
    "article_id": 1,
    "created_at": "2026-01-01T00:00:00.000Z",
    "updated_at": "2026-01-01T00:00:00.000Z"
  }
]
```

#### screenshots.json

```json
[
  {
    "id": 1,
    "path": "images/screenshot_001.jpg",
    "article_id": 1,
    "created_at": "2026-01-01T00:00:00.000Z",
    "updated_at": "2026-01-01T00:00:00.000Z"
  }
]
```

#### settings.json

```json
[
  {
    "id": 1,
    "key": "theme_mode",
    "value": "system",
    "created_at": "2026-01-01T00:00:00.000Z",
    "updated_at": "2026-01-01T00:00:00.000Z"
  }
]
```

#### ai_configs.json

```json
[
  {
    "id": 1,
    "name": "通用配置",
    "api_address": "https://api.openai.com",
    "api_token": "sk-xxx",
    "model_name": "gpt-4",
    "function_type": 0,
    "inherit_from_general": false,
    "is_default": true,
    "created_at": "2026-01-01T00:00:00.000Z",
    "updated_at": "2026-01-01T00:00:00.000Z"
  }
]
```

#### weekly_summaries.json

```json
[
  {
    "id": 1,
    "week_start_date": "2026-04-20T00:00:00.000Z",
    "week_end_date": "2026-04-26T00:00:00.000Z",
    "content": "# 周报内容",
    "article_count": 10,
    "diary_count": 5,
    "viewpoint_count": 3,
    "article_ids": "1,2,3",
    "diary_ids": "1,2",
    "viewpoint_ids": "1",
    "app_ideas": "想法",
    "status": "completed",
    "created_at": "2026-04-26T00:00:00.000Z",
    "updated_at": "2026-04-26T00:00:00.000Z"
  }
]
```

#### sessions.json

```json
[
  {
    "id": 1,
    "session_id": "abc123",
    "is_authenticated": true,
    "username": "user",
    "last_accessed_at": "2026-04-26T00:00:00.000Z",
    "created_at": "2026-01-01T00:00:00.000Z",
    "updated_at": "2026-01-01T00:00:00.000Z"
  }
]
```

## Part 1: Flutter 导出（main 分支）

### 新增文件

- `lib/app/services/export_service.dart` — 导出服务

### ExportService 设计

```dart
class ExportService extends AppService {
  ExportService._();
  static final ExportService i = ExportService._();

  @override
  final ServicePriority priority = ServicePriority.normal;

  final ValueNotifier<double> exportProgress = ValueNotifier(0.0);

  /// 导出所有数据为 ZIP 文件
  /// 返回导出文件路径，失败返回 null
  Future<String?> exportToZip(String outputPath);
}
```

### 导出流程

1. 收集所有 ObjectBox 数据
2. 序列化为 JSON（每实体类型一个文件）
3. 构建 manifest.json
4. 收集被引用的图片文件
5. 打包为 ZIP

### 序列化策略

使用 `dart:convert` 的 `jsonEncode`，手动将每个实体转为 `Map<String, dynamic>`。不复用 freezed 的 `toJson`（因为 EntityModel 包装层不直接支持），直接从 ObjectBox 实体读取字段。

Datetime 字段统一转为 ISO 8601 字符串。null 字段保留为 JSON null。

### 图片收集

- 扫描所有 Article 的 `coverImage` 字段
- 扫描所有 Diary 的 `images` 字段（逗号分隔）
- 扫描所有 Image 记录的 `path` 字段
- 扫描所有 Screenshot 记录的 `path` 字段
- 对每个路径调用 `FileService.i.toAbsolutePath()` 获取绝对路径
- 跳过不存在的文件并记录警告日志
- 在 ZIP 中保持相对路径结构

### UI 集成

在设置页面（`SettingsView`）添加"导出数据（迁移用）"选项：
- 点击后弹出文件保存路径选择（使用 `file_picker` 包，项目已有此依赖）
- 显示进度对话框
- 完成后显示成功提示（含文件路径）

### 错误处理

- 导出前检查存储空间
- 逐实体导出，单实体失败不中断整体流程（记录错误继续）
- 图片缺失只记警告不报错
- 最终汇总导出结果

## Part 2: KMP 导入（android 分支）

### 新增文件

- `shared/src/commonMain/kotlin/com/dailysatori/service/import/ImportService.kt` — 导入服务
- `app/src/main/kotlin/com/dailysatori/ui/pages/data_import/DataImportScreen.kt` — 导入页面

### ImportService 设计

```kotlin
class ImportService(
    private val db: DailySatoriDatabase,
    private val fileManager: FileManager,
) {
    data class ImportResult(
        val success: Boolean,
        val imported: Map<String, Int>,
        val warnings: List<String>,
        val errors: List<String>,
    )

    suspend fun importFromZip(zipPath: String): ImportResult
}
```

### 导入流程

1. 解压 ZIP 到临时目录
2. 读取并验证 manifest.json（检查版本兼容性）
3. 按依赖顺序导入：
   - settings → ai_configs → tags → articles → article_tags → images → screenshots → diaries → books → book_viewpoints → weekly_summaries → sessions
4. ID 映射：旧 ID → 新 ID（SQLDelight 自增 ID 可能不同）
5. 复制图片文件到目标目录
6. 清理临时目录
7. 返回导入结果

### ID 映射

导入时需要维护旧 ID 到新 ID 的映射表，因为关联关系（article_tag、book_viewpoint、image.article_id 等）依赖 ID。

```kotlin
val idMaps = mutableMapOf<String, MutableMap<Long, Long>>()
// e.g. idMaps["article"] = { 1L -> 5L, 2L -> 6L }
```

导入 article_tags 时，通过 `idMaps["article"]!![oldArticleId]` 和 `idMaps["tag"]!![oldTagId]` 获取新 ID。

### UI

新增 `DataImportScreen`，从设置页导航进入：
- 文件选择按钮（SAF picker）
- 导入进度显示
- 导入结果摘要（成功数量、警告、错误）

## Part 3: .gitignore 修复（android 分支）

在 android 分支的 `.gitignore` 中添加：

```
# KMP/Android build artifacts
.gradle/
.kotlin/
app/build/
shared/build/
build/
local.properties
```

## 依赖关系

- Part 1（Flutter 导出）独立实现，无外部依赖
- Part 2（KMP 导入）依赖 Part 1 的导出格式，但可独立开发
- Part 3（.gitignore）独立，可先行处理

建议实施顺序：Part 3 → Part 1 → Part 2
