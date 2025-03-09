import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'package:daily_satori/app/models/diary_model.dart';

/// 单个日记卡片组件
class DiaryCard extends StatelessWidget {
  final DiaryModel diary;
  final VoidCallback onDelete;
  final VoidCallback onEdit;

  const DiaryCard({super.key, required this.diary, required this.onDelete, required this.onEdit});

  @override
  Widget build(BuildContext context) {
    return Card(
      margin: const EdgeInsets.only(bottom: 12),
      elevation: 2,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // 日记内容
            Text(diary.content, style: const TextStyle(fontSize: 16, height: 1.5)),
            const SizedBox(height: 12),

            // 底部信息: 标签, 时间等
            Row(
              children: [
                // 标签
                if (diary.tags != null && diary.tags!.isNotEmpty) Expanded(child: _buildTags()),

                // 时间和操作按钮
                Text(_formatTime(diary.createdAt), style: TextStyle(fontSize: 12, color: Colors.grey[600])),
                const SizedBox(width: 8),
                _buildPopupMenu(context),
              ],
            ),
          ],
        ),
      ),
    );
  }

  /// 构建标签列表
  Widget _buildTags() {
    final tagList = diary.tags?.split(',') ?? [];

    return Wrap(
      spacing: 8,
      runSpacing: 4,
      children:
          tagList.map((tag) {
            if (tag.trim().isEmpty) return const SizedBox.shrink();

            return Container(
              padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
              decoration: BoxDecoration(color: Colors.blue[50], borderRadius: BorderRadius.circular(12)),
              child: Text('#${tag.trim()}', style: TextStyle(fontSize: 12, color: Colors.blue[700])),
            );
          }).toList(),
    );
  }

  /// 构建操作菜单
  Widget _buildPopupMenu(BuildContext context) {
    return PopupMenuButton<String>(
      icon: const Icon(Icons.more_vert, size: 16),
      onSelected: (value) {
        if (value == 'edit') {
          onEdit();
        } else if (value == 'delete') {
          _confirmDelete(context);
        }
      },
      itemBuilder:
          (context) => [
            const PopupMenuItem(
              value: 'edit',
              child: Row(children: [Icon(Icons.edit, size: 16), SizedBox(width: 8), Text('编辑')]),
            ),
            const PopupMenuItem(
              value: 'delete',
              child: Row(children: [Icon(Icons.delete, size: 16), SizedBox(width: 8), Text('删除')]),
            ),
          ],
    );
  }

  /// 确认删除对话框
  void _confirmDelete(BuildContext context) {
    showDialog(
      context: context,
      builder:
          (context) => AlertDialog(
            title: const Text('确认删除'),
            content: const Text('确定要删除这条日记吗？此操作不可撤销。'),
            actions: [
              TextButton(onPressed: () => Navigator.pop(context), child: const Text('取消')),
              TextButton(
                onPressed: () {
                  Navigator.pop(context);
                  onDelete();
                },
                child: const Text('删除'),
              ),
            ],
          ),
    );
  }

  /// 格式化时间
  String _formatTime(DateTime dateTime) {
    return DateFormat('HH:mm').format(dateTime);
  }
}
