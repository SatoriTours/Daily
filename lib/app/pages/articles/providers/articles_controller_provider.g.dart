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
    r'c0c6b73c3a4be9b48be9482a1c3960afa528d008';

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

/// 计算显示标题

@ProviderFor(displayTitle)
final displayTitleProvider = DisplayTitleProvider._();

/// 计算显示标题

final class DisplayTitleProvider
    extends $FunctionalProvider<String, String, String>
    with $Provider<String> {
  /// 计算显示标题
  DisplayTitleProvider._()
    : super(
        from: null,
        argument: null,
        retry: null,
        name: r'displayTitleProvider',
        isAutoDispose: true,
        dependencies: null,
        $allTransitiveDependencies: null,
      );

  @override
  String debugGetCreateSourceHash() => _$displayTitleHash();

  @$internal
  @override
  $ProviderElement<String> $createElement($ProviderPointer pointer) =>
      $ProviderElement(pointer);

  @override
  String create(Ref ref) {
    return displayTitle(ref);
  }

  /// {@macro riverpod.override_with_value}
  Override overrideWithValue(String value) {
    return $ProviderOverride(
      origin: this,
      providerOverride: $SyncValueProvider<String>(value),
    );
  }
}

String _$displayTitleHash() => r'f1ee747b7071a8d5c5f6ccaaa37b99980b0da649';

/// 是否存在筛选条件

@ProviderFor(hasFilters)
final hasFiltersProvider = HasFiltersProvider._();

/// 是否存在筛选条件

final class HasFiltersProvider extends $FunctionalProvider<bool, bool, bool>
    with $Provider<bool> {
  /// 是否存在筛选条件
  HasFiltersProvider._()
    : super(
        from: null,
        argument: null,
        retry: null,
        name: r'hasFiltersProvider',
        isAutoDispose: true,
        dependencies: null,
        $allTransitiveDependencies: null,
      );

  @override
  String debugGetCreateSourceHash() => _$hasFiltersHash();

  @$internal
  @override
  $ProviderElement<bool> $createElement($ProviderPointer pointer) =>
      $ProviderElement(pointer);

  @override
  bool create(Ref ref) {
    return hasFilters(ref);
  }

  /// {@macro riverpod.override_with_value}
  Override overrideWithValue(bool value) {
    return $ProviderOverride(
      origin: this,
      providerOverride: $SyncValueProvider<bool>(value),
    );
  }
}

String _$hasFiltersHash() => r'881657c61d8367cd748b2ab0c0e618cf65183e31';
