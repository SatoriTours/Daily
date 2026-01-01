/// Articles Controller Provider
///
/// 文章列表控制器，负责文章列表的展示、搜索、过滤、分页加载等功能。
/// 通过 ArticleState provider 管理文章数据状态。

library;

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:freezed_annotation/freezed_annotation.dart';
import 'package:riverpod_annotation/riverpod_annotation.dart';

import 'package:daily_satori/app/config/app_config.dart';
import 'package:daily_satori/app/data/article/article_repository.dart';
import 'package:daily_satori/app/data/article/article_model.dart';
import 'package:daily_satori/app/utils/i18n_extension.dart';
import 'package:daily_satori/app/providers/providers.dart';
import 'package:daily_satori/app/services/index.dart';

part 'articles_controller_provider.freezed.dart';
part 'articles_controller_provider.g.dart';

/// ArticlesController 状态
///
/// 包含文章列表页的所有状态数据
@freezed
abstract class ArticlesControllerState with _$ArticlesControllerState {
  const ArticlesControllerState._();

  const factory ArticlesControllerState({
    /// 是否只显示收藏文章
    @Default(false) bool onlyFavorite,

    /// 标签ID（-1表示未选择）
    @Default(-1) int tagId,

    /// 标签名称
    @Default('') String tagName,

    /// 选中的过滤日期
    DateTime? selectedFilterDate,

    /// 日历显示的月份
    DateTime? calendarDisplayedMonth,

    /// 日历选中的日期
    DateTime? calendarSelectedDate,

    /// 最后刷新时间
    DateTime? lastRefreshTime,

    /// ScrollController (不在freezed中管理)
    ScrollController? scrollController,

    /// TextEditingController (不在freezed中管理)
    TextEditingController? searchController,

    /// FocusNode (不在freezed中管理)
    FocusNode? searchFocusNode,
  }) = _ArticlesControllerState;
}

/// ArticlesControllerState 扩展
///
/// 添加基于其他provider的getter和计算方法
extension ArticlesControllerStateX on ArticlesControllerState {
  /// 获取文章列表 (需要通过ref访问)
  /// 使用 ref.watch() 确保 UI 能响应状态变化
  List<ArticleModel> getArticles(WidgetRef ref) {
    return ref.watch(articleStateProvider).articles;
  }

  /// 是否正在加载文章 (需要通过ref访问)
  /// 使用 ref.watch() 确保 UI 能响应状态变化
  bool isLoadingArticles(WidgetRef ref) {
    return ref.watch(articleStateProvider).isLoading;
  }

  /// 获取标题 (需要通过ref访问)
  /// 使用 ref.watch() 确保 UI 能响应搜索状态变化
  String getTitle(WidgetRef ref) {
    final articleState = ref.watch(articleStateProvider);
    final searchQuery = articleState.globalSearchQuery;

    return switch ((searchQuery.isNotEmpty, tagName.isNotEmpty, onlyFavorite, selectedFilterDate != null)) {
      (true, _, _, _) => 'article.search_result'.t.replaceAll('{query}', searchQuery),
      (_, true, _, _) => 'article.filter_by_tag'.t.replaceAll('{tag}', tagName),
      (_, _, true, _) => 'article.favorite_articles'.t,
      (_, _, _, true) => 'article.filter_by_date'.t,
      _ => 'article.all_articles'.t,
    };
  }

  /// 获取每天文章数量统计
  Map<DateTime, int> getDailyArticleCounts() {
    return ArticleRepository.i.getArticleDailyCounts();
  }

  /// 是否存在任一过滤条件 (需要通过ref访问)
  /// 使用 ref.watch() 确保 UI 能响应搜索状态变化
  bool hasActiveFilters(WidgetRef ref) {
    final articleState = ref.watch(articleStateProvider);
    return articleState.globalSearchQuery.isNotEmpty ||
        tagName.isNotEmpty ||
        onlyFavorite ||
        selectedFilterDate != null;
  }
}

/// ArticlesController Provider
///
/// 管理文章列表页的状态和逻辑
@riverpod
class ArticlesController extends _$ArticlesController {
  // ========================================================================
  // 常量配置
  // ========================================================================

  static const int _staleDataThresholdMinutes = 60;

  // ========================================================================
  // 状态管理
  // ========================================================================

  @override
  ArticlesControllerState build() {
    // 初始化
    _initialize();

    // 创建UI控制器
    final scrollController = createScrollController();
    final searchController = createSearchController();
    final searchFocusNode = createFocusNode();

    final now = DateTime.now();
    return ArticlesControllerState(
      calendarDisplayedMonth: now,
      calendarSelectedDate: now,
      lastRefreshTime: now,
      scrollController: scrollController,
      searchController: searchController,
      searchFocusNode: searchFocusNode,
    );
  }

