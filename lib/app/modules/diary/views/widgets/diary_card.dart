import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'package:flutter_markdown/flutter_markdown.dart';
import 'package:daily_satori/app/models/diary_model.dart';
import 'package:daily_satori/app/styles/diary_style.dart';
import 'dart:io';

/// 单个日记卡片组件 - 支持Markdown和图片
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
                // Markdown渲染的日记内容
                MarkdownBody(
                  data: diary.content,
                  selectable: true,
                  styleSheet: MarkdownStyleSheet(
                    p: TextStyle(fontSize: 15, height: 1.5, color: DiaryStyle.primaryTextColor(context)),
                    h1: TextStyle(
                      fontSize: 20,
                      height: 1.5,
                      fontWeight: FontWeight.bold,
                      color: DiaryStyle.primaryTextColor(context),
                    ),
                    h2: TextStyle(
                      fontSize: 18,
                      height: 1.5,
                      fontWeight: FontWeight.bold,
                      color: DiaryStyle.primaryTextColor(context),
                    ),
                    h3: TextStyle(
                      fontSize: 16,
                      height: 1.5,
                      fontWeight: FontWeight.bold,
                      color: DiaryStyle.primaryTextColor(context),
                    ),
                    blockquote: TextStyle(
                      fontSize: 15,
                      height: 1.5,
                      color: DiaryStyle.secondaryTextColor(context),
                      fontStyle: FontStyle.italic,
                    ),
                    code: TextStyle(
                      fontSize: 14,
                      height: 1.5,
                      color: DiaryStyle.accentColor(context),
                      backgroundColor: DiaryStyle.inputBackgroundColor(context),
                    ),
                  ),
                ),

                // 图片显示
                if (diary.images != null && diary.images!.isNotEmpty) ...[
                  const SizedBox(height: 12),
                  _buildImageGallery(context),
                ],

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

  /// 构建图片画廊
  Widget _buildImageGallery(BuildContext context) {
    final List<String> imagePaths = diary.images!.split(',');

    return Container(
      height: 120,
      child: ListView.builder(
        scrollDirection: Axis.horizontal,
        itemCount: imagePaths.length,
        itemBuilder: (context, index) {
          return Container(
            width: 120,
            height: 120,
            margin: EdgeInsets.only(right: 8),
            decoration: BoxDecoration(
              borderRadius: BorderRadius.circular(8),
              color: DiaryStyle.inputBackgroundColor(context),
            ),
            child: ClipRRect(
              borderRadius: BorderRadius.circular(8),
              child: GestureDetector(
                onTap: () => _showImageFullscreen(context, imagePaths[index]),
                child: Image.file(
                  File(imagePaths[index]),
                  fit: BoxFit.cover,
                  errorBuilder: (context, error, stackTrace) {
                    return Center(child: Icon(Icons.broken_image, color: DiaryStyle.secondaryTextColor(context)));
                  },
                ),
              ),
            ),
          );
        },
      ),
    );
  }

  /// 显示全屏图片
  void _showImageFullscreen(BuildContext context, String imagePath) {
    Navigator.of(context).push(
      MaterialPageRoute(
        builder:
            (context) => Scaffold(
              backgroundColor: Colors.black,
              appBar: AppBar(
                backgroundColor: Colors.black,
                iconTheme: IconThemeData(color: Colors.white),
                elevation: 0,
              ),
              body: Center(
                child: InteractiveViewer(
                  panEnabled: true,
                  boundaryMargin: EdgeInsets.all(20),
                  minScale: 0.5,
                  maxScale: 4,
                  child: Image.file(
                    File(imagePath),
                    fit: BoxFit.contain,
                    errorBuilder: (context, error, stackTrace) {
                      return Center(child: Icon(Icons.broken_image, color: Colors.white, size: 64));
                    },
                  ),
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
