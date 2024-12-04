part of 'articles_controller.dart';

extension PartUpdateList on ArticlesController {
  /// 从列表中移除指定ID的文章
  void removeArticleByIdFromList(int id) {
    articles.removeWhere((article) => article.id == id);
  }

  /// 从本地数据库更新列表中的文章
  void updateArticleInList(int articleID) {
    final updatedArticle = articleBox.get(articleID);
    if (updatedArticle == null) return;

    _updateArticleIfExists(updatedArticle);
  }

  /// 更新列表中已存在的文章
  void _updateArticleIfExists(Article article) {
    final index = articles.indexWhere((item) => item.id == article.id);

    if (index != -1) {
      articles[index] = article;
      logger.i("文章已更新: ${article.title}");
    } else {
      logger.i("未找到要更新的文章: ${article.id}");
    }
  }
}
