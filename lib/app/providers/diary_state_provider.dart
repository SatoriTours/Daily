/// 日记状态管理 Provider
///
/// Riverpod 版本的 DiaryStateService，管理日记列表和过滤状态。
library;

import 'package:freezed_annotation/freezed_annotation.dart';
import 'package:riverpod_annotation/riverpod_annotation.dart';

import 'package:daily_satori/app/data/index.dart';
import 'package:daily_satori/app/services/logger_service.dart';

part 'diary_state_provider.freezed.dart';
part 'diary_state_provider.g.dart';

/// 日记状态模型
@freezed
abstract class DiaryStateModel with _$DiaryStateModel {
  const DiaryStateModel._();

  const factory DiaryStateModel({
    @Default([]) List<DiaryModel> diaries,
    @Default(false) bool isLoading,
    @Default(-1) int activeDiaryId,
    DiaryModel? activeDiary,
    @Default('') String globalTagFilter,
    DateTime? globalDateFilter,
    @Default({}) Map<int, DiaryModel> diaryUpdates,
  }) = _DiaryStateModel;
}

/// 日记状态 Provider
@riverpod
class DiaryState extends _$DiaryState {
  @override
  DiaryStateModel build() {
    logger.i('DiaryState Provider 初始化完成');
    return const DiaryStateModel();
  }

  /// 加载日记列表
  Future<void> loadDiaries({String? keyword, String? tag, DateTime? date}) async {
    state = state.copyWith(isLoading: true);
    try {
      logger.i('加载日记列表: keyword=$keyword, tag=$tag, date=$date');

      List<DiaryModel> result;

      if (keyword != null && keyword.isNotEmpty) {
        result = DiaryRepository.i.findByContent(keyword);
      } else if (tag != null && tag.isNotEmpty) {
        result = DiaryRepository.i.findByTag(tag);
      } else if (date != null) {
        result = DiaryRepository.i.findByCreatedDate(date);
      } else {
        result = DiaryRepository.i.findAll();
      }

      state = state.copyWith(diaries: result);
      logger.d('日记列表加载完成: ${result.length}条');
    } catch (e) {
      logger.e('加载日记列表失败', error: e);
    } finally {
      state = state.copyWith(isLoading: false);
    }
  }

  /// 更新列表中的日记
  void updateDiaryInList(int id) {
    final diary = DiaryRepository.i.find(id);
    if (diary == null) return;

    final diaries = List<DiaryModel>.from(state.diaries);
    final index = diaries.indexWhere((d) => d.id == id);
    if (index != -1) {
      diaries[index] = diary;
      state = state.copyWith(diaries: diaries);
    }
    logger.d('更新列表中的日记: ID=$id');
  }

  /// 从列表中移除日记
  void removeDiaryFromList(int id) {
    final updatedDiaries = state.diaries.where((item) => item.id != id).toList();
    state = state.copyWith(diaries: updatedDiaries);
    logger.d('从列表移除日记: ID=$id');
  }

  /// 添加日记到列表
  void addDiaryToList(DiaryModel diary) {
    final updatedDiaries = [diary, ...state.diaries];
    state = state.copyWith(diaries: updatedDiaries);
    logger.d('添加日记到列表: ID=${diary.id}');
  }

  /// 获取日记引用
  DiaryModel? getDiaryRef(int id) {
    final index = state.diaries.indexWhere((item) => item.id == id);
    if (index != -1) {
      setActiveDiary(state.diaries[index]);
      return state.diaries[index];
    }
    return DiaryRepository.i.find(id);
  }

  /// 设置活跃日记
  void setActiveDiary(DiaryModel diary) {
    state = state.copyWith(activeDiaryId: diary.id, activeDiary: diary);
    final content = diary.content.length > 20 ? '${diary.content.substring(0, 20)}...' : diary.content;
    logger.i('设置活跃日记: $content (ID: ${diary.id})');
  }

  /// 清除活跃日记
  void clearActiveDiary() {
    state = state.copyWith(activeDiaryId: -1, activeDiary: null);
    logger.i('清除活跃日记');
  }

  /// 设置全局标签过滤
  void setGlobalTagFilter(String tag) {
    state = state.copyWith(globalTagFilter: tag);
    logger.i('设置全局标签过滤: $tag');
  }

  /// 清除全局标签过滤
  void clearGlobalTagFilter() {
    state = state.copyWith(globalTagFilter: '');
    logger.i('清除全局标签过滤');
  }

  /// 设置全局日期过滤
  void setGlobalDateFilter(DateTime? date) {
    final normalizedDate = date != null ? DateTime(date.year, date.month, date.day) : null;
    state = state.copyWith(globalDateFilter: normalizedDate);
    if (date != null) {
      logger.i('设置全局日期过滤: ${date.year}-${date.month}-${date.day}');
    } else {
      logger.i('清除全局日期过滤');
    }
  }

  /// 清除所有过滤条件
  void clearAllFilters() {
    state = state.copyWith(globalTagFilter: '', globalDateFilter: null);
    logger.i('清除所有过滤条件');
  }

  /// 通知日记更新
  void notifyDiaryUpdated(DiaryModel diary) {
    final updates = Map<int, DiaryModel>.from(state.diaryUpdates);
    updates[diary.id] = diary;
    state = state.copyWith(diaryUpdates: updates);
    logger.i('通知日记更新: ID ${diary.id}');

    // 如果是当前活跃日记，更新活跃日记引用
    if (state.activeDiaryId == diary.id) {
      state = state.copyWith(activeDiary: diary);
    }
  }

  /// 获取日记更新通知
  DiaryModel? getDiaryUpdate(int diaryId) {
    final updates = Map<int, DiaryModel>.from(state.diaryUpdates);
    final diary = updates.remove(diaryId);
    state = state.copyWith(diaryUpdates: updates);
    return diary;
  }

  /// 保存日记
  Future<void> saveDiary(DiaryModel diary) async {
    try {
      if (diary.id <= 0) {
        // 新增
        DiaryRepository.i.saveModel(diary);
        logger.i('新增日记: ${diary.content.substring(0, 20)}...');
        addDiaryToList(diary);
      } else {
        // 更新
        DiaryRepository.i.updateModel(diary);
        logger.i('更新日记: ID=${diary.id}');
        updateDiaryInList(diary.id);
      }
    } catch (e) {
      logger.e('保存日记失败', error: e);
      rethrow;
    }
  }

  /// 删除日记
  Future<void> deleteDiary(int diaryId) async {
    try {
      final diary = DiaryRepository.i.find(diaryId);
      if (diary != null) {
        DiaryRepository.i.remove(diaryId);
        logger.i('删除日记: ID=$diaryId');
        removeDiaryFromList(diaryId);
      }
    } catch (e) {
      logger.e('删除日记失败: ID=$diaryId', error: e);
      rethrow;
    }
  }
}
