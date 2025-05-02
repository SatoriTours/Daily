import 'package:daily_satori/app_exports.dart';
import 'package:daily_satori/app/modules/books/controllers/books_controller.dart';
import 'package:daily_satori/app/modules/books/views/widgets/widgets.dart';
import 'package:daily_satori/app/styles/colors.dart';
import 'package:daily_satori/app/models/book.dart';

/// 读书页面
///
/// 展示所有书籍观点，包含左侧抽屉显示所有书籍和分类
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
      title: Text(
        '读书悟道',
        style: Get.textTheme.titleLarge?.copyWith(fontWeight: FontWeight.w600, color: AppColors.primary(context)),
      ),
      leading: _buildMenuButton(context),
      actions: _buildAppBarActions(context),
      elevation: 1,
      backgroundColor: Theme.of(context).scaffoldBackgroundColor,
    );
  }

  /// 构建菜单按钮
  Widget _buildMenuButton(BuildContext context) {
    return IconButton(
      icon: const Icon(Icons.menu_book),
      onPressed: () => _showBooksFilterDialog(context),
      tooltip: '选择书籍',
    );
  }

  /// 显示书籍过滤对话框
  void _showBooksFilterDialog(BuildContext context) {
    showModalBottomSheet(
      context: context,
      backgroundColor: Theme.of(context).scaffoldBackgroundColor,
      shape: const RoundedRectangleBorder(borderRadius: BorderRadius.vertical(top: Radius.circular(16))),
      builder: (context) => _buildBooksFilterContent(context),
    );
  }

  /// 构建书籍过滤对话框内容
  Widget _buildBooksFilterContent(BuildContext context) {
    return Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        // 标题栏
        Padding(
          padding: const EdgeInsets.all(16),
          child: Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text('选择书籍', style: Get.textTheme.titleMedium),
              IconButton(
                icon: const Icon(Icons.close, size: 20),
                onPressed: () => Navigator.pop(context),
                padding: EdgeInsets.zero,
                constraints: const BoxConstraints(minWidth: 40, minHeight: 40),
              ),
            ],
          ),
        ),

        const Divider(height: 1),

        // 书籍列表
        Expanded(
          child: Obx(() {
            return ListView.builder(
              padding: const EdgeInsets.symmetric(vertical: 8),
              itemCount: controller.books.length + 1, // +1 用于"所有书籍"选项
              itemBuilder: (context, index) {
                // 第一项是"所有书籍"
                if (index == 0) {
                  return _buildBookFilterItem(context, null, controller.selectedBook.value == null);
                }

                // 其他项是具体的书籍
                final book = controller.books[index - 1];
                return _buildBookFilterItem(context, book, controller.selectedBook.value?.id == book.id);
              },
            );
          }),
        ),

        const Divider(height: 1),

        // 清除过滤按钮
        Padding(
          padding: const EdgeInsets.all(16),
          child: Center(
            child: InkWell(
              onTap: () {
                controller.selectedBook.value = null;
                controller.allViewpoints.clear();
                Navigator.pop(context);
              },
              borderRadius: BorderRadius.circular(20),
              child: Padding(
                padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
                child: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Icon(Icons.clear, size: 16, color: AppColors.primary(context)),
                    const SizedBox(width: 8),
                    Text('查看所有观点', style: TextStyle(color: AppColors.primary(context))),
                  ],
                ),
              ),
            ),
          ),
        ),
      ],
    );
  }

  /// 构建单个书籍过滤项
  Widget _buildBookFilterItem(BuildContext context, BookModel? book, bool isSelected) {
    final backgroundColor = isSelected ? AppColors.primary(context).withValues(alpha: 51) : Colors.transparent;

    final textColor =
        isSelected ? AppColors.primary(context) : Theme.of(context).textTheme.bodyLarge?.color ?? Colors.black;

    return InkWell(
      onTap: () {
        if (book != null) {
          controller.selectBook(book);
        } else {
          // 选择"所有书籍"选项
          controller.selectedBook.value = null;
          controller.allViewpoints.clear();
          // 这里需要实现加载所有观点的逻辑
          // TODO: 实现加载所有观点的逻辑
        }
        Navigator.pop(context);
      },
      child: Container(
        padding: const EdgeInsets.symmetric(vertical: 12, horizontal: 16),
        color: backgroundColor,
        child: Row(
          children: [
            Icon(book == null ? Icons.all_inclusive : Icons.book_outlined, size: 20, color: textColor),
            const SizedBox(width: 12),
            Expanded(
              child: Text(
                book?.title ?? '所有书籍',
                style: TextStyle(color: textColor, fontWeight: isSelected ? FontWeight.bold : FontWeight.normal),
              ),
            ),
            if (isSelected) Icon(Icons.check_circle, color: AppColors.primary(context), size: 20),
          ],
        ),
      ),
    );
  }

  /// 构建应用栏操作按钮
  List<Widget> _buildAppBarActions(BuildContext context) {
    return [_buildAddBookButton(), _buildDeleteBookButton()];
  }

  /// 构建添加书籍按钮
  Widget _buildAddBookButton() {
    return IconButton(
      icon: const Icon(Icons.add, size: 20),
      onPressed: controller.showAddBookDialog,
      tooltip: '添加书籍',
      padding: const EdgeInsets.all(8),
      constraints: const BoxConstraints(minWidth: 36, minHeight: 36),
    );
  }

  /// 构建删除书籍按钮
  Widget _buildDeleteBookButton() {
    return Obx(
      () =>
          controller.selectedBook.value != null
              ? IconButton(
                icon: const Icon(Icons.delete_outline, size: 20),
                onPressed: () => _showDeleteBookDialog(),
                tooltip: '删除书籍',
                padding: const EdgeInsets.all(8),
                constraints: const BoxConstraints(minWidth: 36, minHeight: 36),
              )
              : const SizedBox.shrink(),
    );
  }

  /// 构建页面主体
  Widget _buildBody(BuildContext context) {
    return Obx(() {
      if (controller.isLoadingViewpoints.value && controller.allViewpoints.isEmpty) {
        return const Center(child: CircularProgressIndicator());
      }

      if (controller.allViewpoints.isEmpty) {
        return const Center(
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Icon(Icons.auto_stories, size: 64, color: Colors.grey),
              SizedBox(height: 16),
              Text('暂无观点，请先添加书籍', style: TextStyle(fontSize: 18)),
            ],
          ),
        );
      }

      // 显示所有观点内容
      return _buildViewpointContent();
    });
  }

  /// 构建观点内容
  Widget _buildViewpointContent() {
    final context = Get.context!;
    final book = controller.selectedBook.value;
    if (book == null) return const SizedBox();

    return Column(
      children: [
        _buildBookInfoHeader(context, book),
        Expanded(
          child: PageView.builder(
            controller: PageController(initialPage: controller.currentViewpointIndex.value),
            onPageChanged: (index) {
              controller.currentViewpointIndex.value = index;
            },
            itemCount: controller.allViewpoints.length,
            itemBuilder: (context, index) {
              final viewpoint = controller.allViewpoints[index];
              return SingleChildScrollView(
                padding: const EdgeInsets.fromLTRB(16, 8, 16, 16),
                child: ViewpointCard(
                  viewpoint: viewpoint,
                  onDelete: () => _showDeleteViewpointDialog(context, viewpoint),
                ),
              );
            },
          ),
        ),
      ],
    );
  }

  /// 构建书籍信息头部
  Widget _buildBookInfoHeader(BuildContext context, BookModel book) {
    return Container(
      padding: const EdgeInsets.fromLTRB(16, 12, 16, 8),
      child: Column(
        children: [
          Row(
            children: [
              Icon(Icons.auto_stories, color: AppColors.primary(context)),
              const SizedBox(width: 8),
              Expanded(
                child: GestureDetector(
                  onTap: () => _showBookDetails(context, book),
                  child: Row(
                    children: [
                      Expanded(
                        child: Text(
                          book.title,
                          style: Get.textTheme.titleMedium?.copyWith(
                            fontWeight: FontWeight.bold,
                            decoration: TextDecoration.underline,
                            decorationColor: AppColors.primary(context).withValues(alpha: 0.5),
                          ),
                          overflow: TextOverflow.ellipsis,
                        ),
                      ),
                      Icon(Icons.info_outline, size: 16, color: AppColors.primary(context)),
                    ],
                  ),
                ),
              ),
              Obx(
                () => Text(
                  '${controller.currentViewpointIndex.value + 1}/${controller.allViewpoints.length}',
                  style: Get.textTheme.bodyMedium?.copyWith(fontWeight: FontWeight.w500),
                ),
              ),
            ],
          ),
          const Divider(height: 24),
        ],
      ),
    );
  }

  /// 显示书籍详细信息对话框
  void _showBookDetails(BuildContext context, BookModel book) {
    Get.dialog(
      AlertDialog(
        title: Text(book.title),
        content: Container(
          width: double.maxFinite,
          child: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                _buildBookCover(context),
                const SizedBox(height: 16),
                Text('作者：${book.author}', style: Get.textTheme.bodyMedium),
                const SizedBox(height: 8),
                Text('分类：${book.category}', style: Get.textTheme.bodyMedium),
                if (book.introduction.isNotEmpty) ...[
                  const SizedBox(height: 16),
                  Text('简介', style: Get.textTheme.titleSmall?.copyWith(fontWeight: FontWeight.bold)),
                  const SizedBox(height: 8),
                  Text(book.introduction, style: Get.textTheme.bodyMedium?.copyWith(height: 1.5)),
                ],
                const SizedBox(height: 16),
                Text('观点数量：${controller.allViewpoints.length}', style: Get.textTheme.bodyMedium),
              ],
            ),
          ),
        ),
        actions: [TextButton(onPressed: Get.back, child: const Text('关闭'))],
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      ),
      barrierDismissible: true,
    );
  }

  /// 构建书籍封面
  Widget _buildBookCover(BuildContext context) {
    return Center(
      child: Container(
        width: 120,
        height: 180,
        decoration: BoxDecoration(
          color: AppColors.cardBackground(context),
          borderRadius: BorderRadius.circular(8),
          boxShadow: [BoxShadow(color: Colors.black.withValues(alpha: 0.1), blurRadius: 4, offset: const Offset(0, 2))],
        ),
        child: Center(child: Icon(Icons.menu_book, size: 48, color: AppColors.primary(context).withValues(alpha: 0.5))),
      ),
    );
  }

  /// 显示删除书籍确认对话框
  void _showDeleteBookDialog() {
    final book = controller.selectedBook.value;
    if (book == null) return;

    Get.dialog(
      AlertDialog(
        title: const Text('删除书籍'),
        content: Text('确定要删除《${book.title}》吗？\n此操作无法撤销，书籍相关的所有观点也将被删除。'),
        actions: _buildDeleteBookDialogActions(book),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      ),
      barrierDismissible: true,
    );
  }

  /// 构建删除书籍对话框按钮
  List<Widget> _buildDeleteBookDialogActions(BookModel book) {
    return [
      TextButton(onPressed: Get.back, child: const Text('取消')),
      TextButton(
        onPressed: () {
          Get.back();
          controller.deleteBook(book.id);
        },
        style: TextButton.styleFrom(foregroundColor: Colors.red),
        child: const Text('删除'),
      ),
    ];
  }

  /// 显示删除观点确认对话框
  void _showDeleteViewpointDialog(BuildContext context, BookViewpointModel viewpoint) {
    Get.dialog(
      AlertDialog(
        title: const Text('删除观点'),
        content: Text('确定要删除"${viewpoint.title}"观点吗？\n此操作无法撤销。'),
        actions: _buildDeleteViewpointDialogActions(viewpoint),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      ),
      barrierDismissible: true,
    );
  }

  /// 构建删除观点对话框按钮
  List<Widget> _buildDeleteViewpointDialogActions(BookViewpointModel viewpoint) {
    return [
      TextButton(onPressed: Get.back, child: const Text('取消')),
      TextButton(
        onPressed: () {
          Get.back();
          controller.deleteViewpoint(viewpoint.id);
        },
        style: TextButton.styleFrom(foregroundColor: Colors.red),
        child: const Text('删除'),
      ),
    ];
  }
}
