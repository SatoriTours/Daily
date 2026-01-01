// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'settings_controller_provider.dart';

// **************************************************************************
// RiverpodGenerator
// **************************************************************************

// GENERATED CODE - DO NOT MODIFY BY HAND
// ignore_for_file: type=lint, type=warning
/// SettingsController Provider
///
/// 管理设置页的状态和逻辑

@ProviderFor(SettingsController)
final settingsControllerProvider = SettingsControllerProvider._();

/// SettingsController Provider
///
/// 管理设置页的状态和逻辑
final class SettingsControllerProvider
    extends $NotifierProvider<SettingsController, SettingsControllerState> {
  /// SettingsController Provider
  ///
  /// 管理设置页的状态和逻辑
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
    r'c561c225176ac6ba4e9bc78ce8efcd2eb533a669';

/// SettingsController Provider
///
/// 管理设置页的状态和逻辑

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
