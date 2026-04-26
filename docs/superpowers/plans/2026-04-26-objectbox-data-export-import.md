# ObjectBox 数据导出与 KMP 导入 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现 Flutter 版 ObjectBox 数据导出为 JSON ZIP，以及 KMP 版从 JSON ZIP 导入到 SQLDelight。

**Architecture:** Flutter 端新增 ExportService 将 11 种实体序列化为 JSON 并打包图片为 ZIP。KMP 端新增 ImportService 解析 JSON 并通过 ID 映射导入 SQLDelight。两段代码分属不同 git 分支。

**Tech Stack:** Dart (archive, file_picker), Kotlin (kotlinx.serialization, SQLDelight)

---

## 文件结构

### main 分支（Flutter 导出）

| 操作 | 文件路径 | 职责 |
|------|----------|------|
| 创建 | `lib/app/services/export_service.dart` | 导出服务核心：序列化 + 打包 |
| 修改 | `lib/app/pages/settings/views/settings_view.dart` | 添加"导出数据"设置项 |
| 修改 | `lib/app/pages/settings/providers/settings_controller_provider.dart` | 添加导出相关状态和方法 |

### android 分支（KMP 导入 + .gitignore）

| 操作 | 文件路径 | 职责 |
|------|----------|------|
| 修改 | `.gitignore` | 添加 KMP/Android build 目录 |
| 创建 | `shared/src/commonMain/kotlin/com/dailysatori/service/import/ImportService.kt` | 导入服务核心 |
| 创建 | `app/src/main/kotlin/com/dailysatori/ui/pages/data_import/DataImportScreen.kt` | 导入页面 UI |

---

## Task 1: 修复 android 分支 .gitignore

**分支:** `android`

**Files:**
- Modify: `.gitignore`

- [ ] **Step 1: 在 .gitignore 末尾添加 KMP/Android 构建产物忽略规则**

在文件末尾追加：

```
# KMP/Android build artifacts
.gradle/
.kotlin/
app/build/
shared/build/
build/
local.properties
```

- [ ] **Step 2: 验证未跟踪的构建目录已被忽略**

Run: `git status`
Expected: `.gradle/`, `.kotlin/`, `app/build/`, `shared/build/`, `build/`, `local.properties` 不再出现在 untracked files 中

---

## Task 2: 创建 ExportService 核心序列化

**分支:** `main`

**Files:**
- Create: `lib/app/services/export_service.dart`

- [ ] **Step 1: 切换到 main 分支**

Run: `git stash && git checkout main`
(如果有未提交的改动先 stash)

- [ ] **Step 2: 创建 export_service.dart 文件**

创建 `lib/app/services/export_service.dart`，内容如下：

