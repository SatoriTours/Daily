import 'dart:async';
import 'package:flutter/material.dart';

import 'package:daily_satori/app/services/service_registry.dart';
import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/services/services.dart';

// 应用加载好前执行
Future<void> initApp() async {
  final stopwatch = Stopwatch()..start();

  WidgetsFlutterBinding.ensureInitialized();

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

// 应用准备好之后执行(主要是UI准备好)
void onAppReady() {
  _scheduleMemoryCleanup();
}

// 定期内存清理
void _scheduleMemoryCleanup() {
  Timer.periodic(const Duration(minutes: 15), (timer) {
    try {
      FreeDiskService.i.clean();
    } catch (_) {}
  });
}

// 应用退出时执行
Future<void> clearApp() async {
  await ServiceRegistry.i.disposeAll();
}
