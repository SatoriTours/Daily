// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'app_state_provider.dart';

// **************************************************************************
// RiverpodGenerator
// **************************************************************************

// GENERATED CODE - DO NOT MODIFY BY HAND
// ignore_for_file: type=lint, type=warning
/// 全局应用状态 Provider

@ProviderFor(AppGlobalState)
final appGlobalStateProvider = AppGlobalStateProvider._();

/// 全局应用状态 Provider
final class AppGlobalStateProvider
    extends $NotifierProvider<AppGlobalState, AppStateModel> {
  /// 全局应用状态 Provider
  AppGlobalStateProvider._()
    : super(
        from: null,
        argument: null,
        retry: null,
        name: r'appGlobalStateProvider',
        isAutoDispose: true,
        dependencies: null,
        $allTransitiveDependencies: null,
      );

  @override
  String debugGetCreateSourceHash() => _$appGlobalStateHash();

  @$internal
  @override
  AppGlobalState create() => AppGlobalState();

  /// {@macro riverpod.override_with_value}
  Override overrideWithValue(AppStateModel value) {
    return $ProviderOverride(
      origin: this,
      providerOverride: $SyncValueProvider<AppStateModel>(value),
    );
  }
}

String _$appGlobalStateHash() => r'd2e97ce2fa1669446a9265cabfe924260606a6a9';

/// 全局应用状态 Provider

abstract class _$AppGlobalState extends $Notifier<AppStateModel> {
  AppStateModel build();
  @$mustCallSuper
  @override
  void runBuild() {
    final ref = this.ref as $Ref<AppStateModel, AppStateModel>;
    final element =
        ref.element
            as $ClassProviderElement<
              AnyNotifier<AppStateModel, AppStateModel>,
              AppStateModel,
              Object?,
              Object?
            >;
    element.handleCreate(ref, build);
  }
}
