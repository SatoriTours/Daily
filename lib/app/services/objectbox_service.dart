import 'package:flutter/material.dart' as material;

import 'package:get/get.dart';
import 'package:path/path.dart' as path;
import 'package:path_provider/path_provider.dart';
import 'package:sqflite/sqflite.dart';

import 'package:daily_satori/app/modules/articles/controllers/articles_controller.dart';
import 'package:daily_satori/app/objectbox/article.dart';
import 'package:daily_satori/app/objectbox/image.dart';
import 'package:daily_satori/app/objectbox/screenshot.dart';
import 'package:daily_satori/app/objectbox/setting.dart';
import 'package:daily_satori/app/objectbox/tag.dart';
import 'package:daily_satori/app/routes/app_pages.dart';
import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/global.dart';
import 'package:daily_satori/objectbox.g.dart';

class ObjectboxService {
  // 单例模式
  ObjectboxService._();
  static final ObjectboxService _instance = ObjectboxService._();
  static ObjectboxService get i => _instance;

  // 数据库目录名
  static const dbDir = 'obx-daily';

  // ObjectBox 存储实例
  late final Store _store;
  Store get store => _store;

  // ObjectBox Admin 实例
  late Admin _admin;

  // 获取指定类型的 Box
  Box<T> box<T>() => _store.box<T>();

  /// 初始化 ObjectBox 服务
  Future<void> init() async {
    logger.i("[初始化服务] ObjectboxService");
    final docsDir = await getApplicationDocumentsDirectory();
    _store = await openStore(directory: path.join(docsDir.path, dbDir));

    // 开发环境启用 Admin
    if (!isProduction && Admin.isAvailable()) {
      _admin = Admin(store, bindUri: 'http://0.0.0.0:9000');
    }
  }

  /// 释放资源
  void dispose() {
    _store.close();
    _admin.close();
  }

  /// 清空所有数据
  void _clear() {
    _store.box<Article>().removeAll();
    _store.box<Tag>().removeAll();
    _store.box<Image>().removeAll();
    _store.box<Screenshot>().removeAll();
  }

  /// 检查并执行 SQLite 数据迁移
  Future<void> checkAndMigrateFromSQLite() async {
    if (shouldMigrateFromSQLite()) {
      showFullScreenLoading(tips: '数据迁移中', barrierColor: material.Colors.black);
      await migrateFromSQLite();
      Get.close();

      // 如果在文章页面,刷新列表
      if (Get.currentRoute == Routes.ARTICLES) {
        Get.find<ArticlesController>().reloadArticles();
      }
    }
  }

  /// 检查是否需要迁移数据
  bool shouldMigrateFromSQLite() {
    logger.i("[检查迁移] 检查是否需要从SQLite迁移数据");
    return _store.box<Article>().count() == 0;
  }

  /// 从 SQLite 迁移数据到 ObjectBox
  Future<void> migrateFromSQLite() async {
    logger.i("[数据迁移] 开始从SQLite迁移数据到ObjectBox");

    // 打开 SQLite 数据库
    final docsDir = await getApplicationDocumentsDirectory();
    final sqliteDB = await openDatabase(path.join(docsDir.path, 'daily_satori.db.sqlite'));

    // 获取所有 Box 实例
    final articleBox = box<Article>();
    final tagBox = box<Tag>();
    final imageBox = box<Image>();
    final screenshotBox = box<Screenshot>();
    final settingBox = box<Setting>();

    // 迁移文章数据
    final articles = await sqliteDB.query('articles');
    logger.i("开始导入 ${articles.length} 篇文章");

    for (final article in articles) {
      // 创建文章实体
      final newArticle = _createArticleFromSQLite(article);
      final articleId = articleBox.put(newArticle);

      // 迁移标签
      await _migrateArticleTags(sqliteDB, articleId, newArticle, tagBox);

      // 保存更新后的文章
      articleBox.put(newArticle);

      // 迁移图片
      await _migrateArticleImages(sqliteDB, article, newArticle, imageBox);

      // 迁移截图
      await _migrateArticleScreenshots(sqliteDB, articleId, newArticle, screenshotBox);
    }

    // 迁移设置
    await _migrateSettings(sqliteDB, settingBox);

    logger.i("[数据迁移] 从SQLite迁移数据到ObjectBox完成");
  }

