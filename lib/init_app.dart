import 'dart:async';
import 'dart:ui';
import 'package:flutter/material.dart';

import 'package:daily_satori/app/config/app_config.dart';
import 'package:daily_satori/app/services/service_registry.dart';
import 'package:daily_satori/app/services/services.dart';

// 应用加载好前执行
Future<void> initApp() async {
  final stopwatch = Stopwatch()..start();

  WidgetsFlutterBinding.ensureInitialized();

  // 设置全局错误处理器
  _setupErrorHandlers();

  // 注册并初始化
  ServiceRegistry.i.registerAll();
  await ServiceRegistry.i.initializeAll();

  // 在 UI 准备后初始化低优先级
  WidgetsBinding.instance.addPostFrameCallback((_) {
    ServiceRegistry.i.initializeLowPriority();
  });

  stopwatch.stop();
  logger.i('[Satori] 应用初始化完成，耗时: ${stopwatch.elapsedMilliseconds}ms');
}

// 设置全局错误处理器
void _setupErrorHandlers() {
  // 捕获Flutter框架错误
  FlutterError.onError = (FlutterErrorDetails details) {
    // 过滤图片加载404错误，避免污染日志
    final exception = details.exception;
    if (exception.toString().contains('HttpException') && exception.toString().contains('404')) {
      // 静默处理404图片错误
      logger.d('图片资源不可用(404)，已使用占位图');
      return;
    }

    // 其他错误正常处理
    FlutterError.presentError(details);
    logger.e('Flutter错误: ${details.exception}', stackTrace: details.stack);
  };

  // 捕获异步错误
  PlatformDispatcher.instance.onError = (error, stack) {
    // 过滤图片加载404错误
    if (error.toString().contains('HttpException') && error.toString().contains('404')) {
      logger.d('图片资源不可用(404)，已使用占位图');
      return true; // 表示错误已处理
    }

    logger.e('异步错误: $error', stackTrace: stack);
    return true;
  };
}

// 应用准备好之后执行(主要是UI准备好)
void onAppReady() {
  _scheduleMemoryCleanup();
}

// 定期内存清理
void _scheduleMemoryCleanup() {
  Timer.periodic(SessionConfig.checkInterval, (timer) {
    try {
      FreeDiskService.i.clean();
    } catch (_) {}
  });
}

// 应用退出时执行
Future<void> clearApp() async {
  await ServiceRegistry.i.disposeAll();
}
