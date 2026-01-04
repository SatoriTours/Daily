import 'dart:async';
import 'dart:ui';
import 'package:flutter/material.dart';

import 'package:daily_satori/app/services/services.dart';

/// 应用初始化入口
Future<void> initApp() async {
  WidgetsFlutterBinding.ensureInitialized();

  _setupErrorHandlers();

  ServiceRegistry.i.registerAll();
  await ServiceRegistry.i.initializeAll();

  WidgetsBinding.instance.addPostFrameCallback((_) {
    ServiceRegistry.i.initializeLowPriority();
  });
}

/// 设置全局错误处理器
void _setupErrorHandlers() {
  if (const bool.fromEnvironment('FLUTTER_TEST')) {
    return;
  }

  /// 捕获 Flutter 框架错误
  FlutterError.onError = (FlutterErrorDetails details) {
    final exception = details.exception;
    if (exception.toString().contains('HttpException') &&
        exception.toString().contains('404')) {
      logger.d('图片资源不可用(404)，已使用占位图');
      return;
    }
    FlutterError.presentError(details);
    logger.e('Flutter错误: ${details.exception}', stackTrace: details.stack);
  };

  /// 捕获异步错误
  PlatformDispatcher.instance.onError = (error, stack) {
    if (error.toString().contains('HttpException') &&
        error.toString().contains('404')) {
      logger.d('图片资源不可用(404)，已使用占位图');
      return true;
    }
    logger.e('异步错误: $error', stackTrace: stack);
    return true;
  };
}

/// 应用退出清理
Future<void> clearApp() async {
  await ServiceRegistry.i.disposeAll();
}