```dart
import 'dart:convert';
import 'dart:io';

import 'package:archive/archive.dart' as arch;
import 'package:flutter/foundation.dart';
import 'package:path/path.dart' as path;
import 'package:package_info_plus/package_info_plus.dart';

import 'package:daily_satori/app/data/data.dart';
import 'package:daily_satori/app/objectbox/objectbox.dart';
import 'package:daily_satori/app/services/file_service.dart';
import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/services/objectbox_service.dart';

class ExportService {
  ExportService._();
  static final ExportService i = ExportService._();

  final ValueNotifier<double> exportProgress = ValueNotifier(0.0);
  final List<String> _warnings = [];

  Future<String?> exportToZip(String outputPath) async {
    _warnings.clear();
    exportProgress.value = 0.0;

    try {
      final archive = arch.Archive();
      final counts = <String, int>{};

      final steps = 13;
      var step = 0;

      void progress() {
        step++;
        exportProgress.value = step / steps;
      }

      _addJsonToArchive(archive, 'manifest.json', await _buildManifest(counts));
      progress();

      _addJsonToArchive(
        archive,
        'articles.json',
        _serializeArticles(counts),
      );
      progress();

      _addJsonToArchive(
        archive,
        'diaries.json',
        _serializeEntities<Diary>(DiaryRepository.i.all(), counts, 'diaries', _diaryToMap),
      );
      progress();

      _addJsonToArchive(
        archive,
        'books.json',
        _serializeEntities<Book>(BookRepository.i.all(), counts, 'books', _bookToMap),
      );
      progress();

      _addJsonToArchive(
        archive,
        'book_viewpoints.json',
        _serializeEntities<BookViewpoint>(
          BookViewpointRepository.i.all(),
          counts,
          'book_viewpoints',
          _bookViewpointToMap,
        ),
      );
      progress();

      _addJsonToArchive(
        archive,
        'tags.json',
        _serializeEntities<Tag>(TagRepository.i.all(), counts, 'tags', _tagToMap),
      );
      progress();

      _addJsonToArchive(
        archive,
        'article_tags.json',
        _serializeArticleTags(counts),
      );
      progress();

      _addJsonToArchive(
        archive,
        'images.json',
        _serializeImages(counts),
      );
      progress();

      _addJsonToArchive(
        archive,
        'screenshots.json',
        _serializeScreenshots(counts),
      );
      progress();

      _addJsonToArchive(
        archive,
        'settings.json',
        _serializeEntities<Setting>(
          SettingRepository.i.all(),
          counts,
          'settings',
          _settingToMap,
        ),
      );
      progress();

      _addJsonToArchive(
        archive,
        'ai_configs.json',
        _serializeEntities<AIConfig>(
          AIConfigRepository.i.all(),
          counts,
          'ai_configs',
          _aiConfigToMap,
        ),
      );
      progress();

      _addJsonToArchive(
        archive,
        'weekly_summaries.json',
        _serializeEntities<WeeklySummary>(
          WeeklySummaryRepository.i.all(),
          counts,
          'weekly_summaries',
          _weeklySummaryToMap,
        ),
      );
      progress();

      _addJsonToArchive(
        archive,
        'sessions.json',
        _serializeEntities<SessionEntity>(
          SessionRepository.i.all(),
          counts,
          'sessions',
          _sessionToMap,
        ),
      );
      progress();

      await _addImagesToArchive(archive);
      _updateManifestCounts(archive, counts);

      final zipData = arch.ZipEncoder().encode(archive);
      if (zipData == null) return null;

      final file = File(outputPath);
      await file.writeAsBytes(zipData);

      logger.i('[ExportService] 导出完成: $outputPath, 警告: ${_warnings.length}');
      return outputPath;
    } catch (e, stack) {
      logger.e('[ExportService] 导出失败', error: e, stackTrace: stack);
      return null;
    }
  }

  void _addJsonToArchive(arch.Archive archive, String name, dynamic data) {
    final jsonStr = const JsonEncoder.withIndent('  ').convert(data);
    archive.addFile(arch.File.fromBytes(
      name,
      utf8.encode(jsonStr),
    ));
  }

  Future<Map<String, dynamic>> _buildManifest(Map<String, int> counts) async {
    String appVersion = 'unknown';
    try {
      final info = await PackageInfo.fromPlatform();
      appVersion = '${info.version}+${info.buildNumber}';
    } catch (_) {}

    return {
      'version': 1,
      'app_version': appVersion,
      'export_time': DateTime.now().toUtc().toIso8601String(),
      'platform': 'flutter',
      'counts': counts,
    };
  }

  void _updateManifestCounts(arch.Archive archive, Map<String, int> counts) {
    archive.files.removeWhere((f) => f.name == 'manifest.json');
    final manifest = {
      'version': 1,
      'app_version': '',
      'export_time': DateTime.now().toUtc().toIso8601String(),
      'platform': 'flutter',
      'counts': counts,
    };
    _addJsonToArchive(archive, 'manifest.json', manifest);
  }

  List<Map<String, dynamic>> _serializeArticles(Map<String, int> counts) {
    final articles = ArticleRepository.i.all();
    counts['articles'] = articles.length;
    return articles.map((m) {
      final e = m.entity;
      return <String, dynamic>{
        'id': e.id,
        'title': e.title,
        'ai_title': e.aiTitle,
        'content': e.content,
        'ai_content': e.aiContent,
        'html_content': e.htmlContent,
        'ai_markdown_content': e.aiMarkdownContent,
        'url': e.url,
        'is_favorite': e.isFavorite,
        'comment': e.comment,
        'status': e.status,
        'cover_image': e.coverImage,
        'cover_image_url': e.coverImageUrl,
        'pub_date': _dtToIso(e.pubDate),
        'created_at': _dtToIso(e.createdAt),
        'updated_at': _dtToIso(e.updatedAt),
      };
    }).toList();
  }

  List<Map<String, dynamic>> _serializeArticleTags(Map<String, int> counts) {
    final articles = ArticleRepository.i.all();
    final result = <Map<String, dynamic>>[];
    var tagCount = 0;
    for (final m in articles) {
      for (final tag in m.entity.tags) {
        result.add({'article_id': m.entity.id, 'tag_id': tag.id});
        tagCount++;
      }
    }
    counts['article_tags'] = tagCount;
    return result;
  }

  List<Map<String, dynamic>> _serializeImages(Map<String, int> counts) {
    final images = ImageRepository.i.all();
    counts['images'] = images.length;
    return images.map((m) {
      final e = m.entity;
      return <String, dynamic>{
        'id': e.id,
        'url': e.url,
        'path': e.path,
        'article_id': e.article.targetId,
        'created_at': _dtToIso(e.createdAt),
        'updated_at': _dtToIso(e.updatedAt),
      };
    }).toList();
  }

  List<Map<String, dynamic>> _serializeScreenshots(Map<String, int> counts) {
    final screenshots = ScreenshotRepository.i.all();
    counts['screenshots'] = screenshots.length;
    return screenshots.map((m) {
      final e = m.entity;
      return <String, dynamic>{
        'id': e.id,
        'path': e.path,
        'article_id': e.article.targetId,
        'created_at': _dtToIso(e.createdAt),
        'updated_at': _dtToIso(e.updatedAt),
      };
    }).toList();
  }

  List<Map<String, dynamic>> _serializeEntities<T>(
    List<dynamic> models,
    Map<String, int> counts,
    String name,
    Map<String, dynamic> Function(dynamic) serializer,
  ) {
    counts[name] = models.length;
    return models.map(serializer).toList();
  }

  Map<String, dynamic> _diaryToMap(dynamic m) {
    final e = (m as DiaryModel).entity;
    return {
      'id': e.id,
      'content': e.content,
      'tags': e.tags,
      'mood': e.mood,
      'images': e.images,
      'created_at': _dtToIso(e.createdAt),
      'updated_at': _dtToIso(e.updatedAt),
    };
  }

  Map<String, dynamic> _bookToMap(dynamic m) {
    final e = (m as BookModel).entity;
    return {
      'id': e.id,
      'title': e.title,
      'author': e.author,
      'category': e.category,
      'cover_image': e.coverImage,
      'introduction': e.introduction,
      'has_update': e.hasUpdate,
      'created_at': _dtToIso(e.createdAt),
      'updated_at': _dtToIso(e.updatedAt),
    };
  }

  Map<String, dynamic> _bookViewpointToMap(dynamic m) {
    final e = (m as BookViewpointModel).entity;
    return {
      'id': e.id,
      'book_id': e.bookId,
      'title': e.title,
      'content': e.content,
      'example': e.example,
      'created_at': _dtToIso(e.createdAt),
      'updated_at': _dtToIso(e.updatedAt),
    };
  }

  Map<String, dynamic> _tagToMap(dynamic m) {
    final e = (m as TagModel).entity;
    return {
      'id': e.id,
      'name': e.name,
      'icon': e.icon,
      'created_at': _dtToIso(e.createdAt),
      'updated_at': _dtToIso(e.updatedAt),
    };
  }

  Map<String, dynamic> _settingToMap(dynamic m) {
    final e = (m as SettingModel).entity;
    return {
      'id': e.id,
      'key': e.key,
      'value': e.value,
      'created_at': _dtToIso(e.createdAt),
      'updated_at': _dtToIso(e.updatedAt),
    };
  }

  Map<String, dynamic> _aiConfigToMap(dynamic m) {
    final e = (m as AIConfigModel).entity;
    return {
      'id': e.id,
      'name': e.name,
      'api_address': e.apiAddress,
      'api_token': e.apiToken,
      'model_name': e.modelName,
      'function_type': e.functionType,
      'inherit_from_general': e.inheritFromGeneral,
      'is_default': e.isDefault,
      'created_at': _dtToIso(e.createdAt),
      'updated_at': _dtToIso(e.updatedAt),
    };
  }

  Map<String, dynamic> _weeklySummaryToMap(dynamic m) {
    final e = (m as WeeklySummaryModel).entity;
    return {
      'id': e.id,
      'week_start_date': _dtToIso(e.weekStartDate),
      'week_end_date': _dtToIso(e.weekEndDate),
      'content': e.content,
      'article_count': e.articleCount,
      'diary_count': e.diaryCount,
      'viewpoint_count': e.viewpointCount,
      'article_ids': e.articleIds,
      'diary_ids': e.diaryIds,
      'viewpoint_ids': e.viewpointIds,
      'app_ideas': e.appIdeas,
      'status': e.status,
      'created_at': _dtToIso(e.createdAt),
      'updated_at': _dtToIso(e.updatedAt),
    };
  }

  Map<String, dynamic> _sessionToMap(dynamic m) {
    final e = (m as SessionModel).entity;
    return {
      'id': e.id,
      'session_id': e.sessionId,
      'is_authenticated': e.isAuthenticated,
      'username': e.username,
      'last_accessed_at': _dtToIso(e.lastAccessedAt),
      'created_at': _dtToIso(e.createdAt),
      'updated_at': _dtToIso(e.updatedAt),
    };
  }

  Future<void> _addImagesToArchive(arch.Archive archive) async {
    final imagePaths = <String>{};

    for (final m in ArticleRepository.i.all()) {
      final cover = m.entity.coverImage;
      if (cover != null && cover.isNotEmpty) imagePaths.add(cover);
    }

    for (final m in DiaryRepository.i.all()) {
      final imgs = m.entity.images;
      if (imgs == null || imgs.isEmpty) continue;
      for (final p in imgs.split(',')) {
        final trimmed = p.trim();
        if (trimmed.isNotEmpty) imagePaths.add(trimmed);
      }
    }

    for (final m in ImageRepository.i.all()) {
      final p = m.entity.path;
      if (p != null && p.isNotEmpty) imagePaths.add(p);
    }

    for (final m in ScreenshotRepository.i.all()) {
      final p = m.entity.path;
      if (p != null && p.isNotEmpty) imagePaths.add(p);
    }

    var added = 0;
    for (final relPath in imagePaths) {
      final absPath = FileService.i.toAbsolutePath(relPath);
      final file = File(absPath);
      if (await file.exists()) {
        final bytes = await file.readAsBytes();
        archive.addFile(arch.File.fromBytes(relPath, bytes));
        added++;
      } else {
        _warnings.add('图片缺失: $relPath');
        logger.w('[ExportService] 图片缺失: $relPath');
      }
    }
    logger.i('[ExportService] 打包图片: $added/${imagePaths.length}');
  }

  String? _dtToIso(DateTime? dt) => dt?.toUtc().toIso8601String();
}
```

