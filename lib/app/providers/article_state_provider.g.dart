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

String _$articleStateHash() => r'06154d199ef3b909cb6de5b0e767618fdf7097a9';

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
