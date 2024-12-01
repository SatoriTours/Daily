import 'dart:developer';

import 'package:flutter_inappwebview/flutter_inappwebview.dart';
import 'package:logger/logger.dart';
import 'package:sentry_flutter/sentry_flutter.dart';

import 'package:daily_satori/global.dart';

late Logger logger;

class LoggerService {
  LoggerService._privateConstructor();
  static final LoggerService _instance = LoggerService._privateConstructor();
  static LoggerService get i => _instance;

  Future<void> init() async {
    log("[Satori] [初始化服务] LoggerService");
    PlatformInAppWebViewController.debugLoggingSettings.enabled = false;
    if (isProduction) {
      Logger.level = Level.info;
    }

    logger = Logger(
      printer: SimplePrinter(colors: false),
      output: _MyConsoleOutput(),
    );
  }
}

class _MyConsoleOutput extends LogOutput {
  @override
  void output(OutputEvent event) {
    final logString = event.lines.join("\n");
    if (isProduction) {
      Sentry.captureMessage(logString);
    } else {
      log(logString, name: "Satori");
    }
  }
}
