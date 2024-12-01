import 'package:flutter/material.dart';

import 'package:get/get.dart';
import 'package:sentry_flutter/sentry_flutter.dart';

import 'package:daily_satori/app/services/sentry_service.dart';
import 'package:daily_satori/app/styles/theme.dart';
import 'package:daily_satori/init_app.dart';

import 'app/routes/app_pages.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();

  await initApp();
  await SentryService.i.runApp(_dailyAppRunner);
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
