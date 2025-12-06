import 'package:daily_satori/app/data/index.dart';
import 'package:daily_satori/app/services/file_service.dart';
import 'package:daily_satori/app/services/migration_service/migration_task.dart';

/// 路径迁移任务 - 将文章和日记中的图片绝对路径转换为相对路径
///
/// 解决问题：
/// - 旧版本存储的是绝对路径（如 /data/user/0/com.xxx/files/images/xxx.jpg）
/// - 备份恢复到其他设备时，由于 app 安装路径不同，导致无法找到图片
/// - 新版本改为存储相对路径（如 images/xxx.jpg），读取时拼接当前设备的 appPath
class PathMigrationTask extends MigrationTask {
  @override
  int get version => 3;

  @override
  String get description => "图片路径从绝对路径迁移为相对路径";

  @override
  Future<bool> shouldRun() async {
    // 检查是否有文章或日记的图片路径是绝对路径
    final hasAbsoluteArticlePath = _hasAbsolutePathInArticles();
    final hasAbsoluteDiaryPath = _hasAbsolutePathInDiaries();

    final needMigration = hasAbsoluteArticlePath || hasAbsoluteDiaryPath;

    if (needMigration) {
      logInfo("检测到需要迁移图片路径数据");
    } else {
      logInfo("无需图片路径迁移");
    }

    return needMigration;
  }

  /// 检查文章中是否有绝对路径
  bool _hasAbsolutePathInArticles() {
    final articles = ArticleRepository.i.allModels();
    for (final article in articles) {
      final coverImage = article.coverImage;
      if (coverImage != null && coverImage.isNotEmpty && coverImage.startsWith('/')) {
        return true;
      }
    }
    return false;
  }

  /// 检查日记中是否有绝对路径
  bool _hasAbsolutePathInDiaries() {
    final diaries = DiaryRepository.i.findAll();
    for (final diary in diaries) {
      final images = diary.images;
      if (images != null && images.isNotEmpty) {
        final paths = images.split(',');
        for (final path in paths) {
          if (path.trim().isNotEmpty && path.trim().startsWith('/')) {
            return true;
          }
        }
      }
    }
    return false;
  }

  @override
  Future<void> migrate() async {
    try {
      // 1. 迁移文章封面图路径
      await _migrateArticlePaths();

      // 2. 迁移日记图片路径
      await _migrateDiaryPaths();

      logSuccess("图片路径迁移完成");
    } catch (e, stackTrace) {
      logError("图片路径迁移失败", error: e, stackTrace: stackTrace);
    }
  }

  /// 迁移文章封面图路径
  Future<void> _migrateArticlePaths() async {
    logInfo("开始迁移文章封面图路径");

    final articles = ArticleRepository.i.allModels();
    logInfo("找到 ${articles.length} 篇文章需要处理");

    final counter = MigrationCounter();

    for (final article in articles) {
      try {
        final coverImage = article.coverImage;

        // 跳过空路径或已经是相对路径的情况
        if (coverImage == null || coverImage.isEmpty) {
          counter.skippedCount++;
          continue;
        }

        if (!coverImage.startsWith('/')) {
          // 已经是相对路径
          counter.skippedCount++;
          continue;
        }

        // 转换为相对路径
        final relativePath = FileService.i.toRelativePath(coverImage);

        if (relativePath != coverImage) {
          article.coverImage = relativePath;
          ArticleRepository.i.updateModel(article);
          counter.migratedCount++;
        } else {
          counter.skippedCount++;
        }
      } catch (e) {
        counter.errorCount++;
        logError("处理文章ID:${article.id}失败", error: e);
      }

      // 定期输出进度日志
      if (counter.totalProcessed % 50 == 0 && counter.totalProcessed > 0) {
        _logProgress("文章", counter);
      }
    }

    // 输出最终结果
    _logProgress("文章", counter, isFinal: true);
  }

  /// 迁移日记图片路径
  Future<void> _migrateDiaryPaths() async {
    logInfo("开始迁移日记图片路径");

    final diaries = DiaryRepository.i.findAll();
    logInfo("找到 ${diaries.length} 条日记需要处理");

    final counter = MigrationCounter();

    for (final diary in diaries) {
      try {
        final images = diary.images;

        // 跳过空路径
        if (images == null || images.isEmpty) {
          counter.skippedCount++;
          continue;
        }

        final paths = images.split(',').map((e) => e.trim()).where((e) => e.isNotEmpty).toList();

        if (paths.isEmpty) {
          counter.skippedCount++;
          continue;
        }

        bool hasChanged = false;
        final newPaths = <String>[];

        for (final path in paths) {
          if (path.startsWith('/')) {
            // 绝对路径，需要转换
            final relativePath = FileService.i.toRelativePath(path);
            newPaths.add(relativePath);
            if (relativePath != path) {
              hasChanged = true;
            }
          } else {
            // 已经是相对路径
            newPaths.add(path);
          }
        }

        if (hasChanged) {
          diary.images = newPaths.join(',');
          DiaryRepository.i.save(diary);
          counter.migratedCount++;
        } else {
          counter.skippedCount++;
        }
      } catch (e) {
        counter.errorCount++;
        logError("处理日记ID:${diary.id}失败", error: e);
      }

      // 定期输出进度日志
      if (counter.totalProcessed % 50 == 0 && counter.totalProcessed > 0) {
        _logProgress("日记", counter);
      }
    }

    // 输出最终结果
    _logProgress("日记", counter, isFinal: true);
  }

  /// 输出迁移进度日志
  void _logProgress(String type, MigrationCounter counter, {bool isFinal = false}) {
    final prefix = isFinal ? "📊 $type路径迁移完成" : "📊 $type路径迁移进度";
    logInfo(
      "$prefix - 已迁移: ${counter.migratedCount}, "
      "跳过: ${counter.skippedCount}, "
      "错误: ${counter.errorCount}, "
      "总计: ${counter.totalProcessed}",
    );
  }
}
