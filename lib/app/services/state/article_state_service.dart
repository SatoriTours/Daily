import 'package:daily_satori/app_exports.dart';

/// 文章更新事件类型
enum ArticleEventType {
  none,
  created,
  updated,
  deleted,
}

/// 文章更新事件
class ArticleUpdateEvent {
  final ArticleEventType type;
  final ArticleModel? article;
  final int? articleId;

  const ArticleUpdateEvent._({
    required this.type,
    this.article,
    this.articleId,
  });

  /// 无事件
  factory ArticleUpdateEvent.none() => const ArticleUpdateEvent._(type: ArticleEventType.none);

  /// 文章创建事件
  factory ArticleUpdateEvent.created(ArticleModel article) => ArticleUpdateEvent._(
        type: ArticleEventType.created,
        article: article,
      );

  /// 文章更新事件
  factory ArticleUpdateEvent.updated(ArticleModel article) => ArticleUpdateEvent._(
        type: ArticleEventType.updated,
        article: article,
      );

  /// 文章删除事件
  factory ArticleUpdateEvent.deleted(int articleId) => ArticleUpdateEvent._(
        type: ArticleEventType.deleted,
        articleId: articleId,
      );

  /// 检查是否影响指定文章
  bool affectsArticle(int articleId) {
    return switch (type) {
      ArticleEventType.created => article?.id == articleId,
      ArticleEventType.updated => article?.id == articleId,
      ArticleEventType.deleted => this.articleId == articleId,
      ArticleEventType.none => false,
    };
  }

  @override
  String toString() {
    return switch (type) {
      ArticleEventType.created => 'ArticleUpdateEvent.created(${article?.id})',
      ArticleEventType.updated => 'ArticleUpdateEvent.updated(${article?.id})',
      ArticleEventType.deleted => 'ArticleUpdateEvent.deleted($articleId)',
      ArticleEventType.none => 'ArticleUpdateEvent.none',
    };
  }
}

/// 全局文章状态管理服务
///
/// 负责管理文章相关的全局状态，包括当前选中的文章、
/// 文章列表的引用管理等，避免控制器之间的紧耦合
class ArticleStateService extends GetxService {
  /// 当前活跃的文章ID（用于在不同页面间共享状态）
  final RxInt _activeArticleId = RxInt(-1);

  /// 当前活跃的文章引用
  final Rxn<ArticleModel> activeArticle = Rxn<ArticleModel>();

  /// 文章更新事件流（用于跨页面同步）
  final Rx<ArticleUpdateEvent> articleUpdateEvent = Rx<ArticleUpdateEvent>(ArticleUpdateEvent.none());

  /// 全局搜索状态
  final RxString globalSearchQuery = ''.obs;
  final RxBool isGlobalSearchActive = false.obs;

  /// 获取当前活跃的文章ID
  int get activeArticleId => _activeArticleId.value;

  /// 获取当前活跃的文章
  ArticleModel? get activeArticleValue => activeArticle.value;

  /// 设置活跃文章
  void setActiveArticle(ArticleModel article) {
    _activeArticleId.value = article.id;
    activeArticle.value = article;
    logger.i('设置活跃文章: ${article.title} (ID: ${article.id})');
  }

  /// 清除活跃文章
  void clearActiveArticle() {
    _activeArticleId.value = -1;
    activeArticle.value = null;
    logger.i('清除活跃文章');
  }

  /// 通知文章更新
  void notifyArticleUpdated(ArticleModel article) {
    logger.i('通知文章更新: ${article.title} (ID: ${article.id})');

    // 如果是当前活跃文章，更新活跃文章引用
    if (_activeArticleId.value == article.id) {
      activeArticle.value = article;
      logger.d('已更新活跃文章引用，状态: ${article.status}');
    }

    // 发布文章更新事件
    articleUpdateEvent.value = ArticleUpdateEvent.updated(article);
  }

  /// 通知文章删除
  void notifyArticleDeleted(int articleId) {
    logger.i('通知文章删除: ID: $articleId');

    // 如果是当前活跃文章，清除活跃文章引用
    if (_activeArticleId.value == articleId) {
      clearActiveArticle();
    }

    // 发布文章删除事件
    articleUpdateEvent.value = ArticleUpdateEvent.deleted(articleId);
  }

  /// 通知文章创建
  void notifyArticleCreated(ArticleModel article) {
    logger.i('通知文章创建: ${article.title} (ID: ${article.id})');

    // 发布文章创建事件
    articleUpdateEvent.value = ArticleUpdateEvent.created(article);
  }

  /// 设置全局搜索
  void setGlobalSearch(String query) {
    globalSearchQuery.value = query;
    isGlobalSearchActive.value = query.isNotEmpty;
    logger.i('设置全局搜索: $query');
  }

  /// 清除全局搜索
  void clearGlobalSearch() {
    globalSearchQuery.value = '';
    isGlobalSearchActive.value = false;
    logger.i('清除全局搜索');
  }

  @override
  void onInit() {
    super.onInit();
    logger.i('ArticleStateService 初始化完成');
  }

  @override
  void onClose() {
    _activeArticleId.close();
    activeArticle.close();
    globalSearchQuery.close();
    isGlobalSearchActive.close();
    super.onClose();
    logger.i('ArticleStateService 已关闭');
  }
}
