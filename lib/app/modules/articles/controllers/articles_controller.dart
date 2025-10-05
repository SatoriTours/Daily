import 'package:daily_satori/app_exports.dart';

/// 文章列表控制器
class ArticlesController extends BaseGetXController with WidgetsBindingObserver {
  /// 过滤状态
  final onlyFavorite = false.obs;
  final tagId = (-1).obs;
  final tagName = ''.obs;
  final selectedFilterDate = Rx<DateTime?>(null);

  /// 状态服务
  late final ArticleStateService _articleStateService;
  late final AppStateService _appStateService;

  /// 文章数据 - 引用自StateService
  RxList<ArticleModel> get articles => _articleStateService.articles;
  RxBool get isLoadingArticles => _articleStateService.isLoading;

  /// UI控制器
  final scrollController = ScrollController();
  final searchController = TextEditingController();
  final searchFocusNode = FocusNode();

  /// 分页大小
  final int _pageSize = 20;

  /// 内部状态
  DateTime _lastRefreshTime = DateTime.now();

  // ==== 生命周期方法 ====

  @override
  void onInit() {
    super.onInit();
    _initStateServices();
    _initScrollListener();
    _initLifecycleObserver();
    _loadInitialData();
  }

  @override
  void onClose() {
    scrollController.dispose();
    searchController.dispose();
    searchFocusNode.dispose();
    WidgetsBinding.instance.removeObserver(this);
    super.onClose();
  }

  @override
  Future<void> didChangeAppLifecycleState(AppLifecycleState state) async {
    if (state == AppLifecycleState.resumed) {
      await _handleAppResume();
    }
  }

  // ==== 公开API ====

  /// 重新加载文章列表
  Future<void> reloadArticles() async {
    logger.i('重新加载文章列表');
    _lastRefreshTime = DateTime.now();

    final query = _buildQueryParams();
    await _articleStateService.reloadArticles(
      keyword: query.keyword,
      favorite: query.favorite,
      tagIds: query.tagIds,
      startDate: query.startDate,
      endDate: query.endDate,
    );

    // 滚动到顶部
    if (scrollController.hasClients) {
      scrollController.jumpTo(0);
    }
    logger.i('文章列表重新加载完成');
  }

  /// 切换搜索状态
  void toggleSearchState() {
    _appStateService.toggleSearchBar();

    if (_appStateService.isSearchBarVisible.value) {
      _prepareSearchFocus();
    } else {
      _clearSearchIfNeeded();
    }
  }

  /// 执行搜索
  Future<void> searchArticles() async {
    final query = searchController.text.trim();
    if (query.isEmpty) {
      clearAllFilters();
      return;
    }

    // 设置全局搜索状态
    _articleStateService.setGlobalSearch(query);
    reloadArticles();
  }

  /// 切换收藏过滤
  void toggleFavorite(bool value) {
    onlyFavorite.value = value;
    reloadArticles();
  }

  /// 按标签过滤
  void filterByTag(int id, String name) {
    tagId.value = id;
    tagName.value = name;
    selectedFilterDate.value = null;
    reloadArticles();
  }

  /// 按日期过滤
  void filterByDate(DateTime date) {
    final selectedDay = DateTime(date.year, date.month, date.day);
    selectedFilterDate.value = selectedDay;
    _resetOtherFilters();
    reloadArticles();
  }

  /// 清除所有过滤条件
  void clearAllFilters() {
    tagId.value = -1;
    tagName.value = '';
    onlyFavorite.value = false;
    selectedFilterDate.value = null;
    searchController.clear();
    _articleStateService.clearGlobalSearch();
    reloadArticles();
  }

  /// 从列表中移除文章
  void removeArticle(int id) {
    _articleStateService.removeArticleFromList(id);
  }

  /// 更新列表中的文章
  void updateArticle(int id) {
    _articleStateService.updateArticleInList(id);
  }

  /// 合并/插入文章（用于新增或外部更新回写）
  void mergeArticle(ArticleModel model) {
    _articleStateService.mergeArticle(model);
  }

  /// 获取某篇文章的共享引用（详情/编辑页应持有此引用而非自行查询）
  ArticleModel? getRef(int id) {
    return _articleStateService.getArticleRef(id);
  }

  /// 获取标题
  String getTitle() {
    final searchQuery = _articleStateService.globalSearchQuery.isNotEmpty
        ? _articleStateService.globalSearchQuery.value
        : searchController.text;

    return switch ((
      _articleStateService.globalSearchQuery.isNotEmpty,
      tagName.value.isNotEmpty,
      onlyFavorite.value,
      selectedFilterDate.value != null,
    )) {
      (true, _, _, _) => '搜索: "$searchQuery"',
      (_, true, _, _) => '标签: ${tagName.value}',
      (_, _, true, _) => '收藏文章',
      (_, _, _, true) => '按日期筛选',
      _ => '全部文章',
    };
  }

  /// 是否存在任一过滤条件（供视图判断显示"已过滤"指示）
  bool hasActiveFilters() {
    return _articleStateService.globalSearchQuery.isNotEmpty ||
        tagName.value.isNotEmpty ||
        onlyFavorite.value ||
        selectedFilterDate.value != null;
  }

