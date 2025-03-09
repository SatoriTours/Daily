import 'package:flutter/material.dart';
import 'package:get/get.dart';
import 'package:intl/intl.dart';
import 'package:daily_satori/app/models/diary_model.dart';
import 'package:daily_satori/app/styles/diary_style.dart';
import 'package:daily_satori/app_exports.dart';

import '../controllers/diary_controller.dart';
import 'widgets/diary_card.dart';
import 'widgets/diary_input.dart';

/// 日记页面
class DiaryView extends GetView<DiaryController> {
  const DiaryView({super.key});

  @override
  Widget build(BuildContext context) {
    // 使用组件实例化
    final inputArea = DiaryInput(controller: controller);

    return Scaffold(
      backgroundColor: DiaryStyle.backgroundColor(context),
      appBar: AppBar(
        backgroundColor: DiaryStyle.cardColor(context),
        elevation: 0.5,
        title: Text('我的日记', style: TextStyle(fontSize: 18, color: DiaryStyle.primaryTextColor(context))),
        actions: [
          IconButton(
            icon: Icon(Icons.search, color: DiaryStyle.secondaryTextColor(context)),
            onPressed: () {
              controller.enableSearch(true);
            },
          ),
          IconButton(
            icon: Icon(Icons.tag, color: DiaryStyle.secondaryTextColor(context)),
            onPressed: () {
              _showTagsDialog(context);
            },
          ),
        ],
      ),
      body: Stack(children: [_buildDiaryList(context), Positioned(left: 0, right: 0, bottom: 0, child: inputArea)]),
      floatingActionButton: FloatingActionButton(
        onPressed: () {
          _showInputDialog(context);
        },
        backgroundColor: DiaryStyle.accentColor(context),
        child: const Icon(Icons.add),
      ),
    );
  }

