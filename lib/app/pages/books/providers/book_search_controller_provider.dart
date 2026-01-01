/// Book Search Controller Provider
///
/// 书籍搜索控制器，管理书籍搜索功能。

library;

import 'package:flutter/material.dart';
import 'package:freezed_annotation/freezed_annotation.dart';
import 'package:riverpod_annotation/riverpod_annotation.dart';

import 'package:daily_satori/app/data/index.dart';
import 'package:daily_satori/app/providers/providers.dart';
import 'package:daily_satori/app/services/index.dart';
import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/utils/utils.dart';
import 'package:daily_satori/app/navigation/app_navigation.dart';
import 'package:daily_satori/app/routes/app_routes.dart';

part 'book_search_controller_provider.freezed.dart';
part 'book_search_controller_provider.g.dart';

/// BookSearchController 状态
@freezed
abstract class BookSearchControllerState with _$BookSearchControllerState {
  const factory BookSearchControllerState({
    @Default(false) bool isLoading,
    @Default(false) bool isSearching,
    @Default([]) List<BookModel> searchResults,
    @Default('') String errorMessage,
    @Default('') String searchKeyword,
  }) = _BookSearchControllerState;
}

/// BookSearchController Provider
@riverpod
class BookSearchController extends _$BookSearchController {
  final TextEditingController searchController = TextEditingController();

  @override
  BookSearchControllerState build() {
    ref.onDispose(() {
      searchController.dispose();
    });
    return const BookSearchControllerState();
  }

  /// 搜索书籍
  Future<void> searchBooks(String keyword) async {
    if (keyword.trim().isEmpty) {
      state = state.copyWith(searchResults: [], searchKeyword: '');
      return;
    }

    state = state.copyWith(isSearching: true, errorMessage: '', searchKeyword: keyword);
    try {
      // 使用 BookRepository 的 findByTitle 方法
      final results = BookRepository.i.findByTitle(keyword);
      final models = results.map((e) => BookRepository.i.toModel(e)).toList();
      state = state.copyWith(isSearching: false, searchResults: models);
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

  /// 选择书籍并跳转到详情页
  void selectBook(BookModel book) {
    ref.read(booksStateProvider.notifier).selectBook(book);
    AppNavigation.toNamed(Routes.books);
  }
}
