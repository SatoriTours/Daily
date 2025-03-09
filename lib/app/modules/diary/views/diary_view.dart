import 'package:intl/intl.dart';
import 'package:daily_satori/app_exports.dart';

import '../controllers/diary_controller.dart';
import 'widgets/diary_card.dart';
import 'widgets/diary_input.dart';
import 'widgets/diary_app_bar.dart';

/// 日记页面
class DiaryView extends GetView<DiaryController> {
  const DiaryView({super.key});

  @override
  Widget build(BuildContext context) {
    // 使用组件实例化
    final appBar = DiaryAppBar(controller: controller);
    final inputArea = DiaryInput(controller: controller);

    return Scaffold(
      appBar: appBar,
      body: Stack(children: [_buildDiaryList(), Positioned(left: 0, right: 0, bottom: 0, child: inputArea)]),
      floatingActionButton: FloatingActionButton(
        onPressed: () {
          _showInputDialog(context);
        },
        child: const Icon(Icons.add),
      ),
    );
  }

  /// 构建日记列表
  Widget _buildDiaryList() {
    return Obx(() {
      if (controller.isLoading.value && controller.diaries.isEmpty) {
        return const Center(child: CircularProgressIndicator());
      }

      if (controller.diaries.isEmpty) {
        return Center(
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Icon(Icons.book, size: 64, color: Colors.grey[400]),
              const SizedBox(height: 16),
              Text('还没有日记，开始记录今天的想法吧', style: TextStyle(fontSize: 16, color: Colors.grey[600])),
            ],
          ),
        );
      }

      // 按日期分组日记
      final groupedDiaries = _groupDiariesByDate(controller.diaries);

      return Padding(
        padding: const EdgeInsets.only(bottom: 80), // 为底部输入框留出空间
        child: ListView.builder(
          controller: controller.scrollController,
          padding: const EdgeInsets.all(16),
          itemCount: groupedDiaries.length,
          itemBuilder: (context, index) {
            final date = groupedDiaries.keys.elementAt(index);
            final diariesForDate = groupedDiaries[date]!;

            return Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                _buildDateHeader(date),
                const SizedBox(height: 8),
                ...diariesForDate.map(
                  (diary) => DiaryCard(
                    diary: diary,
                    onDelete: () => controller.deleteDiary(diary.id),
                    onEdit: () => _showEditDialog(context, diary),
                  ),
                ),
                const SizedBox(height: 16),
              ],
            );
          },
        ),
      );
    });
  }

  /// 构建日期标题
  Widget _buildDateHeader(DateTime date) {
    return Container(
      padding: const EdgeInsets.symmetric(vertical: 8, horizontal: 16),
      decoration: BoxDecoration(color: Colors.grey[200], borderRadius: BorderRadius.circular(20)),
      child: Text(
        _formatDate(date),
        style: const TextStyle(fontSize: 14, fontWeight: FontWeight.bold, color: Colors.black87),
      ),
    );
  }

  /// 显示输入对话框
  void _showInputDialog(BuildContext context) {
    final contentController = TextEditingController();
    final tagsController = TextEditingController();

    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      shape: const RoundedRectangleBorder(borderRadius: BorderRadius.vertical(top: Radius.circular(16))),
      builder: (context) {
        return Padding(
          padding: EdgeInsets.only(bottom: MediaQuery.of(context).viewInsets.bottom, left: 16, right: 16, top: 16),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              const Text('记录一下', style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
              const SizedBox(height: 16),
              TextField(
                controller: contentController,
                maxLines: 5,
                decoration: const InputDecoration(hintText: '写点什么...', border: OutlineInputBorder()),
              ),
              const SizedBox(height: 8),
              TextField(
                controller: tagsController,
                decoration: const InputDecoration(hintText: '标签(用逗号分隔)', border: OutlineInputBorder()),
              ),
              const SizedBox(height: 16),
              ElevatedButton(
                onPressed: () {
                  if (contentController.text.trim().isNotEmpty) {
                    controller.createDiary(contentController.text, tags: tagsController.text);
                    Navigator.pop(context);
                  }
                },
                child: const Text('保存'),
              ),
              const SizedBox(height: 8),
            ],
          ),
        );
      },
    );
  }

  /// 显示编辑对话框
  void _showEditDialog(BuildContext context, DiaryModel diary) {
    final contentController = TextEditingController(text: diary.content);
    final tagsController = TextEditingController(text: diary.tags);

    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      shape: const RoundedRectangleBorder(borderRadius: BorderRadius.vertical(top: Radius.circular(16))),
      builder: (context) {
        return Padding(
          padding: EdgeInsets.only(bottom: MediaQuery.of(context).viewInsets.bottom, left: 16, right: 16, top: 16),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              const Text('编辑日记', style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
              const SizedBox(height: 16),
              TextField(
                controller: contentController,
                maxLines: 5,
                decoration: const InputDecoration(hintText: '写点什么...', border: OutlineInputBorder()),
              ),
              const SizedBox(height: 8),
              TextField(
                controller: tagsController,
                decoration: const InputDecoration(hintText: '标签(用逗号分隔)', border: OutlineInputBorder()),
              ),
              const SizedBox(height: 16),
              ElevatedButton(
                onPressed: () {
                  if (contentController.text.trim().isNotEmpty) {
                    final updatedDiary = DiaryModel(
                      id: diary.id,
                      content: contentController.text,
                      tags: tagsController.text,
                      mood: diary.mood,
                      images: diary.images,
                      createdAt: diary.createdAt,
                    );
                    controller.updateDiary(updatedDiary);
                    Navigator.pop(context);
                  }
                },
                child: const Text('更新'),
              ),
              const SizedBox(height: 8),
            ],
          ),
        );
      },
    );
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

  /// 格式化日期
  String _formatDate(DateTime date) {
    final now = DateTime.now();
    final yesterday = DateTime(now.year, now.month, now.day - 1);
    final dateOnly = DateTime(date.year, date.month, date.day);

    if (dateOnly.isAtSameMomentAs(DateTime(now.year, now.month, now.day))) {
      return '今天 - ${DateFormat('MM月dd日').format(date)}';
    } else if (dateOnly.isAtSameMomentAs(yesterday)) {
      return '昨天 - ${DateFormat('MM月dd日').format(date)}';
    } else {
      return DateFormat('yyyy年MM月dd日').format(date);
    }
  }
}
