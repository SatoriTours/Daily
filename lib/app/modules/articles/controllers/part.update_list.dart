part of 'articles_controller.dart';

extension PartUpdateList on ArticlesController {
  /// 从列表中移除指定ID的文章
  void removeArticleByIdFromList(int id) {
    articles.removeWhere((articleModel) => articleModel.id == id);
  }

  /// 从本地数据库更新列表中的文章
  void updateArticleInList(int articleID) {
    final updatedArticleModel = ArticleRepository.find(articleID);
    if (updatedArticleModel == null) return;

    _updateArticleIfExists(updatedArticleModel);
  }

  /// 更新列表中已存在的文章
  void _updateArticleIfExists(ArticleModel updatedArticleModel) {
    final index = articles.indexWhere((articleModel) => articleModel.id == updatedArticleModel.id);

    if (index != -1) {
      articles[index] = updatedArticleModel;
      logger.i("文章已更新: ${updatedArticleModel.title}");
    } else {
      logger.i("未找到要更新的文章: ${updatedArticleModel.id}");
    }
  }
}
