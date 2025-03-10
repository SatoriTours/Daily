import 'package:daily_satori/app_exports.dart';

/// 文章列表控制器
class ArticlesController extends BaseController with WidgetsBindingObserver {
  /// UI状态
  final isLoading = false.obs;
  final enableSearch = false.obs;
  final onlyFavorite = false.obs;
  final tagId = (-1).obs;
  final tagName = ''.obs;

  /// 文章数据
  final articles = <ArticleModel>[].obs;

  /// UI控制器
  final scrollController = ScrollController();
  final searchController = TextEditingController();

  /// 分页大小
  final int _pageSize = 20;

  /// 内部状态
  String _clipboardText = '';
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
    enableSearch.toggle();
    if (!enableSearch.value) {
      searchController.clear();
      reloadArticles();
    }
  }

  /// 执行搜索
  Future<void> searchArticles() async {
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
    reloadArticles();
  }

  /// 清除所有过滤器
  void clearAllFilters() {
    tagId.value = -1;
    tagName.value = '';
    onlyFavorite.value = false;
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

    final index = articles.indexWhere((item) => item.id == id);
    if (index != -1) {
      articles[index] = article;
    }
  }

  /// 获取标题
  String getTitle() {
    var title = '文章';

    if (onlyFavorite.value) {
      title = '收藏的文章';
    }

    if (tagName.value.isNotEmpty) {
      title = '$title - ${tagName.value}';
    }

    return title;
  }

  /// 检查剪贴板
  Future<void> checkClipboard() async {
    logger.i('检查剪切板内容');

    final text = await getClipboardText();
    final url = getUrlFromText(text);

    if (url.isNotEmpty && url.startsWith('http') && url != _clipboardText) {
      await _promptToSaveUrl(url);
    }
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
    String? keyword = enableSearch.value ? searchController.text.trim() : null;
    bool? favorite = onlyFavorite.value ? true : null;
    List<int>? tagIds = tagId.value > 0 ? [tagId.value] : null;

    isLoading.value = true;

    try {
      return ArticleRepository.where(
        keyword: keyword,
        isFavorite: favorite,
        tagIds: tagIds,
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

  /// 提示保存URL
  Future<void> _promptToSaveUrl(String url) async {
    final message = '获取到剪切板链接:\n${getSubstring(url, length: 30, suffix: '...')}\n\n请确认是否保存?';

    await showConfirmationDialog(
      '是否保存',
      message,
      onConfirmed: () => _saveUrl(url),
      onCanceled: () => _clipboardText = url,
    );
  }

  /// 保存URL
  Future<void> _saveUrl(String url) async {
    if (isProduction) {
      await setClipboardText('');
    }

    Get.toNamed(Routes.SHARE_DIALOG, arguments: {'shareURL': url});
    _clipboardText = url;
  }
}
