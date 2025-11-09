import 'package:daily_satori/app_exports.dart';
import 'package:daily_satori/app/config/app_config.dart';

/// GetX 基础控制器
///
/// 提供标准的 GetX 控制器功能，包括状态管理、
/// 生命周期管理和常用的工具方法
abstract class BaseGetXController extends GetxController {
  /// 是否已初始化
  final RxBool _isInitialized = false.obs;

  /// 是否正在加载
  final RxBool isLoading = false.obs;

  /// 错误消息
  final RxString errorMessage = ''.obs;

  /// 状态服务
  late final AppStateService _appStateService;

  /// 导航服务
  late final NavigationService _navigationService;

  /// 是否已初始化
  bool get isInitialized => _isInitialized.value;

  /// 显示加载状态
  void showLoading([String message = '加载中...']) {
    isLoading.value = true;
    _appStateService.showGlobalLoading(message);
  }

  /// 隐藏加载状态
  void hideLoading() {
    isLoading.value = false;
    _appStateService.hideGlobalLoading();
  }

  /// 显示错误消息
  void showError(String message) {
    errorMessage.value = message;
    _appStateService.showGlobalError(message);
  }

  /// 显示成功消息
  void showSuccess(String message) {
    _appStateService.showGlobalSuccess(message);
  }

  /// 清除错误消息
  void clearError() {
    errorMessage.value = '';
  }

  /// 导航方法
  void navigateTo(String route, {Object? arguments}) {
    _navigationService.toNamed(route, arguments: arguments);
  }

  void navigateOff(String route, {Object? arguments}) {
    _navigationService.offNamed(route, arguments: arguments);
  }

  void navigateBack<T>([T? result]) {
    _navigationService.back(result);
  }

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

  /// 重试机制
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

  @override
  void onInit() {
    super.onInit();
    _initServices();
    _isInitialized.value = true;
    logger.i('$runtimeType 初始化完成');
  }

  void _initServices() {
    _appStateService = Get.find<AppStateService>();
    _navigationService = Get.find<NavigationService>();
  }

  @override
  void onReady() {
    super.onReady();
    logger.i('$runtimeType 准备就绪');
  }

  @override
  void onClose() {
    _isInitialized.close();
    isLoading.close();
    errorMessage.close();
    logger.i('$runtimeType 已关闭');
    super.onClose();
  }
}