  /// 获取每天文章数量统计
  Map<DateTime, int> getDailyArticleCounts() {
    return ArticleRepository.getDailyArticleCounts();
  }

  // 剪贴板检查逻辑已抽离到全局 ClipboardMonitorService，不再在页面 Controller 中实现

  // ==== 私有方法 ====

  void _initStateServices() {
    _articleStateService = Get.find<ArticleStateService>();
    _appStateService = Get.find<AppStateService>();

    // 监听全局搜索状态
    ever(_articleStateService.globalSearchQuery, (query) {
      if (query.isNotEmpty) {
        searchController.text = query;
        reloadArticles();
      }
    });

    // 监听文章更新事件
    ever(_articleStateService.articleUpdateEvent, (event) {
      logger.d("[Articles] 检测到文章事件: $event");

      switch (event.type) {
        case ArticleEventType.created:
          if (event.article != null) {
            mergeArticle(event.article!);
          }
          break;
        case ArticleEventType.updated:
          if (event.article != null) {
            updateArticle(event.article!.id);
          }
          break;
        case ArticleEventType.deleted:
          if (event.articleId != null) {
            removeArticle(event.articleId!);
          }
          break;
        case ArticleEventType.none:
          // 不需要处理
          break;
      }
    });
  }

  void _initScrollListener() {
    scrollController.addListener(() {
      if (!scrollController.hasClients) return;

      final position = scrollController.position;
      if (position.pixels == position.maxScrollExtent) {
        _loadMoreArticles();
      } else if (position.pixels == position.minScrollExtent) {
        _loadPreviousArticles();
      }
    });
  }

  void _initLifecycleObserver() {
    WidgetsBinding.instance.addObserver(this);
  }

  void _loadInitialData() {
    reloadArticles();
    AppUpgradeService.i.checkAndDownloadInbackend();
  }

  Future<void> _handleAppResume() async {
    if (!scrollController.hasClients) return;

    final isAtTop = scrollController.position.pixels <= 30;
    final isDataStale = DateTime.now().difference(_lastRefreshTime).inMinutes >= 60;

    if (isAtTop || isDataStale) {
      await reloadArticles();
    }

    // 剪贴板检查由 ClipboardMonitorService 在应用层统一处理
  }

  /// 获取过滤后的文章列表
  Future<void> _loadArticlesWithQuery([int? referenceId, bool? isGreaterThan]) async {
    final query = _buildQueryParams();

    await _articleStateService.loadArticles(
      keyword: query.keyword,
      favorite: query.favorite,
      tagIds: query.tagIds,
      startDate: query.startDate,
      endDate: query.endDate,
      referenceId: referenceId,
      isGreaterThan: isGreaterThan,
      pageSize: _pageSize,
    );
  }

  /// 构建查询参数
  QueryParams _buildQueryParams() {
    // 优先使用全局搜索，其次是本地搜索
    String? keyword = _articleStateService.globalSearchQuery.isNotEmpty
        ? _articleStateService.globalSearchQuery.trim()
        : (searchController.text.trim().isNotEmpty ? searchController.text.trim() : null);

    return QueryParams(
      keyword: keyword,
      favorite: onlyFavorite.value ? true : null,
      tagIds: tagId.value > 0 ? [tagId.value] : null,
      startDate: selectedFilterDate.value,
      endDate: selectedFilterDate.value != null ? _endOfDay(selectedFilterDate.value!) : null,
    );
  }

  /// 加载更多文章（向后）
  Future<void> _loadMoreArticles() async => _loadAdjacentArticles(loadAfter: true);

  /// 加载之前的文章（向前）
  Future<void> _loadPreviousArticles() async => _loadAdjacentArticles(loadAfter: false);

  /// 通用相邻分页加载逻辑
  Future<void> _loadAdjacentArticles({required bool loadAfter}) async {
    if (articles.isEmpty) return;

    final anchorId = loadAfter ? articles.last.id : articles.first.id;
    logger.i(loadAfter ? '加载ID:$anchorId之后的$_pageSize篇文章' : '加载ID:$anchorId之前的$_pageSize篇文章');

    await _loadArticlesWithQuery(anchorId, loadAfter ? false : true);
  }

  // ==== 私有小工具 ====

  void _prepareSearchFocus() {
    // 如果开启搜索，清空文本并准备接收输入
    searchController.clear();
    // 延迟一下再激活焦点，确保UI已经构建完成
    Future.delayed(const Duration(milliseconds: 100), () {
      searchFocusNode.requestFocus();
    });
  }

  void _clearSearchIfNeeded() {
    // 如果关闭搜索，并且搜索框有内容，则清除并重新加载文章
    if (searchController.text.isNotEmpty) {
      searchController.clear();
      reloadArticles();
    }
  }

  void _resetOtherFilters() {
    tagId.value = -1;
    tagName.value = '';
    onlyFavorite.value = false;
    searchController.clear();
  }

  DateTime _endOfDay(DateTime date) => DateTime(date.year, date.month, date.day, 23, 59, 59);
}

/// 查询参数封装类
class QueryParams {
  final String? keyword;
  final bool? favorite;
  final List<int>? tagIds;
  final DateTime? startDate;
  final DateTime? endDate;

  QueryParams({this.keyword, this.favorite, this.tagIds, this.startDate, this.endDate});
}
