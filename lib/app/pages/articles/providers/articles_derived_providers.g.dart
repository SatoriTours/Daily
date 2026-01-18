// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'articles_derived_providers.dart';

// **************************************************************************
// RiverpodGenerator
// **************************************************************************

// GENERATED CODE - DO NOT MODIFY BY HAND
// ignore_for_file: type=lint, type=warning
/// 计算显示标题

@ProviderFor(displayTitle)
final displayTitleProvider = DisplayTitleProvider._();

/// 计算显示标题

final class DisplayTitleProvider
    extends $FunctionalProvider<String, String, String>
    with $Provider<String> {
  /// 计算显示标题
  DisplayTitleProvider._()
    : super(
        from: null,
        argument: null,
        retry: null,
        name: r'displayTitleProvider',
        isAutoDispose: true,
        dependencies: null,
        $allTransitiveDependencies: null,
      );

  @override
  String debugGetCreateSourceHash() => _$displayTitleHash();

  @$internal
  @override
  $ProviderElement<String> $createElement($ProviderPointer pointer) =>
      $ProviderElement(pointer);

  @override
  String create(Ref ref) {
    return displayTitle(ref);
  }

  /// {@macro riverpod.override_with_value}
  Override overrideWithValue(String value) {
    return $ProviderOverride(
      origin: this,
      providerOverride: $SyncValueProvider<String>(value),
    );
  }
}

String _$displayTitleHash() => r'71b7fb350e62d43500de646b7ed72eead32f3509';

/// 是否存在筛选条件

@ProviderFor(hasFilters)
final hasFiltersProvider = HasFiltersProvider._();

/// 是否存在筛选条件

final class HasFiltersProvider extends $FunctionalProvider<bool, bool, bool>
    with $Provider<bool> {
  /// 是否存在筛选条件
  HasFiltersProvider._()
    : super(
        from: null,
        argument: null,
        retry: null,
        name: r'hasFiltersProvider',
        isAutoDispose: true,
        dependencies: null,
        $allTransitiveDependencies: null,
      );

  @override
  String debugGetCreateSourceHash() => _$hasFiltersHash();

  @$internal
  @override
  $ProviderElement<bool> $createElement($ProviderPointer pointer) =>
      $ProviderElement(pointer);

  @override
  bool create(Ref ref) {
    return hasFilters(ref);
  }

  /// {@macro riverpod.override_with_value}
  Override overrideWithValue(bool value) {
    return $ProviderOverride(
      origin: this,
      providerOverride: $SyncValueProvider<bool>(value),
    );
  }
}

String _$hasFiltersHash() => r'3720dde28fa3e3b66a5dd5b9a0d362deb26c6285';
