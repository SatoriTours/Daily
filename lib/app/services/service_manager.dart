import 'dart:async';

import 'package:daily_satori/app/data/data.dart';
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
import 'package:daily_satori/app/services/i18n_service.dart';
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
class ServiceManager {
  ServiceManager._();
  static final ServiceManager i = ServiceManager._();

  final List<AppService> _services = [];
  final Map<String, ServiceStatus> _status = {};

  List<AppService> get services => List.unmodifiable(_services);
  ServiceStatus? statusOf(String name) => _status[name];

  void register(AppService service) {
    _services.add(service);
  }

  /// 注册项目内的所有服务
  void registerAll() {
    // 基础/关键服务（critical）
    register(LoggerService.i);
    register(FlutterService.i);
    register(TimeService.i);
    register(ObjectboxService.i);
    register(SettingService.i);
    register(FileService.i);
    register(HttpService.i);
    register(I18nService.i);

    // 高优先级服务（high）
    register(FontService.i);
    register(ADBlockService.i);
    register(FreeDiskService.i);
    register(AIConfigService.i);

    // 普通优先级服务（normal）
    register(AiService.i);
    register(BackupService.i);
    register(MigrationService.i);
    register(PluginService.i);

    // 低优先级服务（low）
    register(WebService.i);
    register(ArticleRecoveryService.i);
    register(AppUpgradeService.i);
    register(ShareReceiveService.i);
    register(ClipboardMonitorService.i);
  }

  Future<void> _safeInit(AppService service, {required bool critical}) async {
    final status = ServiceStatus(service);
    _status[service.serviceName] = status..startTime = DateTime.now();

    try {
      developer.log('[I] 初始化服务: ${service.serviceName}', name: 'Satori', level: 800);
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
    if (!AiService.i.isAiEnabled(AIFunctionType.general)) {
      logger.i('[ServiceRegistry] AI 未配置，跳过低优先级服务初始化');
      return;
    }

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
