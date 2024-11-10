import 'package:daily_satori/app/styles/theme.dart';
import 'package:flutter/material.dart';
import 'package:flutter_inappwebview/flutter_inappwebview.dart';
import 'package:get/get.dart';
import 'package:daily_satori/init_app.dart';
import 'package:stack_trace/stack_trace.dart';
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

  runApp(
    GetMaterialApp(
      title: "Daily Satori",
      theme: MyTheme.light,
      darkTheme: MyTheme.dark,
      themeMode: ThemeMode.system,
      initialRoute: AppPages.INITIAL,
      getPages: AppPages.routes,
    ),
  );
}
