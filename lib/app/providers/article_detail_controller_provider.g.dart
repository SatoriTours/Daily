// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'article_detail_controller_provider.dart';

// **************************************************************************
// RiverpodGenerator
// **************************************************************************

// GENERATED CODE - DO NOT MODIFY BY HAND
// ignore_for_file: type=lint, type=warning
/// ArticleDetailController Provider

@ProviderFor(ArticleDetailController)
final articleDetailControllerProvider = ArticleDetailControllerProvider._();

/// ArticleDetailController Provider
final class ArticleDetailControllerProvider
    extends
        $NotifierProvider<
          ArticleDetailController,
          ArticleDetailControllerState
        > {
  /// ArticleDetailController Provider
  ArticleDetailControllerProvider._()
    : super(
        from: null,
        argument: null,
        retry: null,
        name: r'articleDetailControllerProvider',
        isAutoDispose: true,
        dependencies: null,
        $allTransitiveDependencies: null,
      );

  @override
  String debugGetCreateSourceHash() => _$articleDetailControllerHash();

  @$internal
  @override
  ArticleDetailController create() => ArticleDetailController();

  /// {@macro riverpod.override_with_value}
  Override overrideWithValue(ArticleDetailControllerState value) {
    return $ProviderOverride(
      origin: this,
      providerOverride: $SyncValueProvider<ArticleDetailControllerState>(value),
    );
  }
}

String _$articleDetailControllerHash() =>
    r'ddf4052af49a744f43be37ec087e38ddd63bd640';

/// ArticleDetailController Provider

abstract class _$ArticleDetailController
    extends $Notifier<ArticleDetailControllerState> {
  ArticleDetailControllerState build();
  @$mustCallSuper
  @override
  void runBuild() {
    final ref =
        this.ref
            as $Ref<ArticleDetailControllerState, ArticleDetailControllerState>;
    final element =
        ref.element
            as $ClassProviderElement<
              AnyNotifier<
                ArticleDetailControllerState,
                ArticleDetailControllerState
              >,
              ArticleDetailControllerState,
              Object?,
              Object?
            >;
    element.handleCreate(ref, build);
  }
}
