// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'left_bar_controller_provider.dart';

// **************************************************************************
// RiverpodGenerator
// **************************************************************************

// GENERATED CODE - DO NOT MODIFY BY HAND
// ignore_for_file: type=lint, type=warning
/// LeftBarController Provider

@ProviderFor(LeftBarController)
final leftBarControllerProvider = LeftBarControllerProvider._();

/// LeftBarController Provider
final class LeftBarControllerProvider
    extends $NotifierProvider<LeftBarController, LeftBarControllerState> {
  /// LeftBarController Provider
  LeftBarControllerProvider._()
    : super(
        from: null,
        argument: null,
        retry: null,
        name: r'leftBarControllerProvider',
        isAutoDispose: true,
        dependencies: null,
        $allTransitiveDependencies: null,
      );

  @override
  String debugGetCreateSourceHash() => _$leftBarControllerHash();

  @$internal
  @override
  LeftBarController create() => LeftBarController();

  /// {@macro riverpod.override_with_value}
  Override overrideWithValue(LeftBarControllerState value) {
    return $ProviderOverride(
      origin: this,
      providerOverride: $SyncValueProvider<LeftBarControllerState>(value),
    );
  }
}

String _$leftBarControllerHash() => r'97eb85d65f59d5e0c7453e63330ed79688077844';

/// LeftBarController Provider

abstract class _$LeftBarController extends $Notifier<LeftBarControllerState> {
  LeftBarControllerState build();
  @$mustCallSuper
  @override
  void runBuild() {
    final ref =
        this.ref as $Ref<LeftBarControllerState, LeftBarControllerState>;
    final element =
        ref.element
            as $ClassProviderElement<
              AnyNotifier<LeftBarControllerState, LeftBarControllerState>,
              LeftBarControllerState,
              Object?,
              Object?
            >;
    element.handleCreate(ref, build);
  }
}

/// 侧边栏标签列表 Provider
///
/// Derived provider，从 TagRepository 获取所有标签

@ProviderFor(leftBarTags)
final leftBarTagsProvider = LeftBarTagsProvider._();

/// 侧边栏标签列表 Provider
///
/// Derived provider，从 TagRepository 获取所有标签

final class LeftBarTagsProvider
    extends $FunctionalProvider<List<TagModel>, List<TagModel>, List<TagModel>>
    with $Provider<List<TagModel>> {
  /// 侧边栏标签列表 Provider
  ///
  /// Derived provider，从 TagRepository 获取所有标签
  LeftBarTagsProvider._()
    : super(
        from: null,
        argument: null,
        retry: null,
        name: r'leftBarTagsProvider',
        isAutoDispose: true,
        dependencies: null,
        $allTransitiveDependencies: null,
      );

  @override
  String debugGetCreateSourceHash() => _$leftBarTagsHash();

  @$internal
  @override
  $ProviderElement<List<TagModel>> $createElement($ProviderPointer pointer) =>
      $ProviderElement(pointer);

  @override
  List<TagModel> create(Ref ref) {
    return leftBarTags(ref);
  }

  /// {@macro riverpod.override_with_value}
  Override overrideWithValue(List<TagModel> value) {
    return $ProviderOverride(
      origin: this,
      providerOverride: $SyncValueProvider<List<TagModel>>(value),
    );
  }
}

String _$leftBarTagsHash() => r'48401d179c4d85758cd566277b8baef6eb043338';