  /// 构建日记列表
  Widget _buildDiaryList(BuildContext context) {
    return Obx(() {
      if (controller.isLoading.value && controller.diaries.isEmpty) {
        return Center(child: CircularProgressIndicator(color: DiaryStyle.accentColor(context)));
      }

      if (controller.diaries.isEmpty) {
        return Center(
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Icon(
                Icons.note_alt_outlined,
                size: 72,
                color: Theme.of(context).brightness == Brightness.dark ? Colors.grey[700] : Colors.grey[300],
              ),
              const SizedBox(height: 16),
              Text('还没有日记，开始记录今天的想法吧', style: TextStyle(fontSize: 16, color: DiaryStyle.secondaryTextColor(context))),
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
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
          itemCount: groupedDiaries.length,
          itemBuilder: (context, index) {
            final date = groupedDiaries.keys.elementAt(index);
            final diariesForDate = groupedDiaries[date]!;

            return Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Padding(
                  padding: const EdgeInsets.only(top: 16, bottom: 12, left: 4),
                  child: _buildDateHeader(context, date),
                ),
                ...diariesForDate.map(
                  (diary) => DiaryCard(
                    diary: diary,
                    onDelete: () => controller.deleteDiary(diary.id),
                    onEdit: () => _showEditDialog(context, diary),
                  ),
                ),
              ],
            );
          },
        ),
      );
    });
  }

  /// 构建日期标题 - 支持主题
  Widget _buildDateHeader(BuildContext context, DateTime date) {
    return Container(
      padding: const EdgeInsets.symmetric(vertical: 4, horizontal: 0),
      child: Text(
        _formatDate(date),
        style: TextStyle(
          fontSize: 14,
          fontWeight: FontWeight.w500,
          color: DiaryStyle.secondaryTextColor(context),
          letterSpacing: 0.5,
        ),
      ),
    );
  }

  /// 显示输入对话框 - 支持主题
  void _showInputDialog(BuildContext context) {
    final contentController = TextEditingController();
    final tagsController = TextEditingController();

    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: DiaryStyle.bottomSheetColor(context),
      shape: const RoundedRectangleBorder(borderRadius: BorderRadius.vertical(top: Radius.circular(16))),
      builder: (context) {
        return Padding(
          padding: EdgeInsets.only(bottom: MediaQuery.of(context).viewInsets.bottom, left: 20, right: 20, top: 20),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Text(
                    '写日记',
                    style: TextStyle(
                      fontSize: 16,
                      fontWeight: FontWeight.w500,
                      color: DiaryStyle.primaryTextColor(context),
                    ),
                  ),
                  IconButton(
                    icon: Icon(Icons.close, size: 20, color: DiaryStyle.secondaryTextColor(context)),
                    onPressed: () => Navigator.pop(context),
                  ),
                ],
              ),
              const SizedBox(height: 12),
              TextField(
                controller: contentController,
                maxLines: 6,
                autofocus: true,
                style: TextStyle(fontSize: 15, height: 1.5, color: DiaryStyle.primaryTextColor(context)),
                decoration: InputDecoration(
                  hintText: '写点什么...',
                  hintStyle: TextStyle(
                    color: Theme.of(context).brightness == Brightness.dark ? Colors.grey[500] : Colors.grey[400],
                  ),
                  border: InputBorder.none,
                  contentPadding: EdgeInsets.zero,
                ),
              ),
              const SizedBox(height: 12),
              TextField(
                controller: tagsController,
                style: TextStyle(fontSize: 14, color: DiaryStyle.primaryTextColor(context)),
                decoration: InputDecoration(
                  hintText: '添加标签，用逗号分隔',
                  hintStyle: TextStyle(
                    color: Theme.of(context).brightness == Brightness.dark ? Colors.grey[500] : Colors.grey[400],
                  ),
                  prefixIcon: Icon(Icons.tag, size: 18, color: DiaryStyle.secondaryTextColor(context)),
                  border: OutlineInputBorder(
                    borderRadius: BorderRadius.circular(24),
                    borderSide: BorderSide(color: DiaryStyle.dividerColor(context)),
                  ),
                  contentPadding: EdgeInsets.symmetric(vertical: 10),
                ),
              ),
              const SizedBox(height: 20),
              ElevatedButton(
                style: ElevatedButton.styleFrom(
                  backgroundColor: DiaryStyle.accentColor(context),
                  foregroundColor: Colors.white,
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(24)),
                  padding: EdgeInsets.symmetric(vertical: 12),
                ),
                onPressed: () {
                  if (contentController.text.trim().isNotEmpty) {
                    controller.createDiary(contentController.text, tags: tagsController.text);
                    Navigator.pop(context);
                  }
                },
                child: const Text('保存', style: TextStyle(fontSize: 16)),
              ),
              const SizedBox(height: 16),
            ],
          ),
        );
      },
    );
  }

  /// 显示编辑对话框 - 支持主题
  void _showEditDialog(BuildContext context, DiaryModel diary) {
    final contentController = TextEditingController(text: diary.content);
    final tagsController = TextEditingController(text: diary.tags);

    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: DiaryStyle.bottomSheetColor(context),
      shape: const RoundedRectangleBorder(borderRadius: BorderRadius.vertical(top: Radius.circular(16))),
      builder: (context) {
        return Padding(
          padding: EdgeInsets.only(bottom: MediaQuery.of(context).viewInsets.bottom, left: 20, right: 20, top: 20),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Text(
                    '编辑日记',
                    style: TextStyle(
                      fontSize: 16,
                      fontWeight: FontWeight.w500,
                      color: DiaryStyle.primaryTextColor(context),
                    ),
                  ),
                  IconButton(
                    icon: Icon(Icons.close, size: 20, color: DiaryStyle.secondaryTextColor(context)),
                    onPressed: () => Navigator.pop(context),
                  ),
                ],
              ),
              const SizedBox(height: 12),
              TextField(
                controller: contentController,
                maxLines: 6,
                autofocus: true,
                style: TextStyle(fontSize: 15, height: 1.5, color: DiaryStyle.primaryTextColor(context)),
                decoration: InputDecoration(
                  hintText: '写点什么...',
                  hintStyle: TextStyle(
                    color: Theme.of(context).brightness == Brightness.dark ? Colors.grey[500] : Colors.grey[400],
                  ),
                  border: InputBorder.none,
                  contentPadding: EdgeInsets.zero,
                ),
              ),
              const SizedBox(height: 12),
              TextField(
                controller: tagsController,
                style: TextStyle(fontSize: 14, color: DiaryStyle.primaryTextColor(context)),
                decoration: InputDecoration(
                  hintText: '添加标签，用逗号分隔',
                  hintStyle: TextStyle(
                    color: Theme.of(context).brightness == Brightness.dark ? Colors.grey[500] : Colors.grey[400],
                  ),
                  prefixIcon: Icon(Icons.tag, size: 18, color: DiaryStyle.secondaryTextColor(context)),
                  border: OutlineInputBorder(
                    borderRadius: BorderRadius.circular(24),
                    borderSide: BorderSide(color: DiaryStyle.dividerColor(context)),
                  ),
                  contentPadding: EdgeInsets.symmetric(vertical: 10),
                ),
              ),
              const SizedBox(height: 20),
              ElevatedButton(
                style: ElevatedButton.styleFrom(
                  backgroundColor: DiaryStyle.accentColor(context),
                  foregroundColor: Colors.white,
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(24)),
                  padding: EdgeInsets.symmetric(vertical: 12),
                ),
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
                child: const Text('更新', style: TextStyle(fontSize: 16)),
              ),
              const SizedBox(height: 16),
            ],
          ),
        );
      },
    );
  }

  /// 显示标签选择对话框 - 支持主题
  void _showTagsDialog(BuildContext context) {
    showModalBottomSheet(
      context: context,
      backgroundColor: DiaryStyle.bottomSheetColor(context),
      shape: const RoundedRectangleBorder(borderRadius: BorderRadius.vertical(top: Radius.circular(16))),
      builder: (context) {
        return Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          mainAxisSize: MainAxisSize.min,
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(20, 20, 20, 10),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Text(
                    '选择标签',
                    style: TextStyle(
                      fontSize: 16,
                      fontWeight: FontWeight.w500,
                      color: DiaryStyle.primaryTextColor(context),
                    ),
                  ),
                  IconButton(
                    icon: Icon(Icons.close, size: 20, color: DiaryStyle.secondaryTextColor(context)),
                    onPressed: () => Navigator.pop(context),
                  ),
                ],
              ),
            ),
            Divider(height: 1, thickness: 0.5, color: DiaryStyle.dividerColor(context)),
            Container(
              constraints: BoxConstraints(maxHeight: MediaQuery.of(context).size.height * 0.4),
              child: Obx(() {
                if (controller.tags.isEmpty) {
                  return Padding(
                    padding: const EdgeInsets.all(20),
                    child: Center(
                      child: Text('没有找到标签', style: TextStyle(color: DiaryStyle.secondaryTextColor(context))),
                    ),
                  );
                }

                return ListView.separated(
                  shrinkWrap: true,
                  padding: const EdgeInsets.symmetric(vertical: 10),
                  itemCount: controller.tags.length,
                  separatorBuilder:
                      (context, index) => Divider(
                        height: 1,
                        thickness: 0.5,
                        indent: 20,
                        endIndent: 20,
                        color: DiaryStyle.dividerColor(context),
                      ),
                  itemBuilder: (context, index) {
                    final tag = controller.tags[index];
                    return ListTile(
                      dense: true,
                      title: Text('#$tag', style: TextStyle(fontSize: 15, color: DiaryStyle.primaryTextColor(context))),
                      onTap: () {
                        controller.filterByTag(tag);
                        Navigator.pop(context);
                      },
                    );
                  },
                );
              }),
            ),
            Divider(height: 1, thickness: 0.5, color: DiaryStyle.dividerColor(context)),
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 12),
              child: InkWell(
                onTap: () {
                  controller.clearFilters();
                  Navigator.pop(context);
                },
                child: Container(
                  alignment: Alignment.center,
                  padding: EdgeInsets.symmetric(vertical: 12),
                  decoration: BoxDecoration(
                    color: DiaryStyle.inputBackgroundColor(context),
                    borderRadius: BorderRadius.circular(24),
                  ),
                  child: Text(
                    '清除筛选',
                    style: TextStyle(color: DiaryStyle.primaryTextColor(context), fontWeight: FontWeight.w500),
                  ),
                ),
              ),
            ),
            SizedBox(height: 8),
          ],
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

  /// 格式化日期 - flomo风格
  String _formatDate(DateTime date) {
    final now = DateTime.now();
    final yesterday = DateTime(now.year, now.month, now.day - 1);
    final dateOnly = DateTime(date.year, date.month, date.day);

    if (dateOnly.isAtSameMomentAs(DateTime(now.year, now.month, now.day))) {
      return '今天';
    } else if (dateOnly.isAtSameMomentAs(yesterday)) {
      return '昨天';
    } else {
      return DateFormat('yyyy年MM月dd日').format(date);
    }
  }
}
