import 'package:daily_satori/app_exports.dart';
import 'package:daily_satori/app/models/book.dart';
import 'package:daily_satori/app/modules/books/controllers/books_controller.dart';

/// 书籍列表组件
class BookList extends StatelessWidget {
  final BooksController controller;

  const BookList({super.key, required this.controller});

  @override
  Widget build(BuildContext context) {
    return Obx(() {
      if (controller.books.isEmpty) {
        return const SizedBox();
      }

      return Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Padding(
            padding: EdgeInsets.symmetric(horizontal: 16, vertical: 8),
            child: Text('书籍', style: TextStyle(fontWeight: FontWeight.bold)),
          ),
          ...List.generate(controller.books.length, (index) {
            final book = controller.books[index];
            return _buildBookTile(context, book);
          }),
        ],
      );
    });
  }

  /// 构建书籍项
  Widget _buildBookTile(BuildContext context, BookModel book) {
    return Obx(() {
      final isSelected = controller.selectedBook.value?.id == book.id;
      return ListTile(
        title: Text(book.title),
        subtitle: Text(book.author),
        selected: isSelected,
        onTap: () {
          controller.selectBook(book);
          controller.closeDrawer();
        },
      );
    });
  }
}
