/// Diary Controller Provider
///
/// 页面级控制器，管理日记页面的UI状态和用户交互。
library;

import 'package:daily_satori/app_exports.dart';
import 'package:freezed_annotation/freezed_annotation.dart';
import 'package:riverpod_annotation/riverpod_annotation.dart';

part 'diary_controller_provider.freezed.dart';
part 'diary_controller_provider.g.dart';

/// DiaryController 状态
@freezed
abstract class DiaryControllerState with _$DiaryControllerState {
  const DiaryControllerState._();

  const factory DiaryControllerState({
    DateTime? selectedDate,
    @Default('') String searchQuery,
    @Default(false) bool isSearchVisible,
    DateTime? selectedFilterDate,
    @Default('') String currentTag,
    @Default([]) List<String> tags,
  }) = _DiaryControllerState;
}

/// DiaryController Provider
@riverpod
class DiaryController extends _$DiaryController {
  @override
  DiaryControllerState build() {
    _loadInitialData();
    return DiaryControllerState(selectedDate: DateTime.now());
  }

  void _loadInitialData() {
    Future.microtask(() {
      _loadDiaries();
      _extractTags();
    });
  }

  Future<void> _loadDiaries() async {
    await ref
        .read(diaryStateProvider.notifier)
        .loadDiaries(
          keyword: state.searchQuery.isNotEmpty ? state.searchQuery : null,
          date: state.selectedFilterDate,
          tag: state.currentTag.isNotEmpty ? state.currentTag : null,
        );
  }

  void _extractTags() {
    final diaries = ref.read(diaryStateProvider).diaries;
    final allTags = <String>{};
    for (final diary in diaries) {
      if (diary.tags != null && diary.tags!.isNotEmpty) {
        final tagsString = diary.tags!;
        if (tagsString.contains(',')) {
          for (final tag in tagsString.split(',')) {
            if (tag.trim().isNotEmpty) allTags.add(tag.trim());
          }
        } else {
          for (final tag in tagsString.split(' ')) {
            final cleanTag = tag.startsWith('#') ? tag.substring(1) : tag;
            if (cleanTag.isNotEmpty) allTags.add(cleanTag.trim());
          }
        }
      }
    }
    state = state.copyWith(tags: allTags.toList());
  }

  void refreshTags() => _extractTags();

  void toggleSearchVisibility() =>
      state = state.copyWith(isSearchVisible: !state.isSearchVisible);

  void filterByDate(DateTime date) {
    state = state.copyWith(selectedFilterDate: date);
    _loadDiaries();
  }

  void filterByTag(String tag) {
    state = state.copyWith(currentTag: tag);
    _loadDiaries();
  }

  void updateCurrentTag(String tag) => state = state.copyWith(currentTag: tag);

  void enableSearch(bool enabled) =>
      state = state.copyWith(isSearchVisible: enabled);

  void search(String query) {
    state = state.copyWith(searchQuery: query);
    _loadDiaries();
  }

  void clearFilters() {
    state = state.copyWith(
      searchQuery: '',
      selectedFilterDate: null,
      currentTag: '',
    );
    _loadDiaries();
  }

  Future<void> deleteDiary(int id) async {
    await ref.read(diaryStateProvider.notifier).deleteDiary(id);
    _extractTags();
  }
}
