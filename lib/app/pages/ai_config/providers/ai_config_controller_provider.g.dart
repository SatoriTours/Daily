// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'ai_config_controller_provider.dart';

// GENERATED CODE - DO NOT MODIFY BY HAND
// ignore_for_file: type=lint, type=warning
/// AIConfigController Provider

@ProviderFor(AIConfigController)
final aIConfigControllerProvider = AIConfigControllerProvider._();

/// AIConfigController Provider
final class AIConfigControllerProvider
    extends $NotifierProvider<AIConfigController, AIConfigControllerState> {
  /// AIConfigController Provider
  AIConfigControllerProvider._()
    : super(
        from: null,
        argument: null,
        retry: null,
        name: r'aIConfigControllerProvider',
        isAutoDispose: true,
        dependencies: null,
        $allTransitiveDependencies: null,
      );

  @override
  String debugGetCreateSourceHash() => _$aIConfigControllerHash();

  @$internal
  @override
  AIConfigController create() => AIConfigController();

  /// {@macro riverpod.override_with_value}
  Override overrideWithValue(AIConfigControllerState value) {
    return $ProviderOverride(
      origin: this,
      providerOverride: $SyncValueProvider<AIConfigControllerState>(value),
    );
  }
}

String _$aIConfigControllerHash() =>
    r'52ce0cbea913e8c2ae4cc8df88b57f88953622cc';

/// AIConfigController Provider

abstract class _$AIConfigController extends $Notifier<AIConfigControllerState> {
  AIConfigControllerState build();
  @$mustCallSuper
  @override
  void runBuild() {
    final ref =
        this.ref as $Ref<AIConfigControllerState, AIConfigControllerState>;
    final element =
        ref.element
            as $ClassProviderElement<
              AnyNotifier<AIConfigControllerState, AIConfigControllerState>,
              AIConfigControllerState,
              Object?,
              Object?
            >;
    element.handleCreate(ref, build);
  }
}
