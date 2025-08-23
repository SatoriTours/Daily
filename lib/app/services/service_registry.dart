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
import 'package:daily_satori/app/services/book_service.dart';
import 'package:daily_satori/app/services/app_upgrade_service.dart';
import 'package:daily_satori/app/services/share_receive_service.dart';
import 'package:daily_satori/app/services/clipboard_monitor_service.dart';

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
    // 基础/关键
    register(
      FunctionAppService(
        serviceName: 'LoggerService',
        priority: ServicePriority.critical,
        onInit: () => LoggerService.i.init(),
      ),
    );
    register(
      FunctionAppService(
        serviceName: 'FlutterService',
        priority: ServicePriority.critical,
        onInit: () => FlutterService.i.init(),
      ),
    );
    register(
      FunctionAppService(
        serviceName: 'TimeService',
        priority: ServicePriority.critical,
        onInit: () => TimeService.i.init(),
      ),
    );
    register(
      FunctionAppService(
        serviceName: 'ObjectboxService',
        priority: ServicePriority.critical,
        onInit: () => ObjectboxService.i.init(),
        onDispose: () => ObjectboxService.i.dispose(),
      ),
    );
    register(
      FunctionAppService(
        serviceName: 'SettingService',
        priority: ServicePriority.critical,
        onInit: () => SettingService.i.init(),
      ),
    );
    register(
      FunctionAppService(
        serviceName: 'FileService',
        priority: ServicePriority.critical,
        onInit: () => FileService.i.init(),
      ),
    );
    register(
      FunctionAppService(
        serviceName: 'HttpService',
        priority: ServicePriority.critical,
        onInit: () => HttpService.i.init(),
      ),
    );

    // 高优先级
    register(
      FunctionAppService(
        serviceName: 'FontService',
        priority: ServicePriority.high,
        onInit: () => FontService.i.init(),
      ),
    );
    register(
      FunctionAppService(
        serviceName: 'ADBlockService',
        priority: ServicePriority.high,
        onInit: () => ADBlockService.i.init(),
      ),
    );
    register(
      FunctionAppService(
        serviceName: 'FreeDiskService',
        priority: ServicePriority.high,
        onInit: () => FreeDiskService.i.init(),
      ),
    );
    register(
      FunctionAppService(
        serviceName: 'AIConfigService',
        priority: ServicePriority.high,
        onInit: () => AIConfigService.i.init(),
      ),
    );

    // 普通
    register(
      FunctionAppService(serviceName: 'AiService', priority: ServicePriority.normal, onInit: () => AiService.i.init()),
    );
    register(
      FunctionAppService(
        serviceName: 'BackupService',
        priority: ServicePriority.normal,
        onInit: () => BackupService.i.init(),
      ),
    );
    register(
      FunctionAppService(
        serviceName: 'MigrationService',
        priority: ServicePriority.normal,
        onInit: () => MigrationService.i.init(),
      ),
    );
    register(
      FunctionAppService(
        serviceName: 'PluginService',
        priority: ServicePriority.normal,
        onInit: () => PluginService.i.init(),
      ),
    );
    register(
      FunctionAppService(
        serviceName: 'WebService',
        priority: ServicePriority.normal,
        onInit: () => WebService.i.init(),
      ),
    );
    register(
      FunctionAppService(
        serviceName: 'BookService',
        priority: ServicePriority.normal,
        onInit: () => BookService.i.init(),
      ),
    );

    // 低优先级
    register(
      FunctionAppService(
        serviceName: 'AppUpgradeService',
        priority: ServicePriority.low,
        onInit: () => AppUpgradeService.i.init(),
      ),
    );
    register(
      FunctionAppService(
        serviceName: 'ShareReceiveService',
        priority: ServicePriority.low,
        onInit: () => ShareReceiveService.i.init(),
      ),
    );
    register(
      FunctionAppService(
        serviceName: 'ClipboardMonitorService',
        priority: ServicePriority.low,
        onInit: () => ClipboardMonitorService.i.init(),
      ),
    );
  }

  Future<void> _safeInit(AppService service, {required bool critical}) async {
    final st = ServiceStatus(service);
    _status[service.serviceName] = st;
    st.startTime = DateTime.now();
    try {
      await service.init();
      st.isInitialized = true;
    } catch (e) {
      st.error = e;
      if (critical) rethrow;
    } finally {
      st.endTime = DateTime.now();
    }
  }

  Iterable<AppService> _by(ServicePriority p) => _services.where((s) => s.priority == p);

  /// 按优先级初始化
  Future<void> initializeAll() async {
    // critical 同步串行
    for (final s in _by(ServicePriority.critical)) {
      await _safeInit(s, critical: true);
    }
    // high 并行
    await Future.wait(_by(ServicePriority.high).map((s) => _safeInit(s, critical: false)));
    // normal 异步并行
    unawaited(Future.wait(_by(ServicePriority.normal).map((s) => _safeInit(s, critical: false))));
    // low PostFrame 由上层触发，这里不做
  }

  /// 初始化低优先级
  void initializeLowPriority() {
    for (final s in _by(ServicePriority.low)) {
      // fire and forget
      unawaited(_safeInit(s, critical: false));
    }
  }

  Future<void> disposeAll() async {
    for (final s in _services.reversed) {
      try {
        await s.dispose();
      } catch (_) {}
    }
  }
}
