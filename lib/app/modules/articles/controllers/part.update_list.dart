part of 'articles_controller.dart';

extension PartUpdateList on ArticlesController {
  /// 从列表中移除指定ID的文章
  void removeArticleByIdFromList(int id) {
    articleModels.removeWhere((model) => model.id == id);
  }

  /// 从本地数据库更新列表中的文章
  void updateArticleInList(int articleID) {
    final updatedModel = ArticleModel.find(articleID);
    if (updatedModel == null) return;

    _updateArticleIfExists(updatedModel);
  }

  /// 更新列表中已存在的文章
  void _updateArticleIfExists(ArticleModel model) {
    final index = articleModels.indexWhere((item) => item.id == model.id);

    if (index != -1) {
      articleModels[index] = model;
      logger.i("文章已更新: ${model.title}");
    } else {
      logger.i("未找到要更新的文章: ${model.id}");
    }
  }
}
