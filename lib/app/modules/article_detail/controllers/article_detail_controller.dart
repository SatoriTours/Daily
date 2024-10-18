import 'package:daily_satori/app/services/article_service.dart';
import 'package:get/get.dart';

class ArticleDetailController extends GetxController {
  late Article article;

  Future<void> deleteArticle() async {
    await ArticleService.instance.deleteArticle(article.id!);
  }
}
