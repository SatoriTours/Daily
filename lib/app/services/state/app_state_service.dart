import 'package:daily_satori/app_exports.dart';

/// 全局应用状态管理服务
///
/// 负责管理应用级别的状态，包括主题、导航状态、
/// 用户偏好设置等，避免在控制器中分散管理
class AppStateService extends GetxService {
  /// 当前活跃的导航索引
  final RxInt currentNavIndex = 0.obs;

  /// 应用是否处于后台
  final RxBool isAppInBackground = false.obs;

  /// 最后活跃时间（用于判断数据是否需要刷新）
  final Rx<DateTime> lastActiveTime = DateTime.now().obs;

  /// 全局加载状态
  final RxBool isGlobalLoading = false.obs;

  /// 全局错误消息
  final RxString globalErrorMessage = ''.obs;

  /// 全局成功消息
  final RxString globalSuccessMessage = ''.obs;

  /// 是否显示搜索栏
  final RxBool isSearchBarVisible = false.obs;

  /// 当前页面路由
  final RxString currentPage = ''.obs;

  /// 设置当前导航索引
  void setCurrentNavIndex(int index) {
    currentNavIndex.value = index;
    logger.i('设置导航索引: $index');
  }

  /// 设置应用后台状态
  void setAppBackground(bool isBackground) {
    if (isBackground != isAppInBackground.value) {
      isAppInBackground.value = isBackground;
      if (!isBackground) {
        // 应用回到前台，更新最后活跃时间
        lastActiveTime.value = DateTime.now();
        logger.i('应用回到前台');
      } else {
        logger.i('应用进入后台');
      }
    }
  }

  /// 检查数据是否需要刷新（基于最后活跃时间）
  bool needsRefresh(Duration threshold) {
    return DateTime.now().difference(lastActiveTime.value) > threshold;
  }

  /// 显示全局加载状态
  void showGlobalLoading([String message = '加载中...']) {
    isGlobalLoading.value = true;
    logger.i('显示全局加载: $message');
  }

  /// 隐藏全局加载状态
  void hideGlobalLoading() {
    isGlobalLoading.value = false;
    logger.i('隐藏全局加载');
  }

  /// 显示全局错误消息
  void showGlobalError(String message) {
    globalErrorMessage.value = message;
    logger.e('全局错误: $message');

    // 3秒后自动清除错误消息
    Future.delayed(const Duration(seconds: 3), () {
      if (globalErrorMessage.value == message) {
        globalErrorMessage.value = '';
      }
    });
  }

  /// 显示全局成功消息
  void showGlobalSuccess(String message) {
    globalSuccessMessage.value = message;
    logger.i('全局成功: $message');

    // 3秒后自动清除成功消息
    Future.delayed(const Duration(seconds: 3), () {
      if (globalSuccessMessage.value == message) {
        globalSuccessMessage.value = '';
      }
    });
  }

  /// 清除所有全局消息
  void clearGlobalMessages() {
    globalErrorMessage.value = '';
    globalSuccessMessage.value = '';
  }

  /// 设置搜索栏可见性
  void setSearchBarVisible(bool visible) {
    isSearchBarVisible.value = visible;
    logger.i('设置搜索栏可见性: $visible');
  }

  /// 切换搜索栏状态
  void toggleSearchBar() {
    isSearchBarVisible.toggle();
    logger.i('切换搜索栏状态: ${isSearchBarVisible.value}');
  }

  /// 设置当前页面
  void setCurrentPage(String page) {
    currentPage.value = page;
    logger.i('设置当前页面: $page');
  }

  /// 重置应用状态
  void resetAppState() {
    currentNavIndex.value = 0;
    isSearchBarVisible.value = false;
    clearGlobalMessages();
    logger.i('重置应用状态');
  }

  @override
  void onInit() {
    super.onInit();
    logger.i('AppStateService 初始化完成');
  }

  @override
  void onClose() {
    currentNavIndex.close();
    isAppInBackground.close();
    lastActiveTime.close();
    isGlobalLoading.close();
    globalErrorMessage.close();
    globalSuccessMessage.close();
    isSearchBarVisible.close();
    currentPage.close();
    super.onClose();
    logger.i('AppStateService 已关闭');
  }
}