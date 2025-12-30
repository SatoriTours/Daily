/// 读书状态管理 Provider
///
/// Riverpod 版本的 BooksStateService，管理书籍和观点数据。
library;

import 'dart:math';

import 'package:freezed_annotation/freezed_annotation.dart';
import 'package:riverpod_annotation/riverpod_annotation.dart';

import 'package:daily_satori/app/data/index.dart';
import 'package:daily_satori/app/services/book_service.dart';
import 'package:daily_satori/app/services/logger_service.dart';

part 'books_state_provider.freezed.dart';
part 'books_state_provider.g.dart';

/// 展示模式
enum DisplayMode { allRandom, singleBook }

/// 读书状态模型
@freezed
abstract class BooksStateModel with _$BooksStateModel {
  const BooksStateModel._();

  const factory BooksStateModel({
    @Default([]) List<BookViewpointModel> viewpoints,
    @Default(false) bool isLoading,
    @Default(0) int currentViewpointIndex,
    @Default(-1) int filterBookID,
    @Default(false) bool isProcessing,
    @Default(DisplayMode.allRandom) DisplayMode mode,
    int? deepLinkSeedViewpointId,
    BookModel? selectedBook,
  }) = _BooksStateModel;

  /// 获取当前观点
  BookViewpointModel? get currentViewpoint {
    if (viewpoints.isEmpty) return null;
    final index = currentViewpointIndex.clamp(0, viewpoints.length - 1);
    return viewpoints[index];
  }

  /// 获取所有书籍
  List<BookModel> get allBooks {
    return BookService.i.getBooks();
  }
}

/// 读书状态 Provider
@riverpod
class BooksState extends _$BooksState {
  final Random _rand = Random();
  static const int _kRandomCount = 10;

  @override
  BooksStateModel build() {
    logger.i('BooksState Provider 初始化完成');
    return const BooksStateModel();
  }

  /// 获取所有书籍
  List<BookModel> get allBooks {
    return BookService.i.getBooks();
  }

  /// 加载所有观点
  Future<void> loadAllViewpoints() async {
    logger.i('加载观点: bookID: ${state.filterBookID}, mode: ${state.mode}');

    state = state.copyWith(isLoading: true);
    try {
      if (state.filterBookID == -1) {
        await _loadAllBooksViewpoints();
      } else {
        await _loadSpecificBookViewpoints();
      }
    } finally {
      state = state.copyWith(isLoading: false);
    }
  }

  /// 加载所有书籍的观点（随机模式）
  Future<void> _loadAllBooksViewpoints() async {
    final allViewpoints = BookViewpointRepository.i.all();
    if (allViewpoints.isEmpty) {
      state = state.copyWith(viewpoints: []);
      return;
    }

    // 随机选择 _kRandomCount 个观点
    final shuffled = List<BookViewpointModel>.from(allViewpoints)..shuffle(_rand);
    final selected = shuffled.take(_kRandomCount.clamp(0, allViewpoints.length)).toList();

    state = state.copyWith(viewpoints: selected, currentViewpointIndex: 0);
    logger.i('加载 ${selected.length} 个随机观点');
  }

  /// 加载特定书籍的观点
  Future<void> _loadSpecificBookViewpoints() async {
    final bookViewpoints = BookViewpointRepository.i.findModelsByBookIds([state.filterBookID]);

    // 深链模式：如果有指定的观点ID，将其放在第一位
    List<BookViewpointModel> orderedViewpoints;
    if (state.deepLinkSeedViewpointId != null) {
      final seedViewpoint = bookViewpoints.firstWhere(
        (vp) => vp.id == state.deepLinkSeedViewpointId,
        orElse: () => bookViewpoints.first,
      );

      final others = bookViewpoints.where((vp) => vp.id != state.deepLinkSeedViewpointId).toList();
      orderedViewpoints = [seedViewpoint, ...others];
    } else {
      orderedViewpoints = bookViewpoints;
    }

    state = state.copyWith(viewpoints: orderedViewpoints, currentViewpointIndex: 0, deepLinkSeedViewpointId: null);
    logger.i('加载 ${orderedViewpoints.length} 个观点');
  }

  /// 设置当前观点索引
  void setCurrentViewpointIndex(int index) {
    final clampedIndex = index.clamp(0, state.viewpoints.length - 1);
    state = state.copyWith(currentViewpointIndex: clampedIndex);
  }

  /// 设置筛选书籍ID
  void setFilterBookID(int bookID) {
    state = state.copyWith(filterBookID: bookID);
    logger.i('设置筛选书籍ID: $bookID');
  }

  /// 设置展示模式
  void setMode(DisplayMode mode) {
    state = state.copyWith(mode: mode);
    logger.i('设置展示模式: $mode');
  }

  /// 设置深链种子观点ID
  void setDeepLinkSeedViewpointId(int? viewpointId) {
    state = state.copyWith(deepLinkSeedViewpointId: viewpointId);
  }

  /// 设置处理状态
  void setProcessing(bool processing) {
    state = state.copyWith(isProcessing: processing);
  }

  /// 更新列表中的观点
  void updateViewpointInList(int id) {
    final viewpoint = BookViewpointRepository.i.findModel(id);
    if (viewpoint == null) return;

    final viewpoints = List<BookViewpointModel>.from(state.viewpoints);
    final index = viewpoints.indexWhere((vp) => vp.id == id);
    if (index != -1) {
      viewpoints[index] = viewpoint;
      state = state.copyWith(viewpoints: viewpoints);
      logger.d('更新列表中的观点: ID=$id');
    }
  }

  /// 从列表中移除观点
  void removeViewpointFromList(int id) {
    final updatedViewpoints = state.viewpoints.where((vp) => vp.id != id).toList();
    state = state.copyWith(viewpoints: updatedViewpoints);

    // 调整当前索引
    if (state.currentViewpointIndex >= updatedViewpoints.length) {
      state = state.copyWith(currentViewpointIndex: max(0, updatedViewpoints.length - 1));
    }

    logger.d('从列表移除观点: ID=$id');
  }

  /// 添加观点到列表
  void addViewpointToList(BookViewpointModel viewpoint) {
    final updatedViewpoints = [viewpoint, ...state.viewpoints];
    state = state.copyWith(viewpoints: updatedViewpoints);
    logger.d('添加观点到列表: ID=${viewpoint.id}');
  }

  /// 选择书籍
  void selectBook(BookModel book) {
    state = state.copyWith(selectedBook: book);
    logger.i('选择书籍: ${book.title} (ID: ${book.id})');
  }

  /// 删除书籍
  Future<void> deleteBook(int bookId) async {
    try {
      BookRepository.i.remove(bookId);
      logger.i('删除书籍: ID=$bookId');
      // 刷新观点列表
      await loadAllViewpoints();
    } catch (e) {
      logger.e('删除书籍失败: ID=$bookId', error: e);
      rethrow;
    }
  }

  /// 刷新书籍
  Future<void> refreshBook(int bookId) async {
    try {
      final book = BookRepository.i.findModel(bookId);
      if (book != null) {
        // 更新状态
        logger.i('刷新书籍: ${book.title} (ID: $bookId)');
      }
    } catch (e) {
      logger.e('刷新书籍失败: ID=$bookId', error: e);
      rethrow;
    }
  }
}
