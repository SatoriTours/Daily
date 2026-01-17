import 'package:daily_satori/app_exports.dart';
import 'package:daily_satori/app/pages/books/providers/books_controller_provider.dart';
import 'package:daily_satori/app/pages/books/views/widgets/viewpoint_card.dart';
import 'package:daily_satori/app/pages/diary/views/widgets/diary_editor.dart';

/// 读书页面
class BooksView extends ConsumerStatefulWidget {
  const BooksView({super.key});

  @override
  ConsumerState<BooksView> createState() => _BooksViewState();
}

class _BooksViewState extends ConsumerState<BooksView> {
  late final PageController _pageController;

  @override
  void initState() {
    super.initState();
    // 初始化 PageController，使用当前状态中的索引
    final initialIndex = ref.read(booksStateProvider).currentViewpointIndex;
    _pageController = PageController(initialPage: initialIndex);
  }

  @override
  void dispose() {
    _pageController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: _BooksAppBar(onOpenJournal: _openJournalForCurrent),
      body: _BooksBody(pageController: _pageController),
      floatingActionButton: _BooksFab(onPressed: _openJournalForCurrent),
      floatingActionButtonLocation: FloatingActionButtonLocation.endFloat,
    );
  }

  void _openJournalForCurrent() {
    final state = ref.read(booksStateProvider);
    final notifier = ref.read(booksStateProvider.notifier);
    final (content, cursor) = _buildJournalContent(state, notifier);

    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: DiaryStyles.getBottomSheetColor(context),
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(Dimensions.radiusL)),
      ),
      builder: (_) => DiaryEditor(initialContent: content, initialCursorPosition: cursor),
    );
  }

  (String, int) _buildJournalContent(BooksStateModel state, BooksState notifier) {
    if (state.viewpoints.isEmpty) {
      const preset = '# 读书感悟\n\n';
      return (preset, preset.length);
    }

    final idx = state.currentViewpointIndex.clamp(0, state.viewpoints.length - 1);
    final vp = state.viewpoints[idx];
    final book = notifier.findBookById(vp.bookId);

    final cleanTitle = vp.title.trim().replaceAll(RegExp(r'[。.！!？?，,；;：:]+$'), '');
    final bookTitle = (book?.title ?? '未知书籍').trim();
    final author = (book?.author ?? '').trim();

    final buffer = StringBuffer()
      ..writeln('# $bookTitle - $cleanTitle感悟')
      ..writeln();

    final cursorPos = buffer.length;

    buffer
      ..writeln()
      ..writeln()
      ..writeln('---')
      ..writeln()
      ..writeln('**观点**：${vp.title.trim()}')
      ..writeln();

    if (vp.content.trim().isNotEmpty) {
      buffer
        ..writeln('**内容**：')
        ..writeln()
        ..writeln('> ${vp.content.trim()}')
        ..writeln();
    }

    buffer.write('**出处**：《$bookTitle》');
    if (author.isNotEmpty) buffer.write(' · $author');
    buffer
      ..writeln()
      ..writeln()
      ..writeln('[查看原始观点](app://books/viewpoint/${vp.id})');

    return (buffer.toString(), cursorPos);
  }
}

/// 悬浮按钮
class _BooksFab extends StatelessWidget {
  final VoidCallback onPressed;

  const _BooksFab({required this.onPressed});

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    return FloatingActionButton.small(
      heroTag: 'books_quick_journal',
      tooltip: 'tooltip.add_insight'.t,
      onPressed: onPressed,
      backgroundColor: colorScheme.primary,
      foregroundColor: colorScheme.onPrimary,
      child: const Icon(Icons.edit_note, size: Dimensions.iconSizeM),
    );
  }
}

/// 应用栏
class _BooksAppBar extends ConsumerWidget implements PreferredSizeWidget {
  final VoidCallback onOpenJournal;

  const _BooksAppBar({required this.onOpenJournal});