注意：上面的代码中，`DiaryModel`、`BookModel`、`BookViewpointModel`、`TagModel`、`SettingModel`、`AIConfigModel`、`WeeklySummaryModel`、`SessionModel`、`ImageModel`、`ScreenshotModel`、`ArticleModel` 都是项目中已有的 model 类。`_serializeEntities` 使用泛型 + 回调函数避免为每个实体类型写重复的列表遍历代码。

需要确认 `data.dart` 导出了所有需要的 model 和 repository 类。如果 `DiaryModel` 等没有在 `data.dart` 中导出，需要从各自的 model 文件中直接 import。

- [ ] **Step 3: 运行 flutter analyze 验证无错误**

Run: `flutter analyze`
Expected: `No issues found!`

如果有 import 错误，需要检查 `lib/app/data/data.dart` 是否导出了所有需要的 model 类。如果没有，需要添加相应的 import。

---

## Task 3: 在设置页面添加导出 UI

**分支:** `main`

**Files:**
- Modify: `lib/app/pages/settings/providers/settings_controller_provider.dart`
- Modify: `lib/app/pages/settings/views/settings_view.dart`

- [ ] **Step 1: 在 SettingsControllerState 中添加导出相关状态**

在 `SettingsControllerState` 的 factory 中添加：

```dart
@Default(false) bool isExporting,
@Default(0.0) double exportProgress,
```

