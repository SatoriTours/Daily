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

  ArticlesCompanion _createArticleMap(
      {required String url,
      required String title,
      required String aiTitle,
      required String textContent,
      required String aiContent,
      required String htmlContent,
      required String imageUrl,
      required String imagePath,
      // required String screenshotPath,
      required String publishedTime}) {
    return ArticlesCompanion(
      title: drift.Value(title),
      aiTitle: drift.Value(aiTitle),
      content: drift.Value(textContent),
      aiContent: drift.Value(aiContent),
      htmlContent: drift.Value(htmlContent),
      url: drift.Value(url),
      imageUrl: drift.Value(imageUrl),
      imagePath: drift.Value(imagePath),
      screenshotPath: drift.Value(''), // 清空screenshotPath, 使用新的表存储的多张图
      pubDate: drift.Value(DateTime.tryParse(publishedTime)?.toUtc() ?? DateTime.now().toUtc()),
      comment: drift.Value(commentController.text),
    );
  }

  Future<Article?> _saveOrUpdateArticle(String url, ArticlesCompanion article) async {
    final Article? newArticle;
    if (isUpdate) {
      logger.i("[更新文章] aiTitle => ${article.aiTitle}, imagePath => ${article.imagePath}");
      newArticle = await ArticleService.i.updateArticle(articleID, article);
      if (newArticle != null) {
        Get.find<ArticlesController>().updateArticleInList(newArticle);
      }
    } else {
      logger.i("[新增文章] aiTitle => ${article.aiTitle}, imagePath => ${article.imagePath}");
      newArticle = await ArticleService.i.saveArticle(article);
    }
    return newArticle;
  }
}
