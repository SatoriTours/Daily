import 'package:daily_satori/app_exports.dart';
import 'package:daily_satori/app/utils/base_list_controller.dart';

/// 重构后的文章列表控制器
/// 使用BaseListController统一处理列表逻辑
class ArticlesController extends BaseListController<ArticleModel> with WidgetsBindingObserver {
  /// 额外状态
  final tagName = ''.obs;
  final tagId = (-1).obs;

  /// 内部状态
  final DateTime _lastRefreshTime = DateTime.now();

  @override
  void onInit() {
    super.onInit();
    _initLifecycleObserver();
    _loadInitialData();
  }

  @override
  void onClose() {
    WidgetsBinding.instance.removeObserver(this);
    super.onClose();
  }

  @override
  Future<void> didChangeAppLifecycleState(AppLifecycleState state) async {
    if (state == AppLifecycleState.resumed) {
      await _handleAppResume();
    }
  }

  // ==== 重写基类方法 ====

  @override
  Future<List<ArticleModel>> fetchData({required int page, int? limit}) async {
    return ArticleRepository.where(
      keyword: searchQuery.value.isNotEmpty ? searchQuery.value : null,
      isFavorite: isFavoriteFilter.value ? true : null,
      tagIds: tagId.value > 0 ? [tagId.value] : null,
      startDate: selectedDate.value,
      endDate: selectedDate.value != null
          ? DateTime(selectedDate.value!.year, selectedDate.value!.month, selectedDate.value!.day, 23, 59, 59)
          : null,
      referenceId: page > 1 ? items.lastOrNull?.id : null,
      isGreaterThan: page > 1 ? false : null,
      pageSize: limit ?? 20,
    );
  }

  @override
  bool applySearchFilter(ArticleModel item, String query) {
    final title = (item.title ?? '').toLowerCase();
    final summary = (item.summary).toLowerCase();
    final q = query.toLowerCase();
    return title.contains(q) || summary.contains(q);
  }

  @override
  bool applyTagFilter(ArticleModel item, List<int> tagIds) {
    return tagIds.any((tagId) => item.tagIds.contains(tagId));
  }

  @override
  bool applyDateFilter(ArticleModel item, DateTime date) {
    if (item.createdAt == null) return false;
    final itemDate = DateTime(item.createdAt!.year, item.createdAt!.month, item.createdAt!.day);
    final filterDate = DateTime(date.year, date.month, date.day);
    return itemDate == filterDate;
  }

  @override
  bool applyFavoriteFilter(ArticleModel item) {
    return item.isFavorite;
  }

  // ==== 公开API ====

  /// 按标签过滤
  void filterByTag(int id, String name) {
    tagId.value = id;
    tagName.value = name;
    selectedDate.value = null;
    isFavoriteFilter.value = false;
    clearSearch();
    refreshData();
  }

  /// 按日期过滤
  void filterByDate(DateTime date) {
    selectDate(date);
    tagId.value = -1;
    tagName.value = '';
    isFavoriteFilter.value = false;
    refreshData();
  }

  /// 获取标题
  String getTitle() {
    if (searchQuery.value.isNotEmpty) {
      return '搜索: "${searchQuery.value}"';
    }
    if (tagName.value.isNotEmpty) {
      return '标签: ${tagName.value}';
    }
    if (isFavoriteFilter.value) {
      return '收藏文章';
    }
    if (selectedDate.value != null) {
      return '按日期筛选';
    }
    return '全部文章';
  }

  /// 获取每天文章数量统计
  Map<DateTime, int> getDailyArticleCounts() {
    return ArticleRepository.getDailyArticleCounts();
  }

  /// 检查剪贴板
  Future<void> checkClipboard() async {
    await ClipboardUtils.checkAndNavigateToShareDialog();
  }

  /// 从列表中移除文章
  void removeArticle(int id) {
    items.removeWhere((article) => article.id == id);
    filteredItems.removeWhere((article) => article.id == id);
  }

  /// 更新列表中的文章
  void updateArticle(int id) {
    final article = ArticleRepository.find(id);
    if (article == null) return;

    final index = items.indexWhere((item) => item.id == id);
    if (index != -1) {
      items[index] = article;
      applyFilters(); // 重新应用过滤
    }
  }

  // ==== 私有方法 ====

  void _initLifecycleObserver() {
    WidgetsBinding.instance.addObserver(this);
  }

  void _loadInitialData() {
    loadInitialData();
    checkClipboard();
    AppUpgradeService.i.checkAndDownloadInbackend();
  }

  Future<void> _handleAppResume() async {
    final isAtTop = scrollController.position.pixels <= 30;
    final isDataStale = DateTime.now().difference(_lastRefreshTime).inMinutes >= 60;

    if (isAtTop || isDataStale) {
      await refreshData();
    }

    await checkClipboard();
  }
}
