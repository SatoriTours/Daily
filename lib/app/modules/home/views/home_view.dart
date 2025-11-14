import 'package:daily_satori/app_exports.dart';
import 'package:flutter/foundation.dart';
import 'package:daily_satori/app/extensions/i18n_extension.dart';
import '../controllers/home_controller.dart';
import 'package:daily_satori/app/modules/articles/views/articles_view.dart';
import 'package:daily_satori/app/modules/books/views/books_view.dart';
import 'package:daily_satori/app/modules/diary/views/diary_view.dart';
import 'package:daily_satori/app/modules/settings/views/settings_view.dart';

/// HomeView: 应用主页视图
/// 包含:
/// 1. 底部导航栏
/// 2. 主要内容区域（文章、日记、读书、设置）
class HomeView extends GetView<HomeController> {
  const HomeView({super.key});

  static const String _tag = 'HomeView';

  @override
  Widget build(BuildContext context) {
    _logBuild();
    return Obx(() {
      final currentIndex = controller.currentIndex.value;
      _logPageSwitch(currentIndex);

      return Scaffold(
        body: IndexedStack(
          index: currentIndex,
          children: const [ArticlesView(), DiaryView(), BooksView(), SettingsView()],
        ),
        bottomNavigationBar: _buildNavigationBar(currentIndex),
      );
    });
  }

  /// 构建底部导航栏
  Widget _buildNavigationBar(int currentIndex) {
    return BottomNavigationBar(
      currentIndex: currentIndex,
      onTap: controller.changePage,
      type: BottomNavigationBarType.fixed,
      items: [
        BottomNavigationBarItem(icon: Icon(Icons.article_outlined), activeIcon: Icon(Icons.article), label: 'nav.articles'.t),
        BottomNavigationBarItem(icon: Icon(Icons.book_outlined), activeIcon: Icon(Icons.book), label: 'nav.diary'.t),
        BottomNavigationBarItem(icon: Icon(Icons.menu_book_outlined), activeIcon: Icon(Icons.menu_book), label: 'nav.books'.t),
        BottomNavigationBarItem(icon: Icon(Icons.person_outline), activeIcon: Icon(Icons.person), label: 'nav.settings'.t),
      ],
    );
  }

  /// 记录页面构建日志
  void _logBuild() {
    if (kDebugMode) {
      logger.i('主页视图构建 [$_tag:${DateTime.now()}]');
    }
  }

  /// 记录页面切换日志
  void _logPageSwitch(int index) {
    if (kDebugMode) {
      final pages = ['nav.articles'.t, 'nav.diary'.t, 'nav.books'.t, 'nav.settings'.t];
      logger.i('切换到${pages[index]} [$_tag:${DateTime.now()}]');
    }
  }
}