  @override
  Size get preferredSize => const Size.fromHeight(kToolbarHeight);

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return SAppBar(
      title: Text('title.books_wisdom'.t, style: AppTypography.titleLarge.copyWith(color: Colors.white)),
      leading: IconButton(
        icon: const Icon(Icons.menu_book),
        onPressed: () => _showFilterDialog(context),
        tooltip: 'title.select_book'.t,
      ),
      actions: [
        IconButton(
          icon: const Icon(Icons.add, size: Dimensions.iconSizeM),
          onPressed: () => AppNavigation.toNamed(Routes.bookSearch),
          tooltip: 'tooltip.add_book'.t,
        ),
        _buildPopupMenu(context, ref),
      ],
      elevation: 1,
      centerTitle: true,
      backgroundColorLight: AppColors.primary,
      backgroundColorDark: AppColors.backgroundDark,
      foregroundColor: Colors.white,
    );
  }

  Widget _buildPopupMenu(BuildContext context, WidgetRef ref) {
    return PopupMenuButton<String>(
      icon: const Icon(Icons.more_horiz, size: Dimensions.iconSizeM),
      onSelected: (value) => _handleMenuAction(value, ref),
      itemBuilder: (_) => [
        SPopupMenuItem(value: 'shuffle', icon: Icons.shuffle, text: 'menu.shuffle'.t),
        SPopupMenuItem(value: 'refresh', icon: Icons.refresh, text: 'menu.refresh_book'.t),
        SPopupMenuItem(value: 'delete', icon: Icons.delete_outline, text: 'menu.delete_book'.t),
      ],
    );
  }

  void _handleMenuAction(String action, WidgetRef ref) {
    final controller = ref.read(booksControllerProvider.notifier);
    final state = ref.read(booksStateProvider);
    final notifier = ref.read(booksStateProvider.notifier);

    switch (action) {
      case 'shuffle':
        controller.refreshRecommendations();
      case 'refresh':
        _confirmRefreshBook(ref, state, notifier);
      case 'delete':
        _confirmDeleteBook(ref, state, notifier);
    }
  }

  void _showFilterDialog(BuildContext context) {
    showModalBottomSheet(
      context: context,
      backgroundColor: Theme.of(context).scaffoldBackgroundColor,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(Dimensions.radiusL)),
      ),
      builder: (_) => const _BooksFilterSheet(),
    );
  }

  void _confirmDeleteBook(WidgetRef ref, BooksStateModel state, BooksState notifier) {
    final book = _getCurrentBook(state, notifier);
    if (book == null) {
      UIUtils.showError('error.no_book_to_delete');
      return;
    }

    DialogUtils.showConfirm(
      title: 'dialog.delete_book'.t,
      message: '${'dialog.delete_book_confirm'.t}《${book.title}》？\n${'dialog.delete_book_warning'.t}',
      confirmText: 'button.delete'.t,
      cancelText: 'button.cancel'.t,
      onConfirm: () => ref.read(booksControllerProvider.notifier).deleteBook(book.id),
    );
  }

  void _confirmRefreshBook(WidgetRef ref, BooksStateModel state, BooksState notifier) {
    final book = _getCurrentBook(state, notifier) ?? state.allBooks.firstOrNull;
    if (book == null) {
      UIUtils.showError('error.no_book_to_refresh');
      return;
    }

    DialogUtils.showConfirm(
      title: 'dialog.refresh_book'.t,
      message: '${'dialog.refresh_book_message'.t}《${book.title}》${'dialog.refresh_book_warning'.t}',
      confirmText: 'button.refresh'.t,
      cancelText: 'button.cancel'.t,
      onConfirm: () => _executeRefresh(ref, book),
    );
  }

  Future<void> _executeRefresh(WidgetRef ref, BookModel book) async {
    DialogUtils.showLoading(tips: '${'dialog.refreshing_book'.t}《${book.title}》...');
    try {
      await ref.read(booksControllerProvider.notifier).refreshBook(book.id);
      DialogUtils.hideLoading();
      UIUtils.showSuccess('《${book.title}》${'success.book_refreshed'.t}');
    } catch (_) {
      DialogUtils.hideLoading();
      UIUtils.showError('error.refresh_failed');
    }
  }

  BookModel? _getCurrentBook(BooksStateModel state, BooksState notifier) {
    final currentViewpoint = notifier.getCurrentViewpoint();
    if (currentViewpoint != null) {
      return notifier.findBookById(currentViewpoint.bookId);
    }
    if (state.filterBookID != -1) {
      return state.allBooks.where((b) => b.id == state.filterBookID).firstOrNull;
    }
    return null;
  }
}

