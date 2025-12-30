/// Home Controller Provider
///
/// 主页控制器，管理底部导航栏状态和页面切换逻辑。

library;

import 'package:flutter/foundation.dart';
import 'package:freezed_annotation/freezed_annotation.dart';
import 'package:riverpod_annotation/riverpod_annotation.dart';

import 'package:daily_satori/app/services/logger_service.dart';

part 'home_controller_provider.freezed.dart';
part 'home_controller_provider.g.dart';

/// HomeController 状态
///
/// 包含主页的所有状态数据
@freezed
abstract class HomeControllerState with _$HomeControllerState {
  /// 构造函数
  const factory HomeControllerState({
    /// 当前选中的页面索引
    /// 0: 文章页面
    /// 1: 日记页面
    /// 2: 读书页面
    /// 3: AI助手页面
    /// 4: 设置页面
    @Default(0) int currentIndex,
  }) = _HomeControllerState;

  /// 从 JSON 创建
  factory HomeControllerState.fromJson(Map<String, dynamic> json) =>
      _$HomeControllerStateFromJson(json);
}

/// HomeController Provider
///
/// 管理主页的状态和逻辑
@riverpod
class HomeController extends _$HomeController {
  // ========================================================================
  // 常量
  // ========================================================================

  static const String _tag = 'HomeController';

  // ========================================================================
  // 状态管理
  // ========================================================================

  @override
  HomeControllerState build() {
    _logPageInit();
    return const HomeControllerState();
  }

  // ========================================================================
  // 业务方法
  // ========================================================================

  /// 切换页面
  ///
  /// [index] 目标页面索引
  void changePage(int index) {
    if (index == state.currentIndex) return;

    final oldIndex = state.currentIndex;

    // 更新状态
    state = state.copyWith(currentIndex: index);

    if (kDebugMode) {
      logger.i('页面切换: $oldIndex -> $index [$_tag:${DateTime.now()}]');
    }
  }

  // ========================================================================
  // 私有方法
  // ========================================================================

  /// 记录页面初始化日志
  void _logPageInit() {
    if (kDebugMode) {
      logger.i('主页初始化完成 [$_tag:${DateTime.now()}]');
    }
  }
}
