/// Base Riverpod Controller Mixin
///
/// 提供 Riverpod controllers 的通用功能，替代原 BaseController。
library;

import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

/// Riverpod Controller 基础 Mixin
///
/// 提供通用功能：
/// - safeExecute: 安全的异步操作执行
/// - 状态管理辅助方法
mixin BaseRiverpodController {
  /// 获取 WidgetRef（由子类提供）
  WidgetRef get ref;

  /// 安全执行异步操作
  ///
  /// 包装异步操作，提供统一的错误处理和加载状态管理。
  ///
  /// 参数：
  /// - operation: 要执行的异步操作
  /// - loadingMessage: 加载提示信息（可选）
  /// - errorMessage: 错误提示信息（可选）
  /// - onSuccess: 成功回调（可选）
  /// - onError: 错误回调（可选）
  ///
  /// 返回：
  /// - 操作结果，如果失败返回 null
  Future<T?> safeExecute<T>(
    Future<T> Function() operation, {
    String? loadingMessage,
    String? errorMessage,
    Function(T)? onSuccess,
    Function(Object error)? onError,
  }) async {
    try {
      // 显示加载状态
      if (loadingMessage != null) {
        _showLoading(loadingMessage);
      }

      // 执行操作
      final result = await operation();

      // 成功回调
      if (onSuccess != null) {
        onSuccess(result);
      }

      // 隐藏加载状态
      if (loadingMessage != null) {
        _hideLoading();
      }

      return result;
    } catch (e) {
      // 隐藏加载状态
      if (loadingMessage != null) {
        _hideLoading();
      }

      // 错误处理
      final error = errorMessage ?? '操作失败: $e';
      _showError(error);

      // 错误回调
      if (onError != null) {
        onError(e);
      }

      // 记录错误日志
      if (kDebugMode) {
        print('[BaseRiverpodController] Error: $e');
      }

      return null;
    }
  }

  /// 显示加载状态
  ///
  /// 子类可以重写此方法来自定义加载提示。
  void _showLoading(String message) {
    // TODO: 在 Phase 2 中实现，使用 appStateProvider
    // ref.read(appStateProvider.notifier).showGlobalLoading(message);
  }

  /// 隐藏加载状态
  ///
  /// 子类可以重写此方法来自定义加载提示。
  void _hideLoading() {
    // TODO: 在 Phase 2 中实现，使用 appStateProvider
    // ref.read(appStateProvider.notifier).hideGlobalLoading();
  }

  /// 显示错误消息
  ///
  /// 子类可以重写此方法来自定义错误提示。
  void _showError(String message) {
    // TODO: 在 Phase 2 中实现，使用 appStateProvider
    // ref.read(appStateProvider.notifier).showGlobalError(message);
  }

  /// 显示成功消息
  ///
  /// 子类可以重写此方法来自定义成功提示。
  void showSuccess(String message) {
    // TODO: 在 Phase 2 中实现，使用 appStateProvider
    // ref.read(appStateProvider.notifier).showGlobalSuccess(message);
  }

  /// 显示信息消息
  ///
  /// 子类可以重写此方法来自定义信息提示。
  void showInfo(String message) {
    // TODO: 在 Phase 2 中实现，使用 appStateProvider
    // ref.read(appStateProvider.notifier).showGlobalInfo(message);
  }
}

/// Riverpod Controller 重试辅助类
///
/// 提供重试机制的工具类。
class RiverpodRetry {
  /// 重试执行操作
  ///
  /// 在操作失败时自动重试指定次数。
  ///
  /// 参数：
  /// - operation: 要执行的操作
  /// - maxAttempts: 最大尝试次数（默认 3 次）
  /// - delay: 重试间隔（默认 1 秒）
  /// - onError: 错误回调（每次失败时调用）
  ///
  /// 返回：
  /// - 操作结果，如果所有尝试都失败则抛出最后一个异常
  static Future<T> execute<T>({
    required Future<T> Function() operation,
    int maxAttempts = 3,
    Duration delay = const Duration(seconds: 1),
    void Function(Object error, int attempt)? onError,
  }) async {
    Object? lastError;

    for (int attempt = 1; attempt <= maxAttempts; attempt++) {
      try {
        return await operation();
      } catch (e) {
        lastError = e;

        if (onError != null) {
          onError(e, attempt);
        }

        // 如果还有重试机会，等待后重试
        if (attempt < maxAttempts) {
          await Future.delayed(delay);
        }
      }
    }

    // 所有尝试都失败，抛出最后一个错误
    throw lastError is Exception
        ? lastError
        : Exception('Operation failed after $maxAttempts attempts: $lastError');
  }
}
