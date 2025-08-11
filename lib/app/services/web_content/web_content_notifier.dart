import 'package:get/get.dart';
import 'package:daily_satori/app/modules/articles/controllers/articles_controller.dart';
import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/repositories/article_repository.dart';

/// 使用统一的ArticleStatus定义
export 'package:daily_satori/app/repositories/article_repository.dart' show ArticleStatus;

/// 网页内容通知器
/// 专门负责通知UI更新文章状态，避免直接依赖控制器
class WebContentNotifier {
  
  /// 通知文章已更新
  void notifyArticleUpdated(int articleId) {
    try {
      if (Get.isRegistered<ArticlesController>()) {
        Get.find<ArticlesController>().updateArticle(articleId);
        logger.d('[WebContentNotifier] 已通知UI更新文章 #$articleId');
      }
    } catch (e) {
      logger.w('[WebContentNotifier] 通知更新失败: #$articleId, $e');
    }
  }
  
  /// 通知文章处理失败
  void notifyArticleFailed(int articleId, String errorMessage) {
    try {
      if (Get.isRegistered<ArticlesController>()) {
        Get.find<ArticlesController>().updateArticle(articleId);
        logger.d('[WebContentNotifier] 已通知UI文章处理失败 #$articleId');
      }
    } catch (e) {
      logger.w('[WebContentNotifier] 通知失败失败: #$articleId, $e');
    }
  }
  
  /// 通知文章列表刷新
  void notifyArticlesListRefreshed() {
    try {
      if (Get.isRegistered<ArticlesController>()) {
        Get.find<ArticlesController>().reloadArticles();
        logger.d('[WebContentNotifier] 已通知UI刷新文章列表');
      }
    } catch (e) {
      logger.w('[WebContentNotifier] 通知刷新失败: $e');
    }
  }
  
  /// 通知文章已删除
  void notifyArticleDeleted(int articleId) {
    try {
      if (Get.isRegistered<ArticlesController>()) {
        Get.find<ArticlesController>().removeArticle(articleId);
        logger.d('[WebContentNotifier] 已通知UI删除文章 #$articleId');
      }
    } catch (e) {
      logger.w('[WebContentNotifier] 通知删除失败: #$articleId, $e');
    }
  }
  
  /// 广播文章状态变化
  void broadcastArticleStatusChange(int articleId, ArticleStatus status) {
    notifyArticleUpdated(articleId);
  }
  
  /// 检查控制器是否已注册
  bool isArticlesControllerRegistered() {
    return Get.isRegistered<ArticlesController>();
  }
  
  /// 获取控制器实例
  ArticlesController? getArticlesController() {
    try {
      return Get.isRegistered<ArticlesController>() 
          ? Get.find<ArticlesController>() 
          : null;
    } catch (e) {
      return null;
    }
  }
}