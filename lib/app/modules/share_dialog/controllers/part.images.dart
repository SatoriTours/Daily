part of 'share_dialog_controller.dart';

extension PartImages on ShareDialogController {
  Future<List<ImageDownloadResult>> _downloadImages(List<String> imageUrls) async {
    final imageResults = await Future.wait(imageUrls.map((imageUrl) async {
      return ImageDownloadResult(imageUrl, await _imageDownTask(imageUrl));
    }));
    return imageResults.where((result) => result.imagePath.isNotEmpty).toList();
  }

  Future<void> _saveImages(Article? article, List<ImageDownloadResult> results) async {
    if (article == null || results.isEmpty) return;
    await db.articleImages.deleteWhere((tbl) => tbl.article.equals(article.id));
    for (var result in results) {
      await _saveDownloadImage(article.id, result);
    }
    logger.i("网页相关图片保存完成 ${article.id}");
  }

  Future<void> _saveDownloadImage(int articleID, ImageDownloadResult result) async {
    try {
      await db.into(db.articleImages).insert(ArticleImagesCompanion(
            article: drift.Value(articleID),
            imageUrl: drift.Value(result.imageUrl),
            imagePath: drift.Value(result.imagePath),
          ));
      logger.i("保存到数据库: ${result.imageUrl} => ${result.imagePath}");
    } catch (e) {
      logger.e("保存图片失败: ${result.imagePath}, 错误: $e");
    }
  }

  Future<String> _imageDownTask(String imageUrl) async {
    return await HttpService.i.downloadImage(imageUrl);
  }
}

class ImageDownloadResult {
  final String imageUrl;
  final String imagePath;

  ImageDownloadResult(this.imageUrl, this.imagePath);
}
