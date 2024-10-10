import 'package:daily_satori/app/services/article_service.dart';
import 'package:get/get.dart';

class ArticlesController extends GetxController {
  final List<Article> articles = <Article>[].obs;

  Future<void> loadArticles() async {
    final articleDataList = await ArticleService.instance.db.query('articles');
    articles.assignAll(articleDataList.map((data) => Article.fromMap(data)).toList());
  }
}
