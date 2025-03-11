import 'package:daily_satori/app_exports.dart';
import 'package:daily_satori/app/styles/diary_style.dart';

import '../../controllers/diary_controller.dart';
import 'diary_card.dart';
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

      // 获取日记并按创建时间降序排序（最新的在前面）
      final sortedDiaries = List<DiaryModel>.from(controller.diaries)
        ..sort((a, b) => b.createdAt.compareTo(a.createdAt));

      return Padding(
        padding: const EdgeInsets.only(bottom: 80), // 为底部输入框留出空间
        child: ListView.builder(
          controller: controller.scrollController,
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
          itemCount: sortedDiaries.length,
          itemBuilder: (context, index) {
            final diary = sortedDiaries[index];

            return DiaryCard(
              diary: diary,
              onDelete: () => controller.deleteDiary(diary.id),
              onEdit: () => onEditDiary(diary),
            );
          },
        ),
      );
    });
  }
}
