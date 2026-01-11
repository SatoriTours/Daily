// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'home_controller_provider.dart';

// **************************************************************************
// RiverpodGenerator
// **************************************************************************

// GENERATED CODE - DO NOT MODIFY BY HAND
// ignore_for_file: type=lint, type=warning

@ProviderFor(HomeController)
final homeControllerProvider = HomeControllerProvider._();

final class HomeControllerProvider
    extends $NotifierProvider<HomeController, HomeControllerState> {
  HomeControllerProvider._()
    : super(
        from: null,
        argument: null,
        retry: null,
        name: r'homeControllerProvider',
        isAutoDispose: true,
        dependencies: null,
        $allTransitiveDependencies: null,
      );

  @override
  String debugGetCreateSourceHash() => _$homeControllerHash();

  @$internal
  @override
  HomeController create() => HomeController();

  /// {@macro riverpod.override_with_value}
  Override overrideWithValue(HomeControllerState value) {
    return $ProviderOverride(
      origin: this,
      providerOverride: $SyncValueProvider<HomeControllerState>(value),
    );
  }
}

String _$homeControllerHash() => r'dd21833263a770e359301cad31f7c20167fdee8f';

abstract class _$HomeController extends $Notifier<HomeControllerState> {
  HomeControllerState build();
  @$mustCallSuper
  @override
  void runBuild() {
    final ref = this.ref as $Ref<HomeControllerState, HomeControllerState>;
    final element =
        ref.element
            as $ClassProviderElement<
              AnyNotifier<HomeControllerState, HomeControllerState>,
              HomeControllerState,
              Object?,
              Object?
            >;
    element.handleCreate(ref, build);
  }
}
