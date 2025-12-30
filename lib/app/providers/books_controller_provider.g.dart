// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'books_controller_provider.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

_BooksControllerState _$BooksControllerStateFromJson(
  Map<String, dynamic> json,
) => _BooksControllerState(
  lastRefreshTime: json['lastRefreshTime'] == null
      ? null
      : DateTime.parse(json['lastRefreshTime'] as String),
);

Map<String, dynamic> _$BooksControllerStateToJson(
  _BooksControllerState instance,
) => <String, dynamic>{
  'lastRefreshTime': instance.lastRefreshTime?.toIso8601String(),
};

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

String _$booksControllerHash() => r'620af5c518c4b8ee84ba60ab27e6dbe39581b917';

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
