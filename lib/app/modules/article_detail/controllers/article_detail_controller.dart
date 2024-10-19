import 'package:daily_satori/app/services/db_service.dart';
import 'package:get/get.dart';

import 'package:daily_satori/app/databases/database.dart';
import 'package:daily_satori/app/services/article_service.dart';
import 'package:daily_satori/global.dart';

class ArticleDetailController extends GetxController {
  AppDatabase get db => DBService.i.db;

  late Article article;

  Future<void> deleteArticle() async {
    await ArticleService.i.deleteArticle(article.id);
  }

  Future<void> refreshArticle() async {
    logger.i("刷新文章信息");
    article = await ArticleService.i.getArticleById(article.id) ?? article;
  }

  Future<List<ArticleImage>> getArticleImages() async {
    final images = await (db.select(db.articleImages)..where((row) => row.article.equals(article.id))).get();
    return images;
  }
}
