import 'package:daily_satori/app_exports.dart';

import '../controllers/home_controller.dart';
import 'package:daily_satori/app/modules/articles/views/articles_view.dart';
import 'package:daily_satori/app/modules/diary/views/diary_view.dart';
import 'package:daily_satori/app/modules/settings/views/settings_view.dart';
import 'package:daily_satori/app/modules/articles/bindings/articles_binding.dart';
import 'package:daily_satori/app/modules/diary/bindings/diary_binding.dart';
import 'package:daily_satori/app/modules/settings/bindings/settings_binding.dart';

class HomeView extends GetView<HomeController> {
  const HomeView({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Obx(() {
        switch (controller.currentIndex.value) {
          case 0:
            ArticlesBinding().dependencies();
            return const ArticlesView();
          case 1:
            DiaryBinding().dependencies();
            return const DiaryView();
          case 2:
            SettingsBinding().dependencies();
            return const SettingsView();
          default:
            return const SizedBox.shrink();
        }
      }),
      bottomNavigationBar: Obx(() {
        return BottomNavigationBar(
          currentIndex: controller.currentIndex.value,
          onTap: controller.changePage,
          items: const [
            BottomNavigationBarItem(icon: Icon(Icons.article_outlined), activeIcon: Icon(Icons.article), label: '文章'),
            BottomNavigationBarItem(icon: Icon(Icons.book_outlined), activeIcon: Icon(Icons.book), label: '日记'),
            BottomNavigationBarItem(icon: Icon(Icons.person_outline), activeIcon: Icon(Icons.person), label: '我的'),
          ],
        );
      }),
    );
  }
}
