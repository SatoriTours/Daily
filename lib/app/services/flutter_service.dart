import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/services/service_base.dart';
import 'package:flutter/material.dart';

class FlutterService implements AppService {
  // 单例模式
  FlutterService._();
  static final FlutterService _instance = FlutterService._();
  static FlutterService get i => _instance;

  @override
  String get serviceName => 'FlutterService';

  @override
  ServicePriority get priority => ServicePriority.critical;

  /// 初始化 Flutter 服务
  ///
  /// 确保 Flutter 绑定初始化完成,为后续操作做准备
  Future<void> init() async {
    logger.i("[初始化服务] FlutterService");
    WidgetsFlutterBinding.ensureInitialized();
  }

  @override
  void dispose() {}
}
