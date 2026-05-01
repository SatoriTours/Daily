import 'package:daily_satori/app/data/data.dart';
import 'package:daily_satori/app/objectbox/article.dart';
import 'package:daily_satori/app/services/logger_service.dart';

/// 文章管理器
class ArticleManager {
  /// 创建或重置文章
  ArticleModel createOrReset(String url, String comment, bool isUpdate, int articleID) {
    if (url.isEmpty) throw Exception('URL不能为空');
    if (ArticleRepository.i.findByUrl(url) != null && !isUpdate) {
      throw Exception('网页已存在，无法重复添加');
    }
    return isUpdate && articleID > 0 ? _reset(articleID, comment) : _create(url, comment);
  }

  /// 更新文章内容
  void updateWithWebContent(ArticleModel article, ExtractedWebContent webContent) {
    article.title = webContent.title;
    article.content = webContent.content;
    article.htmlContent = webContent.htmlContent;
    article.coverImageUrl = webContent.coverImageUrl;
    article.updatedAt = DateTime.now().toUtc();
    article.status = ArticleStatus.webContentFetched;
    ArticleRepository.i.updateModel(article);
  }

  /// 标记为完成
  void markAsCompleted(int articleId) {
    final article = ArticleRepository.i.findModel(articleId);
    if (article == null) return;
    article.status = ArticleStatus.completed;
    ArticleRepository.i.updateModel(article);
  }

  /// 标记为失败
  void markAsFailed(int articleId, String error) {
    final article = ArticleRepository.i.findModel(articleId);
    if (article == null) return;
    article.status = ArticleStatus.error;
    article.aiContent = error;
    article.updatedAt = DateTime.now().toUtc();
    ArticleRepository.i.updateModel(article);
    logger.e('[WebContent] 文章失败 #$articleId: $error');
  }

  // ========== 内部方法 ==========

  ArticleModel _create(String url, String comment) {
    final now = DateTime.now().toUtc();
    final article = Article(
      url: url,
      title: '正在加载...',
      comment: comment,
      pubDate: now,
      createdAt: now,
      updatedAt: now,
      status: ArticleStatus.pending.value,
    );
    final model = ArticleModel(article);
    final id = ArticleRepository.i.save(model);
    final saved = ArticleRepository.i.findModel(id);
    if (saved == null) throw Exception('创建文章失败');
    logger.i('[WebContent] 新建文章: #$id');
    return saved;
  }

  ArticleModel _reset(int articleId, String comment) {
    final article = ArticleRepository.i.findModel(articleId);
    if (article == null) throw Exception('找不到文章: $articleId');
    article.comment = comment;
    article.aiTitle = '';
    article.aiContent = '';
    article.aiMarkdownContent = '';
    article.coverImage = '';
    article.status = ArticleStatus.pending;
    article.updatedAt = DateTime.now().toUtc();
    ArticleRepository.i.updateModel(article);
    logger.i('[WebContent] 重置文章: #$articleId');
    return article;
  }
}

/// 网页内容数据结构
class ExtractedWebContent {
  final String title, content, htmlContent;
  final String? coverImageUrl;
  ExtractedWebContent({required this.title, required this.content, required this.htmlContent, this.coverImageUrl});
}

/// 文章统计
class ArticleStats {
  final int total, pending, completed, failed;
  ArticleStats({required this.total, required this.pending, required this.completed, required this.failed});
  double get completionRate => total > 0 ? completed / total : 0;
}
