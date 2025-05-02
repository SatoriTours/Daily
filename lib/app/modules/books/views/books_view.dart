import 'package:daily_satori/app_exports.dart';
import 'package:daily_satori/app/modules/books/controllers/books_controller.dart';
import 'package:daily_satori/app/modules/books/views/widgets/widgets.dart';
import 'package:daily_satori/app/styles/colors.dart';
import 'package:daily_satori/app/models/book.dart';

/// 读书页面
///
/// 展示书籍列表和书籍内容，包含左侧抽屉显示所有书籍和分类
class BooksView extends GetView<BooksController> {
  const BooksView({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: _buildAppBar(context),
      drawer: BookDrawer(controller: controller),
      body: _buildBody(context),
      floatingActionButtonLocation: FloatingActionButtonLocation.endFloat,
    );
  }

  /// 构建应用栏
  PreferredSizeWidget _buildAppBar(BuildContext context) {
    return AppBar(
      title: Obx(() {
        final book = controller.selectedBook.value;
        return Text(
          book?.title ?? '我的书架',
          style: Get.textTheme.titleLarge?.copyWith(fontWeight: FontWeight.w600, color: AppColors.primary(context)),
        );
      }),
      leading: IconButton(icon: const Icon(Icons.menu_book), onPressed: controller.openDrawer, tooltip: '打开书架'),
      actions: [
        IconButton(
          icon: const Icon(Icons.add, size: 20),
          onPressed: controller.showAddBookDialog,
          tooltip: '添加书籍',
          padding: const EdgeInsets.all(8),
          constraints: const BoxConstraints(minWidth: 36, minHeight: 36),
        ),
        Obx(
          () =>
              controller.selectedBook.value != null
                  ? IconButton(
                    icon: const Icon(Icons.delete_outline, size: 20),
                    onPressed: () => _showDeleteBookDialog(context),
                    tooltip: '删除书籍',
                    padding: const EdgeInsets.all(8),
                    constraints: const BoxConstraints(minWidth: 36, minHeight: 36),
                  )
                  : const SizedBox.shrink(),
        ),
      ],
      elevation: 1,
      backgroundColor: Theme.of(context).scaffoldBackgroundColor,
    );
  }

  /// 构建页面主体
  Widget _buildBody(BuildContext context) {
    return Obx(() {
      final book = controller.selectedBook.value;

      if (book == null) {
        return const EmptyState();
      }

      if (controller.isLoadingViewpoints.value && controller.bookViewpoints.isEmpty) {
        return const Center(child: CircularProgressIndicator());
      }

      if (controller.bookViewpoints.isEmpty) {
        return LoadingViewpoints(book: book);
      }

      // 显示书籍观点内容
      return ViewpointContent(
        controller: controller,
        viewpoints: controller.bookViewpoints,
        book: book,
        currentIndex: controller.currentViewpointIndex.value,
        onDeleteViewpoint: (viewpoint) => _showDeleteViewpointDialog(context, viewpoint),
      );
    });
  }

  /// 显示删除书籍确认对话框
  void _showDeleteBookDialog(BuildContext context) {
    final book = controller.selectedBook.value;
    if (book == null) return;

    Get.dialog(
      AlertDialog(
        title: const Text('删除书籍'),
        content: Text('确定要删除《${book.title}》吗？\n此操作无法撤销，书籍相关的所有观点也将被删除。'),
        actions: [
          TextButton(onPressed: Get.back, child: const Text('取消')),
          TextButton(
            onPressed: () {
              Get.back();
              controller.deleteBook(book.id);
            },
            style: TextButton.styleFrom(foregroundColor: Colors.red),
            child: const Text('删除'),
          ),
        ],
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      ),
      barrierDismissible: true,
    );
  }

  /// 显示删除观点确认对话框
  void _showDeleteViewpointDialog(BuildContext context, BookViewpointModel viewpoint) {
    Get.dialog(
      AlertDialog(
        title: const Text('删除观点'),
        content: Text('确定要删除"${viewpoint.title}"观点吗？\n此操作无法撤销。'),
        actions: [
          TextButton(onPressed: Get.back, child: const Text('取消')),
          TextButton(
            onPressed: () {
              Get.back();
              controller.deleteViewpoint(viewpoint.id);
            },
            style: TextButton.styleFrom(foregroundColor: Colors.red),
            child: const Text('删除'),
          ),
        ],
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      ),
      barrierDismissible: true,
    );
  }
}
