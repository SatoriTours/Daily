// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'articles_controller_provider.dart';

// **************************************************************************
// RiverpodGenerator
// **************************************************************************

// GENERATED CODE - DO NOT MODIFY BY HAND
// ignore_for_file: type=lint, type=warning
/// ArticlesController Provider
///
/// 管理文章列表页的状态和逻辑

@ProviderFor(ArticlesController)
final articlesControllerProvider = ArticlesControllerProvider._();

/// ArticlesController Provider
///
/// 管理文章列表页的状态和逻辑
final class ArticlesControllerProvider
    extends $NotifierProvider<ArticlesController, ArticlesControllerState> {
  /// ArticlesController Provider
  ///
  /// 管理文章列表页的状态和逻辑
  ArticlesControllerProvider._()
    : super(
        from: null,
        argument: null,
        retry: null,
        name: r'articlesControllerProvider',
        isAutoDispose: true,
        dependencies: null,
        $allTransitiveDependencies: null,
      );

  @override
  String debugGetCreateSourceHash() => _$articlesControllerHash();

  @$internal
  @override
  ArticlesController create() => ArticlesController();

  /// {@macro riverpod.override_with_value}
  Override overrideWithValue(ArticlesControllerState value) {
    return $ProviderOverride(
      origin: this,
      providerOverride: $SyncValueProvider<ArticlesControllerState>(value),
    );
  }
}

String _$articlesControllerHash() =>
    r'2985e9c5cd1281de9aba55a8ed325e33a7bf462c';

/// ArticlesController Provider
///
/// 管理文章列表页的状态和逻辑

abstract class _$ArticlesController extends $Notifier<ArticlesControllerState> {
  ArticlesControllerState build();
  @$mustCallSuper
  @override
  void runBuild() {
    final ref =
        this.ref as $Ref<ArticlesControllerState, ArticlesControllerState>;
    final element =
        ref.element
            as $ClassProviderElement<
              AnyNotifier<ArticlesControllerState, ArticlesControllerState>,
              ArticlesControllerState,
              Object?,
              Object?
            >;
    element.handleCreate(ref, build);
  }
}

/// 页面标题 Provider

@ProviderFor(articlesTitle)
final articlesTitleProvider = ArticlesTitleProvider._();

/// 页面标题 Provider

final class ArticlesTitleProvider
    extends $FunctionalProvider<String, String, String>
    with $Provider<String> {
  /// 页面标题 Provider
  ArticlesTitleProvider._()
    : super(
        from: null,
        argument: null,
        retry: null,
        name: r'articlesTitleProvider',
        isAutoDispose: true,
        dependencies: null,
        $allTransitiveDependencies: null,
      );

  @override
  String debugGetCreateSourceHash() => _$articlesTitleHash();

  @$internal
  @override
  $ProviderElement<String> $createElement($ProviderPointer pointer) =>
      $ProviderElement(pointer);

  @override
  String create(Ref ref) {
    return articlesTitle(ref);
  }

  /// {@macro riverpod.override_with_value}
  Override overrideWithValue(String value) {
    return $ProviderOverride(
      origin: this,
      providerOverride: $SyncValueProvider<String>(value),
    );
  }
}

String _$articlesTitleHash() => r'5ea3551852fd2c25f09be9cba6b80e0f0956f01a';

/// 是否存在筛选条件 Provider

@ProviderFor(articlesHasFilters)
final articlesHasFiltersProvider = ArticlesHasFiltersProvider._();

/// 是否存在筛选条件 Provider

final class ArticlesHasFiltersProvider
    extends $FunctionalProvider<bool, bool, bool>
    with $Provider<bool> {
  /// 是否存在筛选条件 Provider
  ArticlesHasFiltersProvider._()
    : super(
        from: null,
        argument: null,
        retry: null,
        name: r'articlesHasFiltersProvider',
        isAutoDispose: true,
        dependencies: null,
        $allTransitiveDependencies: null,
      );

  @override
  String debugGetCreateSourceHash() => _$articlesHasFiltersHash();

  @$internal
  @override
  $ProviderElement<bool> $createElement($ProviderPointer pointer) =>
      $ProviderElement(pointer);

  @override
  bool create(Ref ref) {
    return articlesHasFilters(ref);
  }

  /// {@macro riverpod.override_with_value}
  Override overrideWithValue(bool value) {
    return $ProviderOverride(
      origin: this,
      providerOverride: $SyncValueProvider<bool>(value),
    );
  }
}

String _$articlesHasFiltersHash() =>
    r'0efaef1157cee79d3d2290512098be401358c39e';
