import 'package:daily_satori/app_exports.dart';
import 'package:flutter/foundation.dart';
import '../controllers/home_controller.dart';
import 'package:daily_satori/app/modules/articles/views/articles_view.dart';
import 'package:daily_satori/app/modules/diary/views/diary_view.dart';
import 'package:daily_satori/app/modules/settings/views/settings_view.dart';

/// HomeView: 应用主页视图
/// 包含:
/// 1. 底部导航栏
/// 2. 主要内容区域（文章、日记、设置）
class HomeView extends GetView<HomeController> {
  const HomeView({super.key});

  static const String _tag = 'HomeView';

  @override
  Widget build(BuildContext context) {
    _logBuild();
    return Scaffold(body: _buildPageContent(), bottomNavigationBar: _buildNavigationBar());
  }

  /// 构建主要内容区域
  Widget _buildPageContent() {
    return Obx(() {
      final currentIndex = controller.currentIndex.value;
      _logPageSwitch(currentIndex);

      return IndexedStack(index: currentIndex, children: const [ArticlesView(), DiaryView(), SettingsView()]);
    });
  }

  /// 构建底部导航栏
  Widget _buildNavigationBar() {
    return Obx(
      () => BottomNavigationBar(
        currentIndex: controller.currentIndex.value,
        onTap: controller.changePage,
        items: const [
          BottomNavigationBarItem(icon: Icon(Icons.article_outlined), activeIcon: Icon(Icons.article), label: '文章'),
          BottomNavigationBarItem(icon: Icon(Icons.book_outlined), activeIcon: Icon(Icons.book), label: '日记'),
          BottomNavigationBarItem(icon: Icon(Icons.person_outline), activeIcon: Icon(Icons.person), label: '我的'),
        ],
      ),
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
      final pages = ['文章页', '日记页', '设置页'];
      logger.i('切换到${pages[index]} [$_tag:${DateTime.now()}]');
    }
  }
}
