import 'package:sqflite/sqflite.dart';

import 'package:daily_satori/app/models/article.dart';
import 'package:daily_satori/app/services/database_service.dart';
import 'package:daily_satori/global.dart';

class ArticleService {
  ArticleService._privateConstructor();
  static final ArticleService _instance = ArticleService._privateConstructor();
  static ArticleService get instance => _instance;

  Future<void> init() async {}

  final String _tableName = 'articles';

  Future<void> saveArticle(Map<String, dynamic> articleData) async {
    if (await articleExists(articleData['url'])) {
      logger.i("文章已存在: ${firstLine(articleData['title'])}");
      return; // 如果记录已存在，则不进行插入
    }

    await db.insert(_tableName, articleData);
    logger.i("文章已保存: ${firstLine(articleData['title'])}");
  }

  Future<void> updateArticle(
      String url, Map<String, dynamic> newArticleData) async {
    final result = await db.update(
      _tableName,
      newArticleData,
      where: 'url = ?',
      whereArgs: [url],
    );

    if (result > 0) {
      logger.i("文章已更新: ${firstLine(newArticleData['title'])}");
    } else {
      logger.i("未找到文章以更新: $url");
    }
  }

  Future<bool> articleExists(String url) async {
    final existingArticle = await db.query(
      _tableName,
      where: 'url = ?',
      whereArgs: [url],
    );
    return existingArticle.isNotEmpty;
  }

  Future<void> deleteArticle(int articleID) async {
    final result = await db.delete(
      _tableName,
      where: 'id = ?',
      whereArgs: [articleID],
    );

    if (result > 0) {
      logger.i("文章已删除: $articleID");
    } else {
      logger.i("未找到文章以删除: $articleID");
    }
  }

  Future<Article?> getArticleById(int articleID) async {
    final List<Map<String, dynamic>> maps = await db.query(
      _tableName,
      where: 'id = ?',
      whereArgs: [articleID],
    );

    if (maps.isNotEmpty) {
      return Article.fromMap(maps.first);
    } else {
      return null; // 返回 null 表示未找到文章
    }
  }

  Future<bool> toggleFavorite(int articleID) async {
    final article = await getArticleById(articleID);
    if (article != null) {
      final newFavoriteStatus = article.isFavorite == 0 ? 1 : 0; // 切换收藏状态
      final result = await db.update(
        _tableName,
        {'is_favorite': newFavoriteStatus},
        where: 'id = ?',
        whereArgs: [articleID],
      );

      if (result > 0) {
        logger.i(newFavoriteStatus == 1
            ? "文章已收藏: $articleID"
            : "文章已取消收藏: $articleID");
        return newFavoriteStatus == 1; // 返回是否收藏
      } else {
        logger.i("未找到文章以更新收藏状态: $articleID");
      }
    }
    return false; // 如果文章不存在，返回未收藏
  }

  Database get db => DatabaseService.instance.database;
}
