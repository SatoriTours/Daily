import 'package:get/get.dart';

import 'package:daily_satori/app/pages/ai_config/bindings/ai_config_binding.dart';
import 'package:daily_satori/app/pages/ai_config/views/ai_config_view.dart';
import 'package:daily_satori/app/pages/ai_config_edit/bindings/ai_config_edit_binding.dart';
import 'package:daily_satori/app/pages/ai_config_edit/views/ai_config_edit_view.dart';
import 'package:daily_satori/app/pages/article_detail/bindings/article_detail_binding.dart';
import 'package:daily_satori/app/pages/article_detail/views/article_detail_view.dart';
import 'package:daily_satori/app/pages/articles/bindings/articles_binding.dart';
import 'package:daily_satori/app/pages/articles/views/articles_view.dart';
import 'package:daily_satori/app/pages/backup_restore/bindings/backup_restore_binding.dart';
import 'package:daily_satori/app/pages/backup_restore/views/backup_restore_view.dart';
import 'package:daily_satori/app/pages/backup_settings/bindings/backup_settings_binding.dart';
import 'package:daily_satori/app/pages/backup_settings/views/backup_settings_view.dart';
import 'package:daily_satori/app/pages/books/bindings/books_binding.dart';
import 'package:daily_satori/app/pages/books/views/books_view.dart';
import 'package:daily_satori/app/pages/books/bindings/book_search_binding.dart';
import 'package:daily_satori/app/pages/books/views/book_search_view.dart';
import 'package:daily_satori/app/pages/diary/bindings/diary_binding.dart';
import 'package:daily_satori/app/pages/diary/views/diary_view.dart';
import 'package:daily_satori/app/pages/home/bindings/home_binding.dart';
import 'package:daily_satori/app/pages/home/views/home_view.dart';
import 'package:daily_satori/app/pages/left_bar/bindings/left_bar_binding.dart';
import 'package:daily_satori/app/pages/left_bar/views/left_bar_view.dart';
import 'package:daily_satori/app/pages/plugin_center/bindings/plugin_center_binding.dart';
import 'package:daily_satori/app/pages/plugin_center/views/plugin_center_view.dart';
import 'package:daily_satori/app/pages/settings/bindings/settings_binding.dart';
import 'package:daily_satori/app/pages/settings/views/settings_view.dart';
import 'package:daily_satori/app/pages/share_dialog/bindings/share_dialog_binding.dart';
import 'package:daily_satori/app/pages/share_dialog/views/share_dialog_view.dart';
import 'package:daily_satori/app/pages/ai_chat/bindings/ai_chat_binding.dart';
import 'package:daily_satori/app/pages/ai_chat/views/ai_chat_view.dart';
import 'package:daily_satori/app/pages/weekly_summary/bindings/weekly_summary_binding.dart';
import 'package:daily_satori/app/pages/weekly_summary/views/weekly_summary_view.dart';

part 'app_routes.dart';

/// 应用程序页面路由配置
class AppPages {
  AppPages._();

  /// 初始路由
  static const initial = Routes.home;

  /// 路由列表
  static final routes = [
    GetPage(name: Routes.home, page: () => const HomeView(), binding: HomeBinding()),
    GetPage(
      name: Routes.shareDialog,
      page: () => const ShareDialogView(),
      binding: ShareDialogBinding(),
      transition: Transition.downToUp,
    ),
    GetPage(name: Routes.settings, page: () => const SettingsView(), binding: SettingsBinding()),
    GetPage(
      name: Routes.articles,
      page: () => const ArticlesView(),
      binding: ArticlesBinding(),
      transition: Transition.noTransition,
    ),
    GetPage(
      name: Routes.articleDetail,
      page: () => const ArticleDetailView(),
      binding: ArticleDetailBinding(),
      transition: Transition.topLevel,
    ),
    GetPage(
      name: Routes.backupRestore,
      page: () => const BackupRestoreView(),
      binding: BackupRestoreBinding(),
      transition: Transition.rightToLeft,
    ),
    GetPage(
      name: Routes.backupSettings,
      page: () => const BackupSettingsView(),
      binding: BackupSettingsBinding(),
      transition: Transition.rightToLeft,
    ),
    GetPage(name: Routes.leftBar, page: () => const LeftBarView(), binding: LeftBarBinding()),
    GetPage(name: Routes.diary, page: () => const DiaryView(), binding: DiaryBinding()),
    GetPage(
      name: Routes.aiConfig,
      page: () => const AIConfigView(),
      binding: AIConfigBinding(),
      transition: Transition.rightToLeft,
    ),
    GetPage(
      name: Routes.aiConfigEdit,
      page: () => const AIConfigEditView(),
      binding: AIConfigEditBinding(),
      transition: Transition.rightToLeft,
    ),
    GetPage(
      name: Routes.pluginCenter,
      page: () => const PluginCenterView(),
      binding: PluginCenterBinding(),
      transition: Transition.rightToLeft,
    ),
    GetPage(
      name: Routes.books,
      page: () => const BooksView(),
      binding: BooksBinding(),
      transition: Transition.noTransition,
    ),
    GetPage(
      name: Routes.bookSearch,
      page: () => const BookSearchView(),
      binding: BookSearchBinding(),
      transition: Transition.rightToLeft,
    ),
    GetPage(
      name: Routes.aiChat,
      page: () => const AIChatView(),
      binding: AIChatBinding(),
      transition: Transition.rightToLeft,
    ),
    GetPage(
      name: Routes.weeklySummary,
      page: () => const WeeklySummaryView(),
      binding: WeeklySummaryBinding(),
      transition: Transition.noTransition,
    ),
  ];
}
