part of 'articles_controller.dart';

extension PartUpdateList on ArticlesController {
  void removeArticleByIdFromList(int id) {
    articles.removeWhere((article) => article.id == id);
  }

  void updateArticleInList(int articleID) {
    final updatedArticle = articleBox.get(articleID);
    if (updatedArticle == null) return;
    int index = articles.indexWhere((article) => article.id == updatedArticle.id);
    if (index != -1) {
      articles[index] = updatedArticle; // 更新文章
      logger.i("文章已更新: ${updatedArticle.title}");
    } else {
      logger.i("未找到要更新的文章: ${updatedArticle.id}");
    }
  }

  Future<void> updateArticleInListFromDB(int articleID) async {
    int index = articles.indexWhere((article) => article.id == articleID);
    if (index != -1) {
      final newArticle = await ArticleService.i.getArticleById(articleID);
      if (newArticle != null) {
        articles[index] = newArticle;
        logger.i("文章已更新: $articleID");
      }
    } else {
      logger.i("未找到要更新的文章: $articleID");
    }
  }
}
