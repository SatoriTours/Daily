import 'dart:async';

import 'package:daily_satori/app/services/service_base.dart';
import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/services/flutter_service.dart';
import 'package:daily_satori/app/services/time_service.dart';
import 'package:daily_satori/app/services/objectbox_service.dart';
import 'package:daily_satori/app/services/setting_service/setting_service.dart';
import 'package:daily_satori/app/services/file_service.dart';
import 'package:daily_satori/app/services/http_service.dart';
import 'package:daily_satori/app/services/font_service.dart';
import 'package:daily_satori/app/services/adblock_service.dart';
import 'package:daily_satori/app/services/freedisk_service.dart';
import 'package:daily_satori/app/services/ai_config_service.dart';
import 'package:daily_satori/app/services/ai_service/ai_service.dart';
import 'package:daily_satori/app/services/backup_service.dart';
import 'package:daily_satori/app/services/migration_service/migration_service.dart';
import 'package:daily_satori/app/services/plugin_service.dart';
import 'package:daily_satori/app/services/web_service/web_service.dart';
import 'package:daily_satori/app/services/app_upgrade_service.dart';
import 'package:daily_satori/app/services/share_receive_service.dart';
import 'package:daily_satori/app/services/clipboard_monitor_service.dart';
import 'package:daily_satori/app/services/i18n/i18n_service.dart';
import 'package:daily_satori/app/services/article_recovery_service.dart';

import 'dart:developer' as developer;

class ServiceStatus {
  final AppService service;
  bool isInitialized = false;
  DateTime? startTime;
  DateTime? endTime;
  Object? error;

  ServiceStatus(this.service);

  Duration? get initializationTime =>
      isInitialized && startTime != null && endTime != null ? endTime!.difference(startTime!) : null;
}

/// 集中式服务注册与生命周期管理
class ServiceRegistry {
  ServiceRegistry._();
  static final ServiceRegistry i = ServiceRegistry._();

  final List<AppService> _services = [];
  final Map<String, ServiceStatus> _status = {};

  List<AppService> get services => List.unmodifiable(_services);
  ServiceStatus? statusOf(String name) => _status[name];

  void register(AppService service) {
    _services.add(service);
  }

  /// 注册项目内的所有服务（使用函数适配器包裹现有单例）
  void registerAll() {
    void registerService(
      String name,
      ServicePriority priority,
      Future<void> Function() init, {
      FutureOr<void> Function()? dispose,
    }) => register(FunctionAppService(serviceName: name, priority: priority, onInit: init, onDispose: dispose));

    // 基础/关键
    registerService('LoggerService', ServicePriority.critical, () => LoggerService.i.init());
    registerService('FlutterService', ServicePriority.critical, () => FlutterService.i.init());
    registerService('TimeService', ServicePriority.critical, () => TimeService.i.init());
    registerService(
      'ObjectboxService',
      ServicePriority.critical,
      ObjectboxService.i.init,
      dispose: ObjectboxService.i.dispose,
    );
    registerService('SettingService', ServicePriority.critical, () => SettingService.i.init());
    registerService('FileService', ServicePriority.critical, () => FileService.i.init());
    registerService('HttpService', ServicePriority.critical, () => HttpService.i.init());
    registerService('I18nService', ServicePriority.critical, () async => I18nService.i.init());

    // 高优先级
    registerService('FontService', ServicePriority.high, () => FontService.i.init());
    registerService('ADBlockService', ServicePriority.high, () => ADBlockService.i.init());
    registerService('FreeDiskService', ServicePriority.high, () => FreeDiskService.i.init());
    registerService('AIConfigService', ServicePriority.high, () => AIConfigService.i.init());

    // 普通
    registerService('AiService', ServicePriority.normal, () => AiService.i.init());
    registerService('BackupService', ServicePriority.normal, () => BackupService.i.init());
    registerService('MigrationService', ServicePriority.normal, () => MigrationService.i.init());
    registerService('PluginService', ServicePriority.normal, () => PluginService.i.init());

    // 低优先级
    registerService('WebService', ServicePriority.low, () => WebService.i.init());
    registerService('ArticleRecoveryService', ServicePriority.low, () => ArticleRecoveryService.i.init());
    registerService('AppUpgradeService', ServicePriority.low, () => AppUpgradeService.i.init());
    registerService('ShareReceiveService', ServicePriority.low, () => ShareReceiveService.i.init());
    registerService('ClipboardMonitorService', ServicePriority.low, () => ClipboardMonitorService.i.init());
  }

  Future<void> _safeInit(AppService service, {required bool critical}) async {
    final status = ServiceStatus(service);
    _status[service.serviceName] = status..startTime = DateTime.now();

    try {
      developer.log('[I] 开始初始化服务: ${service.serviceName}', name: 'Satori', level: 800);
      await service.init();
      status.isInitialized = true;
    } catch (e) {
      status.error = e;
      if (critical) rethrow;
    } finally {
      status.endTime = DateTime.now();
    }
  }

  List<AppService> _servicesByPriority(ServicePriority priority) =>
      _services.where((s) => s.priority == priority).toList();

  /// 按优先级初始化所有服务
  Future<void> initializeAll() async {
    final critical = _servicesByPriority(ServicePriority.critical);
    final high = _servicesByPriority(ServicePriority.high);
    final normal = _servicesByPriority(ServicePriority.normal);

    for (final service in critical) {
      await _safeInit(service, critical: true);
    }

    await Future.wait(high.map((s) => _safeInit(s, critical: false)));

    unawaited(Future.wait(normal.map((s) => _safeInit(s, critical: false))));
  }

  /// 初始化低优先级服务（AI 配置完成后触发）
  void initializeLowPriority() {
    if (!AiService.i.isAiEnabled(0)) {
      logger.i('[ServiceRegistry] AI 未配置，跳过低优先级服务初始化');
      return;
    }

    logger.i('[ServiceRegistry] 开始初始化低优先级服务');
    final low = _servicesByPriority(ServicePriority.low);

    for (final service in low) {
      unawaited(_safeInit(service, critical: false));
    }
  }

  Future<void> disposeAll() async {
    for (final service in _services.reversed) {
      try {
        await service.dispose();
      } catch (_) {}
    }
  }
}
