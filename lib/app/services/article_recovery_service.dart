import 'package:daily_satori/app/data/data.dart';
import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/services/webpage_parser_service.dart';

/// 文章恢复服务
///
/// 负责在应用启动时检测并恢复未完成的文章 AI 处理任务
class ArticleRecoveryService {
  // ====================== 单例实现 ======================
  ArticleRecoveryService._();
  static final ArticleRecoveryService _instance = ArticleRecoveryService._();
  static ArticleRecoveryService get i => _instance;

  // ====================== 初始化 ======================

  /// 初始化服务
  Future<void> init() async {
    logger.i("[初始化服务] ArticleRecoveryService");
    await _resumePendingAiTasks();
  }

  // ====================== 核心方法 ======================

  /// 恢复未完成的 AI 任务
  Future<void> _resumePendingAiTasks() async {
    try {
      final pendingArticles = ArticleRepository.i.findAllPending();
      if (pendingArticles.isEmpty) {
        logger.d("[文章恢复] 没有待处理的文章");
        return;
      }

      logger.i("[文章恢复] 发现 ${pendingArticles.length} 篇未完成的文章，开始自动恢复 AI 处理");

      int successCount = 0;
      int failCount = 0;

      for (final article in pendingArticles) {
        try {
          // 只处理已获取网页内容但未完成 AI 处理的文章
          if (article.status == ArticleStatus.webContentFetched) {
            logger.i("[文章恢复] 恢复文章 AI 处理: ${article.id} - ${article.title}");
            await WebpageParserService.i.processAiTasks(article);
            successCount++;
          }
        } catch (e) {
          logger.e("[文章恢复] 处理文章 ${article.id} 失败: $e");
          failCount++;
        }
      }

      logger.i("[文章恢复] 完成 - 成功: $successCount, 失败: $failCount");
    } catch (e) {
      logger.e("[文章恢复] 恢复 AI 任务失败: $e");
    }
  }
}
