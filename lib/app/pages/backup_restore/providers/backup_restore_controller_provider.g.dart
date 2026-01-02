// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'backup_restore_controller_provider.dart';

// **************************************************************************
// RiverpodGenerator
// **************************************************************************

// GENERATED CODE - DO NOT MODIFY BY HAND
// ignore_for_file: type=lint, type=warning
/// BackupRestoreController Provider

@ProviderFor(BackupRestoreController)
final backupRestoreControllerProvider = BackupRestoreControllerProvider._();

/// BackupRestoreController Provider
final class BackupRestoreControllerProvider
    extends
        $NotifierProvider<
          BackupRestoreController,
          BackupRestoreControllerState
        > {
  /// BackupRestoreController Provider
  BackupRestoreControllerProvider._()
    : super(
        from: null,
        argument: null,
        retry: null,
        name: r'backupRestoreControllerProvider',
        isAutoDispose: true,
        dependencies: null,
        $allTransitiveDependencies: null,
      );

  @override
  String debugGetCreateSourceHash() => _$backupRestoreControllerHash();

  @$internal
  @override
  BackupRestoreController create() => BackupRestoreController();

  /// {@macro riverpod.override_with_value}
  Override overrideWithValue(BackupRestoreControllerState value) {
    return $ProviderOverride(
      origin: this,
      providerOverride: $SyncValueProvider<BackupRestoreControllerState>(value),
    );
  }
}

String _$backupRestoreControllerHash() =>
    r'1a062a31fc13fa5106b1af43f622a6ad82f93579';

/// BackupRestoreController Provider

abstract class _$BackupRestoreController
    extends $Notifier<BackupRestoreControllerState> {
  BackupRestoreControllerState build();
  @$mustCallSuper
  @override
  void runBuild() {
    final ref =
        this.ref
            as $Ref<BackupRestoreControllerState, BackupRestoreControllerState>;
    final element =
        ref.element
            as $ClassProviderElement<
              AnyNotifier<
                BackupRestoreControllerState,
                BackupRestoreControllerState
              >,
              BackupRestoreControllerState,
              Object?,
              Object?
            >;
    element.handleCreate(ref, build);
  }
}
