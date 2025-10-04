import 'package:daily_satori/app_exports.dart';

import '../../controllers/diary_controller.dart';
import 'diary_card.dart';
import 'diary_empty_state.dart';
import 'diary_loading.dart';

/// 日记列表组件
class DiaryList extends StatelessWidget {
  final DiaryController controller;
  final Function(DiaryModel) onEditDiary;

  const DiaryList({super.key, required this.controller, required this.onEditDiary});

  @override
  Widget build(BuildContext context) {
    return Obx(() {
      logger.d('构建日记列表: ${controller.diaries.length}条日记');

      // 加载状态
      if (controller.isLoading.value && controller.diaries.isEmpty) {
        return const DiaryLoading();
      }

      // 空状态
      if (controller.diaries.isEmpty) {
        return const DiaryEmptyState();
      }

      // 有内容状态
      return _buildDiaryListView(context);
    });
  }

  /// 构建日记列表视图
  Widget _buildDiaryListView(BuildContext context) {
    // 获取日记并按创建时间降序排序（最新的在前面）
    final sortedDiaries = List<DiaryModel>.from(controller.diaries)..sort((a, b) => b.createdAt.compareTo(a.createdAt));

    return ListView.builder(
      controller: controller.scrollController,
      padding: const EdgeInsets.only(left: 16, right: 16, top: 8, bottom: 80),
      itemCount: sortedDiaries.length,
      itemBuilder: (context, index) => _buildDiaryCard(context, sortedDiaries[index]),
    );
  }

  /// 构建单个日记卡片
  Widget _buildDiaryCard(BuildContext context, DiaryModel diary) {
    return DiaryCard(diary: diary, onDelete: () => _handleDeleteDiary(diary), onEdit: () => onEditDiary(diary));
  }

  /// 处理删除日记
  void _handleDeleteDiary(DiaryModel diary) {
    logger.i('请求删除日记: ID=${diary.id}');
    controller.deleteDiary(diary.id);
  }
}
