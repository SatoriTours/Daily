// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'weekly_summary_controller_provider.dart';

// **************************************************************************
// RiverpodGenerator
// **************************************************************************

// GENERATED CODE - DO NOT MODIFY BY HAND
// ignore_for_file: type=lint, type=warning
/// WeeklySummaryController Provider

@ProviderFor(WeeklySummaryController)
final weeklySummaryControllerProvider = WeeklySummaryControllerProvider._();

/// WeeklySummaryController Provider
final class WeeklySummaryControllerProvider
    extends
        $NotifierProvider<
          WeeklySummaryController,
          WeeklySummaryControllerState
        > {
  /// WeeklySummaryController Provider
  WeeklySummaryControllerProvider._()
    : super(
        from: null,
        argument: null,
        retry: null,
        name: r'weeklySummaryControllerProvider',
        isAutoDispose: true,
        dependencies: null,
        $allTransitiveDependencies: null,
      );

  @override
  String debugGetCreateSourceHash() => _$weeklySummaryControllerHash();

  @$internal
  @override
  WeeklySummaryController create() => WeeklySummaryController();

  /// {@macro riverpod.override_with_value}
  Override overrideWithValue(WeeklySummaryControllerState value) {
    return $ProviderOverride(
      origin: this,
      providerOverride: $SyncValueProvider<WeeklySummaryControllerState>(value),
    );
  }
}

String _$weeklySummaryControllerHash() =>
    r'8ec665b2bca9809344fe572c05d2d5557a91ac5c';

/// WeeklySummaryController Provider

abstract class _$WeeklySummaryController
    extends $Notifier<WeeklySummaryControllerState> {
  WeeklySummaryControllerState build();
  @$mustCallSuper
  @override
  void runBuild() {
    final ref =
        this.ref
            as $Ref<WeeklySummaryControllerState, WeeklySummaryControllerState>;
    final element =
        ref.element
            as $ClassProviderElement<
              AnyNotifier<
                WeeklySummaryControllerState,
                WeeklySummaryControllerState
              >,
              WeeklySummaryControllerState,
              Object?,
              Object?
            >;
    element.handleCreate(ref, build);
  }
}
