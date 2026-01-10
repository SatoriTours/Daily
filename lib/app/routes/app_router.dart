/// GoRouter 路由配置
///
/// 使用 go_router 管理应用程序路由
library;

import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:daily_satori/app/routes/app_routes.dart';

import 'package:daily_satori/app/pages/home/views/home_view.dart';
import 'package:daily_satori/app/pages/articles/views/articles_view.dart';
import 'package:daily_satori/app/pages/article_detail/views/article_detail_view.dart';
import 'package:daily_satori/app/pages/diary/views/diary_view.dart';
import 'package:daily_satori/app/pages/books/views/books_view.dart';
import 'package:daily_satori/app/pages/books/views/book_search_view.dart';
import 'package:daily_satori/app/pages/settings/views/settings_view.dart';
import 'package:daily_satori/app/pages/ai_config/views/ai_config_view.dart';
import 'package:daily_satori/app/pages/ai_config_edit/views/ai_config_edit_view.dart';
import 'package:daily_satori/app/pages/ai_chat/views/ai_chat_view.dart';
import 'package:daily_satori/app/pages/weekly_summary/views/weekly_summary_view.dart';
import 'package:daily_satori/app/pages/share_dialog/views/share_dialog_view.dart';
import 'package:daily_satori/app/pages/backup_restore/views/backup_restore_view.dart';
import 'package:daily_satori/app/pages/backup_settings/views/backup_settings_view.dart';
import 'package:daily_satori/app/pages/plugin_center/views/plugin_center_view.dart';
import 'package:daily_satori/app/pages/left_bar/views/left_bar_view.dart';
import 'package:daily_satori/app/providers/first_launch_provider.dart';

/// 全局 Navigator Key
final GlobalKey<NavigatorState> rootNavigatorKey = GlobalKey<NavigatorState>();

/// GoRouter 实例
///
/// 注意：此路由需要在 ProviderScope 内部使用，请通过 routerProvider 访问
final GoRouter appRouter = GoRouter(
  navigatorKey: rootNavigatorKey,
  initialLocation: Routes.home,
  debugLogDiagnostics: false,
  redirect: (context, state) {
    // 获取 ProviderContainer
    final container = ProviderScope.containerOf(context);
    final firstLaunchState = container.read(firstLaunchControllerProvider);

    // 配置已完成，允许正常访问
    if (firstLaunchState.isSetupComplete) {
      return null;
    }

    // 配置未完成时，只允许访问以下页面
    final allowedPaths = [
      Routes.settings,
      Routes.aiConfig,
      Routes.aiConfigEdit,
      Routes.backupSettings,
      Routes.backupRestore, // 允许访问恢复备份页面
    ];

    final currentPath = state.uri.path;

    // 如果当前路径在允许列表中，允许访问
    if (allowedPaths.contains(currentPath)) {
      return null;
    }

    // 其他路径都重定向到设置页面
    return Routes.settings;
  },
  routes: [
    GoRoute(
      path: Routes.home,
      name: Routes.home,
      builder: (context, state) => const HomeView(),
    ),
    GoRoute(
      path: Routes.articles,
      name: Routes.articles,
      builder: (context, state) => const ArticlesView(),
    ),
    GoRoute(
      path: Routes.articleDetail,
      name: Routes.articleDetail,
      builder: (context, state) => const ArticleDetailView(),
    ),
    GoRoute(
      path: Routes.diary,
      name: Routes.diary,
      builder: (context, state) => const DiaryView(),
    ),
    GoRoute(
      path: Routes.books,
      name: Routes.books,
      builder: (context, state) => const BooksView(),
    ),
    GoRoute(
      path: Routes.bookSearch,
      name: Routes.bookSearch,
      builder: (context, state) => const BookSearchView(),
    ),
    GoRoute(
      path: Routes.settings,
      name: Routes.settings,
      builder: (context, state) => const SettingsView(),
    ),
    GoRoute(
      path: Routes.aiConfig,
      name: Routes.aiConfig,
      builder: (context, state) => const AIConfigView(),
    ),
    GoRoute(
      path: Routes.aiConfigEdit,
      name: Routes.aiConfigEdit,
      builder: (context, state) => const AIConfigEditView(),
    ),
    GoRoute(
      path: Routes.aiChat,
      name: Routes.aiChat,
      builder: (context, state) => const AIChatView(),
    ),
    GoRoute(
      path: Routes.weeklySummary,
      name: Routes.weeklySummary,
      builder: (context, state) => const WeeklySummaryView(),
    ),
    GoRoute(
      path: Routes.shareDialog,
      name: Routes.shareDialog,
      builder: (context, state) => const ShareDialogView(),
    ),
    GoRoute(
      path: Routes.backupRestore,
      name: Routes.backupRestore,
      builder: (context, state) => const BackupRestoreView(),
    ),
    GoRoute(
      path: Routes.backupSettings,
      name: Routes.backupSettings,
      builder: (context, state) => const BackupSettingsView(),
    ),
    GoRoute(
      path: Routes.pluginCenter,
      name: Routes.pluginCenter,
      builder: (context, state) => const PluginCenterView(),
    ),
    GoRoute(
      path: Routes.leftBar,
      name: Routes.leftBar,
      builder: (context, state) => const LeftBarView(),
    ),
  ],
);