  /// 初始化
  void _initialize() {
    // 只监听文章更新事件字段的变化，避免因其他状态变化导致重复处理
    ref.listen(articleStateProvider.select((state) => state.articleUpdateEvent), (previous, next) {
      // 只在事件实际变化时处理
      if (previous != next) {
        _handleArticleUpdateEvent(next);
      }
    });

    // 延迟执行，避免在 build 期间访问 state
    Future.microtask(() {
      // 加载初始数据
      reloadArticles();
      AppUpgradeService.i.checkAndDownloadInBackground();
    });
  }

  // ========================================================================
  // UI 控制器（需要在 Widget 中创建）
  // ========================================================================

  /// 创建滚动控制器
  ScrollController createScrollController() {
    final controller = ScrollController();
    controller.addListener(() {
      if (!controller.hasClients) return;

      final position = controller.position;
      if (position.pixels == position.maxScrollExtent) {
        loadMoreArticles();
      } else if (position.pixels == position.minScrollExtent) {
        loadPreviousArticles();
      }
    });
    return controller;
  }

  /// 创建搜索控制器
  TextEditingController createSearchController() => TextEditingController();

  /// 创建焦点节点
  FocusNode createFocusNode() => FocusNode();

  // ========================================================================
  // 公开 API - 数据操作
  // ========================================================================

  /// 重新加载文章列表
  Future<void> reloadArticles() async {
    logger.i('重新加载文章列表');
    state = state.copyWith(lastRefreshTime: DateTime.now());

    final query = _buildQueryParams();
    final articleState = ref.read(articleStateProvider.notifier);

    await articleState.reloadArticles(
      keyword: query.keyword,
      favorite: query.favorite,
      tagIds: query.tagIds,
      startDate: query.startDate,
      endDate: query.endDate,
    );

    logger.i('文章列表重新加载完成');
  }

  /// 从列表中移除文章
  void removeArticle(int id) {
    ref.read(articleStateProvider.notifier).removeArticleFromList(id);
  }

  /// 更新列表中的文章
  void updateArticle(int id) {
    ref.read(articleStateProvider.notifier).updateArticleInList(id);
  }

  /// 合并/插入文章（用于新增或外部更新回写）
  void mergeArticle(ArticleModel model) {
    ref.read(articleStateProvider.notifier).mergeArticle(model);
  }

  /// 获取某篇文章的共享引用（详情/编辑页应持有此引用而非自行查询）
  ArticleModel? getRef(int id) {
    return ref.read(articleStateProvider.notifier).getArticleRef(id);
  }

  /// 获取文章列表
  List<ArticleModel> getArticles() {
    return ref.read(articleStateProvider).articles;
  }

  /// 是否正在加载
  bool isLoading() {
    return ref.read(articleStateProvider).isLoading;
  }

  // ========================================================================
  // 公开 API - 搜索功能
  // ========================================================================

  /// 切换搜索状态
  void toggleSearchState() {
    final appState = ref.read(appGlobalStateProvider.notifier);
    appState.toggleSearchBar();
  }

  /// 执行搜索
  Future<void> searchArticles(TextEditingController searchController) async {
    final query = searchController.text.trim();
    if (query.isEmpty) {
      clearAllFilters(searchController);
      return;
    }

    final articleState = ref.read(articleStateProvider.notifier);
    articleState.setGlobalSearch(query);
    await reloadArticles();
  }

  // ========================================================================
  // 公开 API - 过滤功能
  // ========================================================================

  /// 切换收藏过滤
  void toggleFavorite(bool value) {
    state = state.copyWith(onlyFavorite: value);
    reloadArticles();
  }

  /// 按标签过滤
  void filterByTag(int id, String name) {
    state = state.copyWith(tagId: id, tagName: name, selectedFilterDate: null);
    reloadArticles();
  }

  /// 按日期过滤
  void filterByDate(DateTime date) {
    final selectedDay = DateTime(date.year, date.month, date.day);
    state = state.copyWith(selectedFilterDate: selectedDay, tagId: -1, tagName: '', onlyFavorite: false);
    reloadArticles();
  }

  /// 清除所有过滤条件
  void clearAllFilters([TextEditingController? searchController]) {
    state = state.copyWith(tagId: -1, tagName: '', onlyFavorite: false, selectedFilterDate: null);

    searchController?.clear();

    final articleState = ref.read(articleStateProvider.notifier);
    articleState.clearGlobalSearch();
    reloadArticles();
  }

  // ========================================================================
  // 公开 API - UI 辅助方法
  // ========================================================================

  /// 获取页面标题（根据当前过滤状态）
  String getTitle([TextEditingController? searchController]) {
    final articleState = ref.read(articleStateProvider);
    final searchQuery = articleState.globalSearchQuery.isNotEmpty
        ? articleState.globalSearchQuery
        : (searchController?.text.trim() ?? '');

    return switch ((
      articleState.globalSearchQuery.isNotEmpty,
      state.tagName.isNotEmpty,
      state.onlyFavorite,
      state.selectedFilterDate != null,
    )) {
      (true, _, _, _) => 'article.search_result'.t.replaceAll('{query}', searchQuery),
      (_, true, _, _) => 'article.filter_by_tag'.t.replaceAll('{tag}', state.tagName),
      (_, _, true, _) => 'article.favorite_articles'.t,
      (_, _, _, true) => 'article.filter_by_date'.t,
      _ => 'article.all_articles'.t,
    };
  }

