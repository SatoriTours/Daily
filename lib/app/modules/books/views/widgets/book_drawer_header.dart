import 'package:daily_satori/app_exports.dart';
import 'package:daily_satori/app/modules/books/controllers/books_controller.dart';
import 'package:daily_satori/app/styles/colors.dart';

/// 书籍抽屉头部组件
class BookDrawerHeader extends StatelessWidget {
  final BooksController controller;

  const BookDrawerHeader({super.key, required this.controller});

  @override
  Widget build(BuildContext context) {
    return DrawerHeader(
      decoration: BoxDecoration(color: AppColors.primary(context)),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              const Text('我的书架', style: TextStyle(color: Colors.white, fontSize: 24, fontWeight: FontWeight.bold)),
              IconButton(
                icon: const Icon(Icons.add, color: Colors.white),
                onPressed: controller.showAddCategoryDialog,
                tooltip: '添加分类',
              ),
            ],
          ),
          const SizedBox(height: 8),
          Obx(
            () => Text(
              '${controller.books.length}本书 · ${controller.categories.length}个分类',
              style: const TextStyle(color: Colors.white70),
            ),
          ),
        ],
      ),
    );
  }
}
