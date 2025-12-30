import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/data/index.dart';

/// 网页内容通知器
///
/// Riverpod架构下，providers会自动响应Repository数据变化
/// 此类保留用于日志记录和未来扩展的通知机制
class WebContentNotifier {
  /// 通知文章已更新
  void notifyArticleUpdated(int articleId) {
    logger.d('[WebContentNotifier] 文章已更新 #$articleId (Riverpod providers will auto-refresh)');
  }

  /// 通知文章处理失败
  void notifyArticleFailed(int articleId, String errorMessage) {
    logger.w('[WebContentNotifier] 文章处理失败 #$articleId: $errorMessage');
  }

  /// 通知文章列表刷新
  void notifyArticlesListRefreshed() {
    logger.d('[WebContentNotifier] 文章列表已刷新');
  }

  /// 通知文章已删除
  void notifyArticleDeleted(int articleId) {
    logger.d('[WebContentNotifier] 文章已删除 #$articleId');
  }

  /// 广播文章状态变化
  void broadcastArticleStatusChange(int articleId, ArticleStatus status) {
    logger.d('[WebContentNotifier] 文章状态变化 #$articleId -> $status');
  }
}
