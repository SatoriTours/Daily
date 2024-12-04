import 'package:flutter/material.dart';

import 'package:sentry_flutter/sentry_flutter.dart';
import 'package:stack_trace/stack_trace.dart';

import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/global.dart';

class SentryService {
  // 单例模式
  SentryService._();
  static final SentryService _instance = SentryService._();
  static SentryService get i => _instance;

  // Sentry DSN 配置
  static const _productionDsn =
      'https://204472c2f3a84b3139b9ea446f5ddd94@o4508285752901632.ingest.us.sentry.io/4508285950296064';
  static const _developmentDsn =
      'https://7f7fae079723dfbd9f6b86402c40f900@o4508285752901632.ingest.us.sentry.io/4508285754277888';

  /// 初始化 Sentry 服务
  Future<void> init() async {
    logger.i("[初始化服务] SentryService");
    _configureStackTraceDemangling();
  }

  /// 配置堆栈跟踪解析
  void _configureStackTraceDemangling() {
    FlutterError.demangleStackTrace = (StackTrace stack) {
      if (stack is Trace) return stack.vmTrace;
      if (stack is Chain) return stack.toTrace().vmTrace;
      return stack;
    };
  }

  /// 配置 Sentry 选项
  void _configureSentryOptions(SentryFlutterOptions options) {
    options.dsn = isProduction ? _productionDsn : _developmentDsn;

    // 设置采样率为 100%
    options.tracesSampleRate = 1.0;
    options.profilesSampleRate = 1.0;
  }

  /// 运行应用并初始化 Sentry
  Future<void> runApp(Function() appRunner) async {
    await SentryFlutter.init(_configureSentryOptions, appRunner: appRunner);
  }
}
