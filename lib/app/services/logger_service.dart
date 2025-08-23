import 'dart:developer';

import 'package:flutter_inappwebview/flutter_inappwebview.dart';
import 'package:logger/logger.dart';

import 'package:daily_satori/app/utils/app_info_utils.dart';
import 'package:daily_satori/global.dart';
import 'package:daily_satori/app/services/service_base.dart';

late Logger logger;

class LoggerService implements AppService {
  LoggerService._privateConstructor();
  static final LoggerService _instance = LoggerService._privateConstructor();
  static LoggerService get i => _instance;

  @override
  String get serviceName => 'LoggerService';

  @override
  ServicePriority get priority => ServicePriority.critical;

  Future<void> init() async {
    log("[Satori] [初始化服务] LoggerService");
    PlatformInAppWebViewController.debugLoggingSettings.enabled = !AppInfoUtils.isProduction;
    if (AppInfoUtils.isProduction) {
      Logger.level = Level.info;
    }

    logger = Logger(printer: SimplePrinter(colors: false), output: _MyConsoleOutput());
  }

  @override
  void dispose() {}
}

class _MyConsoleOutput extends LogOutput {
  @override
  void output(OutputEvent event) {
    final logString = event.lines.join("\n");
    if (AppInfoUtils.isProduction) {
    } else {
      log(logString, name: "Satori");
    }
  }
}
