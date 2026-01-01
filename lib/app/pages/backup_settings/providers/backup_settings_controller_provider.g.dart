// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'backup_settings_controller_provider.dart';

// **************************************************************************
// RiverpodGenerator
// **************************************************************************

// GENERATED CODE - DO NOT MODIFY BY HAND
// ignore_for_file: type=lint, type=warning
/// BackupSettingsController Provider

@ProviderFor(BackupSettingsController)
final backupSettingsControllerProvider = BackupSettingsControllerProvider._();

/// BackupSettingsController Provider
final class BackupSettingsControllerProvider
    extends
        $NotifierProvider<
          BackupSettingsController,
          BackupSettingsControllerState
        > {
  /// BackupSettingsController Provider
  BackupSettingsControllerProvider._()
    : super(
        from: null,
        argument: null,
        retry: null,
        name: r'backupSettingsControllerProvider',
        isAutoDispose: true,
        dependencies: null,
        $allTransitiveDependencies: null,
      );

  @override
  String debugGetCreateSourceHash() => _$backupSettingsControllerHash();

  @$internal
  @override
  BackupSettingsController create() => BackupSettingsController();

  /// {@macro riverpod.override_with_value}
  Override overrideWithValue(BackupSettingsControllerState value) {
    return $ProviderOverride(
      origin: this,
      providerOverride: $SyncValueProvider<BackupSettingsControllerState>(
        value,
      ),
    );
  }
}

String _$backupSettingsControllerHash() =>
    r'4331cdf95ec3957c06369c17df834083f2208151';

/// BackupSettingsController Provider

abstract class _$BackupSettingsController
    extends $Notifier<BackupSettingsControllerState> {
  BackupSettingsControllerState build();
  @$mustCallSuper
  @override
  void runBuild() {
    final ref =
        this.ref
            as $Ref<
              BackupSettingsControllerState,
              BackupSettingsControllerState
            >;
    final element =
        ref.element
            as $ClassProviderElement<
              AnyNotifier<
                BackupSettingsControllerState,
                BackupSettingsControllerState
              >,
              BackupSettingsControllerState,
              Object?,
              Object?
            >;
    element.handleCreate(ref, build);
  }
}