/// 书籍过滤底部表单
class _BooksFilterSheet extends ConsumerWidget {
  const _BooksFilterSheet();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final books = ref.watch(booksStateProvider.select((s) => s.allBooks));
    final filterBookID = ref.watch(booksStateProvider.select((s) => s.filterBookID));

    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        _buildHeader(context),
        const Divider(height: 1),
        Expanded(
          child: ListView.builder(
            padding: Dimensions.paddingVerticalS,
            itemCount: books.length + 1,
            itemBuilder: (_, index) {
              if (index == 0) {
                return _BookFilterItem(book: null, isSelected: filterBookID == -1);
              }
              final book = books[index - 1];
              return _BookFilterItem(book: book, isSelected: filterBookID == book.id);
            },
          ),
        ),
        const Divider(height: 1),
        _buildClearButton(context, ref),
      ],
    );
  }

  Widget _buildHeader(BuildContext context) {
    return Padding(
      padding: Dimensions.paddingM,
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text('title.select_book'.t, style: AppTypography.titleMedium),
          const IconButton(
            icon: Icon(Icons.close, size: Dimensions.iconSizeM),
            onPressed: AppNavigation.back,
          ),
        ],
      ),
    );
  }

  Widget _buildClearButton(BuildContext context, WidgetRef ref) {
    return Padding(
      padding: Dimensions.paddingM,
      child: Center(
        child: TextButton.icon(
          onPressed: () {
            ref.read(booksControllerProvider.notifier).selectBook(-1);
            AppNavigation.back();
          },
          icon: Icon(Icons.clear, size: Dimensions.iconSizeXs, color: AppColors.getPrimary(context)),
          label: Text(
            'book.view_all_viewpoints'.t,
            style: AppTypography.bodyMedium.copyWith(color: AppColors.getPrimary(context)),
          ),
        ),
      ),
    );
  }
}

/// 书籍过滤项
class _BookFilterItem extends ConsumerWidget {
  final BookModel? book;
  final bool isSelected;

  const _BookFilterItem({required this.book, required this.isSelected});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final primary = AppColors.getPrimary(context);

    final bgColor = isSelected
        ? primary.withValues(alpha: isDark ? Opacities.mediumHigh : Opacities.low)
        : Colors.transparent;
    final textColor = isSelected
        ? (isDark ? Colors.white : primary)
        : Theme.of(context).textTheme.bodyLarge?.color ?? Colors.black;

    return InkWell(
      onTap: () {
        ref.read(booksControllerProvider.notifier).selectBook(book?.id ?? -1);
        AppNavigation.back();
      },
      child: Container(
        padding: const EdgeInsets.symmetric(vertical: Dimensions.spacingM - 4, horizontal: Dimensions.spacingM),
        color: bgColor,
        child: Row(
          children: [
            Icon(
              book == null ? Icons.all_inclusive : Icons.book_outlined,
              size: Dimensions.iconSizeM,
              color: textColor,
            ),
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
        ),
      ),
    );
  }
}

/// 页面主体
class _BooksBody extends ConsumerStatefulWidget {
  final PageController pageController;

  const _BooksBody({required this.pageController});

  @override
  ConsumerState<_BooksBody> createState() => _BooksBodyState();
}

class _BooksBodyState extends ConsumerState<_BooksBody> {
  @override
  Widget build(BuildContext context) {
    final viewpoints = ref.watch(booksStateProvider.select((s) => s.viewpoints));
    final currentIndex = ref.watch(booksStateProvider.select((s) => s.currentViewpointIndex));

    // 同步 PageController 位置与状态
    _syncPageController(currentIndex, viewpoints.length);

    if (viewpoints.isEmpty) {
      return _buildEmptyState();
    }

    return _ViewpointPageView(pageController: widget.pageController, viewpoints: viewpoints);
  }

  void _syncPageController(int targetIndex, int viewpointsLength) {
    if (viewpointsLength == 0) return;

    final controller = widget.pageController;
    if (!controller.hasClients) return;

    final currentPage = controller.page?.round() ?? 0;
    final safeIndex = targetIndex.clamp(0, viewpointsLength - 1);

    if (currentPage != safeIndex) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (controller.hasClients) {
          controller.jumpToPage(safeIndex);
        }
      });
    }
  }

  Widget _buildEmptyState() {
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
}

/// 观点翻页视图
class _ViewpointPageView extends ConsumerWidget {
  final PageController pageController;
  final List<BookViewpointModel> viewpoints;

  const _ViewpointPageView({required this.pageController, required this.viewpoints});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final booksNotifier = ref.read(booksStateProvider.notifier);

    return PageView.builder(
      controller: pageController,
      onPageChanged: (index) {
        // 直接调用 booksStateProvider，避免触发 booksControllerProvider 的 build
        ref.read(booksStateProvider.notifier).setCurrentViewpointIndex(index);
      },
      itemCount: viewpoints.length,
      itemBuilder: (context, index) {
        final viewpoint = viewpoints[index];
        return SingleChildScrollView(
          padding: const EdgeInsets.fromLTRB(
            Dimensions.spacingM,
            Dimensions.spacingS,
            Dimensions.spacingM,
            Dimensions.spacingM,
          ),
          child: ViewpointCard(viewpoint: viewpoint, book: booksNotifier.findBookById(viewpoint.bookId)),
        );
      },
    );
  }
}