- [ ] **Step 2: 在 SettingsController 中添加导出方法**

在 `SettingsController` 类中添加：

```dart
Future<void> exportData() async {
  if (state.isExporting) return;

  final permission = await Permission.manageExternalStorage.request();
  if (!permission.isGranted) {
    UIUtils.showError('请授予应用管理外部存储的权限');
    return;
  }

  final directory = await FilePicker.platform.getDirectoryPath(
    dialogTitle: '选择导出保存位置',
  );
  if (directory == null) return;

  state = state.copyWith(isExporting: true, exportProgress: 0.0);

  try {
    ExportService.i.exportProgress.addListener(_onExportProgress);
    final ts = DateTime.now().toIso8601String().replaceAll(RegExp('[:.]+'), '-');
    final outputPath = path.join(directory, 'daily_satori_export_$ts.zip');

    DialogUtils.showLoading(tips: '正在导出数据...');

    final result = await ExportService.i.exportToZip(outputPath);

    DialogUtils.hideLoading();

    if (result != null) {
      UIUtils.showSuccess('导出成功: $result');
    } else {
      UIUtils.showError('导出失败');
    }
  } catch (e) {
    logger.e('[SettingsController] 导出失败', error: e);
    DialogUtils.hideLoading();
    UIUtils.showError('导出失败: $e');
  } finally {
    ExportService.i.exportProgress.removeListener(_onExportProgress);
    state = state.copyWith(isExporting: false, exportProgress: 0.0);
  }
}

void _onExportProgress() {
  state = state.copyWith(exportProgress: ExportService.i.exportProgress.value);
}
```

需要在文件顶部添加 import：

```dart
import 'package:daily_satori/app/services/export_service.dart';
import 'package:path/path.dart' as path;
```

- [ ] **Step 3: 在 settings_view.dart 的系统设置分区添加导出按钮**

在 `_buildSystemSection` 方法的 `items` 列表中，在"检查更新"项之后添加：

```dart
_buildSettingItemWithProgress(
  context: context,
  title: '导出数据（迁移用）',
  subtitle: state.isExporting
      ? '正在导出... ${(state.exportProgress * 100).toInt()}%'
      : '导出为 JSON 格式，用于迁移到新版本',
  icon: Icons.upload_file_rounded,
  color: AppColors.getInfo(context),
  isLoading: state.isExporting,
  onTap: state.isExporting
      ? null
      : () => ref.read(settingsControllerProvider.notifier).exportData(),
),
```

