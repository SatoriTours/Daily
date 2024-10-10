import 'package:daily_satori/app/services/database_service.dart';
import 'package:daily_satori/global.dart';
import 'package:sqflite/sqflite.dart';

class ArticleService {
  ArticleService._privateConstructor();
  static final ArticleService _instance = ArticleService._privateConstructor();
  static ArticleService get instance => _instance;

  Future<void> init() async {}

  final String _tableName = 'articles';

  Future<void> saveArticle(Map<String, dynamic> articleData) async {
    if (await articleExists(articleData['url'])) {
      logger.i("文章已存在: ${articleData['title']}");
      return; // 如果记录已存在，则不进行插入
    }

    await db.insert(_tableName, articleData);
    logger.i("文章已保存: ${articleData['title']}");
  }

  Future<bool> articleExists(String url) async {
    final existingArticle = await db.query(
      _tableName,
      where: 'url = ?',
      whereArgs: [url],
    );
    return existingArticle.isNotEmpty;
  }

  Database get db => DatabaseService.instance.database;
}
