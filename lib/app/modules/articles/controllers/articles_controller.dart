import 'package:daily_satori/app_exports.dart';
import 'package:daily_satori/app/config/app_config.dart';
import 'package:daily_satori/app/extensions/i18n_extension.dart';

/// 文章列表控制器
///
/// 负责文章列表的展示、搜索、过滤、分页加载等功能
/// 通过 ArticleStateService 管理文章数据状态
class ArticlesController extends BaseGetXController with WidgetsBindingObserver {
  // ========== 构造函数 ==========
  ArticlesController(super._appStateService, this._articleStateService);

  // ========== 状态服务 ==========
  final ArticleStateService _articleStateService;

  // 获取appStateService的便捷访问器
  AppStateService get _appStateService => appStateService;

  // ========== 过滤状态 ==========
  final onlyFavorite = false.obs;
  final tagId = (-1).obs;
  final tagName = ''.obs;
  final selectedFilterDate = Rx<DateTime?>(null);

  // ========== 日历状态 ==========
  final calendarDisplayedMonth = DateTime.now().obs;
  final calendarSelectedDate = DateTime.now().obs;

  // ========== 数据访问器 ==========
  RxList<ArticleModel> get articles => _articleStateService.articles;
  RxBool get isLoadingArticles => _articleStateService.isLoading;

  // ========== UI 控制器 ==========
  final scrollController = ScrollController();
  final searchController = TextEditingController();
  final searchFocusNode = FocusNode();

  // ========== 常量配置 ==========
  final int _pageSize = PaginationConfig.defaultPageSize;
  static const int _staleDataThresholdMinutes = 60;

  // ========== 内部状态 ==========
  DateTime _lastRefreshTime = DateTime.now();

  // ========== 生命周期方法 ==========

  @override
  void onInit() {
    super.onInit();
    _initScrollListener();
    _initLifecycleObserver();
    _initGlobalSearchListener();
    _initArticleUpdateListener();
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

  // ========== 公开 API - 数据操作 ==========

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

    _scrollToTopIfNeeded();
    logger.i('文章列表重新加载完成');
  }

  /// 从列表中移除文章
  void removeArticle(int id) => _articleStateService.removeArticleFromList(id);

  /// 更新列表中的文章
  void updateArticle(int id) => _articleStateService.updateArticleInList(id);

  /// 合并/插入文章（用于新增或外部更新回写）
  void mergeArticle(ArticleModel model) => _articleStateService.mergeArticle(model);

  /// 获取某篇文章的共享引用（详情/编辑页应持有此引用而非自行查询）
  ArticleModel? getRef(int id) => _articleStateService.getArticleRef(id);

  // ========== 公开 API - 搜索功能 ==========

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

    _articleStateService.setGlobalSearch(query);
    reloadArticles();
  }

  // ========== 公开 API - 过滤功能 ==========

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

  // ========== 公开 API - UI 辅助方法 ==========

  /// 获取页面标题（根据当前过滤状态）
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
      (true, _, _, _) => 'article.search_result'.t.replaceAll('{query}', searchQuery),
      (_, true, _, _) => 'article.filter_by_tag'.t.replaceAll('{tag}', tagName.value),
      (_, _, true, _) => 'article.favorite_articles'.t,
      (_, _, _, true) => 'article.filter_by_date'.t,
      _ => 'article.all_articles'.t,
    };
  }

  /// 是否存在任一过滤条件
  bool hasActiveFilters() {
    return _articleStateService.globalSearchQuery.isNotEmpty ||
        tagName.value.isNotEmpty ||
        onlyFavorite.value ||
        selectedFilterDate.value != null;
  }

  /// 获取每天文章数量统计（用于日历视图）
  Map<DateTime, int> getDailyArticleCounts() {
    return ArticleRepository.i.getArticleDailyCounts();
  }

  // ========== 公开 API - 日历功能 ==========

  /// 日历上一月
  void calendarPreviousMonth() {
    calendarDisplayedMonth.value = DateTime(
      calendarDisplayedMonth.value.year,
      calendarDisplayedMonth.value.month - 1,
      1,
    );
  }

  /// 日历下一月
  void calendarNextMonth() {
    calendarDisplayedMonth.value = DateTime(
      calendarDisplayedMonth.value.year,
      calendarDisplayedMonth.value.month + 1,
      1,
    );
  }

  /// 选择日历日期
  void selectCalendarDate(DateTime date) {
    calendarSelectedDate.value = date;
  }

  /// 判断是否是今天
  bool isToday(DateTime date) {
    final now = DateTime.now();
    return date.year == now.year && date.month == now.month && date.day == now.day;
  }

  /// 判断两个日期是否是同一天
  bool isSameDay(DateTime a, DateTime b) {
    return a.year == b.year && a.month == b.month && a.day == b.day;
  }

  // ========== 私有方法 - 初始化 ==========

  /// 初始化全局搜索监听器
  void _initGlobalSearchListener() {
    // 监听全局搜索状态
    ever(_articleStateService.globalSearchQuery, (query) {
      if (query.isNotEmpty) {
        searchController.text = query;
        reloadArticles();
      }
    });
  }

  /// 初始化文章更新事件监听器
  void _initArticleUpdateListener() {
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
    final isDataStale = DateTime.now().difference(_lastRefreshTime).inMinutes >= _staleDataThresholdMinutes;

    if (isAtTop || isDataStale) {
      await reloadArticles();
    }
  }

  // ========== 私有方法 - 数据加载 ==========

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

  /// 加载更多文章（向后滚动）
  Future<void> _loadMoreArticles() async => _loadAdjacentArticles(loadAfter: true);

  /// 加载之前的文章（向前滚动）
  Future<void> _loadPreviousArticles() async => _loadAdjacentArticles(loadAfter: false);

  /// 通用相邻分页加载逻辑
  Future<void> _loadAdjacentArticles({required bool loadAfter}) async {
    if (articles.isEmpty) return;

    final anchorId = loadAfter ? articles.last.id : articles.first.id;
    logger.i(loadAfter ? '加载ID:$anchorId之后的$_pageSize篇文章' : '加载ID:$anchorId之前的$_pageSize篇文章');

    await _loadArticlesWithQuery(anchorId, loadAfter ? false : true);
  }

  // ========== 私有方法 - 工具函数 ==========

  /// 构建查询参数
  QueryParams _buildQueryParams() {
    final keyword = _articleStateService.globalSearchQuery.isNotEmpty
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

  /// 准备搜索焦点
  void _prepareSearchFocus() {
    searchController.clear();
    Future.delayed(const Duration(milliseconds: 100), () {
      searchFocusNode.requestFocus();
    });
  }

  /// 如有需要则清除搜索
  void _clearSearchIfNeeded() {
    if (searchController.text.isNotEmpty) {
      searchController.clear();
      reloadArticles();
    }
  }

  /// 重置其他过滤条件
  void _resetOtherFilters() {
    tagId.value = -1;
    tagName.value = '';
    onlyFavorite.value = false;
    searchController.clear();
  }

  /// 滚动到顶部（如有客户端）
  void _scrollToTopIfNeeded() {
    if (scrollController.hasClients) {
      scrollController.jumpTo(0);
    }
  }

  /// 获取指定日期的结束时间
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
