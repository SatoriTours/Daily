// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'first_launch_provider.dart';

// **************************************************************************
// RiverpodGenerator
// **************************************************************************

// GENERATED CODE - DO NOT MODIFY BY HAND
// ignore_for_file: type=lint, type=warning
/// 首次启动状态

@ProviderFor(FirstLaunchController)
final firstLaunchControllerProvider = FirstLaunchControllerProvider._();

/// 首次启动状态
final class FirstLaunchControllerProvider
    extends $NotifierProvider<FirstLaunchController, FirstLaunchState> {
  /// 首次启动状态
  FirstLaunchControllerProvider._()
    : super(
        from: null,
        argument: null,
        retry: null,
        name: r'firstLaunchControllerProvider',
        isAutoDispose: true,
        dependencies: null,
        $allTransitiveDependencies: null,
      );

  @override
  String debugGetCreateSourceHash() => _$firstLaunchControllerHash();

  @$internal
  @override
  FirstLaunchController create() => FirstLaunchController();

  /// {@macro riverpod.override_with_value}
  Override overrideWithValue(FirstLaunchState value) {
    return $ProviderOverride(
      origin: this,
      providerOverride: $SyncValueProvider<FirstLaunchState>(value),
    );
  }
}

String _$firstLaunchControllerHash() =>
    r'209c213752909d09a8fcd1e95589a985b6dbd9e2';

/// 首次启动状态

abstract class _$FirstLaunchController extends $Notifier<FirstLaunchState> {
  FirstLaunchState build();
  @$mustCallSuper
  @override
  void runBuild() {
    final ref = this.ref as $Ref<FirstLaunchState, FirstLaunchState>;
    final element =
        ref.element
            as $ClassProviderElement<
              AnyNotifier<FirstLaunchState, FirstLaunchState>,
              FirstLaunchState,
              Object?,
              Object?
            >;
    element.handleCreate(ref, build);
  }
}
