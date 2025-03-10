import 'package:get/get.dart';

import '../modules/article_detail/bindings/article_detail_binding.dart';
import '../modules/article_detail/views/article_detail_view.dart';
import '../modules/articles/bindings/articles_binding.dart';
import '../modules/articles/views/articles_view.dart';
import '../modules/backup_restore/bindings/backup_restore_binding.dart';
import '../modules/backup_restore/views/backup_restore_view.dart';
import '../modules/diary/bindings/diary_binding.dart';
import '../modules/diary/views/diary_view.dart';
import '../modules/home/bindings/home_binding.dart';
import '../modules/home/views/home_view.dart';
import '../modules/left_bar/bindings/left_bar_binding.dart';
import '../modules/left_bar/views/left_bar_view.dart';
import '../modules/settings/bindings/settings_binding.dart';
import '../modules/settings/views/settings_view.dart';
import '../modules/share_dialog/bindings/share_dialog_binding.dart';
import '../modules/share_dialog/views/share_dialog_view.dart';

part 'app_routes.dart';

class AppPages {
  AppPages._();

  static const INITIAL = Routes.HOME;
  // static const INITIAL = Routes.SHARE_DIALOG;

  static final routes = [
    GetPage(name: _Paths.HOME, page: () => const HomeView(), binding: HomeBinding()),
    GetPage(
      name: _Paths.SHARE_DIALOG,
      page: () => const ShareDialogView(),
      binding: ShareDialogBinding(),
      transition: Transition.downToUp,
    ),
    GetPage(
      name: _Paths.SETTINGS,
      page: () => const SettingsView(),
      binding: SettingsBinding(),
      transition: Transition.leftToRight,
      transitionDuration: Duration(milliseconds: 200),
      reverseTransitionDuration: Duration(milliseconds: 200),
    ),
    GetPage(
      name: _Paths.ARTICLES,
      page: () => const ArticlesView(),
      binding: ArticlesBinding(),
      transition: Transition.noTransition,
    ),
    GetPage(
      name: _Paths.ARTICLE_DETAIL,
      page: () => const ArticleDetailView(),
      binding: ArticleDetailBinding(),
      transition: Transition.topLevel,
    ),
    GetPage(
      name: _Paths.BACKUP_RESTORE,
      page: () => const BackupRestoreView(),
      binding: BackupRestoreBinding(),
      transition: Transition.rightToLeft,
    ),
    GetPage(
      name: _Paths.LEFT_BAR,
      page: () => const LeftBarView(),
      binding: LeftBarBinding(),
      transition: Transition.leftToRight,
      transitionDuration: Duration(milliseconds: 200),
      reverseTransitionDuration: Duration(milliseconds: 200),
    ),
    GetPage(
      name: _Paths.DIARY,
      page: () => const DiaryView(),
      binding: DiaryBinding(),
      transition: Transition.noTransition,
    ),
  ];
}
