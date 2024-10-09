import 'package:daily_satori/app/services/database_service.dart';
import 'package:daily_satori/global.dart';

class ArticleService {
  ArticleService._privateConstructor();
  static final ArticleService _instance = ArticleService._privateConstructor();
  static ArticleService get instance => _instance;

  Future<void> init() async {}

  final String _tableName = 'articles';

  Future<void> saveArticle(Map<String, dynamic> articleData) async {
    final db = DatabaseService.instance.database;
    final existingArticle = await db.query(
      _tableName,
      where: 'url = ?',
      whereArgs: [articleData['url']],
    );

    if (existingArticle.isNotEmpty) {
      logger.i("文章已存在: ${articleData['title']}");
      return; // 如果记录已存在，则不进行插入
    }

    await db.insert(_tableName, articleData);
    logger.i("文章已保存: ${articleData['title']}");
  }
}
