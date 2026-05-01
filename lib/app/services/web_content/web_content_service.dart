import 'package:daily_satori/app/data/data.dart';
import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/services/service_base.dart';
import 'package:daily_satori/app/services/web_content/article_manager.dart';
import 'package:daily_satori/app/services/web_content/ai_processor.dart';
import 'package:daily_satori/app/services/web_content/content_extractor.dart';
import 'package:daily_satori/app/services/web_content/image_processor.dart';
import 'package:daily_satori/app/services/web_content/web_content_notifier.dart';

/// 网页内容服务
class WebContentService extends AppService {
  WebContentService._();
  static final WebContentService i = WebContentService._();

  @override
  final ServicePriority priority = ServicePriority.low;

  final _contentExtractor = ContentExtractor();
  final _aiProcessor = AiProcessor();
  final _imageProcessor = ImageProcessor();
  final _articleManager = ArticleManager();
  final _notifier = WebContentNotifier.i;

  @override
  Future<void> init() async {}

  /// 保存网页
  Future<ArticleModel> saveWebpage({
    required String url,
    required String comment,
    bool isUpdate = false,
    int articleID = 0,
  }) async {
    logger.i('[WebContent] 保存网页: url=$url, update=$isUpdate, id=$articleID');

    try {
      final article = _articleManager.createOrReset(url, comment, isUpdate, articleID);
      final webContent = await _contentExtractor.extract(url);
      _articleManager.updateWithWebContent(article, webContent);
      _processAiAsync(article);
      logger.i('[WebContent] 处理完成: #${article.id}');
      return article;
    } catch (e, stack) {
      logger.e('[WebContent] 处理失败: $e', error: e, stackTrace: stack);
      if (isUpdate && articleID > 0) {
        _articleManager.markAsFailed(articleID, '处理失败: $e');
        return ArticleRepository.i.findModel(articleID) ?? (throw Exception('找不到文章'));
      }
      throw Exception('保存网页失败: $e');
    }
  }

  /// 重新处理文章
  Future<void> reprocessArticle(int articleId) async {
    final article = ArticleRepository.i.findModel(articleId);
    if (article == null) throw Exception('找不到文章: $articleId');
    logger.i('[WebContent] 重新处理文章: #$articleId');
    _processAiAsync(article);
  }

  /// 检查文章状态
  Future<ArticleStatus> checkStatus(int articleId) async {
    final article = ArticleRepository.i.findModel(articleId);
    return article?.status ?? ArticleStatus.error;
  }

  // ========== 内部方法 ==========

  void _processAiAsync(ArticleModel article) {
    Future.microtask(() => _processAi(article));
  }

  Future<void> _processAi(ArticleModel article) async {
    final id = article.id;
    logger.i('[WebContent] 开始AI处理: #$id');

    try {
      await Future.wait([_aiProcessor.process(article), _imageProcessor.processCover(article)]);
      _articleManager.markAsCompleted(id);
      _notifier.notifyUpdated(id);
      logger.i('[WebContent] AI处理完成: #$id');
    } catch (e) {
      logger.e('[WebContent] AI处理失败: #$id, $e');
      _articleManager.markAsFailed(id, 'AI处理失败: $e');
      _notifier.notifyFailed(id, e.toString());
    }
  }
}
