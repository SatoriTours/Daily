/// Articles 派生状态 Providers
library;

import 'package:daily_satori/app_exports.dart';
import 'package:riverpod_annotation/riverpod_annotation.dart';

import 'articles_controller_provider.dart';

part 'articles_derived_providers.g.dart';

/// 计算显示标题
@riverpod
String displayTitle(Ref ref) {
  final controllerState = ref.watch(articlesControllerProvider);
  final articleState = ref.watch(articleStateProvider);
  final globalSearchQuery = articleState.globalSearchQuery;
  return switch ((
    globalSearchQuery.isNotEmpty,
    controllerState.tagName.isNotEmpty,
    controllerState.onlyFavorite,
    controllerState.selectedFilterDate != null,
  )) {
    (true, _, _, _) => 'article.search_result'.t.replaceAll(
      '{query}',
      globalSearchQuery,
    ),
    (_, true, _, _) => 'article.filter_by_tag'.t.replaceAll(
      '{tag}',
      controllerState.tagName,
    ),
    (_, _, true, _) => 'article.favorite_articles'.t,
    (_, _, _, true) => 'article.filter_by_date'.t,
    _ => 'article.all_articles'.t,
  };
}

/// 是否存在筛选条件
@riverpod
bool hasFilters(Ref ref) {
  final controllerState = ref.watch(articlesControllerProvider);
  final articleState = ref.watch(articleStateProvider);
  final globalSearchQuery = articleState.globalSearchQuery;
  return globalSearchQuery.isNotEmpty ||
      controllerState.tagName.isNotEmpty ||
      controllerState.onlyFavorite ||
      controllerState.selectedFilterDate != null;
}
