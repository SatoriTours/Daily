// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'books_state_provider.dart';

// GENERATED CODE - DO NOT MODIFY BY HAND
// ignore_for_file: type=lint, type=warning
/// 读书状态 Provider

@ProviderFor(BooksState)
final booksStateProvider = BooksStateProvider._();

/// 读书状态 Provider
final class BooksStateProvider
    extends $NotifierProvider<BooksState, BooksStateModel> {
  /// 读书状态 Provider
  BooksStateProvider._()
    : super(
        from: null,
        argument: null,
        retry: null,
        name: r'booksStateProvider',
        isAutoDispose: true,
        dependencies: null,
        $allTransitiveDependencies: null,
      );

  @override
  String debugGetCreateSourceHash() => _$booksStateHash();

  @$internal
  @override
  BooksState create() => BooksState();

  /// {@macro riverpod.override_with_value}
  Override overrideWithValue(BooksStateModel value) {
    return $ProviderOverride(
      origin: this,
      providerOverride: $SyncValueProvider<BooksStateModel>(value),
    );
  }
}

String _$booksStateHash() => r'2b00e354d7769a20f807b8e06d56b7db1cc3b185';

/// 读书状态 Provider

abstract class _$BooksState extends $Notifier<BooksStateModel> {
  BooksStateModel build();
  @$mustCallSuper
  @override
  void runBuild() {
    final ref = this.ref as $Ref<BooksStateModel, BooksStateModel>;
    final element =
        ref.element
            as $ClassProviderElement<
              AnyNotifier<BooksStateModel, BooksStateModel>,
              BooksStateModel,
              Object?,
              Object?
            >;
    element.handleCreate(ref, build);
  }
}
