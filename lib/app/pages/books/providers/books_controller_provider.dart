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
  const factory BooksControllerState({DateTime? lastRefreshTime}) = _BooksControllerState;
}

/// BooksController Provider
@riverpod
class BooksController extends _$BooksController {
  static const _refreshInterval = Duration(hours: 6);

  @override
  BooksControllerState build() {
    // 不在这里加载观点，由 BooksState.build() 负责初始加载
    return BooksControllerState(lastRefreshTime: DateTime.now());
  }

  Future<void> loadAllViewpoints() => ref.read(booksStateProvider.notifier).loadAllViewpoints();

  void previousViewpoint() {
    final current = ref.read(booksStateProvider).currentViewpointIndex;
    if (current > 0) {
      ref.read(booksStateProvider.notifier).setCurrentViewpointIndex(current - 1);
    }
  }

  void nextViewpoint() {
    final state = ref.read(booksStateProvider);
    if (state.currentViewpointIndex < state.viewpoints.length - 1) {
      ref.read(booksStateProvider.notifier).setCurrentViewpointIndex(state.currentViewpointIndex + 1);
    }
  }

  Future<void> selectBook(int bookID) async {
    final notifier = ref.read(booksStateProvider.notifier);
    notifier.setFilterBookID(bookID);
    if (bookID != -1) {
      final book = BookRepository.i.findModel(bookID);
      if (book != null) notifier.selectBook(book);
    }
    await loadAllViewpoints();
  }

  void refreshRecommendations() {
    ref.read(booksStateProvider.notifier).loadAllViewpoints();
    state = state.copyWith(lastRefreshTime: DateTime.now());
  }

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

  void goToViewpointIndex(int index) => ref.read(booksStateProvider.notifier).setCurrentViewpointIndex(index);

  Future<void> deleteBook(int bookId) => ref.read(booksStateProvider.notifier).deleteBook(bookId);

  Future<void> refreshBook(int bookId) => ref.read(booksStateProvider.notifier).refreshBook(bookId);

  void openViewpointById(int viewpointId) {
    final viewpoints = ref.read(booksStateProvider).viewpoints;
    final index = viewpoints.indexWhere((v) => v.id == viewpointId);
    if (index != -1) {
      goToViewpointIndex(index);
    }
  }
}