- [ ] **Step 4: 运行 flutter analyze**

Run: `flutter analyze`
Expected: `No issues found!`

- [ ] **Step 5: 运行 build_runner 生成代码（如果 freezed/generator 有变化）**

Run: `flutter pub run build_runner build --delete-conflicting-outputs`
Expected: 成功生成 `.freezed.dart` 和 `.g.dart` 文件

---

## Task 4: KMP ImportService 实现

**分支:** `android`

**Files:**
- Create: `shared/src/commonMain/kotlin/com/dailysatori/service/import/ImportService.kt`

- [ ] **Step 1: 切换到 android 分支**

Run: `git checkout android`

- [ ] **Step 2: 创建 ImportService.kt**

创建 `shared/src/commonMain/kotlin/com/dailysatori/service/import/ImportService.kt`：

```kotlin
package com.dailysatori.service.import

import com.dailysatori.data.db.DailySatoriDatabase
import com.dailysatori.platform.FileManager
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class ExportManifest(
    val version: Int,
    val app_version: String,
    val export_time: String,
    val platform: String,
    val counts: Map<String, Int>? = null,
)

data class ImportResult(
    val success: Boolean,
    val imported: Map<String, Int>,
    val warnings: List<String>,
    val errors: List<String>,
)

class ImportService(
    private val db: DailySatoriDatabase,
    private val fileManager: FileManager,
) {
    private val json = Json { ignoreUnknownKeys = true }

    private val idMaps = mutableMapOf<String, MutableMap<Long, Long>>()

    fun importFromZip(zipPath: String): ImportResult {
        val warnings = mutableListOf<String>()
        val errors = mutableListOf<String>()
        val imported = mutableMapOf<String, Int>()

        idMaps.clear()

        try {
            val tempDir = fileManager.getCacheDir() + "/import_temp"
            extractZip(zipPath, tempDir)

            val manifestText = fileManager.readFile("$tempDir/manifest.json").decodeToString()
            val manifest = json.decodeFromString<ExportManifest>(manifestText)

            if (manifest.version > 1) {
                warnings.add("导出版本 ${manifest.version} 高于支持版本 1，部分数据可能丢失")
            }

            val importOrder = listOf(
                "settings" to { d: String -> importSettings(d) },
                "ai_configs" to { d: String -> importAiConfigs(d) },
                "tags" to { d: String -> importTags(d) },
                "articles" to { d: String -> importArticles(d) },
                "article_tags" to { d: String -> importArticleTags(d) },
                "images" to { d: String -> importImages(d) },
                "screenshots" to { d: String -> importScreenshots(d) },
                "diaries" to { d: String -> importDiaries(d) },
                "books" to { d: String -> importBooks(d) },
                "book_viewpoints" to { d: String -> importBookViewpoints(d) },
                "weekly_summaries" to { d: String -> importWeeklySummaries(d) },
                "sessions" to { d: String -> importSessions(d) },
            )

            for ((name, importer) in importOrder) {
                val filePath = "$tempDir/$name.json"
                if (!fileManager.exists(filePath)) {
                    warnings.add("$name.json 不存在，跳过")
                    continue
                }
                try {
                    val data = fileManager.readFile(filePath).decodeToString()
                    val count = importer(data)
                    imported[name] = count
                } catch (e: Exception) {
                    errors.add("导入 $name 失败: ${e.message}")
                }
            }

            copyImageFiles(tempDir)

            cleanupDir(tempDir)

            return ImportResult(
                success = errors.isEmpty(),
                imported = imported,
                warnings = warnings,
                errors = errors,
            )
        } catch (e: Exception) {
            return ImportResult(
                success = false,
                imported = imported,
                warnings = warnings,
                errors = listOf("导入失败: ${e.message}"),
            )
        }
    }

    private fun extractZip(zipPath: String, destDir: String) {
        fileManager.extractZip(zipPath, destDir)
    }

    private fun cleanupDir(dir: String) {
        fileManager.deleteFile(dir)
    }

    private fun copyImageFiles(tempDir: String) {
        for (imageDir in listOf("images", "diary_images")) {
            val srcDir = "$tempDir/$imageDir"
            if (!fileManager.exists(srcDir)) return
            val files = fileManager.listFiles(srcDir)
            val destDir = fileManager.getImagesDir()
            for (file in files) {
                val fileName = file.substringAfterLast("/")
                fileManager.copyFile(file, "$destDir/$fileName")
            }
        }
    }

    private fun parseEpochMs(isoString: String?): Long? {
        if (isoString == null) return null
        return try {
            Instant.parse(isoString).toEpochMilliseconds()
        } catch (_: Exception) {
            isoString.toLongOrNull()
        }
    }

    private fun importSettings(data: String): Int {
        val items = json.decodeFromString<List<Map<String, String?>>>(data)
        var count = 0
        for (item in items) {
            try {
                val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
                db.settingQueries.upsertSetting(
                    key = item["key"] ?: continue,
                    value = item["value"],
                    created_at = parseEpochMs(item["created_at"]) ?: now,
                    updated_at = parseEpochMs(item["updated_at"]) ?: now,
                )
                count++
            } catch (_: Exception) {}
        }
        return count
    }

    private fun importAiConfigs(data: String): Int {
        val items = json.decodeFromString<List<Map<String, Any?>>>(data)
        var count = 0
        for (item in items) {
            try {
                val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
                val newId = db.aiConfigQueries.insertAndGetId(
                    name = item["name"] as? String ?: continue,
                    api_address = item["api_address"] as? String ?: continue,
                    api_token = item["api_token"] as? String ?: continue,
                    model_name = item["model_name"] as? String ?: continue,
                    function_type = (item["function_type"] as? Number)?.toInt() ?: 0,
                    inherit_from_general = if (item["inherit_from_general"] == true) 1 else 0,
                    is_default = if (item["is_default"] == true) 1 else 0,
                    created_at = parseEpochMs(item["created_at"] as? String) ?: now,
                    updated_at = parseEpochMs(item["updated_at"] as? String) ?: now,
                )
                val oldId = (item["id"] as? Number)?.toLong() ?: continue
                idMaps.getOrPut("ai_config") { mutableMapOf() }[oldId] = newId
                count++
            } catch (_: Exception) {}
        }
        return count
    }

    private fun importTags(data: String): Int {
        val items = json.decodeFromString<List<Map<String, Any?>>>(data)
        var count = 0
        for (item in items) {
            try {
                val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
                val newId = db.tagQueries.insertAndGetId(
                    name = item["name"] as? String ?: continue,
                    icon = item["icon"] as? String,
                    created_at = parseEpochMs(item["created_at"] as? String) ?: now,
                    updated_at = parseEpochMs(item["updated_at"] as? String) ?: now,
                )
                val oldId = (item["id"] as? Number)?.toLong() ?: continue
                idMaps.getOrPut("tag") { mutableMapOf() }[oldId] = newId
                count++
            } catch (_: Exception) {}
        }
        return count
    }

    private fun importArticles(data: String): Int {
        val items = json.decodeFromString<List<Map<String, Any?>>>(data)
        var count = 0
        for (item in items) {
            try {
                val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
                val newId = db.articleQueries.insertAndGetId(
                    title = item["title"] as? String,
                    ai_title = item["ai_title"] as? String,
                    content = item["content"] as? String,
                    ai_content = item["ai_content"] as? String,
                    html_content = item["html_content"] as? String,
                    ai_markdown_content = item["ai_markdown_content"] as? String,
                    url = item["url"] as? String,
                    is_favorite = if (item["is_favorite"] == true) 1 else 0,
                    comment = item["comment"] as? String,
                    status = item["status"] as? String ?: "pending",
                    cover_image = item["cover_image"] as? String,
                    cover_image_url = item["cover_image_url"] as? String,
                    pub_date = parseEpochMs(item["pub_date"] as? String),
                    created_at = parseEpochMs(item["created_at"] as? String) ?: now,
                    updated_at = parseEpochMs(item["updated_at"] as? String) ?: now,
                )
                val oldId = (item["id"] as? Number)?.toLong() ?: continue
                idMaps.getOrPut("article") { mutableMapOf() }[oldId] = newId
                count++
            } catch (_: Exception) {}
        }
        return count
    }

    private fun importArticleTags(data: String): Int {
        val items = json.decodeFromString<List<Map<String, Number>>>(data)
        var count = 0
        for (item in items) {
            try {
                val oldArticleId = item["article_id"]?.toLong() ?: continue
                val oldTagId = item["tag_id"]?.toLong() ?: continue
                val newArticleId = idMaps["article"]?.get(oldArticleId) ?: continue
                val newTagId = idMaps["tag"]?.get(oldTagId) ?: continue
                db.articleTagQueries.insertArticleTag(newArticleId, newTagId)
                count++
            } catch (_: Exception) {}
        }
        return count
    }

    private fun importImages(data: String): Int {
        val items = json.decodeFromString<List<Map<String, Any?>>>(data)
        var count = 0
        for (item in items) {
            try {
                val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
                val oldArticleId = (item["article_id"] as? Number)?.toLong()
                val newArticleId = oldArticleId?.let { idMaps["article"]?.get(it) }
                db.imageQueries.insertAndGetId(
                    url = item["url"] as? String,
                    path = item["path"] as? String,
                    article_id = newArticleId,
                    created_at = parseEpochMs(item["created_at"] as? String) ?: now,
                    updated_at = parseEpochMs(item["updated_at"] as? String) ?: now,
                )
                count++
            } catch (_: Exception) {}
        }
        return count
    }

    private fun importScreenshots(data: String): Int {
        val items = json.decodeFromString<List<Map<String, Any?>>>(data)
        var count = 0
        for (item in items) {
            val path = item["path"] as? String ?: continue
            logger.d("[ImportService] Screenshot: $path")
            count++
        }
        return count
    }

    private fun importDiaries(data: String): Int {
        val items = json.decodeFromString<List<Map<String, Any?>>>(data)
        var count = 0
        for (item in items) {
            try {
                val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
                db.diaryQueries.insertAndGetId(
                    content = item["content"] as? String ?: continue,
                    tags = item["tags"] as? String,
                    mood = item["mood"] as? String,
                    images = item["images"] as? String,
                    created_at = parseEpochMs(item["created_at"] as? String) ?: now,
                    updated_at = parseEpochMs(item["updated_at"] as? String) ?: now,
                )
                count++
            } catch (_: Exception) {}
        }
        return count
    }

    private fun importBooks(data: String): Int {
        val items = json.decodeFromString<List<Map<String, Any?>>>(data)
        var count = 0
        for (item in items) {
            try {
                val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
                val newId = db.bookQueries.insertAndGetId(
                    title = item["title"] as? String ?: continue,
                    author = item["author"] as? String ?: continue,
                    category = item["category"] as? String ?: continue,
                    cover_image = item["cover_image"] as? String ?: "",
                    introduction = item["introduction"] as? String ?: "",
                    has_update = if (item["has_update"] == true) 1 else 0,
                    created_at = parseEpochMs(item["created_at"] as? String) ?: now,
                    updated_at = parseEpochMs(item["updated_at"] as? String) ?: now,
                )
                val oldId = (item["id"] as? Number)?.toLong() ?: continue
                idMaps.getOrPut("book") { mutableMapOf() }[oldId] = newId
                count++
            } catch (_: Exception) {}
        }
        return count
    }

    private fun importBookViewpoints(data: String): Int {
        val items = json.decodeFromString<List<Map<String, Any?>>>(data)
        var count = 0
        for (item in items) {
            try {
                val oldBookId = (item["book_id"] as? Number)?.toLong() ?: continue
                val newBookId = idMaps["book"]?.get(oldBookId) ?: continue
                val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
                db.bookViewpointQueries.insertAndGetId(
                    book_id = newBookId,
                    title = item["title"] as? String ?: continue,
                    content = item["content"] as? String ?: continue,
                    example = item["example"] as? String ?: "",
                    created_at = parseEpochMs(item["created_at"] as? String) ?: now,
                    updated_at = parseEpochMs(item["updated_at"] as? String) ?: now,
                )
                count++
            } catch (_: Exception) {}
        }
        return count
    }

    private fun importWeeklySummaries(data: String): Int {
        val items = json.decodeFromString<List<Map<String, Any?>>>(data)
        var count = 0
        for (item in items) {
            try {
                val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
                db.weeklySummaryQueries.insertAndGetId(
                    week_start_date = parseEpochMs(item["week_start_date"] as? String) ?: continue,
                    week_end_date = parseEpochMs(item["week_end_date"] as? String) ?: continue,
                    content = item["content"] as? String ?: continue,
                    article_count = (item["article_count"] as? Number)?.toInt() ?: 0,
                    diary_count = (item["diary_count"] as? Number)?.toInt() ?: 0,
                    viewpoint_count = (item["viewpoint_count"] as? Number)?.toInt() ?: 0,
                    article_ids = item["article_ids"] as? String,
                    diary_ids = item["diary_ids"] as? String,
                    viewpoint_ids = item["viewpoint_ids"] as? String,
                    app_ideas = item["app_ideas"] as? String,
                    status = item["status"] as? String ?: "pending",
                    created_at = parseEpochMs(item["created_at"] as? String) ?: now,
                    updated_at = parseEpochMs(item["updated_at"] as? String) ?: now,
                )
                count++
            } catch (_: Exception) {}
        }
        return count
    }

    private fun importSessions(data: String): Int {
        val items = json.decodeFromString<List<Map<String, Any?>>>(data)
        var count = 0
        for (item in items) {
            try {
                val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
                db.sessionQueries.insertAndGetId(
                    session_id = item["session_id"] as? String ?: continue,
                    is_authenticated = if (item["is_authenticated"] == true) 1 else 0,
                    username = item["username"] as? String,
                    last_accessed_at = parseEpochMs(item["last_accessed_at"] as? String) ?: now,
                    created_at = parseEpochMs(item["created_at"] as? String) ?: now,
                    updated_at = parseEpochMs(item["updated_at"] as? String) ?: now,
                )
                count++
            } catch (_: Exception) {}
        }
        return count
    }
}
```

