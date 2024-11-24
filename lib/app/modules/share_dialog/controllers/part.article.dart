part of 'share_dialog_controller.dart';

extension PartArticle on ShareDialogController {
  Future<bool> _checkArticleExists(String url) async {
    if (!isUpdate) {
      if (await ArticleService.i.isArticleExists(url)) {
        _showSnackbar('网页已存在 $url');
        return true;
      }
    }
    if (isUpdate && articleID <= 0) {
      _showSnackbar('网页不存在 $url, 无法更新');
      return true;
    }
    return false;
  }

  Article _createArticle(
      {required String url,
      required String title,
      required String aiTitle,
      required String textContent,
      required String aiContent,
      required String htmlContent,
      required String publishedTime}) {
    return Article(
      title: title,
      aiTitle: aiTitle,
      content: textContent,
      aiContent: aiContent,
      htmlContent: htmlContent,
      url: url,
      pubDate: DateTime.tryParse(publishedTime)?.toUtc() ?? DateTime.now().toUtc(),
      createdAt: DateTime.now().toUtc(),
      updatedAt: DateTime.now().toUtc(),
      comment: commentController.text,
    );
  }

  Future<Article?> _saveOrUpdateArticle(String url, Article article) async {
    final Article? newArticle;
    if (isUpdate) {
      logger.i("[更新文章] aiTitle => ${article.aiTitle}");
      newArticle = await ArticleService.i.updateArticle(articleID, article);
      if (newArticle != null) {
        Get.find<ArticlesController>().updateArticleInList(newArticle);
      }
    } else {
      logger.i("[新增文章] aiTitle => ${article.aiTitle}");
      newArticle = await ArticleService.i.saveArticle(article);
    }
    return newArticle;
  }
}
