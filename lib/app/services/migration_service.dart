import 'dart:io';

import 'package:daily_satori/app/models/models.dart';
import 'package:daily_satori/app/objectbox/article.dart';
import 'package:daily_satori/app/objectbox/image.dart' as db_image;
import 'package:daily_satori/app/objectbox/screenshot.dart';
import 'package:daily_satori/app/repositories/ai_config_repository.dart';
import 'package:daily_satori/app/repositories/article_repository.dart';
import 'package:daily_satori/app/repositories/setting_repository.dart';
import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/services/objectbox_service.dart';
import 'package:daily_satori/app/services/setting_service/setting_service.dart';
import 'package:flutter_settings_screens/flutter_settings_screens.dart';
import 'package:daily_satori/objectbox.g.dart';

/// 迁移服务，用于处理数据模型和文件存储的迁移工作
///
/// 负责处理不同版本之间的数据结构变更、存储模式变更等迁移工作
class MigrationService {
  // 单例模式
  MigrationService._();
  static final MigrationService _instance = MigrationService._();
  static MigrationService get i => _instance;

  /// 初始化服务
  Future<void> init() async {
    logger.i("🔄 [迁移服务] 初始化");

    try {
      // 1. 迁移AI配置数据
      await _migrateAIConfig();

      // 2. 迁移图片相关数据（如果需要）
      if (await _needImageMigration()) {
        await _migrateImages();
      }
    } catch (e, stackTrace) {
      logger.e("❌ [迁移服务] 初始化失败: $e", stackTrace: stackTrace);
    }
  }

  //====================
  // AI配置迁移
  //====================

  /// 迁移AI配置数据从Settings到AI配置管理
  Future<void> _migrateAIConfig() async {
    logger.i("🔄 [迁移服务] 开始AI配置迁移");

    try {
      // 获取现有配置数据
      final oldConfig = _getOldAIConfig();

      // 检查是否需要迁移
      if (!_isAIConfigMigrationNeeded(oldConfig)) {
        logger.i("✅ [迁移服务] AI配置无需迁移");
        return;
      }

      // 获取或创建通用配置
      final generalConfig = await _getOrCreateGeneralConfig();
      if (generalConfig == null) {
        return;
      }

      // 更新配置
      _updateGeneralConfig(generalConfig, oldConfig);

      // 清除旧配置
      _clearOldAIConfig();

      logger.i("✅ [迁移服务] AI配置迁移完成");
    } catch (e, stackTrace) {
      logger.e("❌ [迁移服务] AI配置迁移失败: $e", stackTrace: stackTrace);
    }
  }

  /// 获取旧的AI配置数据
  Map<String, String> _getOldAIConfig() {
    return {
      'apiToken': Settings.getValue<String>(SettingService.openAITokenKey) ?? '',
      'apiAddress':
          Settings.getValue<String>(SettingService.openAIAddressKey) ??
          SettingService.defaultSettings[SettingService.openAIAddressKey] ??
          '',
      'modelName':
          Settings.getValue<String>(SettingService.aiModelKey) ??
          SettingService.defaultSettings[SettingService.aiModelKey] ??
          '',
    };
  }

  /// 判断是否需要进行AI配置迁移
  bool _isAIConfigMigrationNeeded(Map<String, String> oldConfig) {
    final apiToken = oldConfig['apiToken'] ?? '';
    final apiAddress = oldConfig['apiAddress'] ?? '';
    final modelName = oldConfig['modelName'] ?? '';

    // 如果所有设置都为空或为默认值，则不需要迁移
    return !(apiToken.isEmpty &&
        (apiAddress.isEmpty || apiAddress == SettingService.defaultSettings[SettingService.openAIAddressKey]) &&
        (modelName.isEmpty || modelName == SettingService.defaultSettings[SettingService.aiModelKey]));
  }

  /// 获取或创建通用AI配置
  Future<AIConfigModel?> _getOrCreateGeneralConfig() async {
    // 获取通用配置
    AIConfigModel? generalConfig = AIConfigRepository.getGeneralConfig();

    // 如果不存在，则创建默认配置
    if (generalConfig == null) {
      logger.i("🔄 [迁移服务] 创建默认AI配置");
      AIConfigRepository.initDefaultConfigs();
      generalConfig = AIConfigRepository.getGeneralConfig();

      if (generalConfig == null) {
        logger.e("❌ [迁移服务] 创建通用配置失败");
        return null;
      }
    }

    return generalConfig;
  }