注意：上面的代码使用了 `insertAndGetId` 等 SQLDelight 查询方法，这些需要在 `.sq` 文件中定义。需要在 `DailySatori.sq` 中添加对应的 `insertAndGetId` 查询（返回 `SELECT last_insert_rowid()` 的版本）。同时 `FileManager` 需要有 `extractZip` 方法，需要在 expect/actual 中添加。

- [ ] **Step 3: 在 DailySatori.sq 中添加缺少的 insertAndGetId 查询**

在 `shared/src/commonMain/sqldelight/com/dailysatori/shared/db/DailySatori.sq` 中，为每个表添加带返回 ID 的 insert 语句（如果还没有的话）：

```sql
-- 在现有 insert 语句之后添加

insertArticleAndGetId:
INSERT INTO article (title, ai_title, content, ai_content, html_content, ai_markdown_content, url, is_favorite, comment, status, cover_image, cover_image_url, pub_date, created_at, updated_at)
VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);
SELECT last_insert_rowid();

insertTagAndGetId:
INSERT OR IGNORE INTO tag (name, icon, created_at, updated_at)
VALUES (?, ?, ?, ?);
SELECT last_insert_rowid();

insertBookAndGetId:
INSERT INTO book (title, author, category, cover_image, introduction, has_update, created_at, updated_at)
VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?);
SELECT last_insert_rowid();

insertBookViewpointAndGetId:
INSERT INTO book_viewpoint (book_id, title, content, example, created_at, updated_at)
VALUES (?, ?, ?, ?, ?, ?, ?);
SELECT last_insert_rowid();

insertImageAndGetId:
INSERT INTO image (url, path, article_id, created_at, updated_at)
VALUES (?, ?, ?, ?, ?);
SELECT last_insert_rowid();

insertDiaryAndGetId:
INSERT INTO diary (content, tags, mood, images, created_at, updated_at)
VALUES (?, ?, ?, ?, ?, ?);
SELECT last_insert_rowid();

insertAiConfigAndGetId:
INSERT INTO ai_config (name, api_address, api_token, model_name, function_type, inherit_from_general, is_default, created_at, updated_at)
VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?);
SELECT last_insert_rowid();

insertWeeklySummaryAndGetId:
INSERT INTO weekly_summary (week_start_date, week_end_date, content, article_count, diary_count, viewpoint_count, article_ids, diary_ids, viewpoint_ids, app_ideas, status, created_at, updated_at)
VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);
SELECT last_insert_rowid();

insertSessionAndGetId:
INSERT INTO session (session_id, is_authenticated, username, last_accessed_at, created_at, updated_at)
VALUES (?, ?, ?, ?, ?, ?);
SELECT last_insert_rowid();

insertArticleTag:
INSERT OR IGNORE INTO article_tag (article_id, tag_id) VALUES (?, ?);
```

