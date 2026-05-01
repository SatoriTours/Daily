import 'package:daily_satori/app/data/data.dart';
import 'package:daily_satori/app/services/http_service.dart';
import 'package:daily_satori/app/services/logger_service.dart';

/// 图片处理器
class ImageProcessor {
  /// 处理封面图片
  Future<void> processCover(ArticleModel article) async {
    final url = article.coverImageUrl ?? '';
    if (url.isEmpty) return;

    logger.i('[WebContent] 下载封面: $url');
    final path = await HttpService.i.downloadImage(url);
    if (path.isNotEmpty) {
      article.coverImage = path;
      ArticleRepository.i.updateModel(article);
      logger.i('[WebContent] 封面下载成功');
    } else {
      logger.w('[WebContent] 封面下载失败');
    }
  }

  /// 批量下载图片
  Future<List<String>> downloadAll(List<String> urls) async {
    final results = <String>[];
    for (final url in urls) {
      final path = await HttpService.i.downloadImage(url);
      if (path.isNotEmpty) results.add(path);
    }
    return results;
  }
}
