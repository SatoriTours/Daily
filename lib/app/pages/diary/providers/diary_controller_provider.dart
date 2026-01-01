/// Diary Controller Provider
library;

import 'dart:io';
import 'package:flutter/material.dart';
import 'package:freezed_annotation/freezed_annotation.dart';
import 'package:riverpod_annotation/riverpod_annotation.dart';
import 'package:path_provider/path_provider.dart';
import 'package:path/path.dart' as p;

import 'package:daily_satori/app/data/index.dart';
import 'package:daily_satori/app/providers/providers.dart';
import 'package:daily_satori/app/services/logger_service.dart';

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
    @Default(false) bool isLoadingDiaries,
    ScrollController? scrollController,
    TextEditingController? searchController,
    FocusNode? searchFocusNode,
    TextEditingController? contentController,
  }) = _DiaryControllerState;
}

/// DiaryController Provider
@riverpod
class DiaryController extends _$DiaryController {
  String? _imageDirectoryPath;

  @override
  DiaryControllerState build() {
    _initialize();
    return DiaryControllerState(
      selectedDate: DateTime.now(),
      scrollController: ScrollController(),
      searchController: TextEditingController(),
      searchFocusNode: FocusNode(),
      contentController: TextEditingController(),
    );
  }

  void _initialize() {
    Future.microtask(() {
      _loadDiaries();
      _extractTags();
      _createImageDirectory();
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

  Future<void> _createImageDirectory() async {
    try {
      final appDir = await getApplicationDocumentsDirectory();
      final imageDir = Directory(p.join(appDir.path, 'diary_images'));
      if (!await imageDir.exists()) {
        await imageDir.create(recursive: true);
      }
      _imageDirectoryPath = imageDir.path;
    } catch (e) {
      logger.e('创建日记图片目录失败', error: e);
    }
  }

  Future<String?> _copyImagesToLocal(String? images) async {
    if (images == null || images.isEmpty) return images;
    if (_imageDirectoryPath == null) await _createImageDirectory();
    if (_imageDirectoryPath == null) return images;

    final newPaths = <String>[];
    for (final path in images.split(',')) {
      if (path.isEmpty) continue;
      if (path.startsWith(_imageDirectoryPath!)) {
        newPaths.add(path);
        continue;
      }
      try {
        final file = File(path);
        if (await file.exists()) {
          final timestamp = DateTime.now().millisecondsSinceEpoch;
          final ext = p.extension(path);
          final newPath = p.join(_imageDirectoryPath!, '${timestamp}_${newPaths.length}$ext');
          await file.copy(newPath);
          newPaths.add(newPath);
        } else {
          newPaths.add(path);
        }
      } catch (e) {
        logger.e('复制图片失败: $path', error: e);
        newPaths.add(path);
      }
    }
    return newPaths.join(',');
  }

  void toggleSearchVisibility() => state = state.copyWith(isSearchVisible: !state.isSearchVisible);

  void filterByDate(DateTime date) {
    state = state.copyWith(selectedFilterDate: date);
    _loadDiaries();
  }

  void filterByTag(String tag) {
    state = state.copyWith(currentTag: tag);
    _loadDiaries();
  }

  Future<void> createDiary(String content, {String? tags, String? images, DateTime? date}) async {
    final localImages = await _copyImagesToLocal(images);
    final diary = DiaryModel.create(
      content: content,
      tags: tags,
      images: localImages,
      createdAt: date ?? DateTime.now(),
    );
    DiaryRepository.i.save(diary);
    ref.read(diaryStateProvider.notifier).addDiaryToList(diary);
    _extractTags();
  }

  Future<void> updateDiary(int id, String content, {String? tags, String? images}) async {
    final localImages = await _copyImagesToLocal(images);
    final oldDiary = DiaryRepository.i.find(id);
    if (oldDiary != null) {
      oldDiary
        ..content = content
        ..tags = tags
        ..images = localImages
        ..updatedAt = DateTime.now();
      DiaryRepository.i.save(oldDiary);
      ref.read(diaryStateProvider.notifier).updateDiaryInList(id);
      _extractTags();
    }
  }

  void updateCurrentTag(String tag) => state = state.copyWith(currentTag: tag);
  void enableSearch(bool enabled) => state = state.copyWith(isSearchVisible: enabled);

  void search(String query) {
    state = state.copyWith(searchQuery: query);
    _loadDiaries();
  }

  void clearFilters() {
    state = state.copyWith(searchQuery: '', selectedFilterDate: null, currentTag: '');
    _loadDiaries();
  }

  Future<void> deleteDiary(int id) async {
    DiaryRepository.i.remove(id);
    ref.read(diaryStateProvider.notifier).removeDiaryFromList(id);
    _extractTags();
  }
}
