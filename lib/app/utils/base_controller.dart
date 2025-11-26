import 'package:daily_satori/app_exports.dart';
import 'package:daily_satori/app/config/app_config.dart';

/// 基础 GetX 控制器
///
/// 提供统一的状态管理、错误处理和导航功能
/// 所有页面控制器都应继承此类
abstract class BaseController extends GetxController {
  // ========================================================================
  // 依赖服务
  // ========================================================================

  /// 构造函数 - 通过依赖注入获取状态服务（可选）
  BaseController([this._appStateService]);

  /// 状态服务 - 通过依赖注入获取
  final AppStateService? _appStateService;

  /// 获取应用状态服务的便捷访问器（子类可访问）
  AppStateService? get appStateService => _appStateService;

  // ========================================================================
  // 响应式状态
  // ========================================================================

  /// 是否已初始化
  final RxBool _isInitialized = false.obs;

  /// 是否正在加载
  final RxBool isLoading = false.obs;

  /// 错误消息
  final RxString errorMessage = ''.obs;

  /// 是否已初始化
  bool get isInitialized => _isInitialized.value;

  // ========================================================================
  // 加载状态管理
  // ========================================================================

  /// 显示加载状态
  void showLoading([String message = '加载中...']) {
    isLoading.value = true;
    _appStateService?.showGlobalLoading(message);
  }

  /// 隐藏加载状态
  void hideLoading() {
    isLoading.value = false;
    _appStateService?.hideGlobalLoading();
  }

  // ========================================================================
  // 消息反馈
  // ========================================================================

  /// 显示错误消息
  void showError(String message) {
    errorMessage.value = message;
    _appStateService?.showGlobalError(message);
  }

  /// 显示成功消息
  void showSuccess(String message) {
    _appStateService?.showGlobalSuccess(message);
  }

  /// 清除错误消息
  void clearError() {
    errorMessage.value = '';
  }

  // ========================================================================
  // 导航
  // ========================================================================

  /// 导航到指定页面
  void navigateTo(String route, {Object? arguments}) {
    logger.i('[Navigation] 导航到: $route, 参数: $arguments');
    Get.toNamed(route, arguments: arguments);
  }

  /// 导航到指定页面并替换当前页面
  void navigateOff(String route, {Object? arguments}) {
    logger.i('[Navigation] 替换页面: $route, 参数: $arguments');
    Get.offNamed(route, arguments: arguments);
  }

  /// 返回上一页
  void navigateBack<T>([T? result]) {
    logger.i('[Navigation] 返回上一页, 结果: $result');
    Get.back(result: result);
  }

  // ========================================================================
  // 安全执行
  // ========================================================================

  /// 安全执行异步操作
  Future<T?> safeExecute<T>(
    Future<T> Function() operation, {
    String? loadingMessage,
    String? errorMessage,
    Function(T)? onSuccess,
    Function(Object)? onError,
  }) async {
    try {
      if (loadingMessage != null) {
        showLoading(loadingMessage);
      }

      final result = await operation();

      if (onSuccess != null) {
        onSuccess(result);
      }

      hideLoading();
      clearError();
      return result;
    } catch (e) {
      hideLoading();
      final error = errorMessage ?? '操作失败: $e';
      showError(error);
      if (onError != null) {
        onError(e);
      }
      return null;
    }
  }

  /// 带重试机制的安全执行
  Future<T?> retry<T>(
    Future<T> Function() operation, {
    int maxAttempts = NetworkConfig.maxRetries,
    Duration delay = NetworkConfig.retryDelay,
    String? errorMessage,
  }) async {
    for (int attempt = 1; attempt <= maxAttempts; attempt++) {
      try {
        return await operation();
      } catch (e) {
        if (attempt == maxAttempts) {
          final error = errorMessage ?? '重试 $maxAttempts 次后仍然失败: $e';
          showError(error);
          rethrow;
        }
        logger.w('第 $attempt 次尝试失败，${delay.inSeconds}秒后重试: $e');
        await Future.delayed(delay);
      }
    }
    throw Exception('重试失败');
  }

  // ========================================================================
  // 生命周期
  // ========================================================================

  @override
  void onInit() {
    super.onInit();
    _isInitialized.value = true;
    logger.d('$runtimeType 初始化完成');
  }

  @override
  void onReady() {
    super.onReady();
    logger.d('$runtimeType 准备就绪');
  }

  @override
  void onClose() {
    _isInitialized.close();
    isLoading.close();
    errorMessage.close();
    logger.d('$runtimeType 已关闭');
    super.onClose();
  }
}
