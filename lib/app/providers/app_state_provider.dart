/// 全局应用状态管理 Provider
///
/// Riverpod 版本的 AppStateService，管理应用级别的状态。
library;

import 'package:freezed_annotation/freezed_annotation.dart';
import 'package:riverpod_annotation/riverpod_annotation.dart';

import 'package:daily_satori/app/services/logger_service.dart';

part 'app_state_provider.freezed.dart';
part 'app_state_provider.g.dart';

/// 应用状态模型
@freezed
abstract class AppStateModel with _$AppStateModel {
  const AppStateModel._();

  const factory AppStateModel({
    @Default(0) int currentNavIndex,
    @Default(false) bool isAppInBackground,
    required DateTime lastActiveTime,
    @Default(false) bool isGlobalLoading,
    @Default('') String globalErrorMessage,
    @Default('') String globalSuccessMessage,
    @Default('') String globalInfoMessage,
    @Default(false) bool isSearchBarVisible,
    @Default('') String currentPage,
  }) = _AppStateModel;
}

/// 全局应用状态 Provider
@riverpod
class AppGlobalState extends _$AppGlobalState {
  @override
  AppStateModel build() {
    return AppStateModel(lastActiveTime: DateTime.now());
  }

  /// 设置当前导航索引
  void setCurrentNavIndex(int index) {
    state = state.copyWith(currentNavIndex: index);
  }

  /// 设置应用后台状态
  void setAppBackground(bool isBackground) {
    if (isBackground != state.isAppInBackground) {
      state = state.copyWith(
        isAppInBackground: isBackground,
        lastActiveTime: isBackground ? state.lastActiveTime : DateTime.now(),
      );

      if (!isBackground) {
        logger.i('应用回到前台');
      } else {
        logger.i('应用进入后台');
      }
    }
  }

  /// 检查数据是否需要刷新（基于最后活跃时间）
  bool needsRefresh(Duration threshold) {
    return DateTime.now().difference(state.lastActiveTime) > threshold;
  }

  /// 显示全局加载状态
  void showGlobalLoading([String message = '加载中...']) {
    state = state.copyWith(isGlobalLoading: true);
    logger.i('显示全局加载: $message');
  }

  /// 隐藏全局加载状态
  void hideGlobalLoading() {
    state = state.copyWith(isGlobalLoading: false);
    logger.i('隐藏全局加载');
  }

  /// 显示全局错误消息
  void showGlobalError(String message) {
    state = state.copyWith(globalErrorMessage: message);
    logger.e('全局错误: $message');

    // 3秒后自动清除错误消息
    Future.delayed(const Duration(seconds: 3), () {
      if (state.globalErrorMessage == message) {
        state = state.copyWith(globalErrorMessage: '');
      }
    });
  }

  /// 显示全局成功消息
  void showGlobalSuccess(String message) {
    state = state.copyWith(globalSuccessMessage: message);
    logger.i('全局成功: $message');

    // 3秒后自动清除成功消息
    Future.delayed(const Duration(seconds: 3), () {
      if (state.globalSuccessMessage == message) {
        state = state.copyWith(globalSuccessMessage: '');
      }
    });
  }

  /// 显示全局信息消息
  void showGlobalInfo(String message) {
    state = state.copyWith(globalInfoMessage: message);
    logger.i('全局信息: $message');

    // 3秒后自动清除信息消息
    Future.delayed(const Duration(seconds: 3), () {
      if (state.globalInfoMessage == message) {
        state = state.copyWith(globalInfoMessage: '');
      }
    });
  }

  /// 清除所有全局消息
  void clearGlobalMessages() {
    state = state.copyWith(globalErrorMessage: '', globalSuccessMessage: '');
  }

  /// 设置搜索栏可见性
  void setSearchBarVisible(bool visible) {
    state = state.copyWith(isSearchBarVisible: visible);
    logger.i('设置搜索栏可见性: $visible');
  }

  /// 切换搜索栏状态
  void toggleSearchBar() {
    state = state.copyWith(isSearchBarVisible: !state.isSearchBarVisible);
    logger.i('切换搜索栏状态: ${state.isSearchBarVisible}');
  }

  /// 设置当前页面
  void setCurrentPage(String page) {
    state = state.copyWith(currentPage: page);
    logger.i('设置当前页面: $page');
  }

  /// 重置应用状态
  void resetAppState() {
    state = state.copyWith(
      currentNavIndex: 0,
      isSearchBarVisible: false,
      globalErrorMessage: '',
      globalSuccessMessage: '',
    );
    logger.i('重置应用状态');
  }
}
