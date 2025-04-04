import 'package:flutter/material.dart';
import 'package:get/get.dart';

import 'package:daily_satori/app/routes/app_pages.dart';
import 'package:daily_satori/app/styles/app_theme.dart';
import 'package:daily_satori/init_app.dart';

/// 应用程序入口点
Future<void> main() async {
  await initApp();
  runDailySatoriApp();
}

/// 运行 Daily Satori 应用程序
void runDailySatoriApp() {
  runApp(
    GetMaterialApp(
      title: 'Daily Satori',
      theme: AppTheme.light,
      darkTheme: AppTheme.dark,
      themeMode: ThemeMode.system,
      initialRoute: AppPages.INITIAL,
      getPages: AppPages.routes,
      onReady: onAppReady,
    ),
  );
}
