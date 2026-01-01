import 'dart:async';

import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/data/index.dart';

/// 网页内容通知器
///
/// 用于从服务层通知 Riverpod providers 文章状态变化
class WebContentNotifier {
  // 单例
  WebContentNotifier._();
  static final WebContentNotifier i = WebContentNotifier._();

  /// 文章更新事件流控制器
  final _articleUpdatedController = StreamController<int>.broadcast();

  /// 获取文章更新事件流
  Stream<int> get onArticleUpdated => _articleUpdatedController.stream;

  /// 通知文章已更新
  void notifyArticleUpdated(int articleId) {
    logger.d('[WebContentNotifier] 文章已更新 #$articleId');
    _articleUpdatedController.add(articleId);
  }

  /// 通知文章处理失败
  void notifyArticleFailed(int articleId, String errorMessage) {
    logger.w('[WebContentNotifier] 文章处理失败 #$articleId: $errorMessage');
    // 失败时也通知更新，让UI能显示错误状态
    _articleUpdatedController.add(articleId);
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
