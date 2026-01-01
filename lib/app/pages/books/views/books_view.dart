import 'package:daily_satori/app_exports.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:daily_satori/app/providers/providers.dart';
import 'package:daily_satori/app/pages/books/providers/books_controller_provider.dart';
import 'package:daily_satori/app/pages/books/views/widgets/widgets.dart';
import 'package:daily_satori/app/styles/index.dart';
import 'package:daily_satori/app/components/app_bars/s_app_bar.dart';
import 'package:daily_satori/app/components/menus/s_popup_menu_item.dart';
import 'package:daily_satori/app/pages/diary/views/widgets/diary_editor.dart';
import 'package:daily_satori/app/styles/base/dimensions.dart' as base_dim;

/// 读书页面
///
/// 展示所有书籍观点，包含书籍过滤功能
class BooksView extends ConsumerWidget {
  const BooksView({super.key});
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return Scaffold(
      appBar: _buildAppBar(context, ref),
      body: _buildBody(context, ref),
      floatingActionButton: _buildFloatingQuickJournal(context, ref),
      floatingActionButtonLocation: FloatingActionButtonLocation.endFloat,
    );
  }

  /// 构建应用栏
  PreferredSizeWidget _buildAppBar(BuildContext context, WidgetRef ref) {
    return SAppBar(
      title: _buildAppBarTitle(context),
      leading: _buildAppBarLeading(context, ref),
      actions: _buildAppBarActions(context, ref),
      elevation: 1,
      centerTitle: true,
      backgroundColorLight: AppColors.primary,
      backgroundColorDark: AppColors.backgroundDark,
      foregroundColor: Colors.white,
    );
  }

  /// 构建应用栏标题
  Widget _buildAppBarTitle(BuildContext context) {
    return Text('title.books_wisdom'.t, style: AppTypography.titleLarge.copyWith(color: Colors.white));
  }

  /// 构建应用栏左侧按钮
  Widget _buildAppBarLeading(BuildContext context, WidgetRef ref) {
    return IconButton(
      icon: const Icon(Icons.menu_book),
      onPressed: () => _showBooksFilterDialog(context, ref),
      tooltip: 'title.select_book'.t,
    );
  }

  /// 构建应用栏右侧按钮
  List<Widget> _buildAppBarActions(BuildContext context, WidgetRef ref) {
    return [_buildAddBookButton(context, ref), _buildMoreMenu(context, ref)];
  }

  /// 构建添加书籍按钮
  Widget _buildAddBookButton(BuildContext context, WidgetRef ref) {
    return IconButton(
      icon: const Icon(Icons.add, size: Dimensions.iconSizeM),
      onPressed: () => ref.read(booksControllerProvider.notifier).showAddBookDialog(context),
      tooltip: 'tooltip.add_book'.t,
      padding: Dimensions.paddingS,
      constraints: const BoxConstraints(minWidth: 36, minHeight: 36),
    );
  }

  /// 构建更多菜单（三点）
  Widget _buildMoreMenu(BuildContext context, WidgetRef ref) {
    return PopupMenuButton<String>(
      icon: const Icon(Icons.more_horiz, size: Dimensions.iconSizeM),
      onSelected: (value) => _handleMoreMenuSelection(value, context, ref),
      itemBuilder: (context) => [
        SPopupMenuItem<String>(value: 'shuffle', icon: Icons.shuffle, text: 'menu.shuffle'.t),
        SPopupMenuItem<String>(value: 'refresh', icon: Icons.refresh, text: 'menu.refresh_book'.t),
        SPopupMenuItem<String>(value: 'delete', icon: Icons.delete_outline, text: 'menu.delete_book'.t),
      ],
    );
  }

  /// 处理更多菜单选择
  void _handleMoreMenuSelection(String value, BuildContext context, WidgetRef ref) {
    switch (value) {
      case 'refresh':
        _confirmAndRefreshBook(context, ref);
        break;
      case 'shuffle':
        ref.read(booksControllerProvider.notifier).refreshRecommendations();
        break;
      case 'delete':
        _showDeleteBookDialog(context, ref);
        break;
    }
  }

  /// 显示书籍过滤对话框
  void _showBooksFilterDialog(BuildContext context, WidgetRef ref) {
    final booksState = ref.read(booksStateProvider);
    final books = booksState.allBooks;
    showModalBottomSheet(
      context: context,
      backgroundColor: Theme.of(context).scaffoldBackgroundColor,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(base_dim.Dimensions.radiusL)),
      ),
      builder: (context) => Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          _buildFilterDialogHeader(context),
          const Divider(height: 1),
          _buildFilterDialogList(context, ref, books),
          const Divider(height: 1),
          _buildFilterDialogFooter(context, ref),
        ],
      ),
    );
  }

  /// 构建过滤对话框标题
  Widget _buildFilterDialogHeader(BuildContext context) {
    return Padding(
      padding: Dimensions.paddingM,
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text('title.select_book'.t, style: AppTypography.titleMedium),
          IconButton(
            icon: const Icon(Icons.close, size: Dimensions.iconSizeM),
            onPressed: () => Navigator.pop(context),
            padding: EdgeInsets.zero,
            constraints: const BoxConstraints(minWidth: 40, minHeight: 40),
          ),
        ],
      ),
    );
  }

  /// 构建过滤对话框列表
  Widget _buildFilterDialogList(BuildContext context, WidgetRef ref, List<BookModel> books) {
    final booksState = ref.watch(booksStateProvider);
    return Expanded(
      child: ListView.builder(
        padding: Dimensions.paddingVerticalS,
        itemCount: books.length + 1, // +1 用于"所有书籍"选项
        itemBuilder: (context, index) {
          // 第一项是"所有书籍"
          if (index == 0) {
            return _buildBookFilterItem(context, ref, null, booksState.filterBookID == -1);
          }
          // 其他项是具体的书籍
          final book = books[index - 1];
          return _buildBookFilterItem(context, ref, book, booksState.filterBookID == book.id);
        },
      ),
    );
  }

  /// 构建过滤对话框底部
  Widget _buildFilterDialogFooter(BuildContext context, WidgetRef ref) {
    return Padding(
      padding: Dimensions.paddingM,
      child: Center(
        child: InkWell(
          onTap: () {
            ref.read(booksControllerProvider.notifier).selectBook(-1);
            ref.read(booksControllerProvider.notifier).loadAllViewpoints();
          },
          borderRadius: BorderRadius.circular(Dimensions.radiusL + 4),
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: Dimensions.spacingM, vertical: Dimensions.spacingS),
            child: Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                Icon(Icons.clear, size: Dimensions.iconSizeXs, color: AppColors.getPrimary(context)),
                Dimensions.horizontalSpacerS,
                Text('查看所有观点', style: AppTypography.bodyMedium.copyWith(color: AppColors.getPrimary(context))),
              ],
            ),
          ),
        ),
      ),
    );
  }

  /// 构建单个书籍过滤项
  Widget _buildBookFilterItem(BuildContext context, WidgetRef ref, BookModel? book, bool isSelected) {
    final theme = Theme.of(context);
    final isDark = theme.brightness == Brightness.dark;
    final backgroundColor = isSelected
        ? AppColors.getPrimary(context).withValues(alpha: isDark ? Opacities.mediumHigh : Opacities.low)
        : Colors.transparent;
    final textColor = isSelected
        ? (isDark ? Colors.white : AppColors.getPrimary(context))
        : theme.textTheme.bodyLarge?.color ?? Colors.black;
    return InkWell(
      onTap: () {
        if (book != null) {
          ref.read(booksControllerProvider.notifier).selectBook(book.id);
        } else {
          ref.read(booksControllerProvider.notifier).selectBook(-1);
        }
        ref.read(booksControllerProvider.notifier).loadAllViewpoints();
        Navigator.pop(context);
      },
      child: Container(
        padding: const EdgeInsets.symmetric(vertical: Dimensions.spacingM - 4, horizontal: Dimensions.spacingM),
        color: backgroundColor,
        child: _buildFilterItemContent(book, textColor, isSelected, context),
      ),
    );
  }

  /// 构建过滤项内容
  Widget _buildFilterItemContent(BookModel? book, Color textColor, bool isSelected, BuildContext context) {
    return Row(
      children: [
        Icon(book == null ? Icons.all_inclusive : Icons.book_outlined, size: Dimensions.iconSizeM, color: textColor),
        Dimensions.horizontalSpacerS,
        Expanded(
          child: Text(
            book?.title ?? 'book.all_books'.t,
            style: AppTypography.bodyMedium.copyWith(
              color: textColor,
              fontWeight: isSelected ? FontWeight.bold : FontWeight.normal,
            ),
          ),
        ),
        if (isSelected) Icon(Icons.check_circle, color: textColor, size: Dimensions.iconSizeM),
      ],
    );
  }

  /// 构建页面主体
  Widget _buildBody(BuildContext context, WidgetRef ref) {
    final booksState = ref.watch(booksStateProvider);
    if (booksState.viewpoints.isEmpty) {
      return _buildEmptyView();
    }
    return _buildViewpointList(ref);
  }

  /// 构建空视图
  Widget _buildEmptyView() {
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          const Icon(Icons.auto_stories, size: 64, color: Colors.grey),
          Dimensions.verticalSpacerM,
          Text('empty.no_viewpoint'.t, style: AppTypography.titleMedium),
        ],
      ),
    );
  }

  /// 构建观点列表
  Widget _buildViewpointList(WidgetRef ref) {
    final booksState = ref.watch(booksStateProvider);
    final controllerState = ref.watch(booksControllerProvider);
    return PageView.builder(
      controller: controllerState.pageController,
      onPageChanged: (index) {
        ref.read(booksControllerProvider.notifier).goToViewpointIndex(index);
      },
      itemCount: booksState.viewpoints.length,
      itemBuilder: (context, index) {
        final viewpoint = booksState.viewpoints[index];
        final book = BookRepository.i.find(viewpoint.bookId);
        return SingleChildScrollView(
          padding: const EdgeInsets.fromLTRB(
            Dimensions.spacingM,
            Dimensions.spacingS,
            Dimensions.spacingM,
            Dimensions.spacingM,
          ),
          child: ViewpointCard(viewpoint: viewpoint, book: book),
        );
      },
    );
  }

  /// 悬浮"记感想"按钮，始终可见不被遮挡
  Widget _buildFloatingQuickJournal(BuildContext context, WidgetRef ref) {
    final theme = Theme.of(context);
    final bg = theme.colorScheme.primary;
    final fg = theme.colorScheme.onPrimary;
    return FloatingActionButton.small(
      heroTag: 'books_quick_journal',
      tooltip: 'tooltip.add_insight'.t,
      onPressed: () => _openJournalForCurrent(context, ref),
      backgroundColor: bg,
      foregroundColor: fg,
      elevation: 4,
      hoverElevation: 6,
      focusElevation: 5,
      highlightElevation: 6,
      child: Icon(Icons.edit_note, color: fg, size: Dimensions.iconSizeM),
    );
  }

  void _openJournalForCurrent(BuildContext context, WidgetRef ref) {
    final booksState = ref.read(booksStateProvider);
    // 若当前有观点,拼装带完整信息的模板;否则提供空白感悟模板
    String preset = '';
    int cursorPosition = 0;

    if (booksState.viewpoints.isNotEmpty) {
      final idx = booksState.currentViewpointIndex.clamp(0, booksState.viewpoints.length - 1);
      final vp = booksState.viewpoints[idx];
      final book = BookRepository.i.find(vp.bookId);
      final viewpointTitle = vp.title.trim();
      final content = vp.content.trim();
      final bookTitle = (book?.title ?? '未知书籍').trim();
      final author = (book?.author ?? '').trim();

      // 处理观点标题：移除末尾的标点符号，使标题拼接更自然
      final cleanViewpointTitle = viewpointTitle.replaceAll(RegExp(r'[。.！!？?，,；;：:]+$'), '');

      final buffer = StringBuffer();
      // 添加标题：书名 - 观点标题感悟
      buffer.writeln('# $bookTitle - $cleanViewpointTitle感悟');
      buffer.writeln();

      // 记录光标位置（在标题后空一行的位置）
      cursorPosition = buffer.length;

      // 添加用户输入区域的占位（空行）
      buffer.writeln();
      buffer.writeln();

      // 添加分隔线
      buffer.writeln('---');
      buffer.writeln();

      // 添加观点信息（完整拷贝，防止书籍删除后丢失）
      buffer.writeln('**观点**：$viewpointTitle');
      buffer.writeln();
      if (content.isNotEmpty) {
        buffer.writeln('**内容**：');
        buffer.writeln();
        buffer.writeln('> $content');
        buffer.writeln();
      }
      buffer.write('**出处**：《$bookTitle》');
      if (author.isNotEmpty) {
        buffer.write(' · $author');
      }
      buffer.writeln();
      buffer.writeln();
      // 添加深链（可选，用于快速跳转，但完整信息已拷贝）
      buffer.writeln('[查看原始观点](app://books/viewpoint/${vp.id})');

      preset = buffer.toString();
    } else {
      preset = '# 读书感悟\n\n';
      cursorPosition = preset.length;
    }

    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: DiaryStyles.getBottomSheetColor(context),
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(base_dim.Dimensions.radiusL)),
      ),
      builder: (context) => DiaryEditor(initialContent: preset, initialCursorPosition: cursorPosition),
    );
  }

  /// 显示删除书籍确认对话框
  void _showDeleteBookDialog(BuildContext context, WidgetRef ref) {
    final booksState = ref.read(booksStateProvider);
    BookModel? book;
    // 尝试从当前观点获取书籍
    final currentViewpoint = booksState.currentViewpoint;
    if (currentViewpoint != null) {
      book = BookRepository.i.find(currentViewpoint.bookId);
    } else if (booksState.filterBookID != -1) {
      // 如果没有当前观点但有选中的书籍，直接获取书籍信息
      final books = booksState.allBooks;
      book = books.where((b) => b.id == booksState.filterBookID).firstOrNull;
    }
    if (book == null) {
      UIUtils.showError('error.no_book_to_delete');
      return;
    }
    DialogUtils.showConfirm(
      title: 'dialog.delete_book'.t,
      message: '${'dialog.delete_book_confirm'.t}《${book.title}》？\n${'dialog.delete_book_warning'.t}',
      confirmText: 'button.delete'.t,
      cancelText: 'button.cancel'.t,
      onConfirm: () {
        ref.read(booksControllerProvider.notifier).deleteBook(book!.id);
      },
    );
  }

  /// 确认并刷新当前书籍（带加载提示）
  void _confirmAndRefreshBook(BuildContext context, WidgetRef ref) {
    final booksState = ref.read(booksStateProvider);
    BookModel? book;
    // 尝试从当前观点获取书籍
    final currentViewpoint = booksState.currentViewpoint;
    if (currentViewpoint != null) {
      book = BookRepository.i.find(currentViewpoint.bookId);
    } else if (booksState.filterBookID != -1) {
      // 如果没有当前观点但有选中的书籍，直接获取书籍信息
      final books = booksState.allBooks;
      book = books.where((b) => b.id == booksState.filterBookID).firstOrNull;
    }
    if (book == null) {
      // 尝试刷新任意一本书（刷新第一本）
      final books = booksState.allBooks;
      if (books.isNotEmpty) {
        book = books.first;
      }
    }
    if (book == null) {
      UIUtils.showError('error.no_book_to_refresh');
      return;
    }
    DialogUtils.showConfirm(
      title: 'dialog.refresh_book'.t,
      message: '${'dialog.refresh_book_message'.t}《${book.title}》${'dialog.refresh_book_warning'.t}',
      confirmText: 'button.refresh'.t,
      cancelText: 'button.cancel'.t,
      onConfirm: () {
        _doRefreshBook(context, ref, book!);
      },
    );
  }

  /// 执行刷新逻辑并展示更友好的进度提示
  Future<void> _doRefreshBook(BuildContext context, WidgetRef ref, BookModel book) async {
    DialogUtils.showLoading(tips: '${'dialog.refreshing_book'.t}《${book.title}》...');
    try {
      await ref.read(booksControllerProvider.notifier).refreshBook(book.id);
      DialogUtils.hideLoading();
      UIUtils.showSuccess('《${book.title}》${'success.book_refreshed'.t}');
    } catch (e) {
      DialogUtils.hideLoading();
      UIUtils.showError('error.refresh_failed');
    }
  }
}
