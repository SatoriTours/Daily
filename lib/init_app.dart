import 'dart:async';
import 'package:flutter/material.dart';

import 'package:daily_satori/app/services/adblock_service.dart';
import 'package:daily_satori/app/services/ai_service/ai_service.dart';
import 'package:daily_satori/app/services/app_upgrade_service.dart';
import 'package:daily_satori/app/services/backup_service.dart';
import 'package:daily_satori/app/services/file_service.dart';
import 'package:daily_satori/app/services/flutter_service.dart';
import 'package:daily_satori/app/services/font_service.dart';
import 'package:daily_satori/app/services/freedisk_service.dart';
import 'package:daily_satori/app/services/http_service.dart';
import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/services/objectbox_service.dart';
import 'package:daily_satori/app/services/plugin_service.dart';
import 'package:daily_satori/app/services/setting_service/setting_service.dart';
import 'package:daily_satori/app/services/share_receive_service.dart';
import 'package:daily_satori/app/services/time_service.dart';
import 'package:daily_satori/app/services/web_service/web_service.dart';
import 'package:daily_satori/app/services/webpage_parser_service.dart';
import 'package:daily_satori/app/services/migration_service.dart';

// 应用加载优先级
enum ServicePriority {
  critical, // 关键服务，必须在应用启动前完成
  high, // 高优先级，应在应用启动后立即初始化
  normal, // 普通优先级，应在应用启动后异步初始化
  low, // 低优先级，可延迟初始化
}

// 服务加载状态
class ServiceStatus {
  final String name;
  bool isInitialized = false;
  DateTime? startTime;
  DateTime? endTime;
  Exception? error;

  ServiceStatus(this.name);

  Duration? get initializationTime =>
      isInitialized && startTime != null && endTime != null ? endTime!.difference(startTime!) : null;
}

// 应用服务管理器
class AppServiceManager {
  static final Map<String, ServiceStatus> _services = {};

  // 获取服务初始化状态
  static ServiceStatus? getServiceStatus(String serviceName) {
    return _services[serviceName];
  }

  // 安全初始化服务，捕获错误
  static Future<void> initService(String name, Future<void> Function() initializer, {bool critical = false}) async {
    final status = ServiceStatus(name);
    _services[name] = status;

    status.startTime = DateTime.now();
    try {
      await initializer();
      status.isInitialized = true;
    } catch (e) {
      status.error = e is Exception ? e : Exception(e.toString());
      if (critical) {
        rethrow; // 关键服务初始化失败，抛出异常
      }
    } finally {
      status.endTime = DateTime.now();
    }
  }
}

// 应用加载好前执行
Future<void> initApp() async {
  // 启动性能跟踪
  final stopwatch = Stopwatch()..start();

  // 初始化关键基础服务
  await _initCriticalServices();

  // 初始化高优先级服务（并行）
  await _initHighPriorityServices();

  // 初始化普通优先级服务（可延迟）
  _initNormalPriorityServices();

  // 预加载低优先级服务（应用就绪后）
  WidgetsBinding.instance.addPostFrameCallback((_) {
    _initLowPriorityServices();
  });

  stopwatch.stop();
  final initTime = stopwatch.elapsedMilliseconds;
  print('[Satori] 应用初始化完成，耗时: ${initTime}ms');
}

// 应用准备好之后执行(主要是UI准备好)
void onAppReady() {
  // 确保必要的服务已初始化
  if (AppServiceManager.getServiceStatus('ShareReceiveService')?.isInitialized != true) {
    ShareReceiveService.i.init();
  }

  // 执行其他UI准备好后需要执行的逻辑
  _scheduleMemoryCleanup();
}

// 定期内存清理
void _scheduleMemoryCleanup() {
  // 设置定期清理内存的计划
  Timer.periodic(const Duration(minutes: 15), (timer) {
    // 处理内存相关的清理任务
    if (AppServiceManager.getServiceStatus('FreeDiskService')?.isInitialized == true) {
      FreeDiskService.i.clean();
    }
  });
}

// 应用退出时执行
Future<void> clearApp() async {
  // 确保数据库正确关闭
  if (AppServiceManager.getServiceStatus('ObjectboxService')?.isInitialized == true) {
    ObjectboxService.i.dispose();
  }

  // 关闭网页解析服务
  if (AppServiceManager.getServiceStatus('WebpageParserService')?.isInitialized == true) {
    WebpageParserService.i.dispose();
  }

  // 清理其他资源
  _cancelAllTimers();
}

// 取消所有定时器
void _cancelAllTimers() {
  // 在这里添加其他需要取消的定时器
}

// 初始化关键基础服务，必须同步完成
Future<void> _initCriticalServices() async {
  await AppServiceManager.initService('LoggerService', () => LoggerService.i.init(), critical: true);
  await AppServiceManager.initService('FlutterService', () => FlutterService.i.init(), critical: true);
  await AppServiceManager.initService('TimeService', () => TimeService.i.init(), critical: true);
  await AppServiceManager.initService('ObjectboxService', () => ObjectboxService.i.init(), critical: true);
  await AppServiceManager.initService('SettingService', () => SettingService.i.init(), critical: true);
  await AppServiceManager.initService('FileService', () => FileService.i.init(), critical: true);
  await AppServiceManager.initService('HttpService', () => HttpService.i.init(), critical: true);
}

// 初始化高优先级服务，并行执行
Future<void> _initHighPriorityServices() async {
  await Future.wait([
    AppServiceManager.initService('FontService', () => FontService.i.init()),
    AppServiceManager.initService('ADBlockService', () => ADBlockService.i.init()),
    AppServiceManager.initService('FreeDiskService', () => FreeDiskService.i.init()),
    AppServiceManager.initService('WebpageParserService', () => WebpageParserService.i.init()),
  ]);
}

// 初始化普通优先级服务，异步执行
void _initNormalPriorityServices() {
  // 不等待这些服务完成初始化
  AppServiceManager.initService('AiService', () => AiService.i.init());
  AppServiceManager.initService('BackupService', () => BackupService.i.init());
  AppServiceManager.initService('MigrationService', () => MigrationService.i.init());
  AppServiceManager.initService('PluginService', () => PluginService.i.init());
  AppServiceManager.initService('WebService', () => WebService.i.init());
}

// 初始化低优先级服务，延迟执行
void _initLowPriorityServices() {
  // 延迟执行的低优先级服务
  AppServiceManager.initService('AppUpgradeService', () => AppUpgradeService.i.init());
  AppServiceManager.initService('ShareReceiveService', () => ShareReceiveService.i.init());
}
