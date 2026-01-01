// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'ai_config_edit_controller_provider.dart';

// **************************************************************************
// RiverpodGenerator
// **************************************************************************

// GENERATED CODE - DO NOT MODIFY BY HAND
// ignore_for_file: type=lint, type=warning
/// AIConfigEditController Provider

@ProviderFor(AIConfigEditController)
final aIConfigEditControllerProvider = AIConfigEditControllerProvider._();

/// AIConfigEditController Provider
final class AIConfigEditControllerProvider
    extends
        $NotifierProvider<AIConfigEditController, AIConfigEditControllerState> {
  /// AIConfigEditController Provider
  AIConfigEditControllerProvider._()
    : super(
        from: null,
        argument: null,
        retry: null,
        name: r'aIConfigEditControllerProvider',
        isAutoDispose: true,
        dependencies: null,
        $allTransitiveDependencies: null,
      );

  @override
  String debugGetCreateSourceHash() => _$aIConfigEditControllerHash();

  @$internal
  @override
  AIConfigEditController create() => AIConfigEditController();

  /// {@macro riverpod.override_with_value}
  Override overrideWithValue(AIConfigEditControllerState value) {
    return $ProviderOverride(
      origin: this,
      providerOverride: $SyncValueProvider<AIConfigEditControllerState>(value),
    );
  }
}

String _$aIConfigEditControllerHash() =>
    r'aca4dee07ddfbdd71797151f31eb33233f47d5f1';

/// AIConfigEditController Provider

abstract class _$AIConfigEditController
    extends $Notifier<AIConfigEditControllerState> {
  AIConfigEditControllerState build();
  @$mustCallSuper
  @override
  void runBuild() {
    final ref =
        this.ref
            as $Ref<AIConfigEditControllerState, AIConfigEditControllerState>;
    final element =
        ref.element
            as $ClassProviderElement<
              AnyNotifier<
                AIConfigEditControllerState,
                AIConfigEditControllerState
              >,
              AIConfigEditControllerState,
              Object?,
              Object?
            >;
    element.handleCreate(ref, build);
  }
}
