// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'diary_controller_provider.dart';

// **************************************************************************
// RiverpodGenerator
// **************************************************************************

// GENERATED CODE - DO NOT MODIFY BY HAND
// ignore_for_file: type=lint, type=warning
/// DiaryController Provider

@ProviderFor(DiaryController)
final diaryControllerProvider = DiaryControllerProvider._();

/// DiaryController Provider
final class DiaryControllerProvider
    extends $NotifierProvider<DiaryController, DiaryControllerState> {
  /// DiaryController Provider
  DiaryControllerProvider._()
    : super(
        from: null,
        argument: null,
        retry: null,
        name: r'diaryControllerProvider',
        isAutoDispose: true,
        dependencies: null,
        $allTransitiveDependencies: null,
      );

  @override
  String debugGetCreateSourceHash() => _$diaryControllerHash();

  @$internal
  @override
  DiaryController create() => DiaryController();

  /// {@macro riverpod.override_with_value}
  Override overrideWithValue(DiaryControllerState value) {
    return $ProviderOverride(
      origin: this,
      providerOverride: $SyncValueProvider<DiaryControllerState>(value),
    );
  }
}

String _$diaryControllerHash() => r'6b1e1a06339c8b53a91ca7b64a865ff3eecfe801';

/// DiaryController Provider

abstract class _$DiaryController extends $Notifier<DiaryControllerState> {
  DiaryControllerState build();
  @$mustCallSuper
  @override
  void runBuild() {
    final ref = this.ref as $Ref<DiaryControllerState, DiaryControllerState>;
    final element =
        ref.element
            as $ClassProviderElement<
              AnyNotifier<DiaryControllerState, DiaryControllerState>,
              DiaryControllerState,
              Object?,
              Object?
            >;
    element.handleCreate(ref, build);
  }
}
