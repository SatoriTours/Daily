import 'package:daily_satori/app_exports.dart';

/// 文章更新事件类型
enum ArticleEventType { none, created, updated, deleted }

/// 文章更新事件
class ArticleUpdateEvent {
  final ArticleEventType type;
  final ArticleModel? article;
  final int? articleId;

  const ArticleUpdateEvent._({required this.type, this.article, this.articleId});

  /// 无事件
  factory ArticleUpdateEvent.none() => const ArticleUpdateEvent._(type: ArticleEventType.none);

  /// 文章创建事件
  factory ArticleUpdateEvent.created(ArticleModel article) =>
      ArticleUpdateEvent._(type: ArticleEventType.created, article: article);

  /// 文章更新事件
  factory ArticleUpdateEvent.updated(ArticleModel article) =>
      ArticleUpdateEvent._(type: ArticleEventType.updated, article: article);

  /// 文章删除事件
  factory ArticleUpdateEvent.deleted(int articleId) =>
      ArticleUpdateEvent._(type: ArticleEventType.deleted, articleId: articleId);

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
/// 职责：
/// 1. 管理文章列表数据（唯一数据源）
/// 2. 处理文章的加载、更新、删除操作
/// 3. 管理当前活跃文章和搜索状态
/// 4. 发布文章变更事件通知
class ArticleStateService extends GetxService {
  // ===== 数据层 =====

  /// 文章列表（唯一数据源）
  final RxList<ArticleModel> articles = <ArticleModel>[].obs;

  /// 加载状态
  final RxBool isLoading = false.obs;

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
    logger.i('设置活跃文章: ${article.singleLineTitle} (ID: ${article.id})');
  }

  /// 清除活跃文章
  void clearActiveArticle() {
    _activeArticleId.value = -1;
    activeArticle.value = null;
    logger.i('清除活跃文章');
  }

  /// 通知文章更新
  void notifyArticleUpdated(ArticleModel article) {
    logger.i('通知文章更新: ${article.singleLineTitle} (ID: ${article.id})');

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
    logger.i('通知文章创建: ${article.singleLineTitle} (ID: ${article.id})');

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

  // ===== 数据操作方法 =====

  /// 加载文章列表
  Future<void> loadArticles({
    String? keyword,
    bool? favorite,
    List<int>? tagIds,
    DateTime? startDate,
    DateTime? endDate,
    int? referenceId,
    bool? isGreaterThan,
    int pageSize = 20,
  }) async {
    isLoading.value = true;
    try {
      final result = ArticleRepository.i.queryArticles(
        keyword: keyword,
        isFavorite: favorite,
        tagIds: tagIds,
        startDate: startDate,
        endDate: endDate,
        referenceId: referenceId,
        isGreaterThan: isGreaterThan,
        limit: pageSize,
      );

      if (referenceId == null) {
        // 全新加载，替换所有数据
        articles.assignAll(result);
      } else if (isGreaterThan == false) {
        // 向后加载更多
        articles.addAll(result);
      } else {
        // 向前加载
        articles.insertAll(0, result);
      }

      logger.i('加载文章列表完成: ${result.length} 篇');
    } finally {
      isLoading.value = false;
    }
  }

  /// 重新加载文章列表（清空后重新加载）
  Future<void> reloadArticles({
    String? keyword,
    bool? favorite,
    List<int>? tagIds,
    DateTime? startDate,
    DateTime? endDate,
    int pageSize = 20,
  }) async {
    await loadArticles(
      keyword: keyword,
      favorite: favorite,
      tagIds: tagIds,
      startDate: startDate,
      endDate: endDate,
      pageSize: pageSize,
    );
  }

  /// 更新列表中的文章
  void updateArticleInList(int id) {
    final article = ArticleRepository.i.findModel(id);
    if (article == null) return;

    logger.i('更新列表中的文章: ${article.singleLineTitle} (ID: $id)');

    final index = articles.indexWhere((item) => item.id == id);
    if (index != -1) {
      // 就地合并，保持对象引用不变
      articles[index].copyFrom(article);
      // 触发 Rx 刷新 - 使用正确的语法
      articles.value = List.from(articles);
    }

    // 如果是当前活跃文章，同步更新
    if (_activeArticleId.value == id) {
      activeArticle.value?.copyFrom(article);
      activeArticle.value = activeArticle.value; // 触发刷新
    }
  }

  /// 从列表中移除文章
  void removeArticleFromList(int id) {
    articles.removeWhere((article) => article.id == id);
    logger.i('从列表中移除文章: ID=$id');
  }

  /// 合并/插入文章（用于新增或外部更新）
  void mergeArticle(ArticleModel model) {
    final index = articles.indexWhere((item) => item.id == model.id);
    if (index == -1) {
      articles.insert(0, model);
      logger.i('插入新文章到列表: ${model.singleLineTitle} (ID: ${model.id})');
    } else {
      articles[index].copyFrom(model);
      articles.value = List.from(articles); // 触发刷新
      logger.i('更新列表中的文章: ${model.singleLineTitle} (ID: ${model.id})');
    }
  }

  /// 获取某篇文章的共享引用
  ArticleModel? getArticleRef(int id) {
    final index = articles.indexWhere((item) => item.id == id);
    if (index == -1) {
      // 如果列表中没有，从数据库加载
      final article = ArticleRepository.i.findModel(id);
      if (article != null) {
        setActiveArticle(article);
      }
      return article;
    }

    // 设置为活跃文章
    setActiveArticle(articles[index]);
    return articles[index];
  }

  @override
  void onInit() {
    super.onInit();
    logger.i('ArticleStateService 初始化完成');
  }

  @override
  void onClose() {
    articles.close();
    isLoading.close();
    _activeArticleId.close();
    activeArticle.close();
    articleUpdateEvent.close();
    globalSearchQuery.close();
    isGlobalSearchActive.close();
    super.onClose();
    logger.i('ArticleStateService 已关闭');
  }
}
