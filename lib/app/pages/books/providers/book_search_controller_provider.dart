/// Book Search Controller Provider
///
/// 书籍搜索控制器，管理书籍搜索功能。

library;

import 'package:freezed_annotation/freezed_annotation.dart';
import 'package:riverpod_annotation/riverpod_annotation.dart';

import 'package:daily_satori/app/data/index.dart';
import 'package:daily_satori/app/providers/providers.dart';
import 'package:daily_satori/app/services/index.dart';
import 'package:daily_satori/app/utils/utils.dart';
import 'package:daily_satori/app/navigation/app_navigation.dart';

part 'book_search_controller_provider.freezed.dart';
part 'book_search_controller_provider.g.dart';

/// BookSearchController 状态
@freezed
abstract class BookSearchControllerState with _$BookSearchControllerState {
  const factory BookSearchControllerState({
    @Default(false) bool isLoading,
    @Default(false) bool isSearching,
    @Default([]) List<BookSearchResult> searchResults,
    @Default('') String errorMessage,
    @Default('') String searchKeyword,
    @Default('') String initialKeyword,
  }) = _BookSearchControllerState;
}

/// BookSearchController Provider
@riverpod
class BookSearchController extends _$BookSearchController {
  @override
  BookSearchControllerState build() {
    return const BookSearchControllerState();
  }

  /// 设置初始搜索关键词（在导航前调用）
  void setInitialKeyword(String keyword) {
    state = state.copyWith(initialKeyword: keyword, searchKeyword: keyword);
  }

  /// 清除初始关键词（页面加载后调用）
  String consumeInitialKeyword() {
    final keyword = state.initialKeyword;
    if (keyword.isNotEmpty) {
      state = state.copyWith(initialKeyword: '');
    }
    return keyword;
  }

  /// 搜索书籍（在线搜索）
  Future<void> searchBooks(String keyword) async {
    if (keyword.trim().isEmpty) {
      state = state.copyWith(searchResults: [], searchKeyword: '');
      return;
    }

    state = state.copyWith(isSearching: true, errorMessage: '', searchKeyword: keyword);
    try {
      // 使用 BookService 进行在线搜索
      final results = await BookService.i.searchBooks(keyword);
      state = state.copyWith(isSearching: false, searchResults: results);
    } catch (e) {
      logger.e('[BookSearchController] 搜索失败', error: e);
      state = state.copyWith(isSearching: false, errorMessage: e.toString());
      UIUtils.showError('book_search.search_failed'.t);
    }
  }

  /// 清空搜索结果
  void clearSearch() {
    state = state.copyWith(searchResults: [], searchKeyword: '', errorMessage: '');
  }

  /// 选择书籍并添加到本地，然后返回读书页
  Future<void> selectBook(BookSearchResult searchResult) async {
    state = state.copyWith(isLoading: true);
    try {
      // 使用 BookService 添加书籍
      final book = await BookService.i.addBookFromSearch(searchResult);
      if (book != null) {
        // 刷新书籍列表
        await ref.read(booksStateProvider.notifier).loadAllViewpoints();
        // 选择这本书
        ref.read(booksStateProvider.notifier).selectBook(book);
        UIUtils.showSuccess('book_search.add_success'.t);
      } else {
        UIUtils.showSuccess('book_search.already_exists'.t);
      }
      // 返回上一页（读书 tab）
      AppNavigation.back();
    } catch (e) {
      logger.e('[BookSearchController] 添加书籍失败', error: e);
      UIUtils.showError('book_search.add_failed'.t);
    } finally {
      state = state.copyWith(isLoading: false);
    }
  }
}
