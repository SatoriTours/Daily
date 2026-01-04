/// Books Controller Provider
///
/// 读书页面控制器，管理读书页面的UI状态和用户交互。

library;

import 'package:freezed_annotation/freezed_annotation.dart';

import 'package:daily_satori/app_exports.dart';
import 'package:riverpod_annotation/riverpod_annotation.dart';

part 'books_controller_provider.freezed.dart';
part 'books_controller_provider.g.dart';

/// BooksController 状态
@freezed
abstract class BooksControllerState with _$BooksControllerState {
  const factory BooksControllerState({
    /// 最后刷新时间
    DateTime? lastRefreshTime,

    /// PageController (不在freezed中管理)
    PageController? pageController,

    /// TextEditingController for content (不在freezed中管理)
    TextEditingController? contentController,
  }) = _BooksControllerState;
}

/// BooksController Provider
@riverpod
class BooksController extends _$BooksController {
  static const _refreshInterval = Duration(hours: 6);

  @override
  BooksControllerState build() {
    Future.microtask(() => loadAllViewpoints());
    return BooksControllerState(
      lastRefreshTime: DateTime.now(),
      pageController: PageController(),
      contentController: TextEditingController(),
    );
  }

  /// 加载所有观点
  Future<void> loadAllViewpoints() => ref.read(booksStateProvider.notifier).loadAllViewpoints();

  /// 上一个观点
  void previousViewpoint() {
    final current = ref.read(booksStateProvider).currentViewpointIndex;
    if (current > 0) ref.read(booksStateProvider.notifier).setCurrentViewpointIndex(current - 1);
  }

  /// 下一个观点
  void nextViewpoint() {
    final state = ref.read(booksStateProvider);
    if (state.currentViewpointIndex < state.viewpoints.length - 1) {
      ref.read(booksStateProvider.notifier).setCurrentViewpointIndex(state.currentViewpointIndex + 1);
    }
  }

  /// 选择书籍
  Future<void> selectBook(int bookID) async {
    final notifier = ref.read(booksStateProvider.notifier);
    notifier.setFilterBookID(bookID);
    if (bookID != -1) {
      final book = BookRepository.i.findModel(bookID);
      if (book != null) notifier.selectBook(book);
    }
    await loadAllViewpoints();
  }

  /// 刷新推荐列表
  void refreshRecommendations() {
    ref.read(booksStateProvider.notifier).loadAllViewpoints();
    state = state.copyWith(lastRefreshTime: DateTime.now());
  }

  /// 检查并在需要时刷新推荐列表
  void checkAndRefreshIfNeeded() {
    if (ref.read(booksStateProvider).filterBookID != -1) return;
    final lastRefresh = state.lastRefreshTime;
    if (lastRefresh == null) {
      state = state.copyWith(lastRefreshTime: DateTime.now());
      return;
    }
    if (DateTime.now().difference(lastRefresh) >= _refreshInterval) {
      refreshRecommendations();
    }
  }

  /// 显示添加书籍对话框，返回用户输入的书名
  /// View 层负责处理导航
  Future<String?> showAddBookDialog(BuildContext context) async {
    final titleController = TextEditingController();
    String? searchKeyword;

    await showDialog<void>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: Text('title.add_book'.t),
        content: TextField(
          controller: titleController,
          decoration: InputDecoration(labelText: 'label.book_title'.t, hintText: 'hint.enter_book_name'.t),
          autofocus: false,
          onSubmitted: (value) {
            if (value.trim().isNotEmpty) {
              searchKeyword = value.trim();
              AppNavigation.back();
            }
          },
        ),
        actionsAlignment: MainAxisAlignment.end,
        actions: [
          TextButton(onPressed: () => AppNavigation.back(), child: Text('button.cancel'.t)),
          TextButton(
            onPressed: () {
              if (titleController.text.trim().isNotEmpty) {
                searchKeyword = titleController.text.trim();
                AppNavigation.back();
              }
            },
            child: Text('button.confirm'.t),
          ),
        ],
      ),
    );

    titleController.dispose();
    return searchKeyword;
  }

  /// 跳转到指定观点索引
  void goToViewpointIndex(int index) => ref.read(booksStateProvider.notifier).setCurrentViewpointIndex(index);

  /// 删除书籍
  Future<void> deleteBook(int bookId) => ref.read(booksStateProvider.notifier).deleteBook(bookId);

  /// 刷新书籍数据
  Future<void> refreshBook(int bookId) => ref.read(booksStateProvider.notifier).refreshBook(bookId);

  /// 打开指定ID的观点
  void openViewpointById(int viewpointId) {
    final viewpoints = ref.read(booksStateProvider).viewpoints;
    final index = viewpoints.indexWhere((v) => v.id == viewpointId);
    if (index != -1) {
      goToViewpointIndex(index);
      if (state.pageController?.hasClients == true) {
        state.pageController!.jumpToPage(index);
      }
    }
  }
}