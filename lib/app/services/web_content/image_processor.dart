import 'package:daily_satori/app/services/http_service.dart';
import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/models/article_model.dart';
import 'package:daily_satori/app/repositories/article_repository.dart';

/// 图片处理器
/// 专门负责处理文章图片的下载和存储
class ImageProcessor {
  /// 处理文章封面图片
  Future<void> processCoverImage(ArticleModel article) async {
    final imageUrl = article.coverImageUrl ?? '';

    if (imageUrl.isEmpty) {
      logger.w('[图片:封面] 图片URL为空，跳过处理');
      return;
    }

    logger.i('[图片:封面] ▶ 开始处理封面图片: $imageUrl');

    try {
      final imagePath = await HttpService.i.downloadImage(imageUrl);

      if (imagePath.isNotEmpty) {
        article.coverImage = imagePath;
        ArticleRepository.d.updateModel(article);
        logger.i('[图片:封面] ◀ 封面图片处理成功');
      } else {
        logger.w('[图片:封面] 图片下载结果为空');
      }
    } catch (e) {
      logger.e('[图片:封面] 处理失败: $e');
      throw Exception('处理封面图片失败: $e');
    }
  }

  /// 批量处理图片
  Future<List<String>> processImages(List<String> imageUrls) async {
    final processedImages = <String>[];

    for (final url in imageUrls) {
      try {
        final imagePath = await HttpService.i.downloadImage(url);
        if (imagePath.isNotEmpty) {
          processedImages.add(imagePath);
        }
      } catch (e) {
        logger.w('[图片:批量] 图片下载失败: $url, 错误: $e');
      }
    }

    return processedImages;
  }
}
