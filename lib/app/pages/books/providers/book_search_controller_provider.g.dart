// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'book_search_controller_provider.dart';

// GENERATED CODE - DO NOT MODIFY BY HAND
// ignore_for_file: type=lint, type=warning
/// BookSearchController Provider

@ProviderFor(BookSearchController)
final bookSearchControllerProvider = BookSearchControllerProvider._();

/// BookSearchController Provider
final class BookSearchControllerProvider
    extends $NotifierProvider<BookSearchController, BookSearchControllerState> {
  /// BookSearchController Provider
  BookSearchControllerProvider._()
    : super(
        from: null,
        argument: null,
        retry: null,
        name: r'bookSearchControllerProvider',
        isAutoDispose: true,
        dependencies: null,
        $allTransitiveDependencies: null,
      );

  @override
  String debugGetCreateSourceHash() => _$bookSearchControllerHash();

  @$internal
  @override
  BookSearchController create() => BookSearchController();

  /// {@macro riverpod.override_with_value}
  Override overrideWithValue(BookSearchControllerState value) {
    return $ProviderOverride(
      origin: this,
      providerOverride: $SyncValueProvider<BookSearchControllerState>(value),
    );
  }
}

String _$bookSearchControllerHash() =>
    r'fa34d7a00428b9d8363333076881ab33b477fb41';

/// BookSearchController Provider

abstract class _$BookSearchController
    extends $Notifier<BookSearchControllerState> {
  BookSearchControllerState build();
  @$mustCallSuper
  @override
  void runBuild() {
    final ref =
        this.ref as $Ref<BookSearchControllerState, BookSearchControllerState>;
    final element =
        ref.element
            as $ClassProviderElement<
              AnyNotifier<BookSearchControllerState, BookSearchControllerState>,
              BookSearchControllerState,
              Object?,
              Object?
            >;
    element.handleCreate(ref, build);
  }
}
