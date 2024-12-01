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
  ObjectboxService._privateConstructor();
  static final ObjectboxService _instance =
      ObjectboxService._privateConstructor();
  static ObjectboxService get i => _instance;

  static const dbDir = 'obx-daily';

  late final Store _store;
  Store get store => _store;

  late Admin _admin;

  Box<T> box<T>() => _store.box<T>();

  Future<void> init() async {
    logger.i("[初始化服务] ObjectboxService");
    final docsDir = await getApplicationDocumentsDirectory();
    _store = await openStore(directory: path.join(docsDir.path, dbDir));
    // if (!isProduction) _clear();
    if (Admin.isAvailable() && !isProduction) {
      // Keep a reference until no longer needed or manually closed.
      _admin = Admin(store, bindUri: 'http://0.0.0.0:9000');
    }
  }

  void dispose() {
    _store.close();
    _admin.close();
  }

  void _clear() {
    _store.box<Article>().removeAll();
    _store.box<Tag>().removeAll();
    _store.box<Image>().removeAll();
    _store.box<Screenshot>().removeAll();
  }

  Future<void> checkAndMigrateFromSQLite() async {
    if (ObjectboxService.i.shouldMigrateFromSQLite()) {
      showFullScreenLoading(tips: '数据迁移中', barrierColor: material.Colors.black);
      await ObjectboxService.i.migrateFromSQLite();
      Get.close();
      if (Get.currentRoute == Routes.ARTICLES) {
        Get.find<ArticlesController>().reloadArticles();
      }
    }
  }

  bool shouldMigrateFromSQLite() {
    logger.i("[检查迁移] 检查是否需要从SQLite迁移数据");
    final articleBox = _store.box<Article>();
    return articleBox.count() == 0;
  }

  Future<void> migrateFromSQLite() async {
    logger.i("[数据迁移] 开始从SQLite迁移数据到ObjectBox");
    // if (!isProduction) await Future.delayed(Duration(seconds: 10));
    final docsDir = await getApplicationDocumentsDirectory();
    Database sqliteDB =
        await openDatabase(path.join(docsDir.path, 'daily_satori.db.sqlite'));
    final articleBox = box<Article>();
    final tagBox = box<Tag>();
    final imageBox = box<Image>();
    final screenshotBox = box<Screenshot>();
    final settingBox = box<Setting>();

    // 从SQLite获取所有文章
    final articles = await sqliteDB.query('articles');
    logger.i("开始导入 ${articles.length} 篇文章");

    // 遍历并转换每篇文章
    for (final article in articles) {
      final newArticle = Article(
        title: article['title'] as String?,
        aiTitle: article['ai_title'] as String?,
        content: article['content'] as String?,
        aiContent: article['ai_content'] as String?,
        htmlContent: article['html_content'] as String?,
        url: article['url'] as String?,
        isFavorite: (article['is_favorite'] as int?) == 1,
        comment: article['comment'] as String?,
        pubDate: article['pub_date'] != null
            ? DateTime.fromMillisecondsSinceEpoch(
                (article['pub_date'] as int) * 1000)
            : null,
        updatedAt: article['updated_at'] != null
            ? DateTime.fromMillisecondsSinceEpoch(
                (article['updated_at'] as int) * 1000)
            : null,
        createdAt: article['created_at'] != null
            ? DateTime.fromMillisecondsSinceEpoch(
                (article['created_at'] as int) * 1000)
            : null,
      );

      // 保存文章
      final articleId = articleBox.put(newArticle);

      // 处理标签
      final tags = await sqliteDB.query(
        'article_tags',
        where: 'article_id = ?',
        whereArgs: [article['id']],
      );

      for (final tagRelation in tags) {
        final tag = await sqliteDB.query(
          'tags',
          where: 'id = ?',
          whereArgs: [tagRelation['tag_id']],
        );

        final tagName = tag.first['title'] as String? ?? '';

        if (tagName.isNotEmpty) {
          // 检查标签是否已存在
          final existingTag =
              tagBox.query(Tag_.name.equals(tagName)).build().findFirst();
          if (existingTag != null) {
            newArticle.tags.add(existingTag);
          } else {
            final newTag = Tag(
              name: tagName,
            );
            tagBox.put(newTag);
            newArticle.tags.add(newTag);
          }
        }
      }

      articleBox.put(newArticle);

      // 处理图片
      final images = await sqliteDB.query(
        'article_images',
        where: 'article = ?',
        whereArgs: [article['id']],
      );

      // logger.i("开始导入 ${images.length} 张图片");

      final imageUrl = article['image_url'] as String? ?? '';
      final imagePath = article['image_path'] as String? ?? '';
      if (imageUrl.isNotEmpty && imagePath.isNotEmpty) {
        final newImage = Image(
          url: imageUrl,
          path: imagePath,
        );
        newImage.article.target = newArticle;
        imageBox.put(newImage);
      }

      for (final img in images) {
        final newImage = Image(
          url: img['image_url'] as String?,
          path: img['image_path'] as String?,
        );
        newImage.article.target = newArticle;
        imageBox.put(newImage);
      }

      // 处理截图
      final screenshots = await sqliteDB.query(
        'article_screenshots',
        where: 'article = ?',
        whereArgs: [article['id']],
      );

      // logger.i("开始导入 ${screenshots.length} 张截图");

      for (final screenshot in screenshots) {
        final newScreenshot = Screenshot(
          path: screenshot['image_path'] as String?,
        );
        newScreenshot.article.target = newArticle;
        screenshotBox.put(newScreenshot);
      }
    }

    // 处理设置
    final settings = await sqliteDB.query('settings');
    logger.i("开始导入 ${settings.length} 条设置");

    for (final setting in settings) {
      final newSetting = Setting(
        key: setting['key'] as String?,
        value: setting['value'] as String?,
      );
      logger.i('导入key ${newSetting.key}');
      final existingSetting = settingBox
          .query(Setting_.key.equals(newSetting.key ?? ''))
          .build()
          .findFirst();
      if (existingSetting == null) {
        settingBox.put(newSetting);
      }
    }

    logger.i("[数据迁移] 从SQLite迁移数据到ObjectBox完成");
  }
}