  /// 从 SQLite 记录创建文章实体
  Article _createArticleFromSQLite(Map<String, dynamic> data) {
    return Article(
      title: data['title'] as String?,
      aiTitle: data['ai_title'] as String?,
      content: data['content'] as String?,
      aiContent: data['ai_content'] as String?,
      htmlContent: data['html_content'] as String?,
      url: data['url'] as String?,
      isFavorite: (data['is_favorite'] as int?) == 1,
      comment: data['comment'] as String?,
      pubDate: _convertTimestamp(data['pub_date']),
      updatedAt: _convertTimestamp(data['updated_at']),
      createdAt: _convertTimestamp(data['created_at']),
    );
  }

  /// 转换时间戳
  DateTime? _convertTimestamp(dynamic timestamp) {
    return timestamp != null ? DateTime.fromMillisecondsSinceEpoch(timestamp * 1000) : null;
  }

  /// 迁移文章标签
  Future<void> _migrateArticleTags(
    Database sqliteDB,
    int articleId,
    Article article,
    Box<Tag> tagBox,
  ) async {
    final tags = await sqliteDB.query(
      'article_tags',
      where: 'article_id = ?',
      whereArgs: [articleId],
    );

    for (final tagRelation in tags) {
      final tag = await sqliteDB.query(
        'tags',
        where: 'id = ?',
        whereArgs: [tagRelation['tag_id']],
      );

      final tagName = tag.first['title'] as String? ?? '';
      if (tagName.isEmpty) continue;

      final existingTag = tagBox.query(Tag_.name.equals(tagName)).build().findFirst();
      if (existingTag != null) {
        article.tags.add(existingTag);
      } else {
        final newTag = Tag(name: tagName);
        tagBox.put(newTag);
        article.tags.add(newTag);
      }
    }
  }

  /// 迁移文章图片
  Future<void> _migrateArticleImages(
    Database sqliteDB,
    Map<String, dynamic> articleData,
    Article article,
    Box<Image> imageBox,
  ) async {
    // 迁移主图片
    final imageUrl = articleData['image_url'] as String? ?? '';
    final imagePath = articleData['image_path'] as String? ?? '';
    if (imageUrl.isNotEmpty && imagePath.isNotEmpty) {
      _createAndSaveImage(imageUrl, imagePath, article, imageBox);
    }

    // 迁移其他图片
    final images = await sqliteDB.query(
      'article_images',
      where: 'article = ?',
      whereArgs: [articleData['id']],
    );

    for (final img in images) {
      _createAndSaveImage(
        img['image_url'] as String?,
        img['image_path'] as String?,
        article,
        imageBox,
      );
    }
  }

  /// 创建并保存图片
  void _createAndSaveImage(
    String? url,
    String? path,
    Article article,
    Box<Image> imageBox,
  ) {
    final newImage = Image(url: url, path: path);
    newImage.article.target = article;
    imageBox.put(newImage);
  }

  /// 迁移文章截图
  Future<void> _migrateArticleScreenshots(
    Database sqliteDB,
    int articleId,
    Article article,
    Box<Screenshot> screenshotBox,
  ) async {
    final screenshots = await sqliteDB.query(
      'article_screenshots',
      where: 'article = ?',
      whereArgs: [articleId],
    );

    for (final screenshot in screenshots) {
      final newScreenshot = Screenshot(path: screenshot['image_path'] as String?);
      newScreenshot.article.target = article;
      screenshotBox.put(newScreenshot);
    }
  }

  /// 迁移设置
  Future<void> _migrateSettings(Database sqliteDB, Box<Setting> settingBox) async {
    final settings = await sqliteDB.query('settings');
    logger.i("开始导入 ${settings.length} 条设置");

    for (final setting in settings) {
      final newSetting = Setting(
        key: setting['key'] as String?,
        value: setting['value'] as String?,
      );

      final existingSetting = settingBox.query(Setting_.key.equals(newSetting.key ?? '')).build().findFirst();

      if (existingSetting == null) {
        settingBox.put(newSetting);
      }
    }
  }
}
