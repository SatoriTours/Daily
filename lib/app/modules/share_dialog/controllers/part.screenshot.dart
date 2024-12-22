part of 'share_dialog_controller.dart';

extension PartScreenshot on ShareDialogController {
  Future<void> _saveScreenshots(Article article, List<String> screenshotPaths) async {
    logger.i("[ShareDialogController] 开始保存截图: ${screenshotPaths.length}");

    article.screenshots.removeWhere((screenshot) => true);
    articleBox.put(article);

    // 保存新的截图
    for (var imagePath in screenshotPaths) {
      final screenshot = Screenshot(path: imagePath);
      screenshot.article.target = article;
      screenshotBox.put(screenshot);
    }

    logger.i("网页截图保存完成 ${article.id}");
  }
}
