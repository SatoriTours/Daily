/// Simple Navigation Service
/// 基于 go_router 的导航服务
library;

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:daily_satori/app/routes/app_router.dart';
import 'package:go_router/go_router.dart';

class AppNavigation {
  AppNavigation._();

  /// 获取 Navigator Key（go_router 的 root navigator key）
  static GlobalKey<NavigatorState> get navigatorKey => rootNavigatorKey;

  /// 导航到命名路由
  static Future<T?> toNamed<T>(String routeName, {Object? arguments}) {
    return appRouter.pushNamed<T>(routeName, extra: arguments);
  }

  /// 导航到指定页面
  static Future<T?> to<T>(Widget page) {
    final navigator = navigatorKey.currentState;
    if (navigator == null) return Future.value(null);
    return navigator.push<T>(MaterialPageRoute(builder: (_) => page));
  }

  /// 返回上一页
  static void back<T>({T? result}) {
    if (appRouter.canPop()) {
      appRouter.pop(result);
    }
  }

  /// 替换当前路由
  static Future<T?> offNamed<T>(String routeName, {Object? arguments}) async {
    appRouter.pushReplacementNamed(routeName, extra: arguments);
    return null;
  }

  /// 清空所有路由并导航到新路由
  static Future<T?> offAllNamed<T>(
    String routeName, {
    Object? arguments,
  }) async {
    appRouter.goNamed(routeName, extra: arguments);
    return null;
  }

  static Object? arguments(BuildContext context) {
    final state = GoRouterState.of(context);
    return state.extra;
  }

  /// 退出应用
  static void exitApp() {
    SystemNavigator.pop();
  }
}
