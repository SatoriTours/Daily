/// Articles Controller Provider
///
/// 文章列表页面控制器，管理文章列表的UI状态和用户交互。
library;

import 'package:freezed_annotation/freezed_annotation.dart';

import 'package:daily_satori/app_exports.dart';
import 'package:riverpod_annotation/riverpod_annotation.dart';

import 'package:daily_satori/app/config/app_config.dart';

part 'articles_controller_provider.freezed.dart';
part 'articles_controller_provider.g.dart';

/// ArticlesController 状态
@freezed
abstract class ArticlesControllerState with _$ArticlesControllerState {
  const ArticlesControllerState._();

  const factory ArticlesControllerState({
    @Default(false) bool onlyFavorite,
    @Default(-1) int tagId,
    @Default('') String tagName,
    DateTime? selectedFilterDate,
    DateTime? lastRefreshTime,
  }) = _ArticlesControllerState;
}

/// ArticlesController Provider
@riverpod
class ArticlesController extends _$ArticlesController {
  static const int _staleDataThresholdMinutes = 60;

  @override
  ArticlesControllerState build() {
    _initialize();
    return ArticlesControllerState(lastRefreshTime: DateTime.now());
  }

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

  void removeArticle(int id) {
    ref.read(articleStateProvider.notifier).removeArticleFromList(id);
  }

  void updateArticle(int id) {
    ref.read(articleStateProvider.notifier).updateArticleInList(id);
  }

  void mergeArticle(ArticleModel model) {
    ref.read(articleStateProvider.notifier).mergeArticle(model);
  }

  void toggleSearchState() {
    ref.read(appGlobalStateProvider.notifier).toggleSearchBar();
  }

  Future<void> searchArticles(String query) async {
    if (query.trim().isEmpty) {
      clearAllFilters();
      return;
    }

    ref.read(articleStateProvider.notifier).setGlobalSearch(query.trim());
    await reloadArticles();
  }

  void toggleFavorite(bool value) {
    state = state.copyWith(onlyFavorite: value);
    reloadArticles();
  }

  void filterByTag(int id, String name) {
    state = state.copyWith(tagId: id, tagName: name, selectedFilterDate: null);
    reloadArticles();
  }

  void filterByDate(DateTime date) {
    final selectedDay = DateTime(date.year, date.month, date.day);
    state = state.copyWith(
      selectedFilterDate: selectedDay,
      tagId: -1,
      tagName: '',
      onlyFavorite: false,
    );
    reloadArticles();
  }

  void clearAllFilters() {
    state = state.copyWith(
      tagId: -1,
      tagName: '',
      onlyFavorite: false,
      selectedFilterDate: null,
    );

    ref.read(articleStateProvider.notifier).clearGlobalSearch();
    reloadArticles();
  }

  Future<void> handleAppResume(bool isAtTop) async {
    final lastRefresh = state.lastRefreshTime ?? DateTime.now();
    final isDataStale =
        DateTime.now().difference(lastRefresh).inMinutes >=
        _staleDataThresholdMinutes;

    if (isAtTop || isDataStale) {
      await reloadArticles();
    }
  }

  Future<void> loadMoreArticles() => _loadAdjacentArticles(loadAfter: true);
  Future<void> loadPreviousArticles() =>
      _loadAdjacentArticles(loadAfter: false);

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

  _QueryParams _buildQueryParams() {
    final articleState = ref.read(articleStateProvider);
    return _QueryParams(
      keyword: articleState.globalSearchQuery.isNotEmpty
          ? articleState.globalSearchQuery.trim()
          : null,
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

  _QueryParams({
    this.keyword,
    this.favorite,
    this.tagIds,
    this.startDate,
    this.endDate,
  });
}
