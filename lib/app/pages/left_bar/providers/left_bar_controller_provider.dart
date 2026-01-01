/// Left Bar Controller Provider
///
/// 侧边栏控制器，管理标签列表和展开状态。

library;

import 'package:freezed_annotation/freezed_annotation.dart';
import 'package:riverpod_annotation/riverpod_annotation.dart';

import 'package:daily_satori/app/data/index.dart';

part 'left_bar_controller_provider.freezed.dart';
part 'left_bar_controller_provider.g.dart';

/// LeftBarController 状态
@freezed
abstract class LeftBarControllerState with _$LeftBarControllerState {
  const factory LeftBarControllerState({
    /// 标签列表是否展开
    @Default(true) bool isTagsExpanded,
    /// 所有标签列表
    required List<TagModel> tags,
  }) = _LeftBarControllerState;
}

/// LeftBarController Provider
@riverpod
class LeftBarController extends _$LeftBarController {
  @override
  LeftBarControllerState build() {
    final tags = TagRepository.i.allModels();
    return LeftBarControllerState(tags: tags);
  }

  /// 获取所有标签
  List<TagModel> getTags() {
    return TagRepository.i.allModels();
  }

  /// 切换标签展开状态
  void toggleTagsExpanded() {
    state = state.copyWith(isTagsExpanded: !state.isTagsExpanded);
  }
}
