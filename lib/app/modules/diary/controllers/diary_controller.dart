import 'package:flutter/material.dart';
import 'package:get/get.dart';
import 'package:daily_satori/app/models/diary_model.dart';
import 'package:daily_satori/app/repositories/diary_repository.dart';
import 'package:daily_satori/app_exports.dart';

/// 日记列表控制器
class DiaryController extends BaseController {
  /// UI状态
  final isLoading = false.obs;
  final selectedDate = DateTime.now().obs;
  final searchQuery = ''.obs;
  final currentTag = ''.obs;

  /// 日记数据
  final diaries = <DiaryModel>[].obs;

  /// UI控制器
  final scrollController = ScrollController();
  final searchController = TextEditingController();
  final contentController = TextEditingController();

  // 日记标签列表
  final tags = <String>[].obs;

  // ==== 生命周期方法 ====

  @override
  void onInit() {
    super.onInit();
    _loadDiaries();
    _extractTags();
  }

  @override
  void onClose() {
    scrollController.dispose();
    searchController.dispose();
    contentController.dispose();
    super.onClose();
  }

  // ==== 公共方法 ====

  /// 加载日记列表
  Future<void> loadDiaries() async {
    _loadDiaries();
  }

  /// 创建新日记
  Future<void> createDiary(String content, {String? tags, String? mood, String? images}) async {
    if (content.trim().isEmpty) return;

    isLoading.value = true;

    final diary = DiaryModel(content: content, tags: tags, mood: mood, images: images);

    DiaryRepository.i.save(diary);

    // 重新加载日记列表
    await _loadDiaries();

    // 清空输入框
    contentController.clear();

    isLoading.value = false;
  }

  /// 删除日记
  Future<void> deleteDiary(int id) async {
    isLoading.value = true;

    DiaryRepository.i.delete(id);

    // 重新加载日记列表
    await _loadDiaries();

    isLoading.value = false;
  }

  /// 更新日记
  Future<void> updateDiary(DiaryModel diary) async {
    isLoading.value = true;

    // 更新修改时间
    diary.updatedAt = DateTime.now();
    DiaryRepository.i.save(diary);

    // 重新加载日记列表
    await _loadDiaries();

    isLoading.value = false;
  }

  /// 按标签筛选
  void filterByTag(String tag) {
    currentTag.value = tag;
    _loadDiaries();
  }

  /// 按内容搜索
  void search(String query) {
    searchQuery.value = query;
    _loadDiaries();
  }

  /// 清除筛选条件
  void clearFilters() {
    currentTag.value = '';
    searchQuery.value = '';
    searchController.clear();
    _loadDiaries();
  }

  /// 启用/禁用搜索
  void enableSearch(bool enable) {
    if (enable) {
      searchController.clear();
    } else {
      clearFilters();
    }
  }

  // ==== 私有方法 ====

  /// 加载日记列表
  Future<void> _loadDiaries() async {
    isLoading.value = true;

    List<DiaryModel> result = [];

    // 如果有搜索关键词，执行搜索
    if (searchQuery.value.isNotEmpty) {
      result = DiaryRepository.i.searchByContent(searchQuery.value);
    }
    // 如果有选择的标签，按标签筛选
    else if (currentTag.value.isNotEmpty) {
      result = DiaryRepository.i.searchByTag(currentTag.value);
    }
    // 否则加载所有日记
    else {
      result = DiaryRepository.i.getAll();
    }

    diaries.value = result;

    isLoading.value = false;
  }

  /// 提取所有日记中的标签
  void _extractTags() {
    final allDiaries = DiaryRepository.i.getAll();
    final tagSet = <String>{};

    for (final diary in allDiaries) {
      if (diary.tags != null && diary.tags!.isNotEmpty) {
        final diaryTags = diary.tags!.split(',');
        for (final tag in diaryTags) {
          if (tag.trim().isNotEmpty) {
            tagSet.add(tag.trim());
          }
        }
      }
    }

    tags.value = tagSet.toList()..sort();
  }
}
