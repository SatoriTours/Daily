part of 'share_dialog_controller.dart';

extension PartScreenshot on ShareDialogController {
  Future<void> _saveScreenshots(Article? article, List<String> screenshotPaths) async {
    logger.i("[ShareDialogController] 开始保存截图: ${screenshotPaths.length}");
    if (article == null || screenshotPaths.isEmpty) return;

    article.screenshots.removeWhere((screenshot) => true);

    // 保存新的截图
    for (var imagePath in screenshotPaths) {
      final screenshot = Screenshot(path: imagePath)..article.target = article;
      ObjectboxService.i.box<Screenshot>().put(screenshot);
    }

    logger.i("网页截图保存完成 ${article.id}");
  }
}
