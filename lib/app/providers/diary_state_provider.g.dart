// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'diary_state_provider.dart';

// **************************************************************************
// RiverpodGenerator
// **************************************************************************

// GENERATED CODE - DO NOT MODIFY BY HAND
// ignore_for_file: type=lint, type=warning
/// 日记状态 Provider

@ProviderFor(DiaryState)
final diaryStateProvider = DiaryStateProvider._();

/// 日记状态 Provider
final class DiaryStateProvider
    extends $NotifierProvider<DiaryState, DiaryStateModel> {
  /// 日记状态 Provider
  DiaryStateProvider._()
    : super(
        from: null,
        argument: null,
        retry: null,
        name: r'diaryStateProvider',
        isAutoDispose: true,
        dependencies: null,
        $allTransitiveDependencies: null,
      );

  @override
  String debugGetCreateSourceHash() => _$diaryStateHash();

  @$internal
  @override
  DiaryState create() => DiaryState();

  /// {@macro riverpod.override_with_value}
  Override overrideWithValue(DiaryStateModel value) {
    return $ProviderOverride(
      origin: this,
      providerOverride: $SyncValueProvider<DiaryStateModel>(value),
    );
  }
}

String _$diaryStateHash() => r'cf42a9631e4b7abc77024c1e66de45b076ec0f6f';

/// 日记状态 Provider

abstract class _$DiaryState extends $Notifier<DiaryStateModel> {
  DiaryStateModel build();
  @$mustCallSuper
  @override
  void runBuild() {
    final ref = this.ref as $Ref<DiaryStateModel, DiaryStateModel>;
    final element =
        ref.element
            as $ClassProviderElement<
              AnyNotifier<DiaryStateModel, DiaryStateModel>,
              DiaryStateModel,
              Object?,
              Object?
            >;
    element.handleCreate(ref, build);
  }
}
