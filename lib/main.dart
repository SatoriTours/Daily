import 'package:flutter/material.dart';

import 'package:get/get.dart';

import 'package:daily_satori/app/styles/app_theme.dart';
import 'package:daily_satori/init_app.dart';

import 'app/routes/app_pages.dart';

Future<void> main() async {
  await initApp();
  _dailyAppRunner();
}

void _dailyAppRunner() {
  runApp(
    GetMaterialApp(
      title: "Daily Satori",
      theme: AppTheme.light,
      darkTheme: AppTheme.dark,
      themeMode: ThemeMode.system,
      initialRoute: AppPages.INITIAL,
      getPages: AppPages.routes,
      onReady: onAppReady,
    ),
  );
}
