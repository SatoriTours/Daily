import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'package:daily_satori/app/models/diary_model.dart';
import 'package:daily_satori/app/styles/diary_style.dart';

/// 单个日记卡片组件 - 支持深色/浅色主题
class DiaryCard extends StatelessWidget {
  final DiaryModel diary;
  final VoidCallback onDelete;
  final VoidCallback onEdit;

  const DiaryCard({super.key, required this.diary, required this.onDelete, required this.onEdit});

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.only(bottom: 12),
      decoration: BoxDecoration(
        color: DiaryStyle.cardColor(context),
        borderRadius: BorderRadius.circular(12),
        boxShadow: DiaryStyle.cardShadow(context),
      ),
      child: Material(
        color: Colors.transparent,
        borderRadius: BorderRadius.circular(12),
        clipBehavior: Clip.antiAlias,
        child: InkWell(
          onLongPress: () {
            _showOptionsSheet(context);
          },
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                // 日记内容
                Text(
                  diary.content,
                  style: TextStyle(fontSize: 15, height: 1.5, color: DiaryStyle.primaryTextColor(context)),
                ),

                // 标签和时间
                if (diary.tags != null && diary.tags!.isNotEmpty) ...[const SizedBox(height: 12), _buildTags(context)],

                const SizedBox(height: 8),

                // 时间
                Align(
                  alignment: Alignment.bottomRight,
                  child: Text(
                    _formatTime(diary.createdAt),
                    style: TextStyle(fontSize: 12, color: DiaryStyle.timeTextColor(context)),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  /// 构建标签列表 - 支持主题
  Widget _buildTags(BuildContext context) {
    final tagList = diary.tags?.split(',') ?? [];

    return Wrap(
      spacing: 8,
      runSpacing: 4,
      children:
          tagList.map((tag) {
            if (tag.trim().isEmpty) return const SizedBox.shrink();

            return Container(
              padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
              decoration: BoxDecoration(
                color: DiaryStyle.tagBackgroundColor(context),
                borderRadius: BorderRadius.circular(12),
              ),
              child: Text('#${tag.trim()}', style: TextStyle(fontSize: 12, color: DiaryStyle.tagTextColor(context))),
            );
          }).toList(),
    );
  }

  /// 显示操作选项 - 支持主题
  void _showOptionsSheet(BuildContext context) {
    showModalBottomSheet(
      context: context,
      backgroundColor: DiaryStyle.bottomSheetColor(context),
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.vertical(top: Radius.circular(16))),
      builder: (context) {
        return SafeArea(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              ListTile(
                leading: Icon(Icons.edit, color: DiaryStyle.accentColor(context)),
                title: Text('编辑', style: TextStyle(color: DiaryStyle.primaryTextColor(context))),
                onTap: () {
                  Navigator.pop(context);
                  onEdit();
                },
              ),
              Divider(height: 0.5, thickness: 0.5, indent: 16, endIndent: 16, color: DiaryStyle.dividerColor(context)),
              ListTile(
                leading: Icon(
                  Icons.delete,
                  color: Colors.red[Theme.of(context).brightness == Brightness.dark ? 300 : 600],
                ),
                title: Text('删除', style: TextStyle(color: DiaryStyle.primaryTextColor(context))),
                onTap: () {
                  Navigator.pop(context);
                  _confirmDelete(context);
                },
              ),
              SizedBox(height: 8),
            ],
          ),
        );
      },
    );
  }

  /// 确认删除对话框 - 支持主题
  void _confirmDelete(BuildContext context) {
    showDialog(
      context: context,
      builder:
          (context) => AlertDialog(
            title: Text('删除确认', style: TextStyle(color: DiaryStyle.primaryTextColor(context))),
            content: Text('确定要删除这条记录吗？', style: TextStyle(color: DiaryStyle.secondaryTextColor(context))),
            backgroundColor: DiaryStyle.bottomSheetColor(context),
            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
            actions: [
              TextButton(
                onPressed: () => Navigator.pop(context),
                child: Text('取消', style: TextStyle(color: DiaryStyle.secondaryTextColor(context))),
              ),
              TextButton(
                onPressed: () {
                  Navigator.pop(context);
                  onDelete();
                },
                child: Text(
                  '删除',
                  style: TextStyle(color: Colors.red[Theme.of(context).brightness == Brightness.dark ? 300 : 600]),
                ),
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
