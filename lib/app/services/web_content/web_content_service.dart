import 'package:daily_satori/app/services/web_content/content_extractor.dart';
import 'package:daily_satori/app/services/web_content/ai_processor.dart';
import 'package:daily_satori/app/services/web_content/image_processor.dart';
import 'package:daily_satori/app/services/web_content/article_manager.dart';
import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/data/data.dart';
import 'package:daily_satori/app/services/web_content/web_content_notifier.dart';

/// 重构后的网页内容服务
/// 使用单一职责原则，将WebpageParserService拆分为多个专门的服务
class WebContentService {
  // 单例实现
  WebContentService._privateConstructor();
  static final WebContentService _instance = WebContentService._privateConstructor();
  static WebContentService get i => _instance;

  // 依赖的服务
  final _contentExtractor = ContentExtractor();
  final _aiProcessor = AiProcessor();
  final _imageProcessor = ImageProcessor();
  final _articleManager = ArticleManager();

  /// 获取通知器（使用单例）
  WebContentNotifier get _notifier => WebContentNotifier.i;

  // ====================== 公共API ======================

  /// 保存网页（重构后的API）
  Future<ArticleModel> saveWebpage({
    required String url,
    required String comment,
    bool isUpdate = false,
    int articleID = 0,
  }) async {
    logger.i('[WebContentService] ▶ 保存网页: URL=$url, 更新=$isUpdate, ID=$articleID');

    try {
      // 步骤1: 初始化文章
      final article = _articleManager.initializeArticle(
        url: url,
        comment: comment,
        isUpdate: isUpdate,
        articleID: articleID,
      );

      // 步骤2: 提取网页内容
      final webContent = await _contentExtractor.extractContent(url);
      _articleManager.updateWithWebContent(article, webContent);

      // 步骤3: 异步处理AI任务
      _processAiTasksAsync(article);

      logger.i('[WebContentService] ◀ 处理完成: #${article.id}');
      return article;
    } catch (e, stackTrace) {
      logger.e('[WebContentService] 处理失败: $e', error: e, stackTrace: stackTrace);

      if (isUpdate && articleID > 0) {
        _articleManager.markAsFailed(articleID, '处理失败: $e');
        final article = ArticleRepository.i.findModel(articleID);
        if (article != null) return article;
      }

      throw Exception('保存网页失败: $e');
    }
  }

  // ====================== 异步处理 ======================

  /// 异步处理所有AI任务
  void _processAiTasksAsync(ArticleModel article) {
    Future.microtask(() async {
      try {
        await _processAiTasks(article);
      } catch (e) {
        logger.e('[WebContentService] AI任务处理失败: #${article.id}, $e');
      }
    });
  }

  /// 处理AI任务
  Future<void> _processAiTasks(ArticleModel article) async {
    final articleId = article.id;
    logger.i('[WebContentService:AI] ▶ 开始AI处理: #$articleId');

    try {
      // 创建任务列表
      final tasks = [_processContent(article), _processImages(article)];

      // 并行执行
      await Future.wait(tasks);

      // 更新状态为完成
      _articleManager.markAsCompleted(articleId);

      // 通知UI更新
      _notifier.notifyArticleUpdated(articleId);

      logger.i('[WebContentService:AI] ◀ AI处理完成: #$articleId');
    } catch (e) {
      logger.e('[WebContentService:AI] 处理失败: #$articleId, $e');
      _articleManager.markAsFailed(articleId, 'AI处理失败: $e');
      _notifier.notifyArticleFailed(articleId, e.toString());
    }
  }

  /// 处理内容相关的AI任务
  Future<void> _processContent(ArticleModel article) async {
    await _aiProcessor.processAllAiTasks(article);
  }

  /// 处理图片相关的任务
  Future<void> _processImages(ArticleModel article) async {
    await _imageProcessor.processCoverImage(article);
  }

  // ====================== 工具方法 ======================

  /// 重新处理文章（用于重试失败的AI任务）
  Future<void> reprocessArticle(int articleId) async {
    final article = ArticleRepository.i.findModel(articleId);
    if (article == null) {
      throw Exception('找不到文章: $articleId');
    }

    logger.i('[WebContentService] ▶ 重新处理文章: #$articleId');
    _processAiTasksAsync(article);
  }

  /// 检查文章状态
  Future<ArticleStatus> checkArticleStatus(int articleId) async {
    final article = ArticleRepository.i.findModel(articleId);
    return article?.status ?? ArticleStatus.error;
  }
}
