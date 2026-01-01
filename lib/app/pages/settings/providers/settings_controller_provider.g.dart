// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'settings_controller_provider.dart';

// **************************************************************************
// RiverpodGenerator
// **************************************************************************

// GENERATED CODE - DO NOT MODIFY BY HAND
// ignore_for_file: type=lint, type=warning
/// SettingsController Provider

@ProviderFor(SettingsController)
final settingsControllerProvider = SettingsControllerProvider._();

/// SettingsController Provider
final class SettingsControllerProvider
    extends $NotifierProvider<SettingsController, SettingsControllerState> {
  /// SettingsController Provider
  SettingsControllerProvider._()
    : super(
        from: null,
        argument: null,
        retry: null,
        name: r'settingsControllerProvider',
        isAutoDispose: true,
        dependencies: null,
        $allTransitiveDependencies: null,
      );

  @override
  String debugGetCreateSourceHash() => _$settingsControllerHash();

  @$internal
  @override
  SettingsController create() => SettingsController();

  /// {@macro riverpod.override_with_value}
  Override overrideWithValue(SettingsControllerState value) {
    return $ProviderOverride(
      origin: this,
      providerOverride: $SyncValueProvider<SettingsControllerState>(value),
    );
  }
}

String _$settingsControllerHash() =>
    r'461afe0eaf87ed670878f68a2f765bc96e9cda0d';

/// SettingsController Provider

abstract class _$SettingsController extends $Notifier<SettingsControllerState> {
  SettingsControllerState build();
  @$mustCallSuper
  @override
  void runBuild() {
    final ref =
        this.ref as $Ref<SettingsControllerState, SettingsControllerState>;
    final element =
        ref.element
            as $ClassProviderElement<
              AnyNotifier<SettingsControllerState, SettingsControllerState>,
              SettingsControllerState,
              Object?,
              Object?
            >;
    element.handleCreate(ref, build);
  }
}
