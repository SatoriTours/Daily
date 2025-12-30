// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'left_bar_controller_provider.dart';

// **************************************************************************
// RiverpodGenerator
// **************************************************************************

// GENERATED CODE - DO NOT MODIFY BY HAND
// ignore_for_file: type=lint, type=warning
/// LeftBarController Provider

@ProviderFor(LeftBarController)
final leftBarControllerProvider = LeftBarControllerProvider._();

/// LeftBarController Provider
final class LeftBarControllerProvider
    extends $NotifierProvider<LeftBarController, LeftBarControllerState> {
  /// LeftBarController Provider
  LeftBarControllerProvider._()
    : super(
        from: null,
        argument: null,
        retry: null,
        name: r'leftBarControllerProvider',
        isAutoDispose: true,
        dependencies: null,
        $allTransitiveDependencies: null,
      );

  @override
  String debugGetCreateSourceHash() => _$leftBarControllerHash();

  @$internal
  @override
  LeftBarController create() => LeftBarController();

  /// {@macro riverpod.override_with_value}
  Override overrideWithValue(LeftBarControllerState value) {
    return $ProviderOverride(
      origin: this,
      providerOverride: $SyncValueProvider<LeftBarControllerState>(value),
    );
  }
}

String _$leftBarControllerHash() => r'4a8fa5d47bbccd4b50989b49a65ae65980f646c0';

/// LeftBarController Provider

abstract class _$LeftBarController extends $Notifier<LeftBarControllerState> {
  LeftBarControllerState build();
  @$mustCallSuper
  @override
  void runBuild() {
    final ref =
        this.ref as $Ref<LeftBarControllerState, LeftBarControllerState>;
    final element =
        ref.element
            as $ClassProviderElement<
              AnyNotifier<LeftBarControllerState, LeftBarControllerState>,
              LeftBarControllerState,
              Object?,
              Object?
            >;
    element.handleCreate(ref, build);
  }
}
