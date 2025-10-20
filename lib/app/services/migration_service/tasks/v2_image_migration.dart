import 'dart:io';

import 'package:daily_satori/app/models/models.dart';
import 'package:daily_satori/app/objectbox/article.dart';
import 'package:daily_satori/app/objectbox/image.dart' as db_image;
import 'package:daily_satori/app/objectbox/screenshot.dart';
import 'package:daily_satori/app/repositories/article_repository.dart';
import 'package:daily_satori/app/services/migration_service/migration_task.dart';
import 'package:daily_satori/app/services/objectbox_service.dart';
import 'package:daily_satori/objectbox.g.dart';

/// 图片迁移任务 - 将文章中的图片迁移到封面图属性
class ImageMigrationTask extends MigrationTask {
  @override
  int get version => 2;

  @override
  String get description => "文章图片到封面图的迁移";

  @override
  Future<bool> shouldRun() async {
    // 获取图片和截图数据库
    final imageBox = ObjectboxService.i.box<db_image.Image>();
    final screenshotBox = ObjectboxService.i.box<Screenshot>();

    // 如果图片或截图数据库不为空，则需要迁移
    final needMigration = imageBox.count() > 0 || screenshotBox.count() > 0;

    if (needMigration) {
      logInfo("检测到需要迁移图片数据");
    } else {
      logInfo("无需图片数据迁移");
    }

    return needMigration;
  }

  @override
  Future<void> migrate() async {
    try {
      // 1. 迁移封面图
      await _migrateArticleCoverImages();

      // 2. 清理多余的图片数据
      await _clearImageData();

      logSuccess("图片数据迁移完成");
    } catch (e, stackTrace) {
      logError("图片数据迁移失败", error: e, stackTrace: stackTrace);
    }
  }

  /// 迁移文章封面图到新的coverImage属性
  Future<void> _migrateArticleCoverImages() async {
    logInfo("开始迁移文章封面图");

    // 获取所有文章
    final articles = ArticleRepository.instance.allModels();
    logInfo("找到 ${articles.length} 篇文章需要处理");

    // 迁移计数器
    final counter = MigrationCounter();

    // 处理每篇文章
    for (final article in articles) {
      try {
        await _processSingleArticle(article, counter);
      } catch (e) {
        counter.errorCount++;
        logError("处理文章ID:${article.id}失败", error: e);
      }

      // 定期输出进度日志
      if (counter.totalProcessed % 20 == 0) {
        _logMigrationProgress(counter);
      }
    }

    // 输出最终结果
    _logMigrationProgress(counter, isFinal: true);
  }

  /// 处理单个文章的封面图迁移
  Future<void> _processSingleArticle(ArticleModel article, MigrationCounter counter) async {
    final entity = article.entity;

    // 如果已经有封面图，则跳过
    if (entity.coverImage != null && entity.coverImage!.isNotEmpty) {
      counter.skippedCount++;
      return;
    }

    final allImages = entity.images.toList();

    if (allImages.isNotEmpty) {
      // 将第一张图片设置为封面图
      entity.coverImage = allImages.first.path;
      entity.coverImageUrl = allImages.first.url;

      // 保存文章
      ObjectboxService.i.box<Article>().put(entity);
      counter.migratedCount++;

      // 处理多余图片
      await _processExtraImages(allImages, entity);
    } else {
      counter.noImageCount++;
    }
  }

  /// 处理文章额外的图片（第一张保留作为封面图，其余删除）
  Future<void> _processExtraImages(List<db_image.Image> images, Article article) async {
    // 删除除了第一张之外的图片
    if (images.length > 1) {
      for (final image in images.sublist(1)) {
        await _safeDeleteFile(image.path);
      }
    }

    // 注意：screenshots 字段已被移除，不再需要删除截图
  }

  /// 安全删除文件（检查是否被用作封面图）
  Future<void> _safeDeleteFile(String? filePath) async {
    if (filePath == null || filePath.isEmpty) return;

    // 检查是否为封面图
    if (await _isUsedAsCoverImage(filePath)) {
      logInfo("文件被用作封面图，跳过删除: $filePath");
      return;
    }

    // 删除文件
    try {
      final file = File(filePath);
      if (await file.exists()) {
        await file.delete();
      }
    } catch (e) {
      logWarning("删除文件失败: $filePath, 错误: $e");
    }
  }

  /// 检查文件是否被用作封面图
  Future<bool> _isUsedAsCoverImage(String filePath) async {
    final articleBox = ObjectboxService.i.box<Article>();
    final query = articleBox.query(Article_.coverImage.equals(filePath)).build();
    try {
      return query.count() > 0;
    } finally {
      query.close();
    }
  }

  /// 清理图片相关数据
  Future<void> _clearImageData() async {
    logInfo("清理图片数据");

    final imageBox = ObjectboxService.i.box<db_image.Image>();
    final screenshotBox = ObjectboxService.i.box<Screenshot>();

    final imageCount = imageBox.count();
    final screenshotCount = screenshotBox.count();

    imageBox.removeAll();
    screenshotBox.removeAll();

    logSuccess("已清理 $imageCount 张图片和 $screenshotCount 张截图数据");
  }

  /// 输出迁移进度日志
  void _logMigrationProgress(MigrationCounter counter, {bool isFinal = false}) {
    final status = isFinal ? "完成" : "进度";
    logInfo(
      "封面迁移$status: ${counter.migratedCount} 篇成功, ${counter.noImageCount} 篇无图, ${counter.skippedCount} 篇跳过${counter.errorCount > 0 ? ', ${counter.errorCount} 篇失败' : ''}",
    );
  }
}
