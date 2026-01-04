// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'article_detail_controller_provider.dart';

// **************************************************************************
// RiverpodGenerator
// **************************************************************************

// GENERATED CODE - DO NOT MODIFY BY HAND
// ignore_for_file: type=lint, type=warning
/// ArticleDetailController Provider
/// 参数为 int 类型的文章 ID

@ProviderFor(ArticleDetailController)
final articleDetailControllerProvider = ArticleDetailControllerFamily._();

/// ArticleDetailController Provider
/// 参数为 int 类型的文章 ID
final class ArticleDetailControllerProvider
    extends
        $NotifierProvider<
          ArticleDetailController,
          ArticleDetailControllerState
        > {
  /// ArticleDetailController Provider
  /// 参数为 int 类型的文章 ID
  ArticleDetailControllerProvider._({
    required ArticleDetailControllerFamily super.from,
    required int super.argument,
  }) : super(
         retry: null,
         name: r'articleDetailControllerProvider',
         isAutoDispose: true,
         dependencies: null,
         $allTransitiveDependencies: null,
       );

  @override
  String debugGetCreateSourceHash() => _$articleDetailControllerHash();

  @override
  String toString() {
    return r'articleDetailControllerProvider'
        ''
        '($argument)';
  }

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

  @override
  bool operator ==(Object other) {
    return other is ArticleDetailControllerProvider &&
        other.argument == argument;
  }

  @override
  int get hashCode {
    return argument.hashCode;
  }
}

String _$articleDetailControllerHash() =>
    r'0ce2a839fd5804c6fdc7e8e585a9832f61784c36';

/// ArticleDetailController Provider
/// 参数为 int 类型的文章 ID

final class ArticleDetailControllerFamily extends $Family
    with
        $ClassFamilyOverride<
          ArticleDetailController,
          ArticleDetailControllerState,
          ArticleDetailControllerState,
          ArticleDetailControllerState,
          int
        > {
  ArticleDetailControllerFamily._()
    : super(
        retry: null,
        name: r'articleDetailControllerProvider',
        dependencies: null,
        $allTransitiveDependencies: null,
        isAutoDispose: true,
      );

  /// ArticleDetailController Provider
  /// 参数为 int 类型的文章 ID

  ArticleDetailControllerProvider call(int articleId) =>
      ArticleDetailControllerProvider._(argument: articleId, from: this);

  @override
  String toString() => r'articleDetailControllerProvider';
}

/// ArticleDetailController Provider
/// 参数为 int 类型的文章 ID

abstract class _$ArticleDetailController
    extends $Notifier<ArticleDetailControllerState> {
  late final _$args = ref.$arg as int;
  int get articleId => _$args;

  ArticleDetailControllerState build(int articleId);
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
    element.handleCreate(ref, () => build(_$args));
  }
}
