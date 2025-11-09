import 'dart:async';
import 'package:flutter/material.dart';
import 'package:get/get.dart';
import 'package:daily_satori/app/utils/base_controller.dart';
import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/config/app_config.dart';

/// 通用列表控制器基类
///
/// 提供统一的列表管理功能，包括：
/// - 数据加载和分页
/// - 搜索和过滤
/// - 状态管理
/// - 错误处理
abstract class BaseListController<T> extends BaseController {
  // 数据列表
  final items = <T>[].obs;
  final filteredItems = <T>[].obs;

  // 分页相关
  final currentPage = 1.obs;
  final pageSize = PaginationConfig.defaultPageSize.obs;
  final hasMoreData = true.obs;
  final isLoadingMore = false.obs;

  // 搜索相关
  final searchController = TextEditingController();
  final searchFocusNode = FocusNode();
  final isSearchVisible = false.obs;
  final searchQuery = ''.obs;

  // 过滤相关
  final selectedTags = <int>[].obs;
  final selectedDate = Rx<DateTime?>(null);
  final isFavoriteFilter = false.obs;

  // 状态管理
  final isLoading = false.obs;
  final errorMessage = ''.obs;
  final isRefreshing = false.obs;

  // 滚动控制
  final scrollController = ScrollController();

  @override
  void onInit() {
    super.onInit();
    _setupScrollListener();
    _setupSearchListener();
    loadInitialData();
  }

  @override
  void onClose() {
    searchController.dispose();
    searchFocusNode.dispose();
    scrollController.dispose();
    super.onClose();
  }

  /// 设置滚动监听 - 用于自动加载更多
  void _setupScrollListener() {
    scrollController.addListener(() {
      if (scrollController.position.pixels >= scrollController.position.maxScrollExtent - 200) {
        if (hasMoreData.value && !isLoadingMore.value && !isLoading.value) {
          loadMoreData();
        }
      }
    });
  }

  /// 设置搜索监听 - 防抖搜索
  void _setupSearchListener() {
    searchController.addListener(() {
      searchQuery.value = searchController.text;
      _debounceSearch();
    });
  }

  /// 防抖搜索
  Timer? _searchDebounce;
  void _debounceSearch() {
    _searchDebounce?.cancel();
    _searchDebounce = Timer(SearchConfig.debounceTime, () {
      applyFilters();
    });
  }

  /// 初始数据加载
  Future<void> loadInitialData() async {
    try {
      isLoading.value = true;
      errorMessage.value = '';

      final data = await fetchData(page: 1);
      items.assignAll(data);
      filteredItems.assignAll(data);

      currentPage.value = 1;
      hasMoreData.value = data.length >= pageSize.value;
    } catch (e) {
      errorMessage.value = e.toString();
      logger.e('加载初始数据失败', error: e);
    } finally {
      isLoading.value = false;
    }
  }

  /// 下拉刷新
  Future<void> refreshData() async {
    try {
      isRefreshing.value = true;
      errorMessage.value = '';

      final data = await fetchData(page: 1);
      items.assignAll(data);
      applyFilters();

      currentPage.value = 1;
      hasMoreData.value = data.length >= pageSize.value;
    } catch (e) {
      errorMessage.value = e.toString();
      logger.e('刷新数据失败', error: e);
    } finally {
      isRefreshing.value = false;
    }
  }

  /// 加载更多数据
  Future<void> loadMoreData() async {
    if (!hasMoreData.value || isLoadingMore.value) return;

    try {
      isLoadingMore.value = true;

      final nextPage = currentPage.value + 1;
      final data = await fetchData(page: nextPage);

      items.addAll(data);
      applyFilters();

      currentPage.value = nextPage;
      hasMoreData.value = data.length >= pageSize.value;
    } catch (e) {
      logger.e('加载更多数据失败', error: e);
    } finally {
      isLoadingMore.value = false;
    }
  }

  /// 应用过滤条件
  void applyFilters() {
    var result = items.toList();

    // 搜索过滤
    if (searchQuery.value.isNotEmpty) {
      result = result.where((item) => applySearchFilter(item, searchQuery.value)).toList();
    }

    // 标签过滤
    if (selectedTags.isNotEmpty) {
      result = result.where((item) => applyTagFilter(item, selectedTags)).toList();
    }

    // 日期过滤
    if (selectedDate.value != null) {
      result = result.where((item) => applyDateFilter(item, selectedDate.value!)).toList();
    }

    // 收藏过滤
    if (isFavoriteFilter.value) {
      result = result.where((item) => applyFavoriteFilter(item)).toList();
    }

    filteredItems.assignAll(result);
  }

  /// 清空所有过滤条件
  void clearAllFilters() {
    searchController.clear();
    selectedTags.clear();
    selectedDate.value = null;
    isFavoriteFilter.value = false;
    applyFilters();
  }

  /// 切换搜索显示
  void toggleSearch() {
    isSearchVisible.toggle();
    if (!isSearchVisible.value) {
      searchController.clear();
    } else {
      searchFocusNode.requestFocus();
    }
  }

  /// 清除搜索
  void clearSearch() {
    searchController.clear();
    isSearchVisible.value = false;
  }

  /// 选择标签
  void toggleTag(int tagId) {
    if (selectedTags.contains(tagId)) {
      selectedTags.remove(tagId);
    } else {
      selectedTags.add(tagId);
    }
    applyFilters();
  }

  /// 选择日期
  void selectDate(DateTime? date) {
    selectedDate.value = date;
    applyFilters();
  }

  /// 切换收藏过滤
  void toggleFavoriteFilter() {
    isFavoriteFilter.toggle();
    applyFilters();
  }

  /// 获取数据 - 子类必须实现
  Future<List<T>> fetchData({required int page, int? limit});

  /// 搜索过滤 - 子类实现具体逻辑
  bool applySearchFilter(T item, String query);

  /// 标签过滤 - 子类实现具体逻辑
  bool applyTagFilter(T item, List<int> tagIds);

  /// 日期过滤 - 子类实现具体逻辑
  bool applyDateFilter(T item, DateTime date);

  /// 收藏过滤 - 子类实现具体逻辑
  bool applyFavoriteFilter(T item);

  /// 获取过滤后的数据总数
  int get filteredCount => filteredItems.length;

  /// 获取原始数据总数
  int get totalCount => items.length;

  /// 是否有活跃过滤
  bool get hasActiveFilters =>
      searchQuery.value.isNotEmpty || selectedTags.isNotEmpty || selectedDate.value != null || isFavoriteFilter.value;

  /// 获取加载状态文本
  String get loadMoreText {
    if (isLoadingMore.value) return '加载中...';
    if (!hasMoreData.value) return '没有更多数据';
    return '加载更多';
  }
}
