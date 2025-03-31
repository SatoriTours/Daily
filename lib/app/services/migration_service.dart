import 'dart:io';

import 'package:daily_satori/app/models/models.dart';
import 'package:daily_satori/app/objectbox/article.dart';
import 'package:daily_satori/app/objectbox/image.dart' as db_image;
import 'package:daily_satori/app/objectbox/screenshot.dart';
import 'package:daily_satori/app/repositories/ai_config_repository.dart';
import 'package:daily_satori/app/repositories/article_repository.dart';
import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/services/objectbox_service.dart';
import 'package:daily_satori/app/services/setting_service/setting_service.dart';
import 'package:flutter_settings_screens/flutter_settings_screens.dart';
import 'package:daily_satori/objectbox.g.dart';

/// 迁移服务，用于处理数据模型和文件存储的迁移工作
class MigrationService {
  // 单例模式
  MigrationService._();
  static final MigrationService _instance = MigrationService._();
  static MigrationService get i => _instance;

  /// 初始化服务
  Future<void> init() async {
    logger.i("[初始化服务] MigrationService");

    // 迁移AI配置数据
    await migrateAIConfigFromSettings();

    // 检查是否需要迁移
    if (await _needMigration()) {
      logger.i("检测到需要迁移数据");
      await migrateArticleCoverImages();
      await clearAllImagesAndScreenshots();
    } else {
      logger.i("数据已经迁移过，无需再次迁移");
    }
  }

  /// 迁移AI配置数据从Settings到AI配置管理
  Future<void> migrateAIConfigFromSettings() async {
    logger.i("[AI配置迁移] 开始从Settings迁移AI配置数据");

    try {
      // 检查设置中是否存在AI配置数据
      final apiToken = Settings.getValue<String>(SettingService.openAITokenKey) ?? '';
      final apiAddress =
          Settings.getValue<String>(SettingService.openAIAddressKey) ??
          SettingService.defaultSettings[SettingService.openAIAddressKey] ??
          '';
      final modelName =
          Settings.getValue<String>(SettingService.aiModelKey) ??
          SettingService.defaultSettings[SettingService.aiModelKey] ??
          '';

      // 如果所有设置都为空，则不需要迁移
      if ((apiToken.isEmpty) &&
          (apiAddress.isEmpty || apiAddress == SettingService.defaultSettings[SettingService.openAIAddressKey]) &&
          (modelName.isEmpty || modelName == SettingService.defaultSettings[SettingService.aiModelKey])) {
        logger.i("[AI配置迁移] 无需迁移AI配置数据，设置为空或默认值");
        return;
      }

      // 获取或创建通用配置
      AIConfigModel? generalConfig = AIConfigRepository.getGeneralConfig();
      if (generalConfig == null) {
        // 如果不存在通用配置，创建一个
        logger.i("[AI配置迁移] 创建通用配置");
        AIConfigRepository.initDefaultConfigs();
        generalConfig = AIConfigRepository.getGeneralConfig();

        if (generalConfig == null) {
          logger.e("[AI配置迁移] 创建通用配置失败");
          return;
        }
      }

      // 更新通用配置
      logger.i("[AI配置迁移] 更新通用配置");

      // 只更新非空/非默认值
      if (apiToken.isNotEmpty) {
        generalConfig.apiToken = apiToken;
      }

      if (apiAddress.isNotEmpty && apiAddress != SettingService.defaultSettings[SettingService.openAIAddressKey]) {
        generalConfig.apiAddress = apiAddress;
      }

      if (modelName.isNotEmpty && modelName != SettingService.defaultSettings[SettingService.aiModelKey]) {
        generalConfig.modelName = modelName;
      }

      // 保存更新后的配置
      AIConfigRepository.updateAIConfig(generalConfig);

      // 清除Settings中的AI配置数据
      logger.i("[AI配置迁移] 清除Settings中的AI配置数据");
      Settings.setValue<String>(SettingService.openAITokenKey, '');

      // 不清除这两个，保留作为默认值参考
      // Settings.setValue<String>(SettingService.openAIAddressKey, '');
      // Settings.setValue<String>(SettingService.aiModelKey, '');

      logger.i("[AI配置迁移] AI配置数据迁移完成");
    } catch (e, stackTrace) {
      logger.e("[AI配置迁移] 迁移AI配置数据失败: $e", stackTrace: stackTrace);
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

    // 检查文件是否被用作文章封面图，如果是则不删除
    final articleBox = ObjectboxService.i.box<Article>();
    final query = articleBox.query(Article_.coverImage.equals(filePath)).build();
    try {
      final count = query.count();
      if (count > 0) {
        logger.i("文件被用作封面图，跳过删除: $filePath");
        return;
      }
    } finally {
      query.close();
    }

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
