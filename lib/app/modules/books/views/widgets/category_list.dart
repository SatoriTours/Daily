import 'package:daily_satori/app_exports.dart';
import 'package:daily_satori/app/models/book.dart';
import 'package:daily_satori/app/modules/books/controllers/books_controller.dart';

/// 书籍分类列表组件
class CategoryList extends StatelessWidget {
  final BooksController controller;

  const CategoryList({super.key, required this.controller});

  @override
  Widget build(BuildContext context) {
    return Obx(() {
      if (controller.categories.isEmpty) {
        return const SizedBox();
      }

      return Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // 全部书籍
          ListTile(
            leading: const Icon(Icons.menu_book),
            title: const Text('全部书籍'),
            selected: controller.selectedCategoryIndex.value == -1,
            onTap: () {
              controller.selectCategory(-1);
              controller.closeDrawer();
            },
          ),
          const Divider(),
          // 分类列表
          const Padding(
            padding: EdgeInsets.symmetric(horizontal: 16, vertical: 8),
            child: Text('分类', style: TextStyle(fontWeight: FontWeight.bold)),
          ),
          ...List.generate(controller.categories.length, (index) {
            final category = controller.categories[index];
            return _buildCategoryTile(context, category, index);
          }),
          const Divider(),
        ],
      );
    });
  }

  /// 构建分类项
  Widget _buildCategoryTile(BuildContext context, BookCategoryModel category, int index) {
    return ListTile(
      title: Text(category.name),
      selected: controller.selectedCategoryIndex.value == index,
      onTap: () {
        controller.selectCategory(index);
        controller.closeDrawer();
      },
    );
  }
}
