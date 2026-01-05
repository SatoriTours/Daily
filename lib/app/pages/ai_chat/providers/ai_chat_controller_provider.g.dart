// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'ai_chat_controller_provider.dart';

// GENERATED CODE - DO NOT MODIFY BY HAND
// ignore_for_file: type=lint, type=warning

@ProviderFor(AIChatController)
final aIChatControllerProvider = AIChatControllerProvider._();

final class AIChatControllerProvider
    extends $NotifierProvider<AIChatController, AIChatControllerState> {
  AIChatControllerProvider._()
    : super(
        from: null,
        argument: null,
        retry: null,
        name: r'aIChatControllerProvider',
        isAutoDispose: true,
        dependencies: null,
        $allTransitiveDependencies: null,
      );

  @override
  String debugGetCreateSourceHash() => _$aIChatControllerHash();

  @$internal
  @override
  AIChatController create() => AIChatController();

  /// {@macro riverpod.override_with_value}
  Override overrideWithValue(AIChatControllerState value) {
    return $ProviderOverride(
      origin: this,
      providerOverride: $SyncValueProvider<AIChatControllerState>(value),
    );
  }
}

String _$aIChatControllerHash() => r'b87e693faf7b62473cd986a7f903ebdc7fa2409b';

abstract class _$AIChatController extends $Notifier<AIChatControllerState> {
  AIChatControllerState build();
  @$mustCallSuper
  @override
  void runBuild() {
    final ref = this.ref as $Ref<AIChatControllerState, AIChatControllerState>;
    final element =
        ref.element
            as $ClassProviderElement<
              AnyNotifier<AIChatControllerState, AIChatControllerState>,
              AIChatControllerState,
              Object?,
              Object?
            >;
    element.handleCreate(ref, build);
  }
}
