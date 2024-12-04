import 'package:daily_satori/app/services/logger_service.dart';
import 'package:flutter/material.dart';

class FlutterService {
  FlutterService._privateConstructor();
  static final FlutterService _instance = FlutterService._privateConstructor();
  static FlutterService get i => _instance;

  Future<void> init() async {
    logger.i("[初始化服务] FlutterService");
    WidgetsFlutterBinding.ensureInitialized();
  }
}
