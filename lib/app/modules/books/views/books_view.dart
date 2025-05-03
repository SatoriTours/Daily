import 'package:daily_satori/app_exports.dart';
import 'package:daily_satori/app/modules/books/controllers/books_controller.dart';
import 'package:daily_satori/app/modules/books/views/widgets/widgets.dart';
import 'package:daily_satori/app/styles/colors.dart';
import 'package:daily_satori/app/models/book.dart';
import 'package:daily_satori/app/styles/font_style.dart';

/// 读书页面
///
/// 展示所有书籍观点，包含书籍过滤功能
class BooksView extends GetView<BooksController> {
  const BooksView({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: _buildAppBar(context),
      body: _buildBody(context),
      floatingActionButtonLocation: FloatingActionButtonLocation.endFloat,
    );
  }

  /// 构建应用栏
  PreferredSizeWidget _buildAppBar(BuildContext context) {
    return AppBar(
      title: _buildAppBarTitle(context),
      leading: _buildAppBarLeading(context),
      actions: _buildAppBarActions(),
      elevation: 1,
      backgroundColor: Theme.of(context).scaffoldBackgroundColor,
    );
  }

  /// 构建应用栏标题
  Widget _buildAppBarTitle(BuildContext context) {
    return Text(
      '读书悟道',
      style: MyFontStyle.titleLarge.copyWith(fontWeight: FontWeight.w600, color: AppColors.primary(context)),
    );
  }

  /// 构建应用栏左侧按钮
  Widget _buildAppBarLeading(BuildContext context) {
    return IconButton(
      icon: const Icon(Icons.menu_book),
      onPressed: () => _showBooksFilterDialog(context),
      tooltip: '选择书籍',
    );
  }

  /// 构建应用栏右侧按钮
  List<Widget> _buildAppBarActions() {
    return [_buildProcessingIndicator(), _buildAddBookButton(), _buildDeleteBookButton()];
  }

  /// 构建处理中指示器
  Widget _buildProcessingIndicator() {
    return Obx(
      () =>
          controller.isProcessing.value
              ? const Padding(
                padding: EdgeInsets.all(8.0),
                child: SizedBox(width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2)),
              )
              : const SizedBox.shrink(),
    );
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
    return IconButton(
      icon: const Icon(Icons.delete_outline, size: 20),
      onPressed: () => _showDeleteBookDialog(),
      tooltip: '删除书籍',
      padding: const EdgeInsets.all(8),
      constraints: const BoxConstraints(minWidth: 36, minHeight: 36),
    );
  }

  /// 显示书籍过滤对话框
  void _showBooksFilterDialog(BuildContext context) {
    final books = controller.getAllBooks();
    showModalBottomSheet(
      context: context,
      backgroundColor: Theme.of(context).scaffoldBackgroundColor,
      shape: const RoundedRectangleBorder(borderRadius: BorderRadius.vertical(top: Radius.circular(16))),
      builder:
          (context) => Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              _buildFilterDialogHeader(context),
              const Divider(height: 1),
              _buildFilterDialogList(context, books),
              const Divider(height: 1),
              _buildFilterDialogFooter(context),
            ],
          ),
    );
  }

  /// 构建过滤对话框标题
  Widget _buildFilterDialogHeader(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.all(16),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text('选择书籍', style: MyFontStyle.titleMedium),
          IconButton(
            icon: const Icon(Icons.close, size: 20),
            onPressed: () => Navigator.pop(context),
            padding: EdgeInsets.zero,
            constraints: const BoxConstraints(minWidth: 40, minHeight: 40),
          ),
        ],
      ),
    );
  }

  /// 构建过滤对话框列表
  Widget _buildFilterDialogList(BuildContext context, List<BookModel> books) {
    return Expanded(
      child: ListView.builder(
        padding: const EdgeInsets.symmetric(vertical: 8),
        itemCount: books.length + 1, // +1 用于"所有书籍"选项
        itemBuilder: (context, index) {
          // 第一项是"所有书籍"
          if (index == 0) {
            return _buildBookFilterItem(context, null, controller.fliterBookID.value == -1);
          }
          // 其他项是具体的书籍
          final book = books[index - 1];
          return _buildBookFilterItem(context, book, controller.fliterBookID.value == book.id);
        },
      ),
    );
  }

  /// 构建过滤对话框底部
  Widget _buildFilterDialogFooter(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.all(16),
      child: Center(
        child: InkWell(
          onTap: () {
            controller.selectBook(-1);
            controller.loadAllViewpoints();
          },
          borderRadius: BorderRadius.circular(20),
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
            child: Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                Icon(Icons.clear, size: 16, color: AppColors.primary(context)),
                const SizedBox(width: 8),
                Text('查看所有观点', style: MyFontStyle.bodyMedium.copyWith(color: AppColors.primary(context))),
              ],
            ),
          ),
        ),
      ),
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
          controller.selectBook(book.id);
        } else {
          controller.selectBook(-1);
        }
        controller.loadAllViewpoints();
        Navigator.pop(context);
      },
      child: Container(
        padding: const EdgeInsets.symmetric(vertical: 12, horizontal: 16),
        color: backgroundColor,
        child: _buildFilterItemContent(book, textColor, isSelected, context),
      ),
    );
  }

  /// 构建过滤项内容
  Widget _buildFilterItemContent(BookModel? book, Color textColor, bool isSelected, BuildContext context) {
    return Row(
      children: [
        Icon(book == null ? Icons.all_inclusive : Icons.book_outlined, size: 20, color: textColor),
        const SizedBox(width: 12),
        Expanded(
          child: Text(
            book?.title ?? '所有书籍',
            style: MyFontStyle.bodyMedium.copyWith(
              color: textColor,
              fontWeight: isSelected ? FontWeight.bold : FontWeight.normal,
            ),
          ),
        ),
        if (isSelected) Icon(Icons.check_circle, color: AppColors.primary(context), size: 20),
      ],
    );
  }

  /// 构建页面主体
  Widget _buildBody(BuildContext context) {
    return Obx(() {
      if (controller.allViewpoints.isEmpty) {
        return _buildEmptyView();
      }
      return _buildViewpointList();
    });
  }

  /// 构建空视图
  Widget _buildEmptyView() {
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          const Icon(Icons.auto_stories, size: 64, color: Colors.grey),
          const SizedBox(height: 16),
          Text('暂无观点，请先添加书籍', style: MyFontStyle.titleMedium),
        ],
      ),
    );
  }

  /// 构建观点列表
  Widget _buildViewpointList() {
    return PageView.builder(
      controller: PageController(initialPage: controller.currentViewpointIndex.value),
      onPageChanged: (index) {
        controller.currentViewpointIndex.value = index;
      },
      itemCount: controller.allViewpoints.length,
      itemBuilder: (context, index) {
        final viewpoint = controller.allViewpoints[index];
        return SingleChildScrollView(
          padding: const EdgeInsets.fromLTRB(16, 8, 16, 16),
          child: ViewpointCard(viewpoint: viewpoint, book: viewpoint.book),
        );
      },
    );
  }

  /// 显示删除书籍确认对话框
  void _showDeleteBookDialog() {
    final book = controller.currentViewpoint().book;
    if (book == null) return;

    UIUtils.showConfirmation(
      '删除书籍',
      '确定要删除《${book.title}》吗？\n此操作无法撤销，书籍相关的所有观点也将被删除。',
      confirmText: '删除',
      cancelText: '取消',
      onConfirmed: () {
        controller.deleteBook(book.id);
      },
    );
  }
}
