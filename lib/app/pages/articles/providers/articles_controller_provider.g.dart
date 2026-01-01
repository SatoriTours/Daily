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
    r'4be1b4841a5a6e4b65e46c592eeecdaa38012502';

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
