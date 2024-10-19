import 'package:get/get.dart';

import 'package:daily_satori/app/databases/database.dart';
import 'package:daily_satori/app/services/article_service.dart';
import 'package:daily_satori/global.dart';

class ArticleDetailController extends GetxController {
  late Article article;

  Future<void> deleteArticle() async {
    await ArticleService.i.deleteArticle(article.id);
  }

  Future<void> refreshArticle() async {
    logger.i("刷新文章信息");
    article = await ArticleService.i.getArticleById(article.id) ?? article;
  }
}
