import 'package:daily_satori/app_exports.dart';
import 'package:daily_satori/app/modules/books/controllers/books_controller.dart';
import 'package:daily_satori/app/modules/books/views/widgets/widgets.dart';
import 'package:daily_satori/app/styles/colors.dart';
import 'package:daily_satori/app/models/book.dart';
import 'package:daily_satori/app/styles/font_style.dart';
import 'package:daily_satori/app/components/app_bars/s_app_bar.dart';
import 'package:daily_satori/app/components/menus/s_popup_menu_item.dart';
import 'package:daily_satori/app/modules/diary/controllers/diary_controller.dart';
import 'package:daily_satori/app/modules/diary/views/widgets/diary_editor.dart';
import 'package:daily_satori/app/styles/diary_style.dart';
import 'package:daily_satori/app/styles/base/dimensions.dart' as base_dim;

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
      floatingActionButton: _buildFloatingQuickJournal(context),
      floatingActionButtonLocation: FloatingActionButtonLocation.endFloat,
    );
  }

  /// 构建应用栏
  PreferredSizeWidget _buildAppBar(BuildContext context) {
    return SAppBar(
      title: _buildAppBarTitle(context),
      leading: _buildAppBarLeading(context),
      actions: _buildAppBarActions(context),
      elevation: 1,
      // 使用 SAppBar 默认的主题主色背景与白色前景
      centerTitle: true,
    );
  }

  /// 构建应用栏标题
  Widget _buildAppBarTitle(BuildContext context) {
    return Text('读书悟道', style: MyFontStyle.appBarTitleStyle);
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
  List<Widget> _buildAppBarActions(BuildContext context) {
    return [_buildAddBookButton(), _buildMoreMenu(context)];
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

  /// 构建更多菜单（三点）
  Widget _buildMoreMenu(BuildContext context) {
    return PopupMenuButton<String>(
      icon: const Icon(Icons.more_horiz, size: 20),
      onSelected: (value) => _handleMoreMenuSelection(value, context),
      itemBuilder: (context) => [
        SPopupMenuItem<String>(value: 'refresh', icon: Icons.refresh, text: '刷新书籍内容'),
        SPopupMenuItem<String>(value: 'delete', icon: Icons.delete_outline, text: '删除当前书籍'),
      ],
    );
  }

  /// 处理更多菜单选择
  void _handleMoreMenuSelection(String value, BuildContext context) {
    switch (value) {
      case 'refresh':
        _confirmAndRefreshBook();
        break;
      case 'delete':
        _showDeleteBookDialog();
        break;
    }
  }

  /// 显示书籍过滤对话框
  void _showBooksFilterDialog(BuildContext context) {
    final books = controller.getAllBooks();
    showModalBottomSheet(
      context: context,
      backgroundColor: Theme.of(context).scaffoldBackgroundColor,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(base_dim.Dimensions.radiusL)),
      ),
      builder: (context) => Column(
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
            return _buildBookFilterItem(context, null, controller.filterBookID.value == -1);
          }
          // 其他项是具体的书籍
          final book = books[index - 1];
          return _buildBookFilterItem(context, book, controller.filterBookID.value == book.id);
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
    final theme = Theme.of(context);
    final isDark = theme.brightness == Brightness.dark;
    final backgroundColor = isSelected
        ? AppColors.primary(context).withValues(alpha: isDark ? 0.25 : 0.08)
        : Colors.transparent;
    final textColor = isSelected
        ? (isDark ? Colors.white : AppColors.primary(context))
        : theme.textTheme.bodyLarge?.color ?? Colors.black;

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
        if (isSelected) Icon(Icons.check_circle, color: textColor, size: 20),
      ],
    );
  }

  /// 构建页面主体
  Widget _buildBody(BuildContext context) {
    return Obx(() {
      // 读取当前索引以触发重建，从而使 PageView 使用新的 initialPage
      final _ = controller.currentViewpointIndex.value;
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
      controller: controller.pageController,
      onPageChanged: (index) {
        controller.goToViewpointIndex(index);
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

  /// 悬浮“记感想”按钮，始终可见不被遮挡
  Widget _buildFloatingQuickJournal(BuildContext context) {
    if (controller.allViewpoints.isEmpty) return const SizedBox.shrink();
    final theme = Theme.of(context);
    final bg = theme.colorScheme.primary;
    final fg = theme.colorScheme.onPrimary;

    return FloatingActionButton.small(
      heroTag: 'books_quick_journal',
      tooltip: '记感想',
      onPressed: () => _openJournalForCurrent(context),
      backgroundColor: bg,
      foregroundColor: fg,
      elevation: 4,
      hoverElevation: 6,
      focusElevation: 5,
      highlightElevation: 6,
      child: Icon(Icons.edit_note, color: fg, size: 20),
    );
  }

  void _openJournalForCurrent(BuildContext context) {
    final idx = controller.currentViewpointIndex.value;
    if (idx < 0 || idx >= controller.allViewpoints.length) return;
    final vp = controller.allViewpoints[idx];
    final book = vp.book;
    if (book == null) return;

    final diaryController = Get.find<DiaryController>();
    final title = vp.title.trim();
    final bookTitle = book.title.trim();
    final author = book.author.trim();

    final buffer = StringBuffer();
    buffer.writeln('观点：$title');
    if (bookTitle.isNotEmpty) {
      buffer.writeln('来源：《$bookTitle》${author.isNotEmpty ? ' · $author' : ''}');
    }
    buffer.writeln();
    buffer.writeln('[](app://books/viewpoint/${vp.id})');

    diaryController.contentController
      ..clear()
      ..text = buffer.toString()
      ..selection = TextSelection.collapsed(offset: buffer.length);

    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: DiaryStyle.bottomSheetColor(context),
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(base_dim.Dimensions.radiusL)),
      ),
      builder: (context) => DiaryEditor(controller: diaryController),
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

  /// 确认并刷新当前书籍（带加载提示）
  void _confirmAndRefreshBook() {
    final viewpoints = controller.allViewpoints;
    if (viewpoints.isEmpty) {
      UIUtils.showError('暂无可刷新的书籍');
      return;
    }
    final book = controller.currentViewpoint().book;
    if (book == null) {
      UIUtils.showError('未找到当前书籍');
      return;
    }

    UIUtils.showConfirmation(
      '刷新书籍',
      '将重新拉取《${book.title}》的观点内容，这可能需要一些时间。是否继续？',
      confirmText: '刷新',
      cancelText: '取消',
      onConfirmed: () {
        _doRefreshBook(book);
      },
    );
  }

  /// 执行刷新逻辑并展示更友好的进度提示
  Future<void> _doRefreshBook(BookModel book) async {
    UIUtils.showLoading(tips: '正在刷新《${book.title}》...');
    try {
      await controller.refreshBook(book.id);
      UIUtils.hideLoading();
      UIUtils.showSuccess('《${book.title}》已刷新');
    } catch (e) {
      UIUtils.hideLoading();
      UIUtils.showError('刷新失败，请稍后重试');
    }
  }
}
