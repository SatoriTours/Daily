import 'dart:async';
import 'package:daily_satori/app/services/logger_service.dart';

/// 网页内容通知器
class WebContentNotifier {
  WebContentNotifier._();
  static final WebContentNotifier i = WebContentNotifier._();

  final _controller = StreamController<int>.broadcast();
  Stream<int> get onUpdated => _controller.stream;
  Stream<int> get onArticleUpdated => _controller.stream; // 兼容旧名

  void notifyUpdated(int articleId) {
    logger.d('[WebContent] 文章更新 #$articleId');
    _controller.add(articleId);
  }

  void notifyArticleUpdated(int articleId) => notifyUpdated(articleId); // 兼容旧名

  void notifyFailed(int articleId, String error) {
    logger.w('[WebContent] 文章失败 #$articleId: $error');
    _controller.add(articleId);
  }

  void notifyArticleFailed(int articleId, String error) => notifyFailed(articleId, error); // 兼容旧名
}
