import 'dart:io';

import 'package:daily_satori/app/objectbox/article.dart';
import 'package:daily_satori/app/objectbox/image.dart' as db_image;
import 'package:daily_satori/app/objectbox/screenshot.dart';
import 'package:daily_satori/app/repositories/article_repository.dart';
import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/services/objectbox_service.dart';

/// 迁移服务，用于处理数据模型和文件存储的迁移工作
class MigrationService {
  // 单例模式
  MigrationService._();
  static final MigrationService _instance = MigrationService._();
  static MigrationService get i => _instance;

  /// 初始化服务
  Future<void> init() async {
    logger.i("[初始化服务] MigrationService");

    // 检查是否需要迁移
    if (await _needMigration()) {
      logger.i("检测到需要迁移数据");
      await migrateArticleCoverImages();
      await clearAllImagesAndScreenshots();
    } else {
      logger.i("数据已经迁移过，无需再次迁移");
    }
  }

  /// 检查是否需要迁移
  ///
  /// 通过检查图片和截图数据库是否为空来判断
  Future<bool> _needMigration() async {
    // 获取图片和截图数据库
    final imageBox = ObjectboxService.i.box<db_image.Image>();
    final screenshotBox = ObjectboxService.i.box<Screenshot>();

    // 如果图片或截图数据库不为空，则需要迁移
    if (imageBox.count() > 0 || screenshotBox.count() > 0) {
      return true;
    }

    return false;
  }

  /// 迁移文章封面图到新的coverImage属性
  ///
  /// 此方法只进行数据迁移，不删除任何图片文件
  Future<void> migrateArticleCoverImages() async {
    logger.i("开始迁移文章封面图属性");

    // 获取所有文章
    final articles = ArticleRepository.getAll();
    logger.i("总共找到 ${articles.length} 篇文章需要迁移封面");

    int migratedCount = 0;
    int errorCount = 0;
    int noImageCount = 0;
    int skippedCount = 0;

    for (final article in articles) {
      try {
        final entity = article.entity;

        // 如果已经有封面图，则跳过
        if (entity.coverImage != null && entity.coverImage!.isNotEmpty) {
          skippedCount++;
          continue;
        }

        final allImages = entity.images.toList();

        if (allImages.isNotEmpty) {
          // 将第一张图片路径设置为封面图属性
          entity.coverImage = allImages.first.path;
          entity.coverImageUrl = allImages.first.url;
          // 保存更新后的文章
          ObjectboxService.i.box<Article>().put(entity);
          migratedCount++;
        } else {
          noImageCount++;
        }

        // 删除除了第一张之外的图片
        for (final image in allImages.sublist(1)) {
          await _deleteImageFile(image.path);
        }

        // 删除文章相关的截图
        for (final screenshot in entity.screenshots) {
          await _deleteImageFile(screenshot.path);
        }

        // 每处理20篇文章打印一次进度
        if ((migratedCount + errorCount + noImageCount + skippedCount) % 20 == 0) {
          logger.i("封面迁移进度: $migratedCount 篇成功, $noImageCount 篇无图, $skippedCount 篇跳过");
        }
      } catch (e) {
        errorCount++;
        logger.e("迁移文章封面 ID:${article.id}失败: $e");
      }
    }

    logger.i("文章封面迁移完成: 成功=$migratedCount, 无图片=$noImageCount, 跳过=$skippedCount, 失败=$errorCount");
  }

  /// 删除图片文件
  Future<void> _deleteImageFile(String? filePath) async {
    if (filePath == null || filePath.isEmpty) return;

    try {
      final file = File(filePath);
      if (await file.exists()) {
        await file.delete();
      }
    } catch (e) {
      logger.w("删除文件失败: $filePath, 错误: $e");
    }
  }

  /// 清空所有图片和截图
  Future<void> clearAllImagesAndScreenshots() async {
    final imageBox = ObjectboxService.i.box<db_image.Image>();
    final screenshotBox = ObjectboxService.i.box<Screenshot>();

    imageBox.removeAll();
    screenshotBox.removeAll();
  }
}