  /// 使用旧配置更新通用配置
  void _updateGeneralConfig(AIConfigModel generalConfig, Map<String, String> oldConfig) {
    logger.i("🔄 [迁移服务] 更新通用AI配置");

    final apiToken = oldConfig['apiToken'] ?? '';
    final apiAddress = oldConfig['apiAddress'] ?? '';
    final modelName = oldConfig['modelName'] ?? '';

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
  }

  /// 清除旧的AI配置数据
  void _clearOldAIConfig() {
    logger.i("🔄 [迁移服务] 清除旧AI配置数据");
    SettingRepository.removeSetting(SettingService.openAITokenKey);
    SettingRepository.removeSetting(SettingService.openAIAddressKey);
    SettingRepository.removeSetting(SettingService.aiModelKey);
  }

  //====================
  // 图片迁移
  //====================

  /// 检查是否需要进行图片迁移
  Future<bool> _needImageMigration() async {
    // 获取图片和截图数据库
    final imageBox = ObjectboxService.i.box<db_image.Image>();
    final screenshotBox = ObjectboxService.i.box<Screenshot>();

    // 如果图片或截图数据库不为空，则需要迁移
    final needMigration = imageBox.count() > 0 || screenshotBox.count() > 0;

    if (needMigration) {
      logger.i("🔄 [迁移服务] 检测到需要迁移图片数据");
    } else {
      logger.i("✅ [迁移服务] 无需图片数据迁移");
    }

    return needMigration;
  }

  /// 执行图片迁移流程
  Future<void> _migrateImages() async {
    try {
      // 1. 迁移封面图
      await _migrateArticleCoverImages();

      // 2. 清理多余的图片数据
      await _clearImageData();

      logger.i("✅ [迁移服务] 图片数据迁移完成");
    } catch (e, stackTrace) {
      logger.e("❌ [迁移服务] 图片数据迁移失败: $e", stackTrace: stackTrace);
    }
  }

  /// 迁移文章封面图到新的coverImage属性
  Future<void> _migrateArticleCoverImages() async {
    logger.i("🔄 [迁移服务] 开始迁移文章封面图");

    // 获取所有文章
    final articles = ArticleRepository.getAll();
    logger.i("📊 [迁移服务] 找到 ${articles.length} 篇文章需要处理");

    // 迁移计数器
    final counter = _MigrationCounter();

    // 处理每篇文章
    for (final article in articles) {
      try {
        await _processSingleArticle(article, counter);
      } catch (e) {
        counter.errorCount++;
        logger.e("❌ [迁移服务] 处理文章ID:${article.id}失败: $e");
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
  Future<void> _processSingleArticle(ArticleModel article, _MigrationCounter counter) async {
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

    // 删除文章相关的截图
    for (final screenshot in article.screenshots) {
      await _safeDeleteFile(screenshot.path);
    }
  }

  /// 安全删除文件（检查是否被用作封面图）
  Future<void> _safeDeleteFile(String? filePath) async {
    if (filePath == null || filePath.isEmpty) return;

    // 检查是否为封面图
    if (await _isUsedAsCoverImage(filePath)) {
      logger.i("ℹ️ [迁移服务] 文件被用作封面图，跳过删除: $filePath");
      return;
    }

    // 删除文件
    try {
      final file = File(filePath);
      if (await file.exists()) {
        await file.delete();
      }
    } catch (e) {
      logger.w("⚠️ [迁移服务] 删除文件失败: $filePath, 错误: $e");
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
    logger.i("🔄 [迁移服务] 清理图片数据");

    final imageBox = ObjectboxService.i.box<db_image.Image>();
    final screenshotBox = ObjectboxService.i.box<Screenshot>();

    final imageCount = imageBox.count();
    final screenshotCount = screenshotBox.count();

    imageBox.removeAll();
    screenshotBox.removeAll();

    logger.i("✅ [迁移服务] 已清理 $imageCount 张图片和 $screenshotCount 张截图数据");
  }

  /// 输出迁移进度日志
  void _logMigrationProgress(_MigrationCounter counter, {bool isFinal = false}) {
    final status = isFinal ? "完成" : "进度";
    logger.i(
      "📊 [迁移服务] 封面迁移$status: ${counter.migratedCount} 篇成功, ${counter.noImageCount} 篇无图, ${counter.skippedCount} 篇跳过${counter.errorCount > 0 ? ', ${counter.errorCount} 篇失败' : ''}",
    );
  }
}

/// 迁移计数器，用于统计迁移进度
class _MigrationCounter {
  int migratedCount = 0; // 成功迁移数量
  int errorCount = 0; // 错误数量
  int noImageCount = 0; // 无图片数量
  int skippedCount = 0; // 跳过数量

  // 计算总处理数量
  int get totalProcessed => migratedCount + errorCount + noImageCount + skippedCount;
}
