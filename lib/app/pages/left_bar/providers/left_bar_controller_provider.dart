/// Left Bar Controller Provider
///
/// 侧边栏控制器，管理标签列表展开状态。

library;

import 'package:freezed_annotation/freezed_annotation.dart';
import 'package:riverpod_annotation/riverpod_annotation.dart';

import 'package:daily_satori/app/data/data.dart';

part 'left_bar_controller_provider.freezed.dart';
part 'left_bar_controller_provider.g.dart';

/// LeftBarController 状态
@freezed
abstract class LeftBarControllerState with _$LeftBarControllerState {
  const factory LeftBarControllerState({
    /// 标签列表是否展开
    @Default(true) bool isTagsExpanded,
  }) = _LeftBarControllerState;
}

/// LeftBarController Provider
@riverpod
class LeftBarController extends _$LeftBarController {
  @override
  LeftBarControllerState build() {
    return const LeftBarControllerState();
  }

  /// 切换标签展开状态
  void toggleTagsExpanded() {
    state = state.copyWith(isTagsExpanded: !state.isTagsExpanded);
  }
}

/// 侧边栏标签列表 Provider
///
/// Derived provider，从 TagRepository 获取所有标签
@riverpod
List<TagModel> leftBarTags(Ref ref) {
  return TagRepository.i.allModels();
}
