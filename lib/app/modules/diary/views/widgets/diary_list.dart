import 'package:flutter/material.dart';
import 'package:daily_satori/app_exports.dart';
import 'package:daily_satori/app/models/diary_model.dart';
import 'package:daily_satori/app/styles/diary_style.dart';

import '../../controllers/diary_controller.dart';
import 'diary_card.dart';
import 'diary_date_header.dart';
import 'diary_empty_state.dart';

/// 日记列表组件
class DiaryList extends StatelessWidget {
  final DiaryController controller;
  final Function(DiaryModel) onEditDiary;

  const DiaryList({super.key, required this.controller, required this.onEditDiary});

  @override
  Widget build(BuildContext context) {
    return Obx(() {
      if (controller.isLoading.value && controller.diaries.isEmpty) {
        return Center(child: CircularProgressIndicator(color: DiaryStyle.accentColor(context)));
      }

      if (controller.diaries.isEmpty) {
        return const DiaryEmptyState();
      }

      // 按日期分组日记
      final groupedDiaries = _groupDiariesByDate(controller.diaries);

      return Padding(
        padding: const EdgeInsets.only(bottom: 80), // 为底部输入框留出空间
        child: ListView.builder(
          controller: controller.scrollController,
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
          itemCount: groupedDiaries.length,
          itemBuilder: (context, index) {
            final date = groupedDiaries.keys.elementAt(index);
            final diariesForDate = groupedDiaries[date]!;

            return Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Padding(
                  padding: const EdgeInsets.only(top: 12, bottom: 8, left: 4),
                  child: DiaryDateHeader(date: date),
                ),
                ...diariesForDate.map(
                  (diary) => DiaryCard(
                    diary: diary,
                    onDelete: () => controller.deleteDiary(diary.id),
                    onEdit: () => onEditDiary(diary),
                  ),
                ),
              ],
            );
          },
        ),
      );
    });
  }

  /// 按日期分组日记
  Map<DateTime, List<DiaryModel>> _groupDiariesByDate(List<DiaryModel> diaries) {
    final Map<DateTime, List<DiaryModel>> grouped = {};

    for (final diary in diaries) {
      final date = DateTime(diary.createdAt.year, diary.createdAt.month, diary.createdAt.day);

      if (!grouped.containsKey(date)) {
        grouped[date] = [];
      }

      grouped[date]!.add(diary);
    }

    return grouped;
  }
}
