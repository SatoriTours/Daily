import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/models/article_model.dart';
import 'package:daily_satori/app/objectbox/article.dart';
import 'package:daily_satori/app/repositories/article_repository.dart';
import 'package:daily_satori/app/utils/string_extensions.dart';
import 'package:daily_satori/app/services/web_content/content_extractor.dart';

/// 使用统一的ArticleStatus和ArticleFieldName定义
export 'package:daily_satori/app/repositories/article_repository.dart' show ArticleStatus, ArticleFieldName;

/// 文章管理器
/// 专门负责文章的创建、更新和状态管理
class ArticleManager {
  /// 初始化文章（创建或重置）
  Future<ArticleModel> initializeArticle({
    required String url,
    required String comment,
    bool isUpdate = false,
    int articleID = 0,
  }) async {
    logger.i('[ArticleManager] ▶ 初始化文章: URL=$url, 更新=$isUpdate, ID=$articleID');

    // 验证URL
    if (url.isNullOrEmpty) {
      throw Exception('URL不能为空');
    }

    // 检查URL是否已存在
    final existingArticle = await ArticleRepository.instance.findByUrl(url);
    if (existingArticle != null && !isUpdate) {
      throw Exception('网页已存在，无法重复添加');
    }

    final ArticleModel article;
    if (isUpdate && articleID > 0) {
      article = await _resetExistingArticle(articleID, comment);
    } else {
      article = await _createNewArticle(url, comment);
    }

    logger.i('[ArticleManager] ◀ 文章初始化完成: #${article.id}');
    return article;
  }

  /// 更新文章与网页内容
  Future<void> updateWithWebContent(ArticleModel article, ExtractedWebContent webContent) async {
    logger.i('[ArticleManager] ▶ 更新文章内容: #${article.id}');

    article.title = webContent.title;
    article.content = webContent.content;
    article.htmlContent = webContent.htmlContent;
    article.coverImageUrl = webContent.coverImageUrl;
    article.updatedAt = DateTime.now().toUtc();
    article.status = ArticleStatus.webContentFetched;

    await ArticleRepository.instance.updateModel(article);
    logger.i('[ArticleManager] ◀ 文章内容更新完成: #${article.id}');
  }

  /// 标记文章为完成状态
  Future<void> markAsCompleted(int articleId) async {
    final article = ArticleRepository.instance.findModel(articleId);
    if (article == null) return;

    article.status = ArticleStatus.completed;
    await ArticleRepository.instance.updateModel(article);
    logger.i('[ArticleManager] 文章标记为完成: #$articleId');
  }

  /// 标记文章为失败状态
  Future<void> markAsFailed(int articleId, String errorMessage) async {
    final article = ArticleRepository.instance.findModel(articleId);
    if (article == null) return;

    article.status = ArticleStatus.error;
    article.aiContent = errorMessage;
    article.updatedAt = DateTime.now().toUtc();

    await ArticleRepository.instance.updateModel(article);
    logger.e('[ArticleManager] 文章标记为失败: #$articleId, 错误: $errorMessage');
  }

  /// 创建新文章
  Future<ArticleModel> _createNewArticle(String url, String comment) async {
    logger.d('[ArticleManager] ▶ 创建新文章: $url');

    final now = DateTime.now().toUtc();

    final data = {
      'title': '正在加载...',
      'url': url,
      'comment': comment,
      'pubDate': now,
      'createdAt': now,
      'updatedAt': now,
      'status': ArticleStatus.pending,
      ..._initEmptyAiFields(),
    };

    final article = Article(
      url: url,
      title: data['title'] as String,
      comment: comment,
      pubDate: now,
      createdAt: now,
      updatedAt: now,
      status: ArticleStatus.pending,
    );

    final articleModel = await ArticleRepository.instance.createArticleModel(article);

    if (articleModel.id <= 0) {
      throw Exception('创建文章记录失败');
    }

    logger.d('[ArticleManager] ◀ 新文章创建成功: #${articleModel.id}');
    return articleModel;
  }

  /// 重置现有文章
  Future<ArticleModel> _resetExistingArticle(int articleId, String comment) async {
    logger.d('[ArticleManager] ▶ 重置文章: #$articleId');

    final article = ArticleRepository.instance.findModel(articleId);
    if (article == null) {
      throw Exception('找不到要更新的文章: $articleId');
    }

    article.comment = comment;
    _resetArticleAiFields(article);
    article.status = ArticleStatus.pending;
    article.updatedAt = DateTime.now().toUtc();

    await ArticleRepository.instance.updateModel(article);
    logger.d('[ArticleManager] ◀ 文章重置成功: #$articleId');

    return article;
  }

  /// 初始化空的AI字段
  Map<String, String> _initEmptyAiFields() {
    return {
      'aiTitle': '',
      'aiContent': '',
      'aiMarkdownContent': '',
      'content': '',
      'htmlContent': '',
      'coverImage': '',
      'coverImageUrl': '',
    };
  }

  /// 重置文章的AI字段
  void _resetArticleAiFields(ArticleModel article) {
    article.aiTitle = '';
    article.aiContent = '';
    article.aiMarkdownContent = '';
    article.coverImage = '';
  }

  /// 获取文章状态
  Future<String> getArticleStatus(int articleId) async {
    final article = ArticleRepository.instance.findModel(articleId);
    return article?.status ?? ArticleStatus.error;
  }

  /// 获取文章详情
  Future<ArticleModel?> getArticle(int articleId) async {
    return ArticleRepository.instance.findModel(articleId);
  }

  /// 检查URL是否已存在
  Future<bool> isUrlExists(String url) async {
    final existing = await ArticleRepository.instance.findByUrl(url);
    return existing != null;
  }

  /// 获取文章总数
  Future<int> getTotalArticleCount() async {
    return ArticleRepository.instance.count();
  }

  /// 获取待处理文章数量
  Future<int> getPendingArticleCount() async {
    return ArticleRepository.instance.countByStatus(ArticleStatus.pending);
  }

  /// 获取失败文章数量
  Future<int> getFailedArticleCount() async {
    return ArticleRepository.instance.countByStatus(ArticleStatus.error);
  }

  /// 批量更新文章状态
  Future<void> batchUpdateStatus(List<int> articleIds, ArticleStatus status) async {
    for (final id in articleIds) {
      await markAsCompleted(id);
    }
  }

  /// 删除文章
  Future<void> deleteArticle(int articleId) async {
    ArticleRepository.instance.delete(articleId);
    logger.i('[ArticleManager] 文章已删除: #$articleId');
  }

  /// 批量删除文章
  Future<void> batchDeleteArticles(List<int> articleIds) async {
    for (final id in articleIds) {
      await deleteArticle(id);
    }
  }

  /// 获取文章统计信息
  Future<ArticleStats> getArticleStats() async {
    return ArticleStats(
      total: await getTotalArticleCount(),
      pending: await getPendingArticleCount(),
      completed: ArticleRepository.instance.countByStatus(ArticleStatus.completed),
      failed: await getFailedArticleCount(),
    );
  }
}

/// 文章统计数据结构
class ArticleStats {
  final int total;
  final int pending;
  final int completed;
  final int failed;

  ArticleStats({required this.total, required this.pending, required this.completed, required this.failed});

  double get completionRate => total > 0 ? completed / total : 0;
  double get failureRate => total > 0 ? failed / total : 0;
  double get pendingRate => total > 0 ? pending / total : 0;
}

/// 网页内容辅助类
class WebContent {
  final String title;
  final String content;
  final String htmlContent;
  final String? coverImageUrl;

  WebContent({required this.title, required this.content, required this.htmlContent, this.coverImageUrl});
}
