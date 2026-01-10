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
  /// 服务名称，默认取类名（去掉 Service 后缀）
  String get serviceName => runtimeType.toString();

  /// 服务优先级，默认为 normal
  ServicePriority get priority => ServicePriority.normal;

  Future<void> init();
  FutureOr<void> dispose() {}
}
