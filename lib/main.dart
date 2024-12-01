import 'package:flutter/material.dart';

import 'package:flutter_inappwebview/flutter_inappwebview.dart';
import 'package:get/get.dart';
import 'package:sentry_flutter/sentry_flutter.dart';
import 'package:stack_trace/stack_trace.dart';

import 'package:daily_satori/app/styles/theme.dart';
import 'package:daily_satori/global.dart';
import 'package:daily_satori/init_app.dart';

import 'app/routes/app_pages.dart';

Future<void> main() async {
  FlutterError.demangleStackTrace = (StackTrace stack) {
    if (stack is Trace) return stack.vmTrace;
    if (stack is Chain) return stack.toTrace().vmTrace;
    return stack;
  };

  WidgetsFlutterBinding.ensureInitialized();
  PlatformInAppWebViewController.debugLoggingSettings.enabled = false;

  await initApp();
  await SentryFlutter.init(_optionsConfiguration, appRunner: _dailyAppRunner);
}

void _optionsConfiguration(options) {
  if (isProduction) {
    options.dsn =
        'https://204472c2f3a84b3139b9ea446f5ddd94@o4508285752901632.ingest.us.sentry.io/4508285950296064';
  } else {
    options.dsn =
        'https://7f7fae079723dfbd9f6b86402c40f900@o4508285752901632.ingest.us.sentry.io/4508285754277888';
  }
  options.tracesSampleRate = 1.0; // 将 tracesSampleRate 设置为 1.0 以捕获 100% 的追踪事务。
  options.profilesSampleRate =
      1.0; // 性能分析采样率是相对于 tracesSampleRate 的设置为 1.0 将对 100% 的采样事务进行性能分析:
}

void _dailyAppRunner() {
  runApp(
    GetMaterialApp(
      title: "Daily Satori",
      theme: MyTheme.light,
      darkTheme: MyTheme.dark,
      themeMode: ThemeMode.system,
      initialRoute: AppPages.INITIAL,
      getPages: AppPages.routes,
      navigatorObservers: [SentryNavigatorObserver()],
      onReady: onAppReady,
    ),
  );
}
