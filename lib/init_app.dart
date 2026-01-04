import 'dart:async';
import 'dart:ui';
import 'package:flutter/material.dart';

import 'package:daily_satori/app/services/services.dart';
import 'dart:developer' as developer;

/// 应用初始化入口
Future<void> initApp() async {
  WidgetsFlutterBinding.ensureInitialized(); // 初始化 Flutter 绑定, 确保在使用平台通道之前Flutter引擎已初始化

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
    if (exception.toString().contains('HttpException') && exception.toString().contains('404')) {
      _infoLog('图片资源不可用(404)，已使用占位图');
      return;
    }
    FlutterError.presentError(details);
    _errorLog('Flutter错误: ${details.exception}', stackTrace: details.stack);
  };

  /// 捕获异步错误
  PlatformDispatcher.instance.onError = (error, stack) {
    if (error.toString().contains('HttpException') && error.toString().contains('404')) {
      _infoLog('图片资源不可用(404)，已使用占位图');
      return true;
    }
    _errorLog('异步错误: $error', stackTrace: stack);
    return true;
  };
}

// 因为 app 的logger 服务还没初始化完，所以这里用最简单的方式记录日志
void _infoLog(String message) {
  developer.log(message, name: 'DailySatoriSystem', level: 800);
}

void _errorLog(String message, {StackTrace? stackTrace}) {
  developer.log(message, name: 'DailySatoriError', error: message, stackTrace: stackTrace, level: 1000);
}

/// 应用退出清理
Future<void> clearApp() async {
  await ServiceRegistry.i.disposeAll();
}
