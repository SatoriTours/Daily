part of 'share_dialog_controller.dart';

extension PartImages on ShareDialogController {
  Future<void> _saveImages(Article? article, List<String> imageUrls) async {
    if (article == null || imageUrls.isEmpty) return;
    await db.articleImages.deleteWhere((tbl) => tbl.article.equals(article.id));
    await Future.wait(imageUrls.map((imageUrl) async {
      await _downloadAndSaveImage(article.id, imageUrl);
    }));
    logger.i("网页相关图片保存完成 ${article.id}");
  }

  Future<void> _downloadAndSaveImage(int articleID, String url) async {
    try {
      // 下载图片
      String imagePath = await _imageDownTask(url);

      // 更新数据库
      await db.into(db.articleImages).insert(ArticleImagesCompanion(
            article: drift.Value(articleID),
            imageUrl: drift.Value(url),
            imagePath: drift.Value(imagePath),
          ));
      logger.i("图片下载并保存到数据库: $url => imagePath");
    } catch (e) {
      logger.e("下载或保存图片失败: $url, 错误: $e");
    }
  }

  Future<String> _imageDownTask(String imageUrl) async {
    return await HttpService.i.downloadImage(imageUrl);
  }
}
