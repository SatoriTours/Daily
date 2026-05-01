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
    r'd141f623ecad032f5d5a80a94ee60c9fb9383fc8';

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

/// 备份进度监听

@ProviderFor(BackupProgress)
final backupProgressProvider = BackupProgressProvider._();

/// 备份进度监听
final class BackupProgressProvider
    extends $NotifierProvider<BackupProgress, double> {
  /// 备份进度监听
  BackupProgressProvider._()
    : super(
        from: null,
        argument: null,
        retry: null,
        name: r'backupProgressProvider',
        isAutoDispose: true,
        dependencies: null,
        $allTransitiveDependencies: null,
      );

  @override
  String debugGetCreateSourceHash() => _$backupProgressHash();

  @$internal
  @override
  BackupProgress create() => BackupProgress();

  /// {@macro riverpod.override_with_value}
  Override overrideWithValue(double value) {
    return $ProviderOverride(
      origin: this,
      providerOverride: $SyncValueProvider<double>(value),
    );
  }
}

String _$backupProgressHash() => r'30a46065ac64e46170cedf7c4cc381675c840ef5';

/// 备份进度监听

abstract class _$BackupProgress extends $Notifier<double> {
  double build();
  @$mustCallSuper
  @override
  void runBuild() {
    final ref = this.ref as $Ref<double, double>;
    final element =
        ref.element
            as $ClassProviderElement<
              AnyNotifier<double, double>,
              double,
              Object?,
              Object?
            >;
    element.handleCreate(ref, build);
  }
}
