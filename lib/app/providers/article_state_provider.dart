/// 文章状态管理 Provider
///
/// Riverpod 版本的 ArticleStateService，管理文章列表、活跃文章和更新事件。
library;

import 'dart:async';

import 'package:freezed_annotation/freezed_annotation.dart';
import 'package:riverpod_annotation/riverpod_annotation.dart';

import 'package:daily_satori/app/config/app_config.dart';
import 'package:daily_satori/app/data/data.dart';
import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/services/web_content/web_content_notifier.dart';

part 'article_state_provider.freezed.dart';
part 'article_state_provider.g.dart';

/// 文章事件类型
@freezed
abstract class ArticleUpdateEvent with _$ArticleUpdateEvent {
  const factory ArticleUpdateEvent.none() = ArticleUpdateEventNone;
  const factory ArticleUpdateEvent.created(ArticleModel article) = ArticleUpdateEventCreated;
  const factory ArticleUpdateEvent.updated(ArticleModel article) = ArticleUpdateEventUpdated;
  const factory ArticleUpdateEvent.deleted(int articleId) = ArticleUpdateEventDeleted;
}

/// 文章状态模型
@freezed
abstract class ArticleStateModel with _$ArticleStateModel {
  const ArticleStateModel._();

  const factory ArticleStateModel({
    @Default([]) List<ArticleModel> articles,
    @Default(false) bool isLoading,
    @Default(ArticleUpdateEvent.none()) ArticleUpdateEvent articleUpdateEvent,
    @Default('') String globalSearchQuery,
    @Default(false) bool isGlobalSearchActive,
  }) = _ArticleStateModel;
}

/// 文章状态 Provider
///
/// 使用 keepAlive: true 保持 provider 存活，确保能接收服务层的更新通知
@Riverpod(keepAlive: true)
class ArticleState extends _$ArticleState {
  StreamSubscription<int>? _articleUpdateSubscription;

  @override
  ArticleStateModel build() {
    // 监听 WebContentNotifier 的文章更新事件流
    _articleUpdateSubscription = WebContentNotifier.i.onArticleUpdated.listen(_onArticleUpdatedFromService);

    // 在 provider 销毁时取消订阅
    ref.onDispose(() {
      _articleUpdateSubscription?.cancel();
      logger.d('ArticleState Provider 销毁，取消订阅');
    });

    return const ArticleStateModel();
  }

  /// 处理来自服务层的文章更新通知
  void _onArticleUpdatedFromService(int articleId) {
    logger.i('[ArticleState] 收到服务层文章更新通知: #$articleId');

    // 从数据库获取最新文章数据
    final article = ArticleRepository.i.findModel(articleId);
    if (article == null) {
      logger.w('[ArticleState] 找不到文章: #$articleId');
      return;
    }

    // 更新列表中的文章
    updateArticleInList(articleId);

    // 发布更新事件，通知详情页刷新
    notifyArticleUpdated(article);
  }

  /// 通知文章更新
  void notifyArticleUpdated(ArticleModel article) {
    logger.i('通知文章更新: ${article.singleLineTitle} (ID: ${article.id})');

    // 发布文章更新事件
    state = state.copyWith(articleUpdateEvent: ArticleUpdateEvent.updated(article));
  }

  /// 通知文章删除
  void notifyArticleDeleted(int articleId) {
    logger.i('通知文章删除: ID: $articleId');

    // 发布文章删除事件
    state = state.copyWith(articleUpdateEvent: ArticleUpdateEvent.deleted(articleId));
  }

  /// 通知文章创建
  void notifyArticleCreated(ArticleModel article) {
    logger.i('通知文章创建: ${article.singleLineTitle} (ID: ${article.id})');

    // 发布文章创建事件
    state = state.copyWith(articleUpdateEvent: ArticleUpdateEvent.created(article));
  }

