// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'share_dialog_controller_provider.dart';

// **************************************************************************
// RiverpodGenerator
// **************************************************************************

// GENERATED CODE - DO NOT MODIFY BY HAND
// ignore_for_file: type=lint, type=warning
/// ShareDialogController Provider

@ProviderFor(ShareDialogController)
final shareDialogControllerProvider = ShareDialogControllerProvider._();

/// ShareDialogController Provider
final class ShareDialogControllerProvider
    extends
        $NotifierProvider<ShareDialogController, ShareDialogControllerState> {
  /// ShareDialogController Provider
  ShareDialogControllerProvider._()
    : super(
        from: null,
        argument: null,
        retry: null,
        name: r'shareDialogControllerProvider',
        isAutoDispose: true,
        dependencies: null,
        $allTransitiveDependencies: null,
      );

  @override
  String debugGetCreateSourceHash() => _$shareDialogControllerHash();

  @$internal
  @override
  ShareDialogController create() => ShareDialogController();

  /// {@macro riverpod.override_with_value}
  Override overrideWithValue(ShareDialogControllerState value) {
    return $ProviderOverride(
      origin: this,
      providerOverride: $SyncValueProvider<ShareDialogControllerState>(value),
    );
  }
}

String _$shareDialogControllerHash() =>
    r'74005665fd22d388a85be3812e7fd404336f2503';

/// ShareDialogController Provider

abstract class _$ShareDialogController
    extends $Notifier<ShareDialogControllerState> {
  ShareDialogControllerState build();
  @$mustCallSuper
  @override
  void runBuild() {
    final ref =
        this.ref
            as $Ref<ShareDialogControllerState, ShareDialogControllerState>;
    final element =
        ref.element
            as $ClassProviderElement<
              AnyNotifier<
                ShareDialogControllerState,
                ShareDialogControllerState
              >,
              ShareDialogControllerState,
              Object?,
              Object?
            >;
    element.handleCreate(ref, build);
  }
}
