/// Diary Controller Provider
///
/// 日记列表控制器，管理日记列表的展示、搜索、过滤等功能。

library;

import 'package:flutter/material.dart';
import 'package:freezed_annotation/freezed_annotation.dart';
import 'package:riverpod_annotation/riverpod_annotation.dart';

import 'package:daily_satori/app/data/index.dart';
import 'package:daily_satori/app/providers/providers.dart';

part 'diary_controller_provider.freezed.dart';
part 'diary_controller_provider.g.dart';

/// DiaryController 状态
@freezed
abstract class DiaryControllerState with _$DiaryControllerState {
  const DiaryControllerState._();

  const factory DiaryControllerState({
    /// 选中的日期
    DateTime? selectedDate,

    /// 搜索查询
    @Default('') String searchQuery,

    /// 是否搜索框可见
    @Default(false) bool isSearchVisible,

    /// 选中的过滤日期
    DateTime? selectedFilterDate,

    /// 当前标签
    @Default('') String currentTag,

    /// 日记标签列表
    @Default([]) List<String> tags,

    /// ScrollController (不在freezed中管理)
    @JsonKey(includeToJson: false, includeFromJson: false) ScrollController? scrollController,

    /// 搜索控制器 (不在freezed中管理)
    @JsonKey(includeToJson: false, includeFromJson: false) TextEditingController? searchController,

    /// 搜索焦点节点 (不在freezed中管理)
    @JsonKey(includeToJson: false, includeFromJson: false) FocusNode? searchFocusNode,

    /// 内容控制器 (不在freezed中管理)
    @JsonKey(includeToJson: false, includeFromJson: false) TextEditingController? contentController,

    /// 是否正在加载日记
    @Default(false) bool isLoadingDiaries,
  }) = _DiaryControllerState;

  factory DiaryControllerState.fromJson(Map<String, dynamic> json) => _$DiaryControllerStateFromJson(json);
}

/// DiaryControllerState 扩展
///
/// 添加基于其他provider的getter和计算方法
extension DiaryControllerStateX on DiaryControllerState {
  /// 获取日记列表 (需要通过ref访问)
  List<DiaryModel> get diaries => []; // 默认值，实际应从 diaryStateProvider 获取
}

/// DiaryController Provider
@riverpod
class DiaryController extends _$DiaryController {
  @override
  DiaryControllerState build() {
    _initialize();

    // 创建UI控制器
    final scrollController = ScrollController();
    final searchController = TextEditingController();
    final searchFocusNode = FocusNode();
    final contentController = TextEditingController();

    return DiaryControllerState(
      selectedDate: DateTime.now(),
      scrollController: scrollController,
      searchController: searchController,
      searchFocusNode: searchFocusNode,
      contentController: contentController,
      isLoadingDiaries: false,
    );
  }

  /// 初始化
  void _initialize() {
    // 监听全局标签过滤
    ref.listen(diaryStateProvider, (previous, next) {
      // 处理状态变化
    });

    _loadDiaries();
    _extractTags();
    _createImageDirectory();
  }

  /// 获取日记列表
  List<DiaryModel> getDiaries() {
    return ref.read(diaryStateProvider).diaries;
  }

  /// 是否正在加载
  bool isLoading() {
    return ref.read(diaryStateProvider).isLoading;
  }

  /// 加载日记列表
  Future<void> _loadDiaries() async {
    final diaryState = ref.read(diaryStateProvider.notifier);
    await diaryState.loadDiaries(
      keyword: state.searchQuery.isNotEmpty ? state.searchQuery : null,
      date: state.selectedFilterDate,
      tag: state.currentTag.isNotEmpty ? state.currentTag : null,
    );
  }

  /// 提取所有标签
  void _extractTags() {
    final diaries = getDiaries();
    final allTags = <String>{};
    for (final diary in diaries) {
      if (diary.tags != null && diary.tags!.isNotEmpty) {
        // 假设标签以空格分隔，因为 DiaryUtils.extractTags 使用空格
        // 或者检查 DiaryUtils.extractTags 的实现
        // 这里假设是简单的字符串，如果需要分割，应该统一格式
        // 之前的代码似乎假设 tags 是 List<String>，但 Model 中是 String?
        // 我们假设它是一个字符串，可能包含多个标签
        // 如果是 #tag1 #tag2 格式
        final RegExp tagRegex = RegExp(r'#([a-zA-Z0-9\u4e00-\u9fa5]+)');
        final matches = tagRegex.allMatches(diary.tags!);
        for (final match in matches) {
          final tag = match.group(1);
          if (tag != null && tag.isNotEmpty) {
            allTags.add(tag);
          }
        }
      }
    }
    state = state.copyWith(tags: allTags.toList());
  }

  /// 创建图片目录
  void _createImageDirectory() {
    // TODO: 实现图片目录创建
  }

  /// 搜索日记
  void searchDiaries(String query) {
    state = state.copyWith(searchQuery: query);
    _loadDiaries();
  }

  /// 切换搜索框可见性
  void toggleSearchVisibility() {
    state = state.copyWith(isSearchVisible: !state.isSearchVisible);
  }

  /// 按日期过滤
  void filterByDate(DateTime date) {
    state = state.copyWith(selectedFilterDate: date);
    _loadDiaries();
  }

  /// 按标签过滤
  void filterByTag(String tag) {
    state = state.copyWith(currentTag: tag);
    _loadDiaries();
  }

  /// 清除所有过滤条件
  void clearAllFilters() {
    state = state.copyWith(searchQuery: '', selectedFilterDate: null, currentTag: '');
    _loadDiaries();
  }

  /// 创建日记
  Future<void> createDiary(String content, {String? tags, String? images, DateTime? date}) async {
    final diary = DiaryModel.create(content: content, tags: tags, images: images, createdAt: date ?? DateTime.now());
    DiaryRepository.i.save(diary);
    ref.read(diaryStateProvider.notifier).addDiaryToList(diary);
    _extractTags();
  }

  /// 更新日记
  Future<void> updateDiary(int id, String content, {String? tags, String? images}) async {
    final oldDiary = DiaryRepository.i.find(id);
    if (oldDiary != null) {
      oldDiary.content = content;
      oldDiary.tags = tags;
      oldDiary.images = images;
      oldDiary.updatedAt = DateTime.now();
      DiaryRepository.i.save(oldDiary);
      ref.read(diaryStateProvider.notifier).updateDiaryInList(id);
      _extractTags();
    }
  }

  /// 更新当前标签 (用于编辑器)
  void updateCurrentTag(String tag) {
    state = state.copyWith(currentTag: tag);
  }

  /// 启用/禁用搜索
  void enableSearch(bool enabled) {
    state = state.copyWith(isSearchVisible: enabled);
  }

  /// 执行搜索
  void search(String query) {
    state = state.copyWith(searchQuery: query);
    _loadDiaries();
  }

  /// 清除所有过滤器
  void clearFilters() {
    state = state.copyWith(searchQuery: '', selectedFilterDate: null, currentTag: '');
    _loadDiaries();
  }

  /// 删除日记
  Future<void> deleteDiary(int id) async {
    DiaryRepository.i.remove(id);
    ref.read(diaryStateProvider.notifier).removeDiaryFromList(id);
    _extractTags();
  }
}
