import 'package:daily_satori/app/services/db_service.dart';
import 'package:get/get.dart';

import 'package:daily_satori/app/databases/database.dart';
import 'package:daily_satori/app/services/article_service.dart';

class ArticleDetailController extends GetxController {
  AppDatabase get db => DBService.i.db;

  late Article article;

  @override
  void onInit() {
    super.onInit();
    article = Get.arguments;
  }

  Future<void> deleteArticle() async {
    await ArticleService.i.deleteArticle(article.id);
  }

  Future<List<ArticleImage>> getArticleImages() async {
    final images = await (db.select(db.articleImages)..where((row) => row.article.equals(article.id))).get();
    return images;
  }

  Future<List<ArticleScreenshoot>> getArticleScreenshoots() async {
    final screenshoots =
        await (db.select(db.articleScreenshoots)..where((row) => row.article.equals(article.id))).get();
    return screenshoots;
  }
}
