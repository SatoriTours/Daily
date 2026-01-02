import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:daily_satori/app/routes/app_router.dart';
import 'package:daily_satori/app/styles/index.dart';
import 'package:daily_satori/init_app.dart';

/// 应用程序入口点
Future<void> main() async {
  await initApp();
  runDailySatoriApp();
}

/// 运行 Daily Satori 应用程序
void runDailySatoriApp() {
  runApp(
    ProviderScope(
      child: MaterialApp.router(
        title: 'Daily Satori',
        theme: AppTheme.light,
        darkTheme: AppTheme.dark,
        themeMode: ThemeMode.system,
        routerConfig: appRouter,
      ),
    ),
  );
}
