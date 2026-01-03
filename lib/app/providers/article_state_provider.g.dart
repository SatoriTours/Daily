// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'article_state_provider.dart';

// **************************************************************************
// RiverpodGenerator
// **************************************************************************

// GENERATED CODE - DO NOT MODIFY BY HAND
// ignore_for_file: type=lint, type=warning
/// 文章状态 Provider
///
/// 使用 keepAlive: true 保持 provider 存活，确保能接收服务层的更新通知

@ProviderFor(ArticleState)
final articleStateProvider = ArticleStateProvider._();

/// 文章状态 Provider
///
/// 使用 keepAlive: true 保持 provider 存活，确保能接收服务层的更新通知
final class ArticleStateProvider
    extends $NotifierProvider<ArticleState, ArticleStateModel> {
  /// 文章状态 Provider
  ///
  /// 使用 keepAlive: true 保持 provider 存活，确保能接收服务层的更新通知
  ArticleStateProvider._()
    : super(
        from: null,
        argument: null,
        retry: null,
        name: r'articleStateProvider',
        isAutoDispose: false,
        dependencies: null,
        $allTransitiveDependencies: null,
      );

  @override
  String debugGetCreateSourceHash() => _$articleStateHash();

  @$internal
  @override
  ArticleState create() => ArticleState();

  /// {@macro riverpod.override_with_value}
  Override overrideWithValue(ArticleStateModel value) {
    return $ProviderOverride(
      origin: this,
      providerOverride: $SyncValueProvider<ArticleStateModel>(value),
    );
  }
}

String _$articleStateHash() => r'4c3ef1959b0aa64db222906201e4e61a384032ce';

/// 文章状态 Provider
///
/// 使用 keepAlive: true 保持 provider 存活，确保能接收服务层的更新通知

abstract class _$ArticleState extends $Notifier<ArticleStateModel> {
  ArticleStateModel build();
  @$mustCallSuper
  @override
  void runBuild() {
    final ref = this.ref as $Ref<ArticleStateModel, ArticleStateModel>;
    final element =
        ref.element
            as $ClassProviderElement<
              AnyNotifier<ArticleStateModel, ArticleStateModel>,
              ArticleStateModel,
              Object?,
              Object?
            >;
    element.handleCreate(ref, build);
  }
}

/// 所有标签列表 Provider
///
/// Derived provider，从 TagRepository 获取所有标签

@ProviderFor(articleAllTags)
final articleAllTagsProvider = ArticleAllTagsProvider._();

/// 所有标签列表 Provider
///
/// Derived provider，从 TagRepository 获取所有标签

final class ArticleAllTagsProvider
    extends $FunctionalProvider<List<TagModel>, List<TagModel>, List<TagModel>>
    with $Provider<List<TagModel>> {
  /// 所有标签列表 Provider
  ///
  /// Derived provider，从 TagRepository 获取所有标签
  ArticleAllTagsProvider._()
    : super(
        from: null,
        argument: null,
        retry: null,
        name: r'articleAllTagsProvider',
        isAutoDispose: true,
        dependencies: null,
        $allTransitiveDependencies: null,
      );

  @override
  String debugGetCreateSourceHash() => _$articleAllTagsHash();

  @$internal
  @override
  $ProviderElement<List<TagModel>> $createElement($ProviderPointer pointer) =>
      $ProviderElement(pointer);

  @override
  List<TagModel> create(Ref ref) {
    return articleAllTags(ref);
  }

  /// {@macro riverpod.override_with_value}
  Override overrideWithValue(List<TagModel> value) {
    return $ProviderOverride(
      origin: this,
      providerOverride: $SyncValueProvider<List<TagModel>>(value),
    );
  }
}

String _$articleAllTagsHash() => r'018afd68479a06d3ce0182cd7743f0d6ff45e7e0';

/// 文章日期统计 Provider
///
/// Derived provider，从 ArticleRepository 获取文章日期统计

@ProviderFor(articleDailyCounts)
final articleDailyCountsProvider = ArticleDailyCountsProvider._();

/// 文章日期统计 Provider
///
/// Derived provider，从 ArticleRepository 获取文章日期统计

final class ArticleDailyCountsProvider
    extends
        $FunctionalProvider<
          Map<DateTime, int>,
          Map<DateTime, int>,
          Map<DateTime, int>
        >
    with $Provider<Map<DateTime, int>> {
  /// 文章日期统计 Provider
  ///
  /// Derived provider，从 ArticleRepository 获取文章日期统计
  ArticleDailyCountsProvider._()
    : super(
        from: null,
        argument: null,
        retry: null,
        name: r'articleDailyCountsProvider',
        isAutoDispose: true,
        dependencies: null,
        $allTransitiveDependencies: null,
      );

  @override
  String debugGetCreateSourceHash() => _$articleDailyCountsHash();

  @$internal
  @override
  $ProviderElement<Map<DateTime, int>> $createElement(
    $ProviderPointer pointer,
  ) => $ProviderElement(pointer);

  @override
  Map<DateTime, int> create(Ref ref) {
    return articleDailyCounts(ref);
  }

  /// {@macro riverpod.override_with_value}
  Override overrideWithValue(Map<DateTime, int> value) {
    return $ProviderOverride(
      origin: this,
      providerOverride: $SyncValueProvider<Map<DateTime, int>>(value),
    );
  }
}

String _$articleDailyCountsHash() =>
    r'a90c5716f334614bdf7b4f414f78af0520c06bc3';
