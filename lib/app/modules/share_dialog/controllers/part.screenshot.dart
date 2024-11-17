part of 'share_dialog_controller.dart';

extension PartScreenshot on ShareDialogController {
  Future<void> _saveScreenshots(Article? article, List<String> screenshotPaths) async {
    logger.i("[ShareDialogController] 开始保存截图: ${screenshotPaths.length}");
    if (article == null || screenshotPaths.isEmpty) return;

    await db.articleScreenshots.deleteWhere((tbl) => tbl.article.equals(article.id));
    await Future.wait(screenshotPaths.map((imagePath) async {
      await db.into(db.articleScreenshots).insert(ArticleScreenshotsCompanion(
            article: drift.Value(article.id),
            imagePath: drift.Value(imagePath),
          ));
    }));
    logger.i("网页截图保存完成 ${article.id}");
  }
}
