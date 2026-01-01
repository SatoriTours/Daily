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
class BooksView extends ConsumerWidget {
  const BooksView({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return Scaffold(
      appBar: _BooksAppBar(),
      body: const _BooksBody(),
      floatingActionButton: _buildFab(context, ref),
      floatingActionButtonLocation: FloatingActionButtonLocation.endFloat,
    );
  }

  Widget _buildFab(BuildContext context, WidgetRef ref) {
    return FloatingActionButton.small(
      heroTag: 'books_quick_journal',
      tooltip: 'tooltip.add_insight'.t,
      onPressed: () => _openJournalForCurrent(context, ref),
      backgroundColor: Theme.of(context).colorScheme.primary,
      foregroundColor: Theme.of(context).colorScheme.onPrimary,
      child: const Icon(Icons.edit_note, size: Dimensions.iconSizeM),
    );
  }

  void _openJournalForCurrent(BuildContext context, WidgetRef ref) {
    final booksState = ref.read(booksStateProvider);
    final booksNotifier = ref.read(booksStateProvider.notifier);
    String preset = '';
    int cursorPosition = 0;

    if (booksState.viewpoints.isNotEmpty) {
      final idx = booksState.currentViewpointIndex.clamp(0, booksState.viewpoints.length - 1);
      final vp = booksState.viewpoints[idx];
      final book = booksNotifier.findBookById(vp.bookId);
      final cleanTitle = vp.title.trim().replaceAll(RegExp(r'[。.！!？?，,；;：:]+$'), '');
      final bookTitle = (book?.title ?? '未知书籍').trim();
      final author = (book?.author ?? '').trim();

      final buffer = StringBuffer()
        ..writeln('# $bookTitle - $cleanTitle感悟')
        ..writeln();
      cursorPosition = buffer.length;
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
      builder: (_) => DiaryEditor(initialContent: preset, initialCursorPosition: cursorPosition),
    );
  }
}

/// 应用栏
class _BooksAppBar extends ConsumerWidget implements PreferredSizeWidget {
  @override
  Size get preferredSize => const Size.fromHeight(kToolbarHeight);

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return SAppBar(
      title: Text('title.books_wisdom'.t, style: AppTypography.titleLarge.copyWith(color: Colors.white)),
      leading: IconButton(
        icon: const Icon(Icons.menu_book),
        onPressed: () => _showBooksFilterDialog(context, ref),
        tooltip: 'title.select_book'.t,
      ),
      actions: [
        IconButton(
          icon: const Icon(Icons.add, size: Dimensions.iconSizeM),
          onPressed: () => Navigator.of(context).pushNamed(Routes.bookSearch),
          tooltip: 'tooltip.add_book'.t,
        ),
        PopupMenuButton<String>(
          icon: const Icon(Icons.more_horiz, size: Dimensions.iconSizeM),
          onSelected: (value) => _handleMenuSelection(value, context, ref),
          itemBuilder: (_) => [
            SPopupMenuItem<String>(value: 'shuffle', icon: Icons.shuffle, text: 'menu.shuffle'.t),
            SPopupMenuItem<String>(value: 'refresh', icon: Icons.refresh, text: 'menu.refresh_book'.t),
            SPopupMenuItem<String>(value: 'delete', icon: Icons.delete_outline, text: 'menu.delete_book'.t),
          ],
        ),
      ],
      elevation: 1,
      centerTitle: true,
      backgroundColorLight: AppColors.primary,
      backgroundColorDark: AppColors.backgroundDark,
      foregroundColor: Colors.white,
    );
  }

  void _handleMenuSelection(String value, BuildContext context, WidgetRef ref) {
    switch (value) {
      case 'shuffle':
        ref.read(booksControllerProvider.notifier).refreshRecommendations();
      case 'refresh':
        _confirmAndRefreshBook(context, ref);
      case 'delete':
        _showDeleteBookDialog(context, ref);
    }
  }

