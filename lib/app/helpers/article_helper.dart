import 'package:daily_satori/app/objectbox/article.dart';

/// 文章辅助工具类
class ArticleHelper {
  /// 获取文章主图路径
  static String getArticleHeaderImagePath(Article article) {
    return article.images.isEmpty ? '' : (article.images.first.path ?? '');
  }

  /// 检查文章是否有主图
  static bool hasArticleHeaderImage(Article article) {
    final imagePath = getArticleHeaderImagePath(article);
    return imagePath.isNotEmpty;
  }

  /// 检查是否应该显示头部图片
  static bool shouldShowHeaderImage(Article article) {
    final imagePath = getArticleHeaderImagePath(article);
    return imagePath.isNotEmpty && !imagePath.endsWith('.svg');
  }
}
