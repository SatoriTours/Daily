import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'package:flutter_markdown/flutter_markdown.dart';
import 'package:daily_satori/app/models/diary_model.dart';
import 'package:daily_satori/app/styles/diary_style.dart';
import 'package:feather_icons/feather_icons.dart';
import 'dart:io';

import '../../utils/diary_utils.dart';

/// 单个日记卡片组件 - 支持Markdown和图片
class DiaryCard extends StatelessWidget {
  final DiaryModel diary;
  final VoidCallback onDelete;
  final VoidCallback onEdit;

  const DiaryCard({super.key, required this.diary, required this.onDelete, required this.onEdit});

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.only(bottom: 10),
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
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // 顶部时间和更多菜单
              Padding(
                padding: const EdgeInsets.only(left: 16, right: 8, top: 12, bottom: 4),
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    // 时间
                    Text(
                      _formatDateTime(diary.createdAt),
                      style: TextStyle(fontSize: 12, color: DiaryStyle.secondaryTextColor(context)),
                    ),
                    // 更多菜单按钮
                    IconButton(
                      icon: Icon(FeatherIcons.moreHorizontal, size: 18, color: DiaryStyle.secondaryTextColor(context)),
                      padding: EdgeInsets.zero,
                      constraints: BoxConstraints(),
                      splashRadius: 18,
                      onPressed: () => _showOptionsSheet(context),
                    ),
                  ],
                ),
              ),

              // 日记内容区域
              Padding(
                padding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    // Markdown渲染的日记内容
                    MarkdownBody(
                      data: diary.content,
                      selectable: true,
                      styleSheet: DiaryUtils.getMarkdownStyleSheet(context),
                    ),

                    // 图片显示
                    if (diary.images != null && diary.images!.isNotEmpty) ...[
                      const SizedBox(height: 12),
                      _buildImageGallery(context),
                    ],

                    // 标签
                    if (diary.tags != null && diary.tags!.isNotEmpty) ...[
                      const SizedBox(height: 12),
                      _buildTags(context),
                    ],
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  // 构建图片预览
  Widget _buildImageGallery(BuildContext context) {
    final List<String> images = diary.images!.split(',');

    return Container(
      height: 100,
      child: ListView.builder(
        scrollDirection: Axis.horizontal,
        itemCount: images.length,
        itemBuilder: (context, index) {
          final String imagePath = images[index];
          final file = File(imagePath);

          // 检查文件是否存在
          if (!file.existsSync()) {
            return Center(
              child: Container(
                width: 100,
                height: 100,
                margin: EdgeInsets.only(right: 8),
                decoration: BoxDecoration(
                  color: DiaryStyle.tagBackgroundColor(context),
                  borderRadius: BorderRadius.circular(8),
                ),
                child: Icon(FeatherIcons.image, color: DiaryStyle.primaryTextColor(context)),
              ),
            );
          }

          return GestureDetector(
            onTap: () {
              _showFullImage(context, imagePath);
            },
            child: Container(
              width: 100,
              height: 100,
              margin: EdgeInsets.only(right: 8),
              decoration: BoxDecoration(
                borderRadius: BorderRadius.circular(8),
                image: DecorationImage(image: FileImage(file), fit: BoxFit.cover),
              ),
            ),
          );
        },
      ),
    );
  }

  // 构建标签
  Widget _buildTags(BuildContext context) {
    final List<String> tags = diary.tags!.split(',');

    return Wrap(
      spacing: 8,
      runSpacing: 8,
      children:
          tags
              .map(
                (tag) => Container(
                  padding: EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                  decoration: BoxDecoration(
                    color: DiaryStyle.tagBackgroundColor(context),
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Icon(FeatherIcons.hash, size: 12, color: DiaryStyle.secondaryTextColor(context)),
                      SizedBox(width: 4),
                      Text(tag, style: TextStyle(fontSize: 12, color: DiaryStyle.secondaryTextColor(context))),
                    ],
                  ),
                ),
              )
              .toList(),
    );
  }

  // 显示全屏图片
  void _showFullImage(BuildContext context, String imagePath) {
    final file = File(imagePath);
    if (!file.existsSync()) return;

    showDialog(
      context: context,
      builder:
          (context) => Dialog(
            insetPadding: EdgeInsets.zero,
            child: Stack(
              fit: StackFit.expand,
              children: [
                // 图片
                InteractiveViewer(minScale: 0.5, maxScale: 3.0, child: Image.file(file, fit: BoxFit.contain)),
                // 关闭按钮
                Positioned(
                  top: 16,
                  right: 16,
                  child: GestureDetector(
                    onTap: () => Navigator.pop(context),
                    child: Container(
                      padding: EdgeInsets.all(8),
                      decoration: BoxDecoration(color: Colors.black.withOpacity(0.5), shape: BoxShape.circle),
                      child: Icon(Icons.close, color: Colors.white, size: 20),
                    ),
                  ),
                ),
              ],
            ),
          ),
    );
  }

  // 显示删除确认对话框
  void _showDeleteConfirmation(BuildContext context) {
    showDialog(
      context: context,
      builder:
          (context) => AlertDialog(
            title: Text('确认删除'),
            content: Text('你确定要删除这条日记吗？此操作无法撤销。'),
            actions: [
              TextButton(onPressed: () => Navigator.pop(context), child: Text('取消')),
              TextButton(
                onPressed: () {
                  Navigator.pop(context);
                  onDelete();
                },
                child: Text('删除', style: TextStyle(color: Colors.red)),
              ),
            ],
          ),
    );
  }

  // 显示操作底部弹窗
  void _showOptionsSheet(BuildContext context) {
    showModalBottomSheet(
      context: context,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.vertical(top: Radius.circular(16))),
      builder: (context) {
        return Container(
          padding: EdgeInsets.symmetric(vertical: 20),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              ListTile(
                leading: Icon(FeatherIcons.edit2, color: DiaryStyle.accentColor(context)),
                title: Text('编辑日记'),
                onTap: () {
                  Navigator.pop(context);
                  onEdit();
                },
              ),
              ListTile(
                leading: Icon(FeatherIcons.trash2, color: Colors.red),
                title: Text('删除日记', style: TextStyle(color: Colors.red)),
                onTap: () {
                  Navigator.pop(context);
                  _showDeleteConfirmation(context);
                },
              ),
            ],
          ),
        );
      },
    );
  }

  // 格式化日期和时间 "yyyy-MM-dd HH:mm:ss"
  String _formatDateTime(DateTime dateTime) {
    return DateFormat('yyyy-MM-dd HH:mm:ss').format(dateTime);
  }
}
