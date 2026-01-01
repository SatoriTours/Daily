// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'diary_controller_provider.dart';

// **************************************************************************
// JsonSerializableGenerator
// **************************************************************************

_DiaryControllerState _$DiaryControllerStateFromJson(
  Map<String, dynamic> json,
) => _DiaryControllerState(
  selectedDate: json['selectedDate'] == null
      ? null
      : DateTime.parse(json['selectedDate'] as String),
  searchQuery: json['searchQuery'] as String? ?? '',
  isSearchVisible: json['isSearchVisible'] as bool? ?? false,
  selectedFilterDate: json['selectedFilterDate'] == null
      ? null
      : DateTime.parse(json['selectedFilterDate'] as String),
  currentTag: json['currentTag'] as String? ?? '',
  tags:
      (json['tags'] as List<dynamic>?)?.map((e) => e as String).toList() ??
      const [],
  isLoadingDiaries: json['isLoadingDiaries'] as bool? ?? false,
);

Map<String, dynamic> _$DiaryControllerStateToJson(
  _DiaryControllerState instance,
) => <String, dynamic>{
  'selectedDate': instance.selectedDate?.toIso8601String(),
  'searchQuery': instance.searchQuery,
  'isSearchVisible': instance.isSearchVisible,
  'selectedFilterDate': instance.selectedFilterDate?.toIso8601String(),
  'currentTag': instance.currentTag,
  'tags': instance.tags,
  'isLoadingDiaries': instance.isLoadingDiaries,
};

// **************************************************************************
// RiverpodGenerator
// **************************************************************************

// GENERATED CODE - DO NOT MODIFY BY HAND
// ignore_for_file: type=lint, type=warning
/// DiaryController Provider

@ProviderFor(DiaryController)
final diaryControllerProvider = DiaryControllerProvider._();

/// DiaryController Provider
final class DiaryControllerProvider
    extends $NotifierProvider<DiaryController, DiaryControllerState> {
  /// DiaryController Provider
  DiaryControllerProvider._()
    : super(
        from: null,
        argument: null,
        retry: null,
        name: r'diaryControllerProvider',
        isAutoDispose: true,
        dependencies: null,
        $allTransitiveDependencies: null,
      );

  @override
  String debugGetCreateSourceHash() => _$diaryControllerHash();

  @$internal
  @override
  DiaryController create() => DiaryController();

  /// {@macro riverpod.override_with_value}
  Override overrideWithValue(DiaryControllerState value) {
    return $ProviderOverride(
      origin: this,
      providerOverride: $SyncValueProvider<DiaryControllerState>(value),
    );
  }
}

String _$diaryControllerHash() => r'4d612903cde4b229c11292794919e0ed5f1c9424';

/// DiaryController Provider

abstract class _$DiaryController extends $Notifier<DiaryControllerState> {
  DiaryControllerState build();
  @$mustCallSuper
  @override
  void runBuild() {
    final ref = this.ref as $Ref<DiaryControllerState, DiaryControllerState>;
    final element =
        ref.element
            as $ClassProviderElement<
              AnyNotifier<DiaryControllerState, DiaryControllerState>,
              DiaryControllerState,
              Object?,
              Object?
            >;
    element.handleCreate(ref, build);
  }
}
