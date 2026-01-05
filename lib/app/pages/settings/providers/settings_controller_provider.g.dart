// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'settings_controller_provider.dart';

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
    r'7045f0cc8bb792adf8f6c11032ff808e55fab1c8';

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

/// Web 服务密码 Provider

@ProviderFor(webServerPassword)
final webServerPasswordProvider = WebServerPasswordProvider._();

/// Web 服务密码 Provider

final class WebServerPasswordProvider
    extends $FunctionalProvider<String, String, String>
    with $Provider<String> {
  /// Web 服务密码 Provider
  WebServerPasswordProvider._()
    : super(
        from: null,
        argument: null,
        retry: null,
        name: r'webServerPasswordProvider',
        isAutoDispose: true,
        dependencies: null,
        $allTransitiveDependencies: null,
      );

  @override
  String debugGetCreateSourceHash() => _$webServerPasswordHash();

  @$internal
  @override
  $ProviderElement<String> $createElement($ProviderPointer pointer) =>
      $ProviderElement(pointer);

  @override
  String create(Ref ref) {
    return webServerPassword(ref);
  }

  /// {@macro riverpod.override_with_value}
  Override overrideWithValue(String value) {
    return $ProviderOverride(
      origin: this,
      providerOverride: $SyncValueProvider<String>(value),
    );
  }
}

String _$webServerPasswordHash() => r'166f4c5aad00ea1210b97109a0d6fa8f43658e8f';
