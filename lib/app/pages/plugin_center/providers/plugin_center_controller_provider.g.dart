// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'plugin_center_controller_provider.dart';

// **************************************************************************
// RiverpodGenerator
// **************************************************************************

// GENERATED CODE - DO NOT MODIFY BY HAND
// ignore_for_file: type=lint, type=warning
/// PluginCenterController Provider

@ProviderFor(PluginCenterController)
final pluginCenterControllerProvider = PluginCenterControllerProvider._();

/// PluginCenterController Provider
final class PluginCenterControllerProvider
    extends
        $NotifierProvider<PluginCenterController, PluginCenterControllerState> {
  /// PluginCenterController Provider
  PluginCenterControllerProvider._()
    : super(
        from: null,
        argument: null,
        retry: null,
        name: r'pluginCenterControllerProvider',
        isAutoDispose: true,
        dependencies: null,
        $allTransitiveDependencies: null,
      );

  @override
  String debugGetCreateSourceHash() => _$pluginCenterControllerHash();

  @$internal
  @override
  PluginCenterController create() => PluginCenterController();

  /// {@macro riverpod.override_with_value}
  Override overrideWithValue(PluginCenterControllerState value) {
    return $ProviderOverride(
      origin: this,
      providerOverride: $SyncValueProvider<PluginCenterControllerState>(value),
    );
  }
}

String _$pluginCenterControllerHash() =>
    r'c81b500a73136d0346b143568c05abae15c8091c';

/// PluginCenterController Provider

abstract class _$PluginCenterController
    extends $Notifier<PluginCenterControllerState> {
  PluginCenterControllerState build();
  @$mustCallSuper
  @override
  void runBuild() {
    final ref =
        this.ref
            as $Ref<PluginCenterControllerState, PluginCenterControllerState>;
    final element =
        ref.element
            as $ClassProviderElement<
              AnyNotifier<
                PluginCenterControllerState,
                PluginCenterControllerState
              >,
              PluginCenterControllerState,
              Object?,
              Object?
            >;
    element.handleCreate(ref, build);
  }
}
