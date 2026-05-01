/// Home Controller Provider
///
/// 主页控制器，管理底部导航栏状态和页面切换逻辑。

library;

import 'package:flutter/foundation.dart';

import 'package:daily_satori/app_exports.dart';
import 'package:freezed_annotation/freezed_annotation.dart';
import 'package:riverpod_annotation/riverpod_annotation.dart';

part 'home_controller_provider.freezed.dart';
part 'home_controller_provider.g.dart';

/// HomeController 状态
///
/// 包含主页的所有状态数据
@freezed
abstract class HomeControllerState with _$HomeControllerState {
  const factory HomeControllerState({
    /// 当前选中的页面索引 0: 文章  1: 日记  2: 读书  3: AI助手  4: 设置
    @Default(0) int currentIndex,
  }) = _HomeControllerState;
}

@riverpod
class HomeController extends _$HomeController {
  @override
  HomeControllerState build() {
    return const HomeControllerState();
  }

  /// 切换页面 [index] 目标页面索引
  void changePage(int index) {
    if (index == state.currentIndex) return;

    // 更新状态
    state = state.copyWith(currentIndex: index);
  }
}