- [ ] **Step 4: 在 FileManager expect/actual 中添加 extractZip 方法**

在 `shared/src/commonMain/kotlin/com/dailysatori/platform/FileManager.kt` 的 expect 类中添加：

```kotlin
expect fun extractZip(zipPath: String, destDir: String)
```

在 `app/src/main/kotlin/com/dailysatori/platform/AndroidFileManager.kt` 的 actual 类中添加：

```kotlin
actual fun extractZip(zipPath: String, destDir: String) {
    java.util.zip.ZipFile(zipPath).use { zip ->
        val dest = File(destDir)
        if (!dest.exists()) dest.mkdirs()
        zip.entries().asSequence().forEach { entry ->
            val file = File(dest, entry.name)
            if (entry.isDirectory) {
                file.mkdirs()
            } else {
                file.parentFile?.mkdirs()
                zip.getInputStream(entry).use { input ->
                    file.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 5: 构建 KMP 项目验证编译通过**

Run: `./gradlew :shared:build`
Expected: BUILD SUCCESSFUL

---

## Task 5: KMP 导入页面 UI

**分支:** `android`

**Files:**
- Create: `app/src/main/kotlin/com/dailysatori/ui/pages/data_import/DataImportScreen.kt`

- [ ] **Step 1: 创建 DataImportScreen.kt**

创建 `app/src/main/kotlin/com/dailysatori/ui/pages/data_import/DataImportScreen.kt`，包含文件选择、进度显示、结果展示。

此文件的具体实现需要参考 android 分支中已有的页面风格（如 BackupRestoreScreen），使用 Compose + ViewModel + StateFlow 模式。

核心功能：
1. SAF 文件选择器选取 ZIP 文件
2. 调用 ImportService.importFromZip()
3. 显示导入进度
4. 显示结果摘要（各实体导入数量 + 警告 + 错误）

- [ ] **Step 2: 在导航中添加 DataImport 路由**

在 Navigation 的 NavHost 中添加 DataImport 路由，并在设置页面添加导航入口。

- [ ] **Step 3: 构建并验证**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL
