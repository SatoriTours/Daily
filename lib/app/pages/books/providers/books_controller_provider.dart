/// Books Controller Provider
///
/// 读书页面控制器，管理读书页面的UI状态和用户交互。

library;

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:freezed_annotation/freezed_annotation.dart';
import 'package:riverpod_annotation/riverpod_annotation.dart';

import 'package:daily_satori/app/data/index.dart';
import 'package:daily_satori/app/providers/providers.dart';
import 'package:daily_satori/app/services/index.dart';
import 'package:daily_satori/app/utils/i18n_extension.dart';

part 'books_controller_provider.freezed.dart';
part 'books_controller_provider.g.dart';

/// BooksController 状态
@freezed
abstract class BooksControllerState with _$BooksControllerState {
  const BooksControllerState._();

  const factory BooksControllerState({
    /// 最后刷新时间
    DateTime? lastRefreshTime,

    /// PageController (不在freezed中管理)
    // ignore: invalid_annotation_target
    @JsonKey(includeToJson: false, includeFromJson: false) PageController? pageController,

    /// TextEditingController for content (不在freezed中管理)
    // ignore: invalid_annotation_target
    @JsonKey(includeToJson: false, includeFromJson: false) TextEditingController? contentController,
  }) = _BooksControllerState;

  factory BooksControllerState.fromJson(Map<String, dynamic> json) => _$BooksControllerStateFromJson(json);
}

/// BooksControllerState 扩展
///
/// 添加基于其他provider的getter和计算方法
extension BooksControllerStateX on BooksControllerState {
  /// 获取过滤书籍ID (需要通过ref访问)
  int get filterBookID => -1; // 默认值，实际应从 booksStateProvider 获取

  /// 获取所有观点 (需要通过ref访问)
  List<BookViewpointModel> get allViewpoints => []; // 默认值，实际应从 booksStateProvider 获取

  /// 获取当前观点索引 (需要通过ref访问)
  int get currentViewpointIndex => 0; // 默认值，实际应从 booksStateProvider 获取

  /// 获取所有书籍 (需要通过ref访问)
  List<BookModel> getAllBooks(WidgetRef ref) {
    return ref.read(booksStateProvider).allBooks;
  }

  /// 获取当前观点 (需要通过ref访问)
  BookViewpointModel? currentViewpoint(WidgetRef ref) {
    final index = currentViewpointIndex;
    final viewpoints = allViewpoints;
    if (index >= 0 && index < viewpoints.length) {
      return viewpoints[index];
    }
    return null;
  }
}

/// BooksController Provider
@riverpod
class BooksController extends _$BooksController {
  // 刷新间隔（6小时）
  static const _refreshInterval = Duration(hours: 6);

  @override
  BooksControllerState build() {
    Future.microtask(() => _loadAllViewpoints());

    // 创建UI控制器
    final pageController = PageController();
    final contentController = TextEditingController();

    return BooksControllerState(
      lastRefreshTime: DateTime.now(),
      pageController: pageController,
      contentController: contentController,
    );
  }

  /// 获取所有观点
  List<BookViewpointModel> getViewpoints() {
    return ref.read(booksStateProvider).viewpoints;
  }

  /// 获取当前观点索引
  int getCurrentViewpointIndex() {
    return ref.read(booksStateProvider).currentViewpointIndex;
  }

  /// 上一个观点
  void previousViewpoint() {
    final currentState = ref.read(booksStateProvider);
    if (currentState.currentViewpointIndex > 0) {
      ref.read(booksStateProvider.notifier).setCurrentViewpointIndex(currentState.currentViewpointIndex - 1);
    }
  }

  /// 下一个观点
  void nextViewpoint() {
    final currentState = ref.read(booksStateProvider);
    if (currentState.currentViewpointIndex < currentState.viewpoints.length - 1) {
      ref.read(booksStateProvider.notifier).setCurrentViewpointIndex(currentState.currentViewpointIndex + 1);
    }
  }

  /// 获取过滤书籍ID
  int getFilterBookID() {
    return ref.read(booksStateProvider).filterBookID;
  }

  /// 是否正在处理
  bool isProcessing() {
    return ref.read(booksStateProvider).isProcessing;
  }

  /// 是否正在加载书籍
  bool isLoadingBooks() {
    return ref.read(booksStateProvider).isLoading;
  }

  /// 加载所有书籍
  List<BookModel> getAllBooks() {
    return ref.read(booksStateProvider).allBooks;
  }

  /// 加载所有观点
  Future<void> _loadAllViewpoints() async {
    final booksState = ref.read(booksStateProvider.notifier);
    await booksState.loadAllViewpoints();
  }

  /// 选择书籍
  Future<void> selectBook(int bookID) async {
    final booksState = ref.read(booksStateProvider.notifier);
    // 设置筛选书籍ID
    booksState.setFilterBookID(bookID);
    // 如果选择了具体的书籍，也设置 selectedBook
    if (bookID != -1) {
      final book = BookRepository.i.findModel(bookID);
      if (book != null) {
        booksState.selectBook(book);
      }
    }
    // 加载对应的观点
    await _loadAllViewpoints();
  }

  /// 获取当前观点
  BookViewpointModel? getCurrentViewpoint() {
    final index = getCurrentViewpointIndex();
    final viewpoints = getViewpoints();
    if (index >= 0 && index < viewpoints.length) {
      return viewpoints[index];
    }
    return null;
  }

  /// 刷新推荐列表
  void refreshRecommendations() {
    final booksState = ref.read(booksStateProvider.notifier);
    booksState.loadAllViewpoints();
    state = state.copyWith(lastRefreshTime: DateTime.now());
  }

  /// 检查并在需要时刷新推荐列表
  void checkAndRefreshIfNeeded() {
    // 只在"查看所有书籍"模式下自动刷新
    if (getFilterBookID() != -1) return;

    // 检查上次刷新时间
    final lastRefresh = state.lastRefreshTime;
    if (lastRefresh == null) {
      state = state.copyWith(lastRefreshTime: DateTime.now());
      return;
    }

    final now = DateTime.now();
    final timeSinceLastRefresh = now.difference(lastRefresh);

    // 如果距离上次刷新超过6小时，则自动刷新
    if (timeSinceLastRefresh >= _refreshInterval) {
      logger.i('距离上次刷新已${timeSinceLastRefresh.inHours}小时，自动刷新推荐列表');
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
              Navigator.pop(dialogContext);
            }
          },
        ),
        actionsAlignment: MainAxisAlignment.end,
        actions: [
          TextButton(onPressed: () => Navigator.pop(dialogContext), child: Text('button.cancel'.t)),
          TextButton(
            onPressed: () {
              if (titleController.text.trim().isNotEmpty) {
                searchKeyword = titleController.text.trim();
                Navigator.pop(dialogContext);
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

  /// 加载所有观点（公开方法）
  Future<void> loadAllViewpoints() async {
    await _loadAllViewpoints();
  }

  /// 跳转到指定观点索引
  void goToViewpointIndex(int index) {
    final booksState = ref.read(booksStateProvider.notifier);
    booksState.setCurrentViewpointIndex(index);
  }

  /// 删除书籍
  Future<void> deleteBook(int bookId) async {
    final booksState = ref.read(booksStateProvider.notifier);
    await booksState.deleteBook(bookId);
  }

  /// 刷新书籍数据
  Future<void> refreshBook(int bookId) async {
    final booksState = ref.read(booksStateProvider.notifier);
    await booksState.refreshBook(bookId);
  }

  /// 打开指定ID的观点
  Future<void> openViewpointById(int viewpointId) async {
    final viewpoints = getViewpoints();
    final index = viewpoints.indexWhere((v) => v.id == viewpointId);
    if (index != -1) {
      goToViewpointIndex(index);
      // 如果有 PageController，也需要跳转
      if (state.pageController != null && state.pageController!.hasClients) {
        state.pageController!.jumpToPage(index);
      }
    } else {
      logger.w('未找到ID为 $viewpointId 的观点');
    }
  }
}