  /// 清除文章更新事件（防止重复处理）
  void clearArticleUpdateEvent() {
    state = state.copyWith(articleUpdateEvent: const ArticleUpdateEvent.none());
  }

  /// 设置全局搜索
  void setGlobalSearch(String query) {
    state = state.copyWith(globalSearchQuery: query, isGlobalSearchActive: query.isNotEmpty);
    logger.i('设置全局搜索: $query');
  }

  /// 清除全局搜索
  void clearGlobalSearch() {
    state = state.copyWith(globalSearchQuery: '', isGlobalSearchActive: false);
    logger.i('清除全局搜索');
  }

  /// 加载文章列表
  Future<void> loadArticles({
    String? keyword,
    bool? favorite,
    List<int>? tagIds,
    DateTime? startDate,
    DateTime? endDate,
    int? referenceId,
    bool? isGreaterThan,
    int pageSize = PaginationConfig.defaultPageSize,
  }) async {
    state = state.copyWith(isLoading: true);
    try {
      final result = ArticleRepository.i.findArticles(
        keyword: keyword,
        isFavorite: favorite,
        tagIds: tagIds,
        startDate: startDate,
        endDate: endDate,
        referenceId: referenceId,
        isGreaterThan: isGreaterThan,
        limit: pageSize,
      );

      List<ArticleModel> updatedArticles;
      if (referenceId == null) {
        // 全新加载，替换所有数据
        updatedArticles = result;
      } else if (isGreaterThan == false) {
        // 向后加载更多
        updatedArticles = [...state.articles, ...result];
      } else {
        // 向前加载
        updatedArticles = [...result, ...state.articles];
      }

      state = state.copyWith(articles: updatedArticles);
    } finally {
      state = state.copyWith(isLoading: false);
    }
  }

  /// 重新加载文章列表（清空后重新加载）
  Future<void> reloadArticles({
    String? keyword,
    bool? favorite,
    List<int>? tagIds,
    DateTime? startDate,
    DateTime? endDate,
    int pageSize = PaginationConfig.defaultPageSize,
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

    final articles = List<ArticleModel>.from(state.articles);
    final index = articles.indexWhere((item) => item.id == id);
    if (index != -1) {
      articles[index] = article;
      state = state.copyWith(articles: articles);
    }
  }

  /// 从列表中移除文章
  void removeArticleFromList(int id) {
    final updatedArticles = state.articles.where((article) => article.id != id).toList();
    state = state.copyWith(articles: updatedArticles);
    logger.i('从列表中移除文章: ID=$id');
  }

  /// 合并/插入文章（用于新增或外部更新）
  void mergeArticle(ArticleModel model) {
    final articles = List<ArticleModel>.from(state.articles);
    final index = articles.indexWhere((item) => item.id == model.id);

    if (index == -1) {
      articles.insert(0, model);
      logger.i('插入新文章到列表: ${model.singleLineTitle} (ID: ${model.id})');
    } else {
      articles[index] = model;
      logger.i('更新列表中的文章: ${model.singleLineTitle} (ID: ${model.id})');
    }

    state = state.copyWith(articles: articles);
  }

  /// 获取某篇文章的共享引用
  ArticleModel? getArticleRef(int id) {
    final index = state.articles.indexWhere((item) => item.id == id);
    if (index == -1) {
      // 如果列表中没有，从数据库加载
      return ArticleRepository.i.findModel(id);
    }

    return state.articles[index];
  }
}

// 派生 Providers (Derived State)

/// 所有标签列表 Provider
///
/// Derived provider，从 TagRepository 获取所有标签
@riverpod
List<TagModel> articleAllTags(Ref ref) {
  return TagRepository.i.allModels();
}

/// 文章日期统计 Provider
///
/// Derived provider，从 ArticleRepository 获取文章日期统计
@riverpod
Map<DateTime, int> articleDailyCounts(Ref ref) {
  return ArticleRepository.i.getArticleDailyCounts();
}
