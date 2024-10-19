import 'package:get/get.dart';

import 'package:daily_satori/app/models/article.dart';
import 'package:daily_satori/app/services/article_service.dart';
import 'package:daily_satori/global.dart';

class ArticleDetailController extends GetxController {
  late Article article;

  Future<void> deleteArticle() async {
    await ArticleService.instance.deleteArticle(article.id!);
  }

  Future<void> refreshArticle() async {
    logger.i("刷新文章信息");
    article =
        await ArticleService.instance.getArticleById(article.id!) ?? article;
  }
}
