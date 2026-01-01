// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'books_controller_provider.dart';

// **************************************************************************
// RiverpodGenerator
// **************************************************************************

// GENERATED CODE - DO NOT MODIFY BY HAND
// ignore_for_file: type=lint, type=warning
/// BooksController Provider

@ProviderFor(BooksController)
final booksControllerProvider = BooksControllerProvider._();

/// BooksController Provider
final class BooksControllerProvider
    extends $NotifierProvider<BooksController, BooksControllerState> {
  /// BooksController Provider
  BooksControllerProvider._()
    : super(
        from: null,
        argument: null,
        retry: null,
        name: r'booksControllerProvider',
        isAutoDispose: true,
        dependencies: null,
        $allTransitiveDependencies: null,
      );

  @override
  String debugGetCreateSourceHash() => _$booksControllerHash();

  @$internal
  @override
  BooksController create() => BooksController();

  /// {@macro riverpod.override_with_value}
  Override overrideWithValue(BooksControllerState value) {
    return $ProviderOverride(
      origin: this,
      providerOverride: $SyncValueProvider<BooksControllerState>(value),
    );
  }
}

String _$booksControllerHash() => r'15fdf0a76af89692f05d530da5e84493c80303f4';

/// BooksController Provider

abstract class _$BooksController extends $Notifier<BooksControllerState> {
  BooksControllerState build();
  @$mustCallSuper
  @override
  void runBuild() {
    final ref = this.ref as $Ref<BooksControllerState, BooksControllerState>;
    final element =
        ref.element
            as $ClassProviderElement<
              AnyNotifier<BooksControllerState, BooksControllerState>,
              BooksControllerState,
              Object?,
              Object?
            >;
    element.handleCreate(ref, build);
  }
}
