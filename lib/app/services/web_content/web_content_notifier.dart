import 'package:get/get.dart';
import 'package:daily_satori/app/services/state/article_state_service.dart';
import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/repositories/article_repository.dart';

/// 使用统一的ArticleStatus定义
export 'package:daily_satori/app/repositories/article_repository.dart' show ArticleStatus;

/// 网页内容通知器
/// 专门负责通知UI更新文章状态，通过状态服务避免直接依赖控制器
class WebContentNotifier {

  /// 通知文章已更新
  void notifyArticleUpdated(int articleId) {
    try {
      final article = ArticleRepository.find(articleId);
      if (article != null && Get.isRegistered<ArticleStateService>()) {
        Get.find<ArticleStateService>().notifyArticleUpdated(article);
        logger.d('[WebContentNotifier] 已通过状态服务通知更新文章 #$articleId');
      }
    } catch (e) {
      logger.w('[WebContentNotifier] 通知更新失败: #$articleId, $e');
    }
  }
  
  /// 通知文章处理失败
  void notifyArticleFailed(int articleId, String errorMessage) {
    notifyArticleUpdated(articleId); // 失败时也更新状态
  }

  /// 通知文章列表刷新
  void notifyArticlesListRefreshed() {
    // 列表刷新由状态服务自动处理，不需要特殊通知
    logger.d('[WebContentNotifier] 文章列表刷新通知已由状态服务处理');
  }

  /// 通知文章已删除
  void notifyArticleDeleted(int articleId) {
    try {
      if (Get.isRegistered<ArticleStateService>()) {
        Get.find<ArticleStateService>().clearActiveArticle();
        logger.d('[WebContentNotifier] 已通过状态服务清除活跃文章 #$articleId');
      }
    } catch (e) {
      logger.w('[WebContentNotifier] 通知删除失败: #$articleId, $e');
    }
  }
  
  /// 广播文章状态变化
  void broadcastArticleStatusChange(int articleId, ArticleStatus status) {
    notifyArticleUpdated(articleId);
  }
}