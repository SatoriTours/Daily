import 'package:daily_satori/app_exports.dart';

/// 文章列表控制器
class ArticlesController extends BaseController with WidgetsBindingObserver {
  /// UI状态
  final isLoading = false.obs;
  final enableSearch = false.obs;
  final isSearchVisible = false.obs;
  final onlyFavorite = false.obs;
  final tagId = (-1).obs;
  final tagName = ''.obs;
  final selectedFilterDate = Rx<DateTime?>(null);

  /// 文章数据
  final articles = <ArticleModel>[].obs;

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

    final newArticles = _fetchArticles();
    articles.assignAll(newArticles);

    // 滚动到顶部
    if (scrollController.hasClients) {
      scrollController.jumpTo(0);
    }
    logger.i('文章列表重新加载完成');
  }

  /// 切换搜索状态
  void toggleSearchState() {
    isSearchVisible.toggle();

    if (isSearchVisible.value) {
      // 如果开启搜索，清空文本并准备接收输入
      searchController.clear();
      // 延迟一下再激活焦点，确保UI已经构建完成
      Future.delayed(const Duration(milliseconds: 100), () {
        searchFocusNode.requestFocus();
      });
    } else {
      // 如果关闭搜索，并且搜索框有内容，则清除并重新加载文章
      if (searchController.text.isNotEmpty) {
        searchController.clear();
        reloadArticles();
      }
    }
  }

  /// 执行搜索
  Future<void> searchArticles() async {
    if (searchController.text.trim().isEmpty) {
      clearAllFilters();
      return;
    }

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
    tagId.value = -1;
    tagName.value = '';
    onlyFavorite.value = false;
    searchController.clear();
    reloadArticles();
  }

  /// 清除所有过滤条件
  void clearAllFilters() {
    tagId.value = -1;
    tagName.value = '';
    onlyFavorite.value = false;
    selectedFilterDate.value = null;
    searchController.clear();
    reloadArticles();
  }

  /// 从列表中移除文章
  void removeArticle(int id) {
    articles.removeWhere((article) => article.id == id);
  }

  /// 更新列表中的文章
  void updateArticle(int id) {
    final article = ArticleRepository.find(id);
    if (article == null) return;

    logger.i('更新文章状态 ${article.title}');

    final index = articles.indexWhere((item) => item.id == id);
    if (index != -1) {
      articles[index] = article;
    }
  }

  /// 获取标题
  String getTitle() {
    // 搜索过滤
    if (searchController.text.isNotEmpty) {
      return '搜索: "${searchController.text}"';
    }

    // 标签过滤
    if (tagName.value.isNotEmpty) {
      return '标签: ${tagName.value}';
    }

    // 收藏过滤
    if (onlyFavorite.value) {
      return '收藏文章';
    }

    // 日期过滤
    if (selectedFilterDate.value != null) {
      return '按日期筛选';
    }

    // 默认标题
    return '全部文章';
  }

  /// 获取每天文章数量统计
  Map<DateTime, int> getDailyArticleCounts() {
    return ArticleRepository.getDailyArticleCounts();
  }

  /// 检查剪贴板
  Future<void> checkClipboard() async {
    logger.i('检查剪切板内容');
    await ClipboardUtils.checkAndNavigateToShareDialog();
  }

  // ==== 私有方法 ====

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
    checkClipboard();
    AppUpgradeService.i.checkAndDownloadInbackend();
  }

  Future<void> _handleAppResume() async {
    if (!scrollController.hasClients) return;

    final isAtTop = scrollController.position.pixels <= 30;
    final isDataStale = DateTime.now().difference(_lastRefreshTime).inMinutes >= 60;

    if (isAtTop || isDataStale) {
      await reloadArticles();
    }

    await checkClipboard();
  }

  /// 获取过滤后的文章列表
  List<ArticleModel> _fetchArticles([int? referenceId, bool? isGreaterThan]) {
    String? keyword = searchController.text.trim().isNotEmpty ? searchController.text.trim() : null;
    bool? favorite = onlyFavorite.value ? true : null;
    List<int>? tagIds = tagId.value > 0 ? [tagId.value] : null;
    DateTime? startDate = selectedFilterDate.value;
    DateTime? endDate =
        selectedFilterDate.value != null
            ? DateTime(
              selectedFilterDate.value!.year,
              selectedFilterDate.value!.month,
              selectedFilterDate.value!.day,
              23,
              59,
              59,
            )
            : null;

    isLoading.value = true;

    try {
      return ArticleRepository.where(
        keyword: keyword,
        isFavorite: favorite,
        tagIds: tagIds,
        startDate: startDate,
        endDate: endDate,
        referenceId: referenceId,
        isGreaterThan: isGreaterThan,
        pageSize: _pageSize,
      );
    } finally {
      isLoading.value = false;
    }
  }

  /// 加载更多文章
  Future<void> _loadMoreArticles() async {
    if (articles.isEmpty) return;

    isLoading.value = true;

    try {
      final articleId = articles.last.id;
      logger.i('加载ID:$articleId之后的$_pageSize篇文章');

      final newArticles = _fetchArticles(articleId, false);
      articles.addAll(newArticles);
    } finally {
      isLoading.value = false;
    }
  }

  /// 加载之前的文章
  Future<void> _loadPreviousArticles() async {
    if (articles.isEmpty) return;

    isLoading.value = true;

    try {
      final articleId = articles.first.id;
      logger.i('加载ID:$articleId之前的$_pageSize篇文章');

      final newArticles = _fetchArticles(articleId, true);
      articles.insertAll(0, newArticles);
    } finally {
      isLoading.value = false;
    }
  }
}
