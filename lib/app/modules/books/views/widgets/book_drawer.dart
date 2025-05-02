import 'package:daily_satori/app_exports.dart';
import 'package:daily_satori/app/modules/books/controllers/books_controller.dart';
import 'package:daily_satori/app/modules/books/views/widgets/book_drawer_header.dart';
import 'package:daily_satori/app/modules/books/views/widgets/book_list.dart';
import 'package:daily_satori/app/modules/books/views/widgets/category_list.dart';

/// 书籍抽屉组件
class BookDrawer extends StatelessWidget {
  final BooksController controller;

  const BookDrawer({super.key, required this.controller});

  @override
  Widget build(BuildContext context) {
    return Drawer(
      child: Column(
        children: [
          BookDrawerHeader(controller: controller),
          Expanded(
            child: Obx(() {
              if (controller.isLoadingBooks.value && controller.books.isEmpty && controller.categories.isEmpty) {
                return const Center(child: CircularProgressIndicator());
              }

              return ListView(children: [CategoryList(controller: controller), BookList(controller: controller)]);
            }),
          ),
        ],
      ),
    );
  }
}
