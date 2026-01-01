/// Articles Controller Provider
///
/// 文章列表控制器，负责文章列表的展示、搜索、过滤、分页加载等功能。
/// 通过 ArticleState provider 管理文章数据状态。

library;

import 'package:flutter/material.dart';
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

    /// 最后刷新时间
    DateTime? lastRefreshTime,

    /// ScrollController
    ScrollController? scrollController,

    /// TextEditingController
    TextEditingController? searchController,

    /// FocusNode
    FocusNode? searchFocusNode,
  }) = _ArticlesControllerState;
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
    _initialize();

    return ArticlesControllerState(
      lastRefreshTime: DateTime.now(),
      scrollController: _createScrollController(),
      searchController: TextEditingController(),
      searchFocusNode: FocusNode(),
    );
  }

  /// 初始化
  void _initialize() {
    ref.listen(articleStateProvider, (prev, next) {
      final prevEvent = prev?.articleUpdateEvent;
      final nextEvent = next.articleUpdateEvent;
      if (prevEvent != nextEvent) _handleArticleUpdateEvent(nextEvent);
    });

    Future.microtask(() {
      reloadArticles();
      AppUpgradeService.i.checkAndDownloadInBackground();
    });
  }

  ScrollController _createScrollController() {
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

  // ========================================================================
  // 公开 API - 数据操作
  // ========================================================================

  /// 重新加载文章列表
  Future<void> reloadArticles() async {
    state = state.copyWith(lastRefreshTime: DateTime.now());
    final query = _buildQueryParams();
    await ref
        .read(articleStateProvider.notifier)
        .reloadArticles(
          keyword: query.keyword,
          favorite: query.favorite,
          tagIds: query.tagIds,
          startDate: query.startDate,
          endDate: query.endDate,
        );
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

  /// 获取每天文章数量统计（用于日历视图）
  Map<DateTime, int> getDailyArticleCounts() => ArticleRepository.i.getArticleDailyCounts();

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
    if (event is ArticleUpdateEventNone) return;

    switch (event) {
      case ArticleUpdateEventCreated(:final article):
        mergeArticle(article);
      case ArticleUpdateEventUpdated(:final article):
        updateArticle(article.id);
      case ArticleUpdateEventDeleted(:final articleId):
        removeArticle(articleId);
      case ArticleUpdateEventNone():
        break;
    }
    ref.read(articleStateProvider.notifier).clearArticleUpdateEvent();
  }

  Future<void> loadMoreArticles() => _loadAdjacentArticles(loadAfter: true);
  Future<void> loadPreviousArticles() => _loadAdjacentArticles(loadAfter: false);

  Future<void> _loadAdjacentArticles({required bool loadAfter}) async {
    final articles = ref.read(articleStateProvider).articles;
    if (articles.isEmpty) return;

    final anchorId = loadAfter ? articles.last.id : articles.first.id;
    final query = _buildQueryParams();

    await ref
        .read(articleStateProvider.notifier)
        .loadArticles(
          keyword: query.keyword,
          favorite: query.favorite,
          tagIds: query.tagIds,
          startDate: query.startDate,
          endDate: query.endDate,
          referenceId: anchorId,
          isGreaterThan: !loadAfter,
          pageSize: PaginationConfig.defaultPageSize,
        );
  }

  _QueryParams _buildQueryParams() {
    final articleState = ref.read(articleStateProvider);
    return _QueryParams(
      keyword: articleState.globalSearchQuery.isNotEmpty ? articleState.globalSearchQuery.trim() : null,
      favorite: state.onlyFavorite ? true : null,
      tagIds: state.tagId > 0 ? [state.tagId] : null,
      startDate: state.selectedFilterDate,
      endDate: state.selectedFilterDate != null
          ? DateTime(
              state.selectedFilterDate!.year,
              state.selectedFilterDate!.month,
              state.selectedFilterDate!.day,
              23,
              59,
              59,
            )
          : null,
    );
  }
}

class _QueryParams {
  final String? keyword;
  final bool? favorite;
  final List<int>? tagIds;
  final DateTime? startDate;
  final DateTime? endDate;

  _QueryParams({this.keyword, this.favorite, this.tagIds, this.startDate, this.endDate});
}

// ============================================================================
// 派生 Providers (Derived State)
// ============================================================================

/// 页面标题 Provider
@riverpod
String articlesTitle(Ref ref) {
  final state = ref.watch(articlesControllerProvider);
  final articleState = ref.watch(articleStateProvider);
  final searchQuery = articleState.globalSearchQuery;

  return switch ((
    searchQuery.isNotEmpty,
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

/// 是否存在筛选条件 Provider
@riverpod
bool articlesHasFilters(Ref ref) {
  final state = ref.watch(articlesControllerProvider);
  final articleState = ref.watch(articleStateProvider);

  return articleState.globalSearchQuery.isNotEmpty ||
      state.tagName.isNotEmpty ||
      state.onlyFavorite ||
      state.selectedFilterDate != null;
}