  /// 是否存在任一过滤条件
  bool hasActiveFilters() {
    final articleState = ref.read(articleStateProvider);
    return articleState.globalSearchQuery.isNotEmpty ||
        state.tagName.isNotEmpty ||
        state.onlyFavorite ||
        state.selectedFilterDate != null;
  }

  /// 获取每天文章数量统计（用于日历视图）
  Map<DateTime, int> getDailyArticleCounts() {
    return ArticleRepository.i.getArticleDailyCounts();
  }

  // ========================================================================
  // 公开 API - 日历功能
  // ========================================================================

  /// 日历上一月
  void calendarPreviousMonth() {
    final current = state.calendarDisplayedMonth ?? DateTime.now();
    state = state.copyWith(calendarDisplayedMonth: DateTime(current.year, current.month - 1, 1));
  }

  /// 日历下一月
  void calendarNextMonth() {
    final current = state.calendarDisplayedMonth ?? DateTime.now();
    state = state.copyWith(calendarDisplayedMonth: DateTime(current.year, current.month + 1, 1));
  }

  /// 选择日历日期
  void selectCalendarDate(DateTime date) {
    state = state.copyWith(calendarSelectedDate: date);
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

  // ========================================================================
  // 应用生命周期处理
  // ========================================================================

  /// 处理应用恢复
  Future<void> handleAppResume(ScrollController? scrollController) async {
    if (scrollController == null || !scrollController.hasClients) return;

    final isAtTop = scrollController.position.pixels <= 30;
    final lastRefresh = state.lastRefreshTime ?? DateTime.now();
    final isDataStale = DateTime.now().difference(lastRefresh).inMinutes >= _staleDataThresholdMinutes;

    if (isAtTop || isDataStale) {
      await reloadArticles();
    }
  }

  // ========================================================================
  // 私有方法
  // ========================================================================

  /// 处理文章更新事件
  void _handleArticleUpdateEvent(ArticleUpdateEvent event) {
    // 忽略空事件
    if (event is ArticleUpdateEventNone) return;

    logger.d("[Articles] 检测到文章事件: $event");

    switch (event) {
      case ArticleUpdateEventCreated(:final article):
        mergeArticle(article);
        break;
      case ArticleUpdateEventUpdated(:final article):
        updateArticle(article.id);
        break;
      case ArticleUpdateEventDeleted(:final articleId):
        removeArticle(articleId);
        break;
      case ArticleUpdateEventNone():
        // 不需要处理
        break;
    }

    // 重置事件，防止重复处理
    ref.read(articleStateProvider.notifier).clearArticleUpdateEvent();
  }

  /// 加载更多文章（向后滚动）
  Future<void> loadMoreArticles() => _loadAdjacentArticles(loadAfter: true);

  /// 加载之前的文章（向前滚动）
  Future<void> loadPreviousArticles() => _loadAdjacentArticles(loadAfter: false);

  /// 通用相邻分页加载逻辑
  Future<void> _loadAdjacentArticles({required bool loadAfter}) async {
    final articles = getArticles();
    if (articles.isEmpty) return;

    final anchorId = loadAfter ? articles.last.id : articles.first.id;
    const pageSize = PaginationConfig.defaultPageSize;

    logger.i(loadAfter ? '加载ID:$anchorId之后的$pageSize篇文章' : '加载ID:$anchorId之前的$pageSize篇文章');

    final query = _buildQueryParams();
    final articleState = ref.read(articleStateProvider.notifier);

    await articleState.loadArticles(
      keyword: query.keyword,
      favorite: query.favorite,
      tagIds: query.tagIds,
      startDate: query.startDate,
      endDate: query.endDate,
      referenceId: anchorId,
      isGreaterThan: loadAfter ? false : true,
      pageSize: pageSize,
    );
  }

  /// 构建查询参数
  _QueryParams _buildQueryParams() {
    final articleState = ref.read(articleStateProvider);
    final keyword = articleState.globalSearchQuery.isNotEmpty ? articleState.globalSearchQuery.trim() : null;

    return _QueryParams(
      keyword: keyword,
      favorite: state.onlyFavorite ? true : null,
      tagIds: state.tagId > 0 ? [state.tagId] : null,
      startDate: state.selectedFilterDate,
      endDate: state.selectedFilterDate != null ? _endOfDay(state.selectedFilterDate!) : null,
    );
  }

  /// 获取指定日期的结束时间
  DateTime _endOfDay(DateTime date) => DateTime(date.year, date.month, date.day, 23, 59, 59);
}

/// 查询参数封装类
class _QueryParams {
  final String? keyword;
  final bool? favorite;
  final List<int>? tagIds;
  final DateTime? startDate;
  final DateTime? endDate;

  _QueryParams({this.keyword, this.favorite, this.tagIds, this.startDate, this.endDate});
}