  void _showBooksFilterDialog(BuildContext context, WidgetRef ref) {
    showModalBottomSheet(
      context: context,
      backgroundColor: Theme.of(context).scaffoldBackgroundColor,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(base_dim.Dimensions.radiusL)),
      ),
      builder: (_) => _BooksFilterDialog(),
    );
  }

  void _showDeleteBookDialog(BuildContext context, WidgetRef ref) {
    final book = _getCurrentBook(ref);
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

  void _confirmAndRefreshBook(BuildContext context, WidgetRef ref) {
    final book = _getCurrentBook(ref) ?? ref.read(booksStateProvider).allBooks.firstOrNull;
    if (book == null) {
      UIUtils.showError('error.no_book_to_refresh');
      return;
    }
    DialogUtils.showConfirm(
      title: 'dialog.refresh_book'.t,
      message: '${'dialog.refresh_book_message'.t}《${book.title}》${'dialog.refresh_book_warning'.t}',
      confirmText: 'button.refresh'.t,
      cancelText: 'button.cancel'.t,
      onConfirm: () async {
        DialogUtils.showLoading(tips: '${'dialog.refreshing_book'.t}《${book.title}》...');
        try {
          await ref.read(booksControllerProvider.notifier).refreshBook(book.id);
          DialogUtils.hideLoading();
          UIUtils.showSuccess('《${book.title}》${'success.book_refreshed'.t}');
        } catch (_) {
          DialogUtils.hideLoading();
          UIUtils.showError('error.refresh_failed');
        }
      },
    );
  }

  BookModel? _getCurrentBook(WidgetRef ref) {
    final booksState = ref.read(booksStateProvider);
    final booksNotifier = ref.read(booksStateProvider.notifier);
    final currentViewpoint = booksNotifier.getCurrentViewpoint();
    if (currentViewpoint != null) {
      return booksNotifier.findBookById(currentViewpoint.bookId);
    }
    if (booksState.filterBookID != -1) {
      return booksState.allBooks.where((b) => b.id == booksState.filterBookID).firstOrNull;
    }
    return null;
  }
}

/// 书籍过滤对话框
class _BooksFilterDialog extends ConsumerWidget {
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final books = ref.watch(booksStateProvider.select((s) => s.allBooks));
    final filterBookID = ref.watch(booksStateProvider.select((s) => s.filterBookID));

    return Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        // 标题
        Padding(
          padding: Dimensions.paddingM,
          child: Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text('title.select_book'.t, style: AppTypography.titleMedium),
              IconButton(
                icon: const Icon(Icons.close, size: Dimensions.iconSizeM),
                onPressed: () => Navigator.pop(context),
              ),
            ],
          ),
        ),
        const Divider(height: 1),
        // 列表
        Expanded(
          child: ListView.builder(
            padding: Dimensions.paddingVerticalS,
            itemCount: books.length + 1,
            itemBuilder: (_, index) {
              if (index == 0) {
                return _buildItem(context, ref, null, filterBookID == -1);
              }
              final book = books[index - 1];
              return _buildItem(context, ref, book, filterBookID == book.id);
            },
          ),
        ),
        const Divider(height: 1),
        // 底部
        Padding(
          padding: Dimensions.paddingM,
          child: Center(
            child: TextButton.icon(
              onPressed: () {
                ref.read(booksControllerProvider.notifier).selectBook(-1);
                Navigator.pop(context);
              },
              icon: Icon(Icons.clear, size: Dimensions.iconSizeXs, color: AppColors.getPrimary(context)),
              label: Text(
                'book.view_all_viewpoints'.t,
                style: AppTypography.bodyMedium.copyWith(color: AppColors.getPrimary(context)),
              ),
            ),
          ),
        ),
      ],
    );
  }

  Widget _buildItem(BuildContext context, WidgetRef ref, BookModel? book, bool isSelected) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final bgColor = isSelected
        ? AppColors.getPrimary(context).withValues(alpha: isDark ? Opacities.mediumHigh : Opacities.low)
        : Colors.transparent;
    final textColor = isSelected
        ? (isDark ? Colors.white : AppColors.getPrimary(context))
        : Theme.of(context).textTheme.bodyLarge?.color ?? Colors.black;

    return InkWell(
      onTap: () {
        ref.read(booksControllerProvider.notifier).selectBook(book?.id ?? -1);
        Navigator.pop(context);
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
class _BooksBody extends ConsumerWidget {
  const _BooksBody();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final viewpoints = ref.watch(booksStateProvider.select((s) => s.viewpoints));
    if (viewpoints.isEmpty) {
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
    return _buildViewpointList(ref, viewpoints);
  }

  Widget _buildViewpointList(WidgetRef ref, List<BookViewpointModel> viewpoints) {
    final controllerState = ref.watch(booksControllerProvider);
    final booksNotifier = ref.read(booksStateProvider.notifier);
    return PageView.builder(
      controller: controllerState.pageController,
      onPageChanged: (index) => ref.read(booksControllerProvider.notifier).goToViewpointIndex(index),
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
