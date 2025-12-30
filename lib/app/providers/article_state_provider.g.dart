// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'article_state_provider.dart';

// **************************************************************************
// RiverpodGenerator
// **************************************************************************

// GENERATED CODE - DO NOT MODIFY BY HAND
// ignore_for_file: type=lint, type=warning
/// 文章状态 Provider

@ProviderFor(ArticleState)
final articleStateProvider = ArticleStateProvider._();

/// 文章状态 Provider
final class ArticleStateProvider
    extends $NotifierProvider<ArticleState, ArticleStateModel> {
  /// 文章状态 Provider
  ArticleStateProvider._()
    : super(
        from: null,
        argument: null,
        retry: null,
        name: r'articleStateProvider',
        isAutoDispose: true,
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

String _$articleStateHash() => r'2ac0a849f6faa57d29106ae7838faef61b1355b8';

/// 文章状态 Provider

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
