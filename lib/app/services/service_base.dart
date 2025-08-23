import 'dart:async';

/// 服务加载优先级
enum ServicePriority {
  critical, // 关键，必须在应用启动前完成
  high, // 高优先级，应用启动后立即初始化
  normal, // 普通优先级，应用启动后异步初始化
  low, // 低优先级，可延迟初始化
}

/// 服务生命周期契约
abstract class AppService {
  String get serviceName;
  ServicePriority get priority;

  Future<void> init();
  FutureOr<void> dispose() {}
}

/// 用函数适配现有单例服务的轻量适配器
class FunctionAppService implements AppService {
  @override
  final String serviceName;
  @override
  final ServicePriority priority;
  final Future<void> Function() onInit;
  final FutureOr<void> Function()? onDispose;

  FunctionAppService({required this.serviceName, required this.priority, required this.onInit, this.onDispose});

  @override
  Future<void> init() => onInit();

  @override
  FutureOr<void> dispose() => onDispose?.call();
}
