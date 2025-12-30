import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:daily_satori/app/navigation/app_navigation.dart';
import 'package:daily_satori/app/routes/app_routes.dart';
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
    // Phase 2: 使用 Riverpod + Flutter Navigator
    // GetX 仅用于 Controllers，导航已移除
    ProviderScope(
      child: MaterialApp(
        title: 'Daily Satori',
        theme: AppTheme.light,
        darkTheme: AppTheme.dark,
        themeMode: ThemeMode.system,
        navigatorKey: AppNavigation.navigatorKey,
        initialRoute: Routes.home,
        onGenerateRoute: AppNavigation.generateRoute,
      ),
    ),
  );
}
