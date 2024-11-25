import 'package:daily_satori/app/styles/theme.dart';
import 'package:daily_satori/global.dart';
import 'package:flutter/material.dart';
import 'package:flutter_inappwebview/flutter_inappwebview.dart';
import 'package:get/get.dart';
import 'package:daily_satori/init_app.dart';
import 'package:stack_trace/stack_trace.dart';
import 'app/routes/app_pages.dart';
import 'package:sentry_flutter/sentry_flutter.dart';

Future<void> main() async {
  FlutterError.demangleStackTrace = (StackTrace stack) {
    if (stack is Trace) return stack.vmTrace;
    if (stack is Chain) return stack.toTrace().vmTrace;
    return stack;
  };

  WidgetsFlutterBinding.ensureInitialized();
  PlatformInAppWebViewController.debugLoggingSettings.enabled = false;

  await SentryFlutter.init(
    (options) {
      if (isProduction) {
        options.dsn = 'https://204472c2f3a84b3139b9ea446f5ddd94@o4508285752901632.ingest.us.sentry.io/4508285950296064';
      } else {
        options.dsn = 'https://7f7fae079723dfbd9f6b86402c40f900@o4508285752901632.ingest.us.sentry.io/4508285754277888';
      }

      // Set tracesSampleRate to 1.0 to capture 100% of transactions for tracing.
      // We recommend adjusting this value in production.
      options.tracesSampleRate = 1.0;
      // The sampling rate for profiling is relative to tracesSampleRate
      // Setting to 1.0 will profile 100% of sampled transactions:
      options.profilesSampleRate = 1.0;
    },
    appRunner: () async {
      await initApp();
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
      // await clearApp();
    },
  );
}
