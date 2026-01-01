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
    r'5702e7204d5fa68b243a359e2bb71717f2733777';

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

String _$displayTitleHash() => r'0659fd43cf36fb8bb48b9e4db1a5464b39c89be1';

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

String _$hasFiltersHash() => r'f41ca78f6cce3a61ae30b3c6a53fc24604c1a165';
