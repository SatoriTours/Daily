/// Simple Navigation Service
/// 替代 GetX 导航功能
library;

import 'package:flutter/material.dart';
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
import 'package:daily_satori/app/services/logger_service.dart';

class AppNavigation {
  AppNavigation._();

  static final GlobalKey<NavigatorState> navigatorKey = GlobalKey<NavigatorState>();

  static Route<dynamic> generateRoute(RouteSettings settings) {
    switch (settings.name) {
      case Routes.home:
        return MaterialPageRoute(builder: (_) => const HomeView());

      case Routes.articles:
        return MaterialPageRoute(builder: (_) => const ArticlesView());

      case Routes.articleDetail:
        return MaterialPageRoute(
          builder: (_) => const ArticleDetailView(),
          settings: settings,
        );

      case Routes.diary:
        return MaterialPageRoute(builder: (_) => const DiaryView());

      case Routes.books:
        return MaterialPageRoute(builder: (_) => const BooksView());

      case Routes.bookSearch:
        return MaterialPageRoute(
          builder: (_) => const BookSearchView(),
          settings: settings,
        );

      case Routes.settings:
        return MaterialPageRoute(builder: (_) => const SettingsView());

      case Routes.aiConfig:
        return MaterialPageRoute(builder: (_) => const AIConfigView());

      case Routes.aiConfigEdit:
        return MaterialPageRoute(
          builder: (_) => const AIConfigEditView(),
          settings: settings,
        );

      case Routes.aiChat:
        return MaterialPageRoute(builder: (_) => const AIChatView());

      case Routes.weeklySummary:
        return MaterialPageRoute(builder: (_) => const WeeklySummaryView());

      case Routes.shareDialog:
        return MaterialPageRoute(
          builder: (_) => const ShareDialogView(),
          settings: settings,
        );

      case Routes.backupRestore:
        return MaterialPageRoute(builder: (_) => const BackupRestoreView());

      case Routes.backupSettings:
        return MaterialPageRoute(builder: (_) => const BackupSettingsView());

      case Routes.pluginCenter:
        return MaterialPageRoute(builder: (_) => const PluginCenterView());

      case Routes.leftBar:
        return MaterialPageRoute(builder: (_) => const LeftBarView());

      default:
        logger.w('Unknown route: ${settings.name}');
        return MaterialPageRoute(builder: (_) => const HomeView());
    }
  }

  static Future<T?> toNamed<T>(String routeName, {Object? arguments}) {
    final navigator = navigatorKey.currentState;
    if (navigator == null) return Future.value(null);
    return navigator.pushNamed<T>(routeName, arguments: arguments);
  }

  static Future<T?> to<T>(Widget page) {
    final navigator = navigatorKey.currentState;
    if (navigator == null) return Future.value(null);
    return navigator.push<T>(MaterialPageRoute(builder: (_) => page));
  }

  static void back<T>({T? result}) {
    if (navigatorKey.currentState?.canPop() == true) {
      navigatorKey.currentState?.pop<T>(result);
    }
  }

  static Future<T?> offNamed<T>(String routeName, {Object? arguments}) {
    final navigator = navigatorKey.currentState;
    if (navigator == null) return Future.value(null);
    return navigator.pushReplacementNamed<T, T>(routeName, arguments: arguments);
  }

  static Future<T?> offAllNamed<T>(String routeName, {Object? arguments}) {
    final navigator = navigatorKey.currentState;
    if (navigator == null) return Future.value(null);
    return navigator.pushNamedAndRemoveUntil<T>(
      routeName,
      (route) => false,
      arguments: arguments,
    );
  }
}
