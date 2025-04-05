import 'package:get/get.dart';

import 'package:daily_satori/app/modules/ai_config/bindings/ai_config_binding.dart';
import 'package:daily_satori/app/modules/ai_config/views/ai_config_view.dart';
import 'package:daily_satori/app/modules/ai_config_edit/bindings/ai_config_edit_binding.dart';
import 'package:daily_satori/app/modules/ai_config_edit/views/ai_config_edit_view.dart';
import 'package:daily_satori/app/modules/article_detail/bindings/article_detail_binding.dart';
import 'package:daily_satori/app/modules/article_detail/views/article_detail_view.dart';
import 'package:daily_satori/app/modules/articles/bindings/articles_binding.dart';
import 'package:daily_satori/app/modules/articles/views/articles_view.dart';
import 'package:daily_satori/app/modules/backup_restore/bindings/backup_restore_binding.dart';
import 'package:daily_satori/app/modules/backup_restore/views/backup_restore_view.dart';
import 'package:daily_satori/app/modules/diary/bindings/diary_binding.dart';
import 'package:daily_satori/app/modules/diary/views/diary_view.dart';
import 'package:daily_satori/app/modules/home/bindings/home_binding.dart';
import 'package:daily_satori/app/modules/home/views/home_view.dart';
import 'package:daily_satori/app/modules/left_bar/bindings/left_bar_binding.dart';
import 'package:daily_satori/app/modules/left_bar/views/left_bar_view.dart';
import 'package:daily_satori/app/modules/settings/bindings/settings_binding.dart';
import 'package:daily_satori/app/modules/settings/views/settings_view.dart';
import 'package:daily_satori/app/modules/share_dialog/bindings/share_dialog_binding.dart';
import 'package:daily_satori/app/modules/share_dialog/views/share_dialog_view.dart';

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
    GetPage(name: Routes.backupRestore, page: () => const BackupRestoreView(), binding: BackupRestoreBinding()),
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
  ];
}
